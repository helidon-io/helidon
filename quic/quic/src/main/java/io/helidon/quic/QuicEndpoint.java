/*
 * Copyright (c) 2021, 2026 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.helidon.quic;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.IllegalFormatException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;

import io.helidon.common.Api;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.socket.SocketContext;
import io.helidon.quic.QuicSelector.QuicNioSelector;
import io.helidon.quic.QuicSelector.QuicVirtualThreadPoller;
import io.helidon.quic.packet.QuicPacket.HeadersType;
import io.helidon.quic.packet.QuicPacketDecoder;

import static io.helidon.quic.QuicCloseCommand.silent;
import static io.helidon.quic.QuicEndpoint.ChannelType.BLOCKING_WITH_VIRTUAL_THREADS;
import static io.helidon.quic.QuicEndpoint.ChannelType.NON_BLOCKING_WITH_SELECTOR;

/**
 * A QUIC Endpoint. A QUIC endpoint encapsulate a DatagramChannel
 * and is registered with a Selector. It subscribes for read and
 * write events from the selector, and implements a readLoop and
 * a writeLoop.
 * <p>
 * The read event or write event are triggered by the selector
 * thread. When the read event is triggered, all available datagrams
 * are read from the channel and pushed into a read queue.
 * Then the readLoop is triggered.
 * When the write event is triggered, the key interestOps are
 * modified to pause write events, and the writeLoop is triggered.
 * <p>
 * The readLoop and writeLoop should never execute on the selector
 * thread, but rather, in the client's executor.
 * <p>
 * When the writeLoop is triggered, it polls the writeQueue and
 * writes as many datagram as it can to the channel. At the end,
 * if there still remains some datagrams in the writeQueue, the
 * write event is resumed. Otherwise, the writeLoop is next
 * triggered when new datagrams are added to the writeQueue.
 * <p>
 * When the readLoop is triggered, it polls the read queue
 * and attempts to match each received packet with a
 * QuicConnection. If no connection matches, it attempts
 * to match the packet with stateless reset tokens.
 * If no stateless reset token match, the packet is
 * discarded.
 */
@Api.Internal
public abstract sealed class QuicEndpoint implements AutoCloseable
        permits QuicEndpoint.QuicSelectableEndpoint, QuicEndpoint.QuicVirtualThreadedEndpoint {
    private static final System.Logger LOGGER = System.getLogger(QuicEndpoint.class.getName());

    private final Executor executor;
    private final QuicInboundQueue<Datagram> readQueue;
    private final ConcurrentLinkedQueue<QuicDatagram> writeQueue = new ConcurrentLinkedQueue<>();
    private final QuicTimerQueue timerQueue;
    private final QuicInstance quicInstance;
    private final String name;
    private final DatagramChannel channel;
    private final String channelId;
    private final ByteBuffer receiveBuffer;
    private final int maxUdpPayloadSize;
    private final boolean datagramSendAsync;
    private final int maxBufferedHigh;
    private final int maxBufferedLow;
    private final boolean unsafeRawData;
    private final ReentrantLock closeLock = new ReentrantLock();
    private final ReentrantLock routeLock = new ReentrantLock();
    private QuicDatagram queuedWriteInFlight;
    private final AtomicInteger activeChannelWrites = new AtomicInteger();
    // A ConcurrentMap to store registered connections.
    // The connection IDs might come from external sources. They implement Comparable
    // to mitigate collision attacks.
    // This map must not share the idFactory with other maps,
    // see RFC 9000 section 21.11. Stateless Reset Oracle
    private final ConcurrentMap<QuicConnectionId, QuicPacketReceiver> connections =
            new ConcurrentHashMap<>();
    // a factory of local connection IDs.
    private final QuicConnectionIdFactory idFactory;
    // Key used to encrypt tokens before storing in {@link #peerIssuedResetTokens}
    private final Key tokenEncryptionKey;
    // keeps a link of the peer issued stateless reset token to the corresponding connection that
    // will be closed if the specific stateless reset token is received
    private final ConcurrentMap<PeerIssuedResetToken, QuicPacketReceiver> peerIssuedResetTokens =
            new ConcurrentHashMap<>();
    private final AtomicInteger buffered = new AtomicInteger();
    // A synchronous scheduler to consume the readQueue list;
    private final SequentialScheduler readLoopScheduler =
            SequentialScheduler.lockingScheduler(this::readLoop);
    // A synchronous scheduler to consume the writeQueue list;
    private final SequentialScheduler writeLoopScheduler =
            SequentialScheduler.lockingScheduler(this::writeLoop);

    private volatile boolean readingStalled;
    private volatile boolean expectExceptions;
    private volatile boolean closed;

    private QuicEndpoint(QuicInstance quicInstance,
                         QuicRuntimeConfig runtimeConfig,
                         DatagramChannel channel,
                         String name,
                         QuicTimerQueue timerQueue) {
        QuicConfig quicConfig = runtimeConfig.userConfig();
        this.quicInstance = quicInstance;
        this.name = name;
        this.channel = channel;
        this.channelId = identityTag(channel);
        this.maxUdpPayloadSize = quicConfig.maxUdpPayloadSize();
        this.datagramSendAsync = runtimeConfig.endpoint().sendAsync();
        this.maxBufferedHigh = runtimeConfig.endpoint().maxBufferedHigh();
        this.maxBufferedLow = runtimeConfig.endpoint().maxBufferedLow();
        this.unsafeRawData = quicConfig.unsafeRawData();
        this.receiveBuffer = ByteBuffer.allocateDirect(maxUdpPayloadSize);
        this.executor = quicInstance.executor();
        this.timerQueue = timerQueue;
        this.readQueue = new QuicInboundQueue<>(datagram -> datagram.payload().remaining(),
                                                this::buffer,
                                                this::unbuffer);
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            log(System.Logger.Level.DEBUG, "created for %s", channel);
        }
        try {
            KeyGenerator kg = KeyGenerator.getInstance("AES");
            tokenEncryptionKey = kg.generateKey();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("AES key generator not available", e);
        }
        idFactory = isServer()
                ? QuicConnectionIdFactory.server()
                : QuicConnectionIdFactory.client();
    }

    /**
     * Registers the given endpoint with the given selector.
     * <p>
     * An endpoint of class {@link QuicSelectableEndpoint} is only
     * compatible with a selector of type {@link QuicNioSelector}.
     * An endpoint of tyoe {@link QuicVirtualThreadedEndpoint} is only
     * compatible with a selector of type {@link QuicVirtualThreadPoller}.
     * <br>
     * If the given endpoint implementation is not compatible with
     * the given selector implementation an {@link IllegalStateException}
     * is thrown.
     *
     * @param endpoint the endpoint
     * @param selector the selector
     * @throws UncheckedIOException  if selector registration fails
     * @throws IllegalStateException if the endpoint and selector implementations
     *                               are not compatible
     */
    public static void registerWithSelector(QuicEndpoint endpoint, QuicSelector<?> selector) {
        if (selector instanceof QuicVirtualThreadPoller poller) {
            var loopingEndpoint = (QuicVirtualThreadedEndpoint) endpoint;
            poller.register(loopingEndpoint);
        } else if (selector instanceof QuicNioSelector selectable) {
            var selectableEndpoint = (QuicEndpoint.QuicSelectableEndpoint) endpoint;
            selectable.register(selectableEndpoint);
        } else {
            throw new IllegalStateException("Incompatible selector and endpoint implementations: %s <-> %s"
                                                    .formatted(selector.getClass(), endpoint.getClass()));
        }
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            endpoint.log(System.Logger.Level.DEBUG, "endpoint registered with selector");
        }
    }

    /**
     * Returns the connection-ID factory used by this endpoint.
     *
     * @return the connection-ID factory used by this endpoint.
     */
    public QuicConnectionIdFactory idFactory() {
        return idFactory;
    }

    /**
     * Increases the current buffered-byte count.
     *
     * @param bytes number of newly buffered bytes
     * @return updated buffered-byte count
     */
    public int buffer(int bytes) {
        return buffered.addAndGet(bytes);
    }

    /**
     * Decreases the current buffered-byte count.
     *
     * @param bytes number of bytes released from buffering
     * @return updated buffered-byte count
     */
    public int unbuffer(int bytes) {
        var newval = buffered.addAndGet(-bytes);
        if (newval <= maxBufferedLow && !closed) {
            resumeReading();
        }
        return newval;
    }

    /**
     * Returns the number of buffered bytes currently tracked by this endpoint.
     *
     * @return the number of buffered bytes currently tracked by this endpoint.
     */
    public int buffered() {
        return buffered.get();
    }

    /**
     * Returns configured endpoint name.
     *
     * @return configured endpoint name.
     */
    public String name() {
        return name;
    }

    /**
     * Returns datagram channel owned by this endpoint.
     *
     * @return datagram channel owned by this endpoint.
     */
    public DatagramChannel channel() {
        return channel;
    }

    /**
     * Returns the local socket address currently bound to the datagram channel.
     *
     * @return local socket address
     * @throws UncheckedIOException if querying the channel local address fails
     */
    public SocketAddress localAddress() {
        try {
            return channel.getLocalAddress();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to query QUIC endpoint local address", e);
        }
    }

    /**
     * Returns a text form of the local address, or a fallback message when unavailable.
     *
     * @return local address text
     */
    public String localAddressString() {
        try {
            return Utils.socketAddressText(channel.getLocalAddress());
        } catch (IOException io) {
            return "No address available";
        }
    }

    /**
     * Detach the channel from the selector implementation.
     */
    public abstract void detach();

    @Override
    public void close() {
        if (closed) {
            return;
        }
        List<QuicDatagram> droppedDatagrams = new ArrayList<>();
        boolean skipConnectionClose;
        closeLock.lock();
        try {
            if (closed) {
                return;
            }
            closed = true;
            readQueue.closeAndDrain();
            skipConnectionClose = activeChannelWrites.get() != 0 || queuedWriteInFlight != null;
            if (queuedWriteInFlight == null) {
                drainWriteQueue(droppedDatagrams);
            }
        } finally {
            closeLock.unlock();
        }
        awaitRoutePublications();
        try {
            while (!connections.isEmpty()) {
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    log(System.Logger.Level.DEBUG, "closing %d connections", connections.size());
                }
                Set<QuicConnectionImpl> connCloseSent = new HashSet<>();
                for (var cid : connections.keySet()) {
                    // endpoint is closing, so (on a best-effort basis) we send out a datagram
                    // containing a QUIC packet with a CONNECTION_CLOSE frame to the peer.
                    // Immediately after that, we silently terminate the connection since
                    // there's no point maintaining the connection's infrastructure for
                    // sending (or receiving) additional packets when the endpoint itself
                    // won't be around for dealing with the packets.
                    QuicPacketReceiver rcvr = connections.remove(cid);
                    if (rcvr != null) {
                        removeConnection(rcvr);
                    }
                    if (rcvr instanceof QuicConnectionImpl quicConn) {
                        boolean shouldSendConnClose = connCloseSent.add(quicConn);
                        // send the datagram containing the CONNECTION_CLOSE frame only once
                        // per connection
                        if (shouldSendConnClose && !skipConnectionClose) {
                            sendConnectionCloseQuietly(quicConn);
                        }
                    }
                    silentTerminateConnection(rcvr);
                }
            }
            while (!peerIssuedResetTokens.isEmpty()) {
                for (PeerIssuedResetToken resetToken : peerIssuedResetTokens.keySet()) {
                    QuicPacketReceiver receiver = peerIssuedResetTokens.remove(resetToken);
                    if (receiver != null) {
                        removeConnection(receiver);
                        silentTerminateConnection(receiver);
                    }
                }
            }
        } finally {
            try {
                // Endpoint close is best-effort: CONNECTION_CLOSE is sent once above
                // and the UDP channel is closed without waiting for an ACK.
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    log(System.Logger.Level.DEBUG, "Closing channel " + channel);
                }
                writeLoopScheduler.stop();
                readLoopScheduler.stop();
                dropDatagrams(droppedDatagrams);
                expectExceptions = true;
                detachAndCloseChannel();
            } catch (UncheckedIOException io) {
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    log(System.Logger.Level.DEBUG, "Failed to detach and close channel: " + io);
                }
            }
        }
    }

    /**
     * Aborts the endpoint and all tracked connections due to a fatal runtime error.
     *
     * @param error cause that triggered the abort
     */
    public void abort(Throwable error) {

        if (closed) {
            return;
        }
        List<QuicDatagram> droppedDatagrams = new ArrayList<>();
        closeLock.lock();
        try {
            if (closed) {
                return;
            }
            closed = true;
            readQueue.closeAndDrain();
            if (queuedWriteInFlight == null) {
                drainWriteQueue(droppedDatagrams);
            }
        } finally {
            closeLock.unlock();
        }
        awaitRoutePublications();
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            log(System.Logger.Level.DEBUG, "aborting: " + error);
        }
        writeLoopScheduler.stop();
        readLoopScheduler.stop();
        dropDatagrams(droppedDatagrams);
        try {
            while (!connections.isEmpty()) {
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    log(System.Logger.Level.DEBUG, "closing %d connections", connections.size());
                }
                for (var cid : connections.keySet()) {
                    QuicPacketReceiver receiver = connections.remove(cid);
                    if (receiver != null) {
                        removeConnection(receiver);
                    }
                    abortConnection(receiver, error);
                }
            }
            while (!peerIssuedResetTokens.isEmpty()) {
                for (PeerIssuedResetToken resetToken : peerIssuedResetTokens.keySet()) {
                    QuicPacketReceiver receiver = peerIssuedResetTokens.remove(resetToken);
                    if (receiver != null) {
                        removeConnection(receiver);
                        abortConnection(receiver, error);
                    }
                }
            }
        } finally {
            try {
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    log(System.Logger.Level.DEBUG, "Closing channel " + channel);
                }
                detachAndCloseChannel();
            } catch (UncheckedIOException io) {
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    log(System.Logger.Level.DEBUG, "Failed to detach and close channel: " + io);
                }
            }
        }
    }

    @Override
    public String toString() {
        return name;
    }

    /**
     * Schedule a datagram for writing to the underlying channel.
     * If any datagram is pending the given datagram is appended
     * to the list of pending datagrams for writing.
     *
     * @param source      the source connection
     * @param destination the destination address
     * @param payload     the encrypted datagram
     */
    public void pushDatagram(QuicPacketReceiver source, SocketAddress destination, ByteBuffer payload) {
        if (source instanceof QuicConnectionImpl connection
                && destination instanceof InetSocketAddress inetDestination) {
            Optional<QuicPathManager.SendPermit> reservation =
                    connection.pathManager().reserve(inetDestination, payload.remaining());
            if (reservation.isEmpty() || reservation.orElseThrow().size() < payload.remaining()) {
                reservation.ifPresent(QuicPathManager.SendPermit::release);
                source.datagramDropped(QuicDatagram.create(source, destination, payload));
                return;
            }
            pushDatagram(source, destination, payload, reservation.orElseThrow());
            return;
        }
        pushDatagram(source, destination, payload, null);
    }

    void pushDatagram(QuicPacketReceiver source,
                      SocketAddress destination,
                      ByteBuffer payload,
                      QuicPathManager.SendPermit permit) {
        int tosend = payload.remaining();
        String logTag = receiverTag(source, this);
        logDebug(logTag, "attempting to send datagram [%s bytes]", tosend);
        var datagram = QuicDatagram.create(source, destination, payload, permit);
        if (closed) {
            datagram.releasePermit();
            source.datagramDropped(datagram);
            return;
        }
        int sent;
        try {
            // if DGRAM_SEND_ASYNC is true we don't attempt to send from the current
            // thread but push the datagram on the queue and invoke the write loop.
            sent = forceSendAsync() ? 0 : sendDatagram(datagram);
        } catch (UncheckedIOException io) {
            onSendError(datagram, tosend, io);
            return;
        } catch (Throwable failure) {
            rethrow(discardDatagram(datagram, failure));
            return;
        }
        if (sent > 0) {
            completeDatagram(datagram, tosend, sent);
        } else if (sent < 0) {
            datagram.releasePermit();
            source.datagramDropped(datagram);
        } else if (tosend == payload.remaining()) {
            boolean queued;
            closeLock.lock();
            try {
                queued = !closed;
                if (queued) {
                    writeQueue.add(datagram);
                }
            } finally {
                closeLock.unlock();
            }
            if (queued) {
                logDebug(logTag, "datagram [%s bytes] added to write queue, queue size %s",
                         tosend, writeQueue.size());
                try {
                    writeLoopScheduler.runOrSchedule(writeLoopExecutor());
                } catch (Throwable failure) {
                    try {
                        quicInstance.runtimeFailed(failure);
                    } catch (Throwable runtimeFailure) {
                        failure.addSuppressed(runtimeFailure);
                    }
                    try {
                        abort(failure);
                    } catch (Throwable abortFailure) {
                        failure.addSuppressed(abortFailure);
                    }
                    rethrow(failure);
                }
            } else {
                datagram.releasePermit();
                source.datagramDropped(datagram);
            }
        } else {
            datagram.releasePermit();
            source.datagramDropped(datagram);
            logDebug(logTag, "datagram [%s bytes] dropped: payload partially consumed, remaining %s",
                     tosend, payload.remaining());
        }
    }

    /**
     * Called to schedule sending of a datagram that contains a {@code ConnectionCloseFrame}.
     * This will replace the {@link QuicConnectionImpl} with a {@link ClosedConnection} that
     * will replay the datagram containing the  {@code ConnectionCloseFrame} whenever a packet
     * for that connection is received.
     *
     * @param connection  the connection being closed
     * @param destination the peer address
     * @param datagram    the datagram
     */
    public void pushClosingDatagram(QuicConnectionImpl connection, InetSocketAddress destination, ByteBuffer datagram) {
        Optional<QuicPathManager.SendPermit> reservation =
                reservePathDatagram(connection, destination, datagram.remaining());
        if (reservation.isEmpty()) {
            closing(connection, datagram.slice(), connection.pathManager().closingPath());
            connection.datagramDropped(QuicDatagram.create(connection, destination, datagram));
            return;
        }
        pushClosingDatagram(connection, destination, datagram, reservation.orElseThrow());
    }

    void pushClosingDatagram(QuicConnectionImpl connection,
                             InetSocketAddress destination,
                             ByteBuffer datagram,
                             QuicPathManager.SendPermit permit) {
        Objects.requireNonNull(permit, "permit");
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            log(System.Logger.Level.DEBUG, "Pushing closing datagram for " + connection.logTag());
        }
        closing(connection, datagram.slice(), connection.pathManager().closingPath());
        pushDatagram(connection, destination, datagram, permit);
    }

    /**
     * Called to schedule sending of a datagram that contains a single {@code ConnectionCloseFrame}
     * sent in response to a {@code ConnectionClose} frame.
     * This will replace the {@link QuicConnectionImpl} with a {@link DrainingConnection} that
     * will discard all incoming packets.
     *
     * @param connection  the connection being closed
     * @param destination the peer address
     * @param datagram    the datagram
     */
    public void pushClosedDatagram(QuicConnectionImpl connection,
                                   InetSocketAddress destination,
                                   ByteBuffer datagram) {
        Optional<QuicPathManager.SendPermit> reservation =
                reservePathDatagram(connection, destination, datagram.remaining());
        if (reservation.isEmpty()) {
            draining(connection);
            connection.datagramDropped(QuicDatagram.create(connection, destination, datagram));
            return;
        }
        pushClosedDatagram(connection, destination, datagram, reservation.orElseThrow());
    }

    void pushClosedDatagram(QuicConnectionImpl connection,
                            InetSocketAddress destination,
                            ByteBuffer datagram,
                            QuicPathManager.SendPermit permit) {
        Objects.requireNonNull(permit, "permit");
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            log(System.Logger.Level.DEBUG, "Pushing closed datagram for " + connection.logTag());
        }
        draining(connection);
        pushDatagram(connection, destination, datagram, permit);
    }

    private Optional<QuicPathManager.SendPermit> reservePathDatagram(QuicConnectionImpl connection,
                                                                     InetSocketAddress destination,
                                                                     int size) {
        Optional<QuicPathManager.SendPermit> reservation = connection.pathManager().reserve(destination, size);
        if (reservation.isPresent() && reservation.orElseThrow().size() < size) {
            reservation.orElseThrow().release();
            return Optional.empty();
        }
        return reservation;
    }

    /**
     * Add the cid to connection mapping to the endpoint.
     *
     * @param cid        the connection ID to be added
     * @param connection the connection that should be mapped to the cid
     * @return true if connection ID was added, false otherwise
     */
    boolean addConnectionId(QuicConnectionId cid, QuicPacketReceiver connection) {
        routeLock.lock();
        try {
            QuicEndpointRouteLifecycle routeLifecycle = routeLifecycle(connection);
            if (closed || (routeLifecycle != null && !routeLifecycle.isOwner(connection))) {
                return false;
            }
            var old = connections.putIfAbsent(cid, connection);
            if (old == null && routeLifecycle != null) {
                routeLifecycle.connectionIdAdded(connection);
            }
            return old == null;
        } finally {
            routeLock.unlock();
        }
    }

    /**
     * Remove the cid to connection mapping from the endpoint.
     *
     * @param cid        the connection ID to be removed
     * @param connection the connection that is mapped to the cid
     * @return true if connection ID was removed, false otherwise
     */
    public boolean removeConnectionId(QuicConnectionId cid, QuicPacketReceiver connection) {
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            log(System.Logger.Level.DEBUG, "removing connection ID " + cid);
        }
        routeLock.lock();
        try {
            if (closed) {
                return false;
            }
            if (connections.get(cid) != connection) {
                return false;
            }
            QuicEndpointRouteLifecycle routeLifecycle = routeLifecycle(connection);
            if (routeLifecycle != null && !routeLifecycle.retireConnectionId(connection)) {
                return false;
            }
            return connections.remove(cid, connection);
        } finally {
            routeLock.unlock();
        }
    }

    /**
     * Moves the connection into draining state mappings.
     *
     * @param connection connection to remap as draining
     */
    public void draining(QuicConnectionImpl connection) {
        QuicPathManager.ClosingPath closingPath = null;
        QuicEndpointRouteLifecycle routeLifecycle = connection.routeLifecycle();
        routeLock.lock();
        try {
            if (routeLifecycle.owner() instanceof ClosedConnection closedConnection) {
                closingPath = closedConnection.closingPath();
            }
        } finally {
            routeLock.unlock();
        }
        if (closingPath == null) {
            closingPath = connection.pathManager().closingPath();
        }
        long idleTimeout = connection.peerPtoMs() * 3; // 3 PTO
        QuicConnectionImpl.EndpointRoutes routes = connection.freezeEndpointRoutes();
        List<QuicConnectionId> connectionIds = routes.connectionIds();
        List<QuicPacketReceiver.PeerResetToken> resetTokens = routes.resetTokens();
        List<PeerIssuedResetToken> resetTokenKeys = routeResetTokens(resetTokens);
        DrainingConnection draining = null;
        QuicPacketReceiver ownerToRemove = null;
        QuicPacketReceiver transferredFrom = null;
        Throwable transferFailure = null;
        boolean completeFailedRemoval = false;
        boolean startTimer = false;
        routeLock.lock();
        try {
            QuicPacketReceiver currentOwner = routeLifecycle.owner();
            if (closed) {
                ownerToRemove = currentOwner;
            } else if (currentOwner instanceof QuicConnectionImpl
                    || currentOwner instanceof ClosingConnection) {
                QuicPathManager.ClosingPath transferPath = currentOwner instanceof ClosedConnection closedConnection
                        ? closedConnection.closingPath()
                        : closingPath;
                DrainingConnection transfer = new DrainingConnection(connectionIds,
                                                                      resetTokens,
                                                                      idleTimeout,
                                                                      transferPath,
                                                                      routeLifecycle);
                draining = transfer;
                transfer.prepareTimerForPublication();
                if (routeLifecycle.transfer(currentOwner, transfer)) {
                    transferredFrom = currentOwner;
                    try {
                        resetTokenKeys.forEach(resetToken -> peerIssuedResetTokens.replace(resetToken,
                                                                                           currentOwner,
                                                                                           transfer));
                        connectionIds.forEach(connectionId -> connections.replace(connectionId, currentOwner, transfer));
                        startTimer = true;
                    } catch (RuntimeException | Error failure) {
                        transferFailure = failure;
                        completeFailedRemoval = routeLifecycle.beginRemoval(transfer);
                        if (completeFailedRemoval) {
                            rollbackRouteTransfer(currentOwner,
                                                  transfer,
                                                  connectionIds,
                                                  resetTokenKeys,
                                                  failure);
                        }
                    }
                }
            }
        } finally {
            routeLock.unlock();
        }
        if (transferredFrom instanceof ClosedConnection closedConnection) {
            closedConnection.cancelTransferredTimer();
        }
        if (startTimer) {
            try {
                draining.startTimer();
            } catch (RuntimeException | Error failure) {
                transferFailure = failure;
                routeLock.lock();
                try {
                    completeFailedRemoval = routeLifecycle.beginRemoval(draining);
                    if (completeFailedRemoval) {
                        rollbackRouteTransfer(transferredFrom,
                                              draining,
                                              connectionIds,
                                              resetTokenKeys,
                                              failure);
                    }
                } finally {
                    routeLock.unlock();
                }
            }
        }
        if (transferFailure != null) {
            if (completeFailedRemoval) {
                draining.routesRemoved(transferFailure);
            }
            rethrow(transferFailure);
        }
        if (ownerToRemove != null) {
            removeConnection(ownerToRemove);
        }
    }

    /**
     * Registers all current connection IDs of a newly created connection.
     *
     * @param quicConnection connection to register
     * @throws IllegalStateException if the connection routes cannot be registered
     */
    public void registerNewConnection(QuicConnectionImpl quicConnection) {
        boolean registered = quicConnection.withStreamDispatchLock(() ->
                quicConnection.withConnectionIdLock(() -> {
                    List<QuicConnectionId> connectionIds = quicConnection.connectionIds();
                    List<QuicConnectionId> distinctConnectionIds = connectionIds.size() <= 1
                            ? connectionIds
                            : List.copyOf(new HashSet<>(connectionIds));
                    routeLock.lock();
                    try {
                        if (closed || !quicConnection.isOpen() || distinctConnectionIds.isEmpty()) {
                            return false;
                        }
                        for (QuicConnectionId connectionId : distinctConnectionIds) {
                            QuicPacketReceiver existing = connections.get(connectionId);
                            if (existing != null && existing != quicConnection) {
                                return false;
                            }
                        }
                        if (!quicConnection.routeLifecycle().register(quicConnection, distinctConnectionIds.size())) {
                            return false;
                        }
                        distinctConnectionIds.forEach((id) -> connections.putIfAbsent(id, quicConnection));
                        return true;
                    } finally {
                        routeLock.unlock();
                    }
                }
        ));
        if (!registered) {
            throw new IllegalStateException("QUIC endpoint cannot register connection routes");
        }
    }

    /**
     * Returns the timer queue associated with this endpoint.
     *
     * @return the timer queue associated with this endpoint.
     */
    public QuicTimerQueue timer() {
        return timerQueue;
    }

    /**
     * Returns whether the underlying datagram channel is closed.
     *
     * @return whether the underlying datagram channel is closed.
     */
    public boolean isChannelClosed() {
        return !channel().isOpen();
    }

    boolean bufferTooBig() {
        return buffered.get() >= maxBufferedHigh;
    }

    boolean readingPaused() {
        return readingStalled;
    }

    /**
     * Updates whether this endpoint has paused reading.
     *
     * @param readingStalled whether reading is paused
     */
    void readingStalled(boolean readingStalled) {
        this.readingStalled = readingStalled;
    }

    final void log(System.Logger.Level level, String format, Object... arguments) {
        if (LOGGER.isLoggable(level)) {
            LOGGER.log(level,
                       () -> decorate(channelId,
                                      arguments.length == 0 ? format : format.formatted(arguments)));
        }
    }

    final void log(System.Logger.Level level, String message, Throwable throwable) {
        if (LOGGER.isLoggable(level)) {
            LOGGER.log(level, decorate(channelId, message), throwable);
        }
    }

    /**
     * Returns whether the write queue has no queued datagrams.
     *
     * @return {@code true} if the write queue is empty
     */
    boolean writeQueueIsEmpty() {
        return writeQueue.isEmpty();
    }

    /**
     * Returns the write-loop scheduler for this endpoint.
     *
     * @return write-loop scheduler
     */
    SequentialScheduler writeLoopScheduler() {
        return writeLoopScheduler;
    }

    abstract void resumeReading();

    abstract void pauseReading();

    String channelId() {
        return channelId;
    }

    String connectionLogTag(Object connection) {
        if (connection instanceof SocketContext context) {
            return context.socketId() + " " + context.childSocketId();
        }
        return channelId + " " + identityTag(connection);
    }

    Executor writeLoopExecutor() {
        return executor;
    }

    int maxUdpPayloadSize() {
        return maxUdpPayloadSize;
    }

    /**
     * Returns the channel strategy used by this endpoint.
     *
     * @return the channel strategy used by this endpoint.
     */
    abstract ChannelType channelType();

    /**
     * Reads available datagrams from the channel into the read queue.
     * <p>
     * We cannot prevent an incoming datagram from being received at this level of the stack. If a datagram is
     * available, we must read it immediately and put it in the read queue.
     * <p>
     * We maintain a counter of the number of bytes currently in the read queue. If that number exceeds a high
     * watermark threshold, we pause reading and stop adding to the queue. As the read queue gets emptied, reading is
     * resumed when a low watermark threshold is crossed in the other direction.
     * <p>
     * At the moment we have a single channel per endpoint, and we use a single endpoint by default. We have a single
     * selector thread, and we copy the data from off-heap to on-heap before adding it to the queue. We can therefore
     * do the reading directly in the selector thread and offload parsing to the read loop on the executor. The read
     * loop in turn resumes reading, if needed, when it crosses the low watermark threshold.
     */
    void channelReadLoop() {
        boolean nonBlocking = channelType() == NON_BLOCKING_WITH_SELECTOR;
        int count;
        var buffer = this.receiveBuffer;
        buffer.clear();
        int initialStart = 1;   // start readloop at first buffer
        // if blocking we want to nudge the scheduler after each read since we don't
        // know how much the next receive will take. If non-blocking, we nudge it
        // after three consecutive read.
        int maxBeforeStart = nonBlocking ? 3 : 1; // nudge again after 3 buffers
        int readLoopStarted = initialStart;
        int totalpkt = 0;
        try {
            int sincepkt = 0;
            while (!isClosed() && !readingPaused()) {
                var pos = buffer.position();
                var limit = buffer.limit();
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    log(System.Logger.Level.DEBUG, "receiving with buffer(pos=%s, limit=%s)", pos, limit);
                }

                SocketAddress source = channel.receive(buffer);
                if (source == null) {
                    if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                        log(System.Logger.Level.DEBUG, "nothing to read...");
                    }
                    if (nonBlocking) {
                        break;
                    }
                }

                totalpkt++;
                sincepkt++;
                buffer.flip();
                count = buffer.remaining();
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    log(System.Logger.Level.DEBUG, "received %s bytes from %s", count, source);
                }
                if (count > 0) {
                    // Optimization: add some basic check here to drop the packet here if:
                    // - it is too small, it is not a quic packet we would handle
                    Datagram datagram = matchDatagram(source, buffer);
                    if (datagram == null) {
                        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                            log(System.Logger.Level.DEBUG, "dropping invalid packet for this instance (%s bytes)", count);
                        }
                        buffer.clear();
                        continue;
                    }
                    // at this point buffer has been copied. We only buffer what's
                    // needed.
                    int rcv = datagram.payload().remaining();
                    if (!readQueue.offer(datagram)) {
                        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                            log(System.Logger.Level.DEBUG, "closed: dropping received datagram (%s bytes)", rcv);
                        }
                        buffer.clear();
                        continue;
                    }
                    int buffered = this.buffered.get();
                    if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                        log(System.Logger.Level.DEBUG,
                            "adding %s in read queue from %s, queue size %s, buffered %s, type %s",
                            rcv,
                            source,
                            readQueue.size(),
                            buffered,
                            datagram.getClass().getSimpleName());
                    }
                    buffer.clear();
                    if (--readLoopStarted == 0 || buffered >= maxBufferedHigh) {
                        readLoopStarted = maxBeforeStart;
                        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                            log(System.Logger.Level.DEBUG, "triggering readLoop");
                        }
                        try {
                            readLoopScheduler.runOrSchedule(executor);
                        } catch (RuntimeException | Error schedulingFailure) {
                            readQueue.removeAndRelease(datagram);
                            throw schedulingFailure;
                        }
                        sincepkt = processPendingReadEvents(nonBlocking, totalpkt, sincepkt);
                    }
                    // check buffered.get() directly as it may have
                    // been decremented by the read loop already
                    if (this.buffered.get() >= maxBufferedHigh) {
                        // we passed the high watermark, let's pause reading.
                        // the read loop should already have been kicked
                        // of above, or will be below when we exit the while
                        // loop
                        pauseReading();
                    }
                } else {
                    if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                        log(System.Logger.Level.DEBUG, "Dropped empty datagram");
                    }
                }
            }
            // trigger code that will process the received
            // datagrams asynchronously
            // => Use a sequential scheduler, making sure it never
            //    runs on this thread.
            if (!readQueue.isEmpty() && readLoopStarted != maxBeforeStart) {
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    log(System.Logger.Level.DEBUG, "triggering readLoop: queue size " + readQueue.size());
                }
                readLoopScheduler.runOrSchedule(executor);
            }
        } catch (Throwable t) {
            Throwable failure = t;
            try {
                throw failure;
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                onReadError(interrupted);
            } catch (Throwable error) {
                onReadError(error);
            }
        } finally {
            if (nonBlocking && LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                log(System.Logger.Level.DEBUG, "channelReadLoop totalpkt:%s", totalpkt);
            }
        }
    }

    void writeLoop() {
        try {
            writeLoop0();
        } catch (Throwable error) {
            if (!expectExceptions && !closed) {
                log(System.Logger.Level.ERROR, "Failed to write to channel", error);
                quicInstance.runtimeFailed(error);
                abort(error);
            }
        }
    }

    int sendDatagram(QuicDatagram datagram) {
        int sent;
        var payload = datagram.payload();
        var tosend = payload.remaining();
        var dest = datagram.address();
        String logTag = receiverTag(datagram.connection(), this);
        logDebug(logTag, "sending datagram(%d) to %s", tosend, dest);
        logDatagram(unsafeRawData, "send", logTag, dest, payload);
        sent = send(payload, dest, false);
        if (sent < 0) {
            logDebug(logTag, "endpoint or channel closed; skipping sending of datagram(%d) to %s", tosend, dest);
            return sent;
        }
        logDebug(logTag, "sent %d bytes to %s", sent, dest);
        return sent;
    }

    int sendStatelessDatagram(SocketAddress destination, ByteBuffer datagram) {
        int bytes = datagram.remaining();
        logDatagram(unsafeRawData, "send", channelId(), destination, datagram);
        int sent = send(datagram, destination, false);
        if (sent < 0 && LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            log(System.Logger.Level.DEBUG,
                "endpoint or channel closed; skipping stateless datagram(%d) to %s",
                bytes,
                destination);
        }
        return sent;
    }

    void completeDatagram(QuicDatagram datagram, int requestedBytes, int sentBytes) {
        datagram.commitPermit(sentBytes);
        if (datagram.connection != null) {
            if (sentBytes == requestedBytes) {
                datagram.connection.datagramSent(datagram);
            } else {
                datagram.connection.datagramDropped(datagram);
            }
        }
    }

    void onSendError(QuicDatagram datagram, int tosend, UncheckedIOException failure) {
        // close the connection this came from?
        // close all the connections whose destination is that address?
        var connection = datagram.connection();
        var dest = datagram.address();
        IOException cause = failure.getCause();
        String msg = cause.getMessage();
        if (msg != null && msg.contains("too big")) {
            int max = -1;
            if (connection instanceof QuicConnectionImpl cimpl) {
                max = cimpl.maxDatagramSize();
            }
            msg = "Failed to send datagram (%s bytes, max: %s) to %s: %s"
                    .formatted(tosend, max, dest, failure);
            if (connection == null && LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                log(System.Logger.Level.DEBUG, msg);
            }
            failure = new UncheckedIOException(msg, cause);
        }
        if (connection != null) {
            datagram.releasePermit();
            connection.datagramDiscarded(datagram);
            connection.onWriteError(failure);
            if (!channel.isOpen()) {
                quicInstance.runtimeFailed(failure);
                abort(failure);
            }
        }
    }

    void closeWriteQueue(Throwable t) {
        List<QuicDatagram> failedDatagrams = new ArrayList<>();
        closeLock.lock();
        try {
            if (queuedWriteInFlight == null) {
                drainWriteQueue(failedDatagrams);
            }
        } finally {
            closeLock.unlock();
        }
        Throwable failure = null;
        for (QuicDatagram qd : failedDatagrams) {
            if (qd.connection != null) {
                failure = discardDatagram(qd, failure);
                try {
                    qd.connection.onWriteError(t);
                } catch (Throwable callbackFailure) {
                    if (failure == null) {
                        failure = callbackFailure;
                    } else {
                        failure.addSuppressed(callbackFailure);
                    }
                }
            }
        }
        if (failure != null) {
            rethrow(failure);
        }
    }

    private void drainWriteQueue(List<QuicDatagram> datagrams) {
        QuicDatagram datagram;
        while ((datagram = writeQueue.poll()) != null) {
            datagrams.add(datagram);
        }
    }

    private void dropDatagrams(List<QuicDatagram> datagrams) {
        for (QuicDatagram datagram : datagrams) {
            if (datagram.connection != null) {
                Throwable failure = null;
                try {
                    datagram.releasePermit();
                } catch (Throwable releaseFailure) {
                    failure = releaseFailure;
                }
                try {
                    datagram.connection.datagramDropped(datagram);
                } catch (Throwable callbackFailure) {
                    if (failure == null) {
                        failure = callbackFailure;
                    } else {
                        failure.addSuppressed(callbackFailure);
                    }
                }
                if (failure != null && LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    log(System.Logger.Level.DEBUG, "Failed to drop queued datagram", failure);
                }
            }
        }
    }

    private Throwable discardDatagram(QuicDatagram datagram, Throwable failure) {
        try {
            datagram.releasePermit();
        } catch (Throwable releaseFailure) {
            if (failure == null) {
                failure = releaseFailure;
            } else {
                failure.addSuppressed(releaseFailure);
            }
        }
        try {
            datagram.connection.datagramDiscarded(datagram);
        } catch (Throwable callbackFailure) {
            if (failure == null) {
                failure = callbackFailure;
            } else {
                failure.addSuppressed(callbackFailure);
            }
        }
        return failure;
    }

    private static void rethrow(Throwable failure) {
        if (failure instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        throw new IllegalStateException(failure);
    }

    // The readloop is triggered whenever new datagrams are
    // added to the read queue.
    void readLoop() {
        try {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                log(System.Logger.Level.DEBUG, "readLoop");
            }
            Datagram datagram = readQueue.poll();
            while (datagram != null) {
                var payload = datagram.payload();
                var source = datagram.address();
                int remaining = payload.remaining();
                var pos = payload.position();
                readQueue.release(remaining);
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    log(System.Logger.Level.DEBUG,
                        "readLoop: type(%x) %d from %s",
                        payload.hasRemaining() ? payload.get(0) : 0,
                        remaining,
                        source);
                }
                try {
                    if (closed) {
                        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                            log(System.Logger.Level.DEBUG, "closed: ignoring incoming datagram");
                        }
                        datagram = readQueue.poll();
                        continue;
                    }
                    switch (datagram) {
                    case QuicDatagram quicDatagram -> {
                        var connection = quicDatagram.connection();
                        logDatagram(unsafeRawData, "recv", receiverTag(connection, this), source, payload);
                        var headersType = QuicPacketDecoder.peekHeaderType(payload, pos);
                        var destConnId = peekConnectionBytes(headersType, payload);
                        connection.processIncoming(source, destConnId, headersType, payload);
                    }
                    case UnmatchedDatagram _ -> {
                        logDatagram(unsafeRawData, "recv", channelId(), source, payload);
                        var headersType = QuicPacketDecoder.peekHeaderType(payload, pos);
                        unmatchedQuicPacket(datagram, headersType, payload);
                    }
                    case StatelessReset statelessReset -> {
                        var connection = statelessReset.connection();
                        logDatagram(unsafeRawData, "recv", receiverTag(connection, this), source, payload);
                        connection.processStatelessReset();
                    }
                    case SendStatelessReset _ -> {
                        logDatagram(unsafeRawData, "send", channelId(), source, payload);
                        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                            log(System.Logger.Level.DEBUG, "Sending stateless reset to %s", source);
                        }
                        send(payload, source, false);
                    }
                    }

                } catch (RuntimeException failure) {
                    log(System.Logger.Level.ERROR, "Failed to handle datagram", failure);
                }
                datagram = readQueue.poll();
            }
        } catch (RuntimeException failure) {
            onReadError(failure);
        }
    }

    /**
     * Checks if the received datagram contains a stateless reset token;
     * returns the associated connection if true, null otherwise.
     *
     * @param source the sender's address
     * @param buffer datagram contents
     * @return connection associated with the stateless token, or {@code null}
     */
    private QuicPacketReceiver checkStatelessReset(SocketAddress source, ByteBuffer buffer) {
        // We couldn't identify the connection: maybe that's a stateless reset?
        if (closed) {
            return null;
        }
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            log(System.Logger.Level.DEBUG,
                "Check if received datagram could be stateless reset (datagram[%d, %s])",
                buffer.remaining(),
                source);
        }
        if (buffer.remaining() < 21) {
            // too short to be a stateless reset:
            // RFC 9000:
            // Endpoints MUST discard packets that are too small to be valid QUIC packets.
            // To give an example, with the set of AEAD functions defined in [QUIC-TLS],
            // short header packets that are smaller than 21 bytes are never valid.
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                log(System.Logger.Level.DEBUG,
                    "Packet too short for a stateless reset (%s bytes < 21)",
                    buffer.remaining());
            }
            return null;
        }
        byte[] tokenBytes = new byte[16];
        buffer.get(buffer.limit() - 16, tokenBytes);
        var token = new PeerIssuedResetToken(makeToken(tokenBytes), source);
        QuicPacketReceiver connection = peerIssuedResetTokens.get(token);
        if (closed) {
            return null;
        }
        if (connection != null) {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                log(System.Logger.Level.DEBUG,
                    "Received reset token (%s bytes) for connection: %s",
                    tokenBytes.length,
                    connection);
            }
            if (unsafeRawData && LOGGER.isLoggable(System.Logger.Level.TRACE)) {
                log(System.Logger.Level.TRACE,
                    "UNSAFE raw reset token for connection %s: %s",
                    connection,
                    HexFormat.of().formatHex(tokenBytes));
            }
        } else {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                log(System.Logger.Level.DEBUG, "Not a stateless reset");
            }
        }
        return connection;
    }

    /**
     * Called when parsing a quic packet that couldn't be matched to any registered
     * connection.
     *
     * @param datagram    The datagram containing the packet
     * @param headersType The quic packet type
     * @param buffer      The complete datagram payload, positioned at the start of its
     *                    first unmatched quic packet. The payload may contain more
     *                    coalesced quic packets.
     */
    protected void unmatchedQuicPacket(Datagram datagram,
                                       HeadersType headersType,
                                       ByteBuffer buffer) {
        QuicInstance instance = quicInstance;
        if (closed) {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                log(System.Logger.Level.DEBUG, "closed: ignoring unmatched datagram");
            }
            return;
        }

        var address = datagram.address();
        if (isServer() && headersType == HeadersType.LONG && hasAvailableVersion(buffer)) {
            // long packets need to be rematched here for servers.
            // we read packets in one thread and process them here in
            // a different thread:
            // the connection may have been added later on when processing
            // a previous long packet in this thread, so we need to
            // check the connection map again here.
            var idbytes = peekConnectionBytes(headersType, buffer);
            var connection = findQuicConnectionFor(address, idbytes, true);
            if (connection != null) {
                // a matching connection was found, this packet is no longer
                // unmatched
                if (connection.accepts(address)) {
                    connection.processIncoming(address, idbytes, headersType, buffer);
                }
                return;
            }
        }

        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            log(System.Logger.Level.DEBUG,
                "Unmatched packet in datagram [%s, %d, %s]",
                headersType,
                buffer.remaining(),
                address);
            log(System.Logger.Level.DEBUG, "Unmatched packet: delegating to instance");
        }
        instance.unmatchedQuicPacket(address, headersType, buffer);
    }

    // Parses the list of active connection
    // Attempts to find one that matches
    // If none match return null
    // Revisit:
    //  if we had an efficient sorted tree where we could locate a connection id
    //  from the idbytes we wouldn't need to use an "unsafe connection id"
    //  quick and dirty solution for now: we use a ConcurrentHashMap and construct
    //  a throw away QuicConnectionId that wrap our mutable idbytes.
    //  This is OK since the classes that may see these bytes are all internal
    //  and won't mutate them.
    QuicPacketReceiver findQuicConnectionFor(SocketAddress peerAddress, ByteBuffer idbytes, boolean longHeaders) {
        if (idbytes == null) {
            return null;
        }
        var cid = idFactory.unsafeConnectionIdFor(idbytes);
        if (cid == null) {
            if (!longHeaders || isClient()) {
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    log(System.Logger.Level.DEBUG,
                        "No connection match for connection ID (%s bytes)",
                        idbytes.remaining());
                }
                if (unsafeRawData && LOGGER.isLoggable(System.Logger.Level.TRACE)) {
                    log(System.Logger.Level.TRACE,
                        "UNSAFE raw unmatched connection ID: %s",
                        Utils.asHexString(idbytes));
                }
                return null;
            }
            // this is a long headers packet and we're the server;
            // the client might still be using the original connection ID
            cid = new PeerConnectionId(idbytes);
        }
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            log(System.Logger.Level.DEBUG, "Looking up QuicConnection for: %s", cid);
        }
        var quicConnection = connections.get(cid);
        return quicConnection;
    }

    // Called in case of RejectedExecutionException, or shutdownNow;
    void abortConnection(QuicPacketReceiver c, Throwable error) {
        try {
            if (c instanceof QuicConnectionImpl connection) {
                connection.terminator().terminate(QuicCloseCommand.transport(error));
            }
        } catch (Throwable t) {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                log(System.Logger.Level.DEBUG, "Failed to close connection %s: %s", c, t);
            }
        } finally {
            if (c != null) {
                c.shutdown();
            }
        }
    }

    boolean isClosed() {
        return closed;
    }

    private void awaitRoutePublications() {
        routeLock.lock();
        try {
            // Wait for a publication that observed the endpoint before close won.
        } finally {
            routeLock.unlock();
        }
    }

    boolean forceSendAsync() {
        return datagramSendAsync || !writeQueue.isEmpty();
    }

    /**
     * This will completely remove the connection from the endpoint. Any subsequent packets
     * directed to this connection from a peer, may end up receiving a stateless reset
     * from this endpoint.
     *
     * @param connection the connection to be removed
     */
    void removeConnection(QuicPacketReceiver connection) {
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            log(System.Logger.Level.DEBUG, "removing connection " + connection);
        }
        List<QuicConnectionId> connectionIds;
        List<QuicPacketReceiver.PeerResetToken> resetTokens;
        if (connection instanceof QuicConnectionImpl quicConnection) {
            QuicConnectionImpl.EndpointRoutes routes = quicConnection.freezeEndpointRoutes();
            connectionIds = routes.connectionIds();
            resetTokens = routes.resetTokens();
        } else {
            connectionIds = connection.connectionIds();
            resetTokens = connection.activeResetTokens();
        }
        QuicEndpointRouteLifecycle routeLifecycle = routeLifecycle(connection);
        List<PeerIssuedResetToken> resetTokenKeys = routeResetTokens(resetTokens);
        boolean completeRemoval = false;
        Throwable removalFailure = null;
        routeLock.lock();
        try {
            if (routeLifecycle != null) {
                completeRemoval = routeLifecycle.beginRemoval(connection);
            }
            connectionIds.forEach(id -> connections.remove(id, connection));
            resetTokenKeys.forEach(resetToken -> peerIssuedResetTokens.remove(resetToken, connection));
        } catch (RuntimeException | Error failure) {
            removalFailure = failure;
        } finally {
            routeLock.unlock();
        }
        if (completeRemoval) {
            if (connection instanceof ClosedConnection closedConnection) {
                closedConnection.routesRemoved(removalFailure);
            } else if (removalFailure == null) {
                routeLifecycle.completeRemoval();
            } else {
                routeLifecycle.completeRemovalExceptionally(removalFailure);
            }
        }
        if (removalFailure instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (removalFailure instanceof Error error) {
            throw error;
        }
    }

    /**
     * Moves the connection into closing state mappings with a replay datagram.
     *
     * @param connection connection to remap as closing
     * @param datagram   datagram containing the closing packet to replay
     */
    protected void closing(QuicConnectionImpl connection, ByteBuffer datagram) {
        closing(connection, datagram, connection.pathManager().closingPath());
    }

    private void closing(QuicConnectionImpl connection,
                         ByteBuffer datagram,
                         QuicPathManager.ClosingPath closingPath) {
        ByteBuffer closingDatagram = ByteBuffer.allocate(datagram.limit());
        closingDatagram.put(datagram.slice());
        closingDatagram.flip();

        long idleTimeout = connection.peerPtoMs() * 3; // 3 PTO
        QuicConnectionImpl.EndpointRoutes routes = connection.freezeEndpointRoutes();
        List<QuicConnectionId> connectionIds = routes.connectionIds();
        List<QuicPacketReceiver.PeerResetToken> resetTokens = routes.resetTokens();
        List<PeerIssuedResetToken> resetTokenKeys = routeResetTokens(resetTokens);
        QuicEndpointRouteLifecycle routeLifecycle = connection.routeLifecycle();
        var closingConnection = new ClosingConnection(connectionIds,
                                                      resetTokens,
                                                      idleTimeout,
                                                      closingDatagram,
                                                      closingPath,
                                                      routeLifecycle);
        QuicPacketReceiver ownerToRemove = null;
        Throwable transferFailure = null;
        boolean completeFailedRemoval = false;
        boolean startTimer = false;
        routeLock.lock();
        try {
            QuicPacketReceiver currentOwner = routeLifecycle.owner();
            if (closed) {
                ownerToRemove = currentOwner;
            } else if (currentOwner == connection) {
                closingConnection.prepareTimerForPublication();
                if (!routeLifecycle.transfer(connection, closingConnection)) {
                    return;
                }
                try {
                    resetTokenKeys.forEach(resetToken -> peerIssuedResetTokens.replace(resetToken,
                                                                                       connection,
                                                                                       closingConnection));
                    connectionIds.forEach(connectionId -> connections.replace(connectionId,
                                                                              connection,
                                                                              closingConnection));
                    startTimer = true;
                } catch (RuntimeException | Error failure) {
                    transferFailure = failure;
                    completeFailedRemoval = routeLifecycle.beginRemoval(closingConnection);
                    if (completeFailedRemoval) {
                        rollbackRouteTransfer(connection,
                                              closingConnection,
                                              connectionIds,
                                              resetTokenKeys,
                                              failure);
                    }
                }
            }
        } finally {
            routeLock.unlock();
        }
        if (startTimer) {
            try {
                closingConnection.startTimer();
            } catch (RuntimeException | Error failure) {
                transferFailure = failure;
                routeLock.lock();
                try {
                    completeFailedRemoval = routeLifecycle.beginRemoval(closingConnection);
                    if (completeFailedRemoval) {
                        rollbackRouteTransfer(connection,
                                              closingConnection,
                                              connectionIds,
                                              resetTokenKeys,
                                              failure);
                    }
                } finally {
                    routeLock.unlock();
                }
            }
        }
        if (transferFailure != null) {
            if (completeFailedRemoval) {
                closingConnection.routesRemoved(transferFailure);
            }
            rethrow(transferFailure);
        }
        if (ownerToRemove != null) {
            removeConnection(ownerToRemove);
        }
    }

    private void rollbackRouteTransfer(QuicPacketReceiver previousOwner,
                                       ClosedConnection transferredOwner,
                                       List<QuicConnectionId> connectionIds,
                                       List<PeerIssuedResetToken> resetTokenKeys,
                                       Throwable transferFailure) {
        try {
            resetTokenKeys.forEach(token -> {
                peerIssuedResetTokens.remove(token, transferredOwner);
                peerIssuedResetTokens.remove(token, previousOwner);
            });
            connectionIds.forEach(connectionId -> {
                connections.remove(connectionId, transferredOwner);
                connections.remove(connectionId, previousOwner);
            });
        } catch (RuntimeException | Error cleanupFailure) {
            transferFailure.addSuppressed(cleanupFailure);
        }
    }

    /**
     * A peer issues a stateless reset token which it can then send to close the connection. This
     * method links the peer issued token against the connection that needs to be closed if/when
     * that stateless reset token arrives in the packet.
     *
     * @param resetToken peer-issued token and its remote address
     * @param connection connection to link the token against
     */
    void associateStatelessResetToken(QuicPacketReceiver.PeerResetToken resetToken,
                                      QuicPacketReceiver connection) {
        Objects.requireNonNull(connection);
        Objects.requireNonNull(resetToken);
        byte[] statelessResetToken = resetToken.token();
        int tokenLength = statelessResetToken.length;
        if (statelessResetToken.length != 16) {
            throw new IllegalArgumentException("Invalid stateless reset token length " + tokenLength);
        }
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            log(System.Logger.Level.DEBUG, "associating stateless reset token with connection %s", connection);
        }
        PeerIssuedResetToken resetTokenKey = peerIssuedResetToken(resetToken);
        routeLock.lock();
        try {
            QuicEndpointRouteLifecycle routeLifecycle = routeLifecycle(connection);
            if (!closed && (routeLifecycle == null || routeLifecycle.isOwner(connection))) {
                this.peerIssuedResetTokens.put(resetTokenKey, connection);
            }
        } finally {
            routeLock.unlock();
        }
    }

    /**
     * Discards the stateless reset token that this endpoint might have previously
     * associated with a connection.
     *
     * @param resetToken stateless reset token and peer address
     * @param connection connection expected to own the token
     */
    void forgetStatelessResetToken(QuicPacketReceiver.PeerResetToken resetToken,
                                   QuicPacketReceiver connection) {
        byte[] statelessResetToken = resetToken.token();
        // just a tiny optimization - we know stateless reset token must be of 16 bytes, if the passed
        // value isn't, then no point doing any more work
        if (statelessResetToken.length != 16) {
            return;
        }
        PeerIssuedResetToken resetTokenKey = peerIssuedResetToken(resetToken);
        routeLock.lock();
        try {
            if (!closed) {
                this.peerIssuedResetTokens.remove(resetTokenKey, connection);
            }
        } finally {
            routeLock.unlock();
        }
    }

    private List<PeerIssuedResetToken> routeResetTokens(List<QuicPacketReceiver.PeerResetToken> resetTokens) {
        if (resetTokens.isEmpty()) {
            return List.of();
        }
        List<PeerIssuedResetToken> result = new ArrayList<>(resetTokens.size());
        resetTokens.forEach(resetToken -> result.add(peerIssuedResetToken(resetToken)));
        return result;
    }

    private PeerIssuedResetToken peerIssuedResetToken(QuicPacketReceiver.PeerResetToken resetToken) {
        return new PeerIssuedResetToken(makeToken(resetToken.token()), resetToken.peerAddress());
    }

    static void logDatagram(boolean unsafeRawData,
                            String direction,
                            String logTag,
                            SocketAddress peer,
                            ByteBuffer payload) {
        boolean debugEnabled = LOGGER.isLoggable(System.Logger.Level.DEBUG);
        boolean traceEnabled = LOGGER.isLoggable(System.Logger.Level.TRACE);
        if (!debugEnabled && !traceEnabled) {
            return;
        }

        ByteBuffer duplicate = payload.duplicate();
        int size = duplicate.remaining();
        String peerDescription = Utils.socketAddressText(peer);
        if (debugEnabled) {
            LOGGER.log(System.Logger.Level.DEBUG,
                       () -> "[%s] %s datagram (%d bytes) %s"
                               .formatted(logTag, direction, size, peerDescription));
        }
        if (unsafeRawData && traceEnabled) {
            byte[] bytes = new byte[size];
            duplicate.get(bytes);
            BufferData bufferData = BufferData.create(bytes);
            LOGGER.log(System.Logger.Level.TRACE,
                       () -> "[%s] UNSAFE raw %s datagram (%d bytes) %s%n%s"
                               .formatted(logTag, direction, size, peerDescription, bufferData.debugDataHex(true)));
        }
    }

    private static String decorate(String logTag, String message) {
        return "[" + logTag + "] " + message;
    }

    private static String formatMessage(String format, Object... params) {
        if (params == null || params.length == 0) {
            return String.valueOf(format);
        }
        try {
            return String.format(Locale.ROOT, format, params);
        } catch (IllegalFormatException _) {
            return format + " " + Arrays.toString(params);
        }
    }

    private static String receiverTag(QuicPacketReceiver receiver, QuicEndpoint endpoint) {
        if (receiver instanceof SocketContext context) {
            return context.socketId() + " " + context.childSocketId();
        }
        return receiver == null ? endpoint.channelId() : endpoint.connectionLogTag(receiver);
    }

    private static String identityTag(Object instance) {
        return "0x" + HexFormat.of().toHexDigits(System.identityHashCode(instance));
    }

    private static Stream<QuicConnectionId> allConnectionIds(QuicPacketReceiver quicConnection) {
        return Stream.concat(quicConnection.connectionIds().stream(), quicConnection.initialConnectionId().stream());
    }

    /**
     * Returns a new {@link QuicEndpoint} of the given {@code endpointType}.
     *
     * @param endpointType the concrete endpoint type, one of {@link QuicSelectableEndpoint
     *                     QuicSelectableEndpoint.class} or {@link QuicVirtualThreadedEndpoint
     *                     QuicVirtualThreadedEndpoint.class}.
     * @param quicInstance  the quic instance
     * @param runtimeConfig runtime configuration
     * @param name          the endpoint name
     * @param bindAddress   the address to bind to
     * @param timerQueue    the timer queue
     * @param <T>           the concrete endpoint type, one of {@link QuicSelectableEndpoint}
     *                      or {@link QuicVirtualThreadedEndpoint}
     * @return a new {@link QuicEndpoint} of the given {@code endpointType}.
     * @throws UncheckedIOException     if endpoint creation or binding fails
     * @throws IllegalArgumentException if the given endpoint type is not one of
     *                                  {@link QuicSelectableEndpoint QuicSelectableEndpoint.class} or
     *                                  {@link QuicVirtualThreadedEndpoint QuicVirtualThreadedEndpoint.class}
     */
    private static <T extends QuicEndpoint> T create(Class<T> endpointType,
                                                     QuicInstance quicInstance,
                                                     QuicRuntimeConfig runtimeConfig,
                                                     String name,
                                                     SocketAddress bindAddress,
                                                     QuicTimerQueue timerQueue) {
        DatagramChannel channel;
        try {
            channel = DatagramChannel.open();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to open QUIC datagram channel", e);
        }
        try {
            // avoid dependency on extnet
            Optional<SocketOption<?>> df = channel.supportedOptions().stream()
                    .filter(o -> "IP_DONTFRAGMENT".equals(o.name())).findFirst();
            if (df.isPresent()) {
                @SuppressWarnings("unchecked")
                var option = (SocketOption<Boolean>) df.get();
                try {
                    channel.setOption(option, true);
                } catch (UnsupportedOperationException | IOException e) {
                    LOGGER.log(System.Logger.Level.DEBUG,
                               "send: IP_DONTFRAGMENT is unavailable for this datagram channel",
                               e);
                }
            }
            if (QuicSelectableEndpoint.class.isAssignableFrom(endpointType)) {
                channel.configureBlocking(false);
            }
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                Utils.configureChannelBuffers(message -> LOGGER.log(System.Logger.Level.DEBUG, message),
                                              channel,
                                              quicInstance.receiveBufferSize(),
                                              quicInstance.sendBufferSize());
            } else {
                Utils.configureChannelBuffers(channel,
                                              quicInstance.receiveBufferSize(),
                                              quicInstance.sendBufferSize());
            }
            channel.bind(bindAddress); // could do that on attach instead?

            if (endpointType.isAssignableFrom(QuicSelectableEndpoint.class)) {
                return endpointType.cast(new QuicSelectableEndpoint(quicInstance,
                                                                     runtimeConfig,
                                                                     channel,
                                                                     name,
                                                                     timerQueue));
            } else if (endpointType.isAssignableFrom(QuicVirtualThreadedEndpoint.class)) {
                return endpointType.cast(new QuicVirtualThreadedEndpoint(quicInstance,
                                                                          runtimeConfig,
                                                                          channel,
                                                                          name,
                                                                          timerQueue));
            } else {
                throw new IllegalArgumentException(endpointType.getName());
            }
        } catch (IOException e) {
            try {
                channel.close();
            } catch (IOException closeFailure) {
                e.addSuppressed(closeFailure);
            }
            throw new UncheckedIOException("Failed to create QUIC endpoint", e);
        } catch (RuntimeException | Error e) {
            try {
                channel.close();
            } catch (IOException closeFailure) {
                e.addSuppressed(closeFailure);
            }
            throw e;
        }
    }

    private int processPendingReadEvents(boolean nonBlocking, int totalpkt, int sincepkt) {
        Deadline pending = timerQueue.pendingScheduledDeadline();
        Deadline now = TimeSource.now();
        if (!nonBlocking || totalpkt <= 1 || !pending.isBefore(now)) {
            return sincepkt;
        }

        // we have read 3 packets, some events are pending, return
        // to the selector to process the event queue
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            log(System.Logger.Level.DEBUG,
                "reschedule needed: %s, totalpkt: %s, sincepkt: %s",
                Utils.debugDeadline(now, pending),
                totalpkt,
                sincepkt);
        }
        timerQueue.processEventsAndReturnNextDeadline(now, executor);
        return 0;
    }

    private void logDebug(String logTag, String format, Object... params) {
        if (!LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            return;
        }
        LOGGER.log(System.Logger.Level.DEBUG, decorate(logTag, formatMessage(format, params)));
    }

    /**
     * Copies one received datagram into exact-size heap storage owned by the queued datagram and its decoded packet graph.
     *
     * @param buffer endpoint receive buffer
     * @return independently owned datagram storage
     */
    private ByteBuffer copyOnHeap(ByteBuffer buffer) {
        ByteBuffer onHeap = ByteBuffer.allocate(buffer.remaining());
        return onHeap.put(buffer).flip();
    }

    /**
     * This method tries to figure out whether the received packet
     * matches a connection, or a stateless reset.
     *
     * @param source the source address
     * @param buffer the incoming datagram payload
     * @return a {@link Datagram} to be processed by the read loop
     *         if a match is found, or null if the datagram can be dropped
     *         immediately
     */
    private Datagram matchDatagram(SocketAddress source, ByteBuffer buffer) {
        HeadersType headersType = QuicPacketDecoder.peekHeaderType(buffer, buffer.position());
        // short header packets whose length is < 21 are never valid
        if (headersType == HeadersType.SHORT && buffer.remaining() < 21) {
            return null;
        }
        boolean serverLongHeaders = headersType == HeadersType.LONG && isServer();
        if (serverLongHeaders && !hasAvailableVersion(buffer)) {
            if (buffer.remaining() < QuicConfigSupport.MINIMUM_DATAGRAM_SIZE) {
                return null;
            }
            return UnmatchedDatagram.create(source, copyOnHeap(buffer));
        }
        ByteBuffer cidbytes = switch (headersType) {
            case LONG, SHORT -> peekConnectionBytes(headersType, buffer);
            default -> null;
        };
        if (cidbytes == null) {
            if (serverLongHeaders && buffer.remaining() >= QuicConfigSupport.MINIMUM_DATAGRAM_SIZE) {
                return UnmatchedDatagram.create(source, copyOnHeap(buffer));
            }
            return null;
        }
        int length = cidbytes.remaining();
        if (length > QuicConnectionId.MAX_CONNECTION_ID_LENGTH) {
            if (serverLongHeaders && buffer.remaining() >= QuicConfigSupport.MINIMUM_DATAGRAM_SIZE) {
                return UnmatchedDatagram.create(source, copyOnHeap(buffer));
            }
            return null;
        }
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            log(System.Logger.Level.DEBUG,
                "headers(%s), connectionId(%d), datagram(%d)",
                headersType,
                cidbytes.remaining(),
                buffer.remaining());
        }
        QuicPacketReceiver connection = findQuicConnectionFor(source, cidbytes, headersType == HeadersType.LONG);
        // check stateless reset
        if (connection == null) {
            if (headersType == HeadersType.SHORT) {
                // a short packet may be a stateless reset, or may
                // trigger a stateless reset
                connection = checkStatelessReset(source, buffer);
                if (connection != null) {
                    // We received a stateless reset, process it later in the readLoop
                    return StatelessReset.create(connection, source, copyOnHeap(buffer));
                } else if (buffer.remaining() > 21) {
                    // check if we should send a stateless reset
                    ByteBuffer reset = idFactory.statelessReset(cidbytes, buffer.remaining() - 1);
                    if (reset != null) {
                        // will send stateless reset later from the read loop
                        return SendStatelessReset.create(source, reset);
                    }
                }
                return null; // drop unmatched short packets
            }
            // client can drop all unmatched long quic packets here
            if (isClient()) {
                return null;
            }
        }

        if (connection != null) {
            if (!connection.accepts(source)) {
                return null;
            }
            return QuicDatagram.create(connection, source, copyOnHeap(buffer));
        } else {
            return UnmatchedDatagram.create(source, copyOnHeap(buffer));
        }
    }

    private int send(ByteBuffer datagram, SocketAddress destination, boolean allowClosed) {
        if (!channel.isOpen() || closed && !allowClosed) {
            return -1;
        }
        activeChannelWrites.incrementAndGet();
        if (!channel.isOpen() || closed && !allowClosed) {
            activeChannelWrites.decrementAndGet();
            return -1;
        }
        try {
            try {
                return channel.send(datagram, destination);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to send QUIC datagram", e);
            }
        } finally {
            activeChannelWrites.decrementAndGet();
        }
    }

    private void writeLoop0() {
        // write as much as we can
        while (true) {
            QuicDatagram datagram;
            closeLock.lock();
            try {
                if (closed || queuedWriteInFlight != null) {
                    return;
                }
                datagram = writeQueue.peek();
                if (datagram == null) {
                    return;
                }
                queuedWriteInFlight = datagram;
            } finally {
                closeLock.unlock();
            }

            int toSend = datagram.payload().remaining();
            int sent = 0;
            Throwable failure = null;
            try {
                sent = sendDatagram(datagram);
            } catch (Throwable sendFailure) {
                failure = sendFailure;
            }

            List<QuicDatagram> droppedDatagrams = null;
            boolean terminal = failure != null || sent != 0 || closed;
            closeLock.lock();
            try {
                terminal = failure != null || sent != 0 || closed;
                if (terminal) {
                    QuicDatagram removed = writeQueue.poll();
                    if (removed != datagram) {
                        throw new IllegalStateException("QUIC write queue ownership changed while sending");
                    }
                    if (failure == null && sent <= 0) {
                        droppedDatagrams = new ArrayList<>();
                        droppedDatagrams.add(datagram);
                    }
                }
                queuedWriteInFlight = null;
                if (closed) {
                    if (droppedDatagrams == null) {
                        droppedDatagrams = new ArrayList<>();
                    }
                    drainWriteQueue(droppedDatagrams);
                }
            } finally {
                closeLock.unlock();
            }

            if (!terminal) {
                return;
            }
            Throwable propagated = failure instanceof UncheckedIOException ? null : failure;
            try {
                if (failure instanceof UncheckedIOException io) {
                    onSendError(datagram, toSend, io);
                } else if (failure != null) {
                    propagated = discardDatagram(datagram, propagated);
                } else if (sent > 0) {
                    completeDatagram(datagram, toSend, sent);
                }
            } catch (Throwable callbackFailure) {
                if (propagated == null) {
                    propagated = callbackFailure;
                } else if (callbackFailure != propagated) {
                    propagated.addSuppressed(callbackFailure);
                }
            } finally {
                if (droppedDatagrams != null) {
                    dropDatagrams(droppedDatagrams);
                }
            }
            if (propagated != null) {
                rethrow(propagated);
            }
            if (closed) {
                return;
            }
        }
    }

    private ByteBuffer peekConnectionBytes(HeadersType headersType, ByteBuffer payload) {
        return switch (headersType) {
            case LONG -> {
                int position = payload.position();
                int remaining = payload.remaining();
                if (remaining < 6) {
                    yield null;
                }
                int length = payload.get(position + 5) & 0xFF;
                if (length > QuicConnectionId.MAX_CONNECTION_ID_LENGTH || length > remaining - 6) {
                    yield null;
                }
                yield payload.slice(position + 6, length);
            }
            case SHORT -> QuicPacketDecoder.peekShortConnectionId(payload, idFactory.connectionIdLength()).orElse(null);
            default -> null;
        };
    }

    private boolean hasAvailableVersion(ByteBuffer payload) {
        int versionNumber = QuicPacketDecoder.peekVersion(payload);
        List<QuicVersion> availableVersions = quicInstance.availableVersions();
        for (int i = 0; i < availableVersions.size(); i++) {
            if (availableVersions.get(i).versionNumber() == versionNumber) {
                return true;
            }
        }
        return false;
    }

    private void onReadError(Throwable t) {
        if (!expectExceptions) {
            expectExceptions = true;
            log(System.Logger.Level.ERROR, "Failed to process QUIC read event", t);
            quicInstance.runtimeFailed(t);
            abort(t);
        }
    }

    private StatelessResetToken makeToken(byte[] tokenBytes) {
        // encrypt token to block timing attacks, see RFC 9000 section 10.3.1
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, tokenEncryptionKey);
            byte[] encryptedBytes = cipher.doFinal(tokenBytes);
            return new StatelessResetToken(encryptedBytes);
        } catch (NoSuchAlgorithmException
                 | NoSuchPaddingException
                 | IllegalBlockSizeException
                 | BadPaddingException
                 | InvalidKeyException e) {
            throw new IllegalStateException("AES encryption failed", e);
        }
    }

    private boolean isServer() {
        return !isClient();
    }

    private boolean isClient() {
        return quicInstance.isClient();
    }

    private void silentTerminateConnection(QuicPacketReceiver c) {
        try {
            if (c instanceof QuicConnectionImpl connection) {
                QuicCloseCommand command = silent("QUIC endpoint closed - no error");
                connection.terminator().terminate(command);
            }
        } catch (Throwable t) {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                log(System.Logger.Level.DEBUG, "Failed to close connection %s: %s", c, t);
            }
        } finally {
            if (c != null) {
                c.shutdown();
            }
        }
    }

    private void detachAndCloseChannel() {
        try {
            detach();
        } finally {
            try {
                channel.close();
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to close QUIC datagram channel", e);
            }
        }
    }

    // sends a datagram with a CONNECTION_CLOSE frame for the connection and ignores
    // any exceptions that may occur while trying to do so.
    private void sendConnectionCloseQuietly(QuicConnectionImpl quicConn) {
        QuicDatagram quicDatagram = null;
        try {
            Optional<Datagram> datagram = quicConn.connectionCloseDatagram();
            if (datagram.isEmpty()) {
                return;
            }
            quicDatagram = (QuicDatagram) datagram.orElseThrow();
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                log(System.Logger.Level.DEBUG, "sending CONNECTION_CLOSE datagram for connection %s", quicConn);
            }
            int sent = send(quicDatagram.payload(), quicDatagram.address(), true);
            if (sent == quicDatagram.payloadSize()) {
                quicDatagram.commitPermit(sent);
            } else {
                quicDatagram.releasePermit();
            }
        } catch (Exception e) {
            if (quicDatagram != null) {
                quicDatagram.releasePermit();
            }
            // ignore
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                log(System.Logger.Level.DEBUG,
                    "failed to send CONNECTION_CLOSE datagram for connection %s due to %s",
                    quicConn,
                    e);
            }
        }
    }

    private QuicEndpointRouteLifecycle routeLifecycle(QuicPacketReceiver receiver) {
        if (receiver instanceof QuicConnectionImpl connection) {
            return connection.routeLifecycle();
        }
        if (receiver instanceof ClosedConnection closedConnection) {
            return closedConnection.routeLifecycle();
        }
        return null;
    }

    /**
     * This interface represent a UDP Datagram. This could be
     * either an incoming datagram or an outgoing datagram.
     */
    public sealed interface Datagram
            permits QuicDatagram, StatelessReset, SendStatelessReset, UnmatchedDatagram {
        /**
         * Returns the peer address.
         *
         * @return the peer address.
         *         For incoming datagrams, this is the sender address.
         *         For outgoing datagrams, this is the destination address.
         */
        SocketAddress address();

        /**
         * Returns the datagram payload.
         *
         * @return the datagram payload.
         */
        ByteBuffer payload();
    }

    /**
     * An incoming UDP datagram for which no connection was found.
     * On the server side it may represent a new connection attempt.
     */
    public static final class UnmatchedDatagram implements Datagram {
        private final SocketAddress address;
        private final ByteBuffer payload;

        private UnmatchedDatagram(SocketAddress address, ByteBuffer payload) {
            this.address = Objects.requireNonNull(address, "address");
            this.payload = Objects.requireNonNull(payload, "payload");
        }

        static UnmatchedDatagram create(SocketAddress address, ByteBuffer payload) {
            return new UnmatchedDatagram(address, payload);
        }

        @Override
        public SocketAddress address() {
            return address;
        }

        @Override
        public ByteBuffer payload() {
            return payload;
        }
    }

    /**
     * A stateless reset datagram that should be sent in response to an incoming datagram
     * targeted at a deleted connection.
     */
    public static final class SendStatelessReset implements Datagram {
        private final SocketAddress address;
        private final ByteBuffer payload;

        private SendStatelessReset(SocketAddress address, ByteBuffer payload) {
            this.address = Objects.requireNonNull(address, "address");
            this.payload = Objects.requireNonNull(payload, "payload");
        }

        static SendStatelessReset create(SocketAddress address, ByteBuffer payload) {
            return new SendStatelessReset(address, payload);
        }

        @Override
        public SocketAddress address() {
            return address;
        }

        @Override
        public ByteBuffer payload() {
            return payload;
        }
    }

    /**
     * An incoming datagram containing a stateless reset for a known connection.
     */
    public static final class StatelessReset implements Datagram {
        private final QuicPacketReceiver connection;
        private final SocketAddress address;
        private final ByteBuffer payload;

        private StatelessReset(QuicPacketReceiver connection, SocketAddress address, ByteBuffer payload) {
            this.connection = Objects.requireNonNull(connection, "connection");
            this.address = Objects.requireNonNull(address, "address");
            this.payload = Objects.requireNonNull(payload, "payload");
        }

        static StatelessReset create(QuicPacketReceiver connection, SocketAddress address, ByteBuffer payload) {
            return new StatelessReset(connection, address, payload);
        }

        /**
         * Returns the connection that should process the reset.
         *
         * @return the connection that should process the reset.
         */
        public QuicPacketReceiver connection() {
            return connection;
        }

        @Override
        public SocketAddress address() {
            return address;
        }

        @Override
        public ByteBuffer payload() {
            return payload;
        }
    }

    /**
     * An incoming or outgoing datagram associated with a specific connection.
     */
    public static final class QuicDatagram implements Datagram {
        private final QuicPacketReceiver connection;
        private final SocketAddress address;
        private final ByteBuffer payload;
        private final QuicPathManager.SendPermit permit;
        private final int payloadSize;

        private QuicDatagram(QuicPacketReceiver connection,
                             SocketAddress address,
                             ByteBuffer payload,
                             QuicPathManager.SendPermit permit) {
            this.connection = Objects.requireNonNull(connection, "connection");
            this.address = Objects.requireNonNull(address, "address");
            this.payload = Objects.requireNonNull(payload, "payload");
            this.permit = permit;
            this.payloadSize = payload.remaining();
        }

        static QuicDatagram create(QuicPacketReceiver connection, SocketAddress address, ByteBuffer payload) {
            return new QuicDatagram(connection, address, payload, null);
        }

        static QuicDatagram create(QuicPacketReceiver connection,
                                   SocketAddress address,
                                   ByteBuffer payload,
                                   QuicPathManager.SendPermit permit) {
            return new QuicDatagram(connection, address, payload, permit);
        }

        /**
         * Returns the connection that owns the datagram.
         *
         * @return the connection that owns the datagram.
         */
        public QuicPacketReceiver connection() {
            return connection;
        }

        @Override
        public SocketAddress address() {
            return address;
        }

        @Override
        public ByteBuffer payload() {
            return payload;
        }

        int payloadSize() {
            return payloadSize;
        }

        void commitPermit(int sentBytes) {
            if (permit == null || sentBytes <= 0) {
                return;
            }
            if (sentBytes > permit.size()) {
                throw new IllegalStateException("Sent datagram exceeds its path reservation");
            }
            if (sentBytes < permit.size()) {
                permit.resize(sentBytes);
            }
            permit.commit();
        }

        void releasePermit() {
            if (permit != null) {
                permit.release();
            }
        }
    }

    enum ChannelType {
        NON_BLOCKING_WITH_SELECTOR,
        BLOCKING_WITH_VIRTUAL_THREADS
    }

    /**
     * A {@link QuicEndpoint} implementation based on non blocking
     * {@linkplain DatagramChannel Datagram Channels} and using a
     * NIO {@link Selector}.
     * This implementation is tied to a {@link QuicNioSelector}.
     */
    static final class QuicSelectableEndpoint extends QuicEndpoint {
        private final ReentrantLock keyLock = new ReentrantLock();
        private volatile SelectionKey key;

        private QuicSelectableEndpoint(QuicInstance quicInstance,
                                       QuicRuntimeConfig runtimeConfig,
                                       DatagramChannel channel,
                                       String name,
                                       QuicTimerQueue timerQueue) {
            super(quicInstance, runtimeConfig, channel, name, timerQueue);
        }

        @Override
        ChannelType channelType() {
            return NON_BLOCKING_WITH_SELECTOR;
        }

        /**
         * Attaches this endpoint to a selector.
         *
         * @param selector the selector to attach to
         * @throws UncheckedIOException if the channel is already closed
         */
        public void attach(Selector selector) {
            // this block is needed to coordinate with detach() and
            // selected(). See comment in selected().
            keyLock.lock();
            try {
                try {
                    this.key = super.channel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE, this);
                } catch (ClosedChannelException e) {
                    throw new UncheckedIOException("Failed to register QUIC endpoint channel", e);
                }
            } finally {
                keyLock.unlock();
            }
        }

        /**
         * Invoked by the {@link QuicSelector} when this endpoint's channel
         * is selected.
         *
         * @param readyOps The operations that are ready for this endpoint.
         */
        public void selected(int readyOps) {
            var key = this.key;
            try {
                if (key == null) {
                    // null keys have been observed here.
                    // key can only be null if it's been cancelled, by detach()
                    // or if the call to channel::register hasn't returned yet
                    // the lock below will block until
                    // channel::register returns if needed.
                    // This can only happen once, when attaching the channel,
                    // so there should be no performance issue in synchronizing
                    // here.
                    keyLock.lock();
                    try {
                        key = this.key;
                    } finally {
                        keyLock.unlock();
                    }
                }

                if (key == null) {
                    if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                        log(System.Logger.Level.DEBUG, "key is null");
                        if (QuicEndpoint.class.desiredAssertionStatus()) {
                            Thread.dumpStack();
                        }
                    }
                    return;
                }

                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    log(System.Logger.Level.DEBUG,
                        "selected(interest=%s, ready=%s)",
                        Utils.interestOps(key),
                        Utils.readyOps(key));
                }

                int interestOps = key.interestOps();

                // Some operations may be ready even when we are not interested.
                // Typically, a channel may be ready for writing even if we have
                // nothing to write. The events we need to invoke are therefore
                // at the intersection of the ready set with the interest set.
                int event = readyOps & interestOps;
                if ((event & SelectionKey.OP_READ) == SelectionKey.OP_READ) {
                    onReadEvent();
                    if (isClosed()) {
                        key.interestOpsAnd(~SelectionKey.OP_READ);
                    }
                }
                if ((event & SelectionKey.OP_WRITE) == SelectionKey.OP_WRITE) {
                    onWriteEvent();
                }
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    log(System.Logger.Level.DEBUG, "interestOps: %s", Utils.interestOps(key));
                }
            } finally {
                if (!channel().isOpen()) {
                    if (key != null) {
                        key.cancel();
                    }
                    close();
                }
            }
        }

        @Override
        public void detach() {
            var key = this.key;
            if (key == null) {
                return;
            }
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                log(System.Logger.Level.DEBUG, "cancelling key: " + key);
            }
            // this block is needed to coordinate with attach() and
            // selected(). See comment in selected().
            keyLock.lock();
            try {
                key.cancel();
                this.key = null;
            } finally {
                keyLock.unlock();
            }
        }

        @Override
        void resumeReading() {
            boolean resumed = false;
            SelectionKey key;
            keyLock.lock();
            try {
                key = this.key;
                if (key != null && key.isValid()) {
                    if (isClosed() || isChannelClosed()) {
                        return;
                    }
                    int ops = key.interestOps();
                    int newops = ops | SelectionKey.OP_READ;
                    if (ops != newops) {
                        key.interestOpsOr(SelectionKey.OP_READ);
                        readingStalled(false);
                        resumed = true;
                    }
                }
            } finally {
                keyLock.unlock();
            }
            if (resumed) {
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    log(System.Logger.Level.DEBUG, "endpoint resumed reading");
                }
                key.selector().wakeup();
            }
        }

        @Override
        void pauseReading() {
            boolean paused = false;
            keyLock.lock();
            try {
                if (readingPaused()) {
                    return;
                }
                if (key != null && key.isValid() && bufferTooBig()) {
                    if (isClosed() || isChannelClosed()) {
                        return;
                    }
                    int ops = key.interestOps();
                    int newops = ops & ~SelectionKey.OP_READ;
                    if (ops != newops) {
                        key.interestOpsAnd(~SelectionKey.OP_READ);
                        readingStalled(true);
                        paused = true;
                    }
                }
            } finally {
                keyLock.unlock();
            }
            if (paused) {
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    log(System.Logger.Level.DEBUG, "endpoint paused reading");
                }
            }
        }

        @Override
        void writeLoop() {
            super.writeLoop();
            // update selection key if needed
            var key = this.key;
            try {
                if (key != null && key.isValid()) {
                    int ops;
                    int newops;
                    keyLock.lock();
                    try {
                        ops = key.interestOps();
                        newops = ops;
                        if (writeQueueIsEmpty()) {
                            // we have nothing else to write for now
                            newops &= ~SelectionKey.OP_WRITE;
                        } else {
                            // there's more to write
                            newops |= SelectionKey.OP_WRITE;
                        }
                        if (newops != ops && key.selector().isOpen()) {
                            key.interestOps(newops);
                            key.selector().wakeup();
                        }
                    } finally {
                        keyLock.unlock();
                    }
                    if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                        log(System.Logger.Level.DEBUG, "leaving writeLoop: ops=%s", Utils.describeOps(newops));
                    }
                }
            } catch (CancelledKeyException x) {
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    log(System.Logger.Level.DEBUG, "key cancelled");
                }
                if (writeQueueIsEmpty()) {
                    return;
                } else {
                    closeWriteQueue(x);
                }
            }
        }

        @Override
        void readLoop() {
            try {
                super.readLoop();
            } finally {
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    log(System.Logger.Level.DEBUG,
                        "leaving readLoop: ops=%s",
                        key == null ? "null-key" : Utils.interestOps(key));
                }
            }
        }

        private void onReadEvent() {
            var key = this.key;
            try {
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    log(System.Logger.Level.DEBUG, "onReadEvent");
                }
                channelReadLoop();
            } finally {
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    log(System.Logger.Level.DEBUG,
                        "Leaving readEvent: ops=%s",
                        key == null ? "null-key" : Utils.interestOps(key));
                }
            }
        }

        private void onWriteEvent() {
            // trigger code that will process the received
            // datagrams asynchronously
            // => Use a sequential scheduler, making sure it never
            //    runs on this thread.
            // Do we need a pub/sub mechanism here?
            // The write event will be paused/resumed by the
            // writeLoop if needed
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                log(System.Logger.Level.DEBUG, "onWriteEvent");
            }
            var key = this.key;
            if (key != null && key.isValid()) {
                int previous;
                keyLock.lock();
                try {
                    previous = key.interestOpsAnd(~SelectionKey.OP_WRITE);
                } finally {
                    keyLock.unlock();
                }
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    log(System.Logger.Level.DEBUG,
                        "key changed from %s to: %s",
                        Utils.describeOps(previous),
                        Utils.interestOps(key));
                }
            }
            writeLoopScheduler().runOrSchedule(writeLoopExecutor());
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG) && key != null) {
                log(System.Logger.Level.DEBUG, "Leaving writeEvent: ops=%s", Utils.interestOps(key));
            }
        }
    }

    /**
     * A {@link QuicEndpoint} implementation based on blocking
     * {@linkplain DatagramChannel Datagram Channels} and using a
     * Virtual Threads to poll the channel.
     * This implementation is tied to a {@link QuicVirtualThreadPoller}.
     */
    static final class QuicVirtualThreadedEndpoint extends QuicEndpoint {
        private final ReentrantLock stateLock = new ReentrantLock();
        private final SequentialScheduler channelScheduler = SequentialScheduler.lockingScheduler(this::channelReadLoop0);
        private volatile Future<?> key;
        private volatile QuicVirtualThreadPoller poller;
        private boolean readingDone;

        private QuicVirtualThreadedEndpoint(QuicInstance quicInstance,
                                            QuicRuntimeConfig runtimeConfig,
                                            DatagramChannel channel,
                                            String name,
                                            QuicTimerQueue timerQueue) {
            super(quicInstance, runtimeConfig, channel, name, timerQueue);
        }

        @Override
        ChannelType channelType() {
            return BLOCKING_WITH_VIRTUAL_THREADS;
        }

        @Override
        public void detach() {
            var key = this.key;
            try {
                if (key != null) {
                    // do not interrupt the reading task if running:
                    // closing the channel later on will ensure that the
                    // task eventually terminates.
                    key.cancel(false);
                }
            } catch (Throwable e) {
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    log(System.Logger.Level.DEBUG, "Failed to cancel future: " + e);
                }
            }
        }

        @Override
        boolean readingPaused() {
            stateLock.lock();
            try {
                readingDone = super.readingPaused();
                return readingDone;
            } finally {
                stateLock.unlock();
            }
        }

        @Override
        void resumeReading() {
            boolean resumed;
            boolean resumedInOtherThread = false;
            QuicVirtualThreadPoller poller;
            stateLock.lock();
            try {
                resumed = readingPaused();
                readingStalled(false);
                poller = this.poller;
                // readingDone is false here, it means reading already resumed
                //    no need to start a new reading thread
                resumedInOtherThread = readingDone;
                if (poller != null && resumedInOtherThread) {
                    readingDone = false;
                    attach(poller);
                }
            } finally {
                stateLock.unlock();
            }
            if (resumedInOtherThread) {
                // last time readingPaused() was called it returned true, so we know
                //    the previous poller thread has stopped reading and will exit.
                //    We attached a new poller above, so reading will resume in that
                //    other thread
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    log(System.Logger.Level.DEBUG, "endpoint resumed reading in new virtual thread");
                }
            } else if (resumed) {
                // readingStalled was true, and readingDone was false - which means some
                //   poller thread is already active, and will find readingStalled == true
                //   and will continue reading. So reading will resume in the currently
                //   active poller thread
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    log(System.Logger.Level.DEBUG, "endpoint resumed reading in same virtual thread");
                }
            } // if readingStalled was false and readingDone was false there is nothing to do.
        }

        @Override
        void pauseReading() {
            boolean paused = false;
            stateLock.lock();
            try {
                if (bufferTooBig()) {
                    paused = true;
                    readingStalled(true);
                }
            } finally {
                stateLock.unlock();
            }
            if (paused) {
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    log(System.Logger.Level.DEBUG, "endpoint paused reading");
                }
            }
        }

        void attach(QuicVirtualThreadPoller poller) {
            this.poller = poller;
            var future = poller.startReading(this);
            stateLock.lock();
            try {
                this.key = future;
            } finally {
                stateLock.unlock();
            }
        }

        Executor writeLoopExecutor() {
            QuicVirtualThreadPoller poller = this.poller;
            if (poller == null) {
                return super.writeLoopExecutor();
            }
            return poller.readLoopExecutor();
        }

        @Override
        void channelReadLoop() {
            channelScheduler.runOrSchedule();
        }

        private void channelReadLoop0() {
            super.channelReadLoop();
        }
    }

    /**
     * Factory for creating QUIC endpoints backed by either selector-driven or virtual-thread channel processing.
     */
    static final class QuicEndpointFactory {
        /**
         * Create a new endpoint factory.
         */
        private QuicEndpointFactory() {
        }

        /**
         * Creates a new endpoint factory.
         *
         * @return new endpoint factory
         */
        static QuicEndpointFactory create() {
            return new QuicEndpointFactory();
        }

        /**
         * Returns a new {@code QuicSelectableEndpoint}.
         *
         * @param quicInstance  the quic instance
         * @param runtimeConfig runtime configuration
         * @param name          the endpoint name
         * @param bindAddress   the address to bind to
         * @param timerQueue    the timer queue
         * @return a new {@code QuicSelectableEndpoint}.
         * @throws UncheckedIOException if endpoint creation or binding fails
         */
        QuicSelectableEndpoint createSelectableEndpoint(QuicInstance quicInstance,
                                                        QuicRuntimeConfig runtimeConfig,
                                                        String name,
                                                        SocketAddress bindAddress,
                                                        QuicTimerQueue timerQueue) {
            return QuicEndpoint.create(QuicSelectableEndpoint.class,
                                       quicInstance,
                                       runtimeConfig,
                                       name,
                                       bindAddress,
                                       timerQueue);
        }

        /**
         * Returns a new {@code QuicVirtualThreadedEndpoint}.
         *
         * @param quicInstance  the quic instance
         * @param runtimeConfig runtime configuration
         * @param name          the endpoint name
         * @param bindAddress   the address to bind to
         * @param timerQueue    the timer queue
         * @return a new {@code QuicVirtualThreadedEndpoint}.
         * @throws UncheckedIOException if endpoint creation or binding fails
         */
        QuicVirtualThreadedEndpoint createVirtualThreadedEndpoint(QuicInstance quicInstance,
                                                                  QuicRuntimeConfig runtimeConfig,
                                                                  String name,
                                                                  SocketAddress bindAddress,
                                                                  QuicTimerQueue timerQueue) {
            return QuicEndpoint.create(QuicVirtualThreadedEndpoint.class,
                                       quicInstance,
                                       runtimeConfig,
                                       name,
                                       bindAddress,
                                       timerQueue);
        }
    }

    /**
     * Represent a closing or draining quic connection: if we receive any packet
     * for this connection we ignore them (if in draining state) or replay the
     * closed packets in decreasing frequency: we reply to the
     * first packet, then to the third, then to the seventh, etc...
     * We stop replying after 16*16/2.
     */
    abstract sealed class ClosedConnection implements QuicPacketReceiver, QuicTimedEvent
            permits QuicEndpoint.ClosingConnection, QuicEndpoint.DrainingConnection {

        // default time we keep the ClosedConnection alive while closing/draining - if
        // PTO information is not available (if 0 is passed as idleTimeoutMs when creating
        // an instance of this class)
        static final long NO_IDLE_TIMEOUT = 2000;
        private final List<QuicConnectionId> localConnectionIds;
        private final long maxIdleTimeMs;
        private final long id;
        private final List<QuicPacketReceiver.PeerResetToken> activeResetTokens;
        private final QuicPathManager.ClosingPath closingPath;
        private final QuicEndpointRouteLifecycle routeLifecycle;
        private final ReentrantLock timerRegistrationLock = new ReentrantLock();
        private int more = 1;
        private int waitformore;
        private volatile Deadline deadline;
        private Deadline updatedDeadline;
        private TimerRegistrationState timerRegistrationState = TimerRegistrationState.CREATED;
        private boolean completeRemovalAfterArm;
        private Throwable deferredRemovalFailure;

        ClosedConnection(List<QuicConnectionId> localConnectionIds,
                         List<QuicPacketReceiver.PeerResetToken> activeResetTokens,
                         long maxIdleTimeMs,
                         QuicPathManager.ClosingPath closingPath,
                         QuicEndpointRouteLifecycle routeLifecycle) {
            this.activeResetTokens = activeResetTokens;
            this.id = QuicTimerQueue.newEventId();
            this.maxIdleTimeMs = maxIdleTimeMs == 0 ? NO_IDLE_TIMEOUT : maxIdleTimeMs;
            this.deadline = Deadline.MAX;
            this.updatedDeadline = Deadline.MAX;
            this.localConnectionIds = List.copyOf(localConnectionIds);
            this.closingPath = closingPath;
            this.routeLifecycle = routeLifecycle;
        }

        final void prepareTimerForPublication() {
            if (timerRegistrationState != TimerRegistrationState.CREATED) {
                throw new IllegalStateException("Closing timer already prepared");
            }
            deadline = TimeSource.now().plusMillis(maxIdleTimeMs);
            updatedDeadline = deadline;
            timerRegistrationState = TimerRegistrationState.PUBLISHED;
        }

        @Override
        public final boolean accepts(SocketAddress source) {
            return closingPath.accepts(source);
        }

        @Override
        public List<QuicConnectionId> connectionIds() {
            return localConnectionIds;
        }

        @Override
        public List<QuicPacketReceiver.PeerResetToken> activeResetTokens() {
            return activeResetTokens;
        }

        @Override
        public final void processIncoming(SocketAddress source,
                                          ByteBuffer destConnId,
                                          HeadersType headersType,
                                          ByteBuffer buffer) {
            closingPath.received(buffer.remaining());
            boolean respond = false;
            boolean finish = false;
            timerRegistrationLock.lock();
            try {
                if (timerRegistrationState == TimerRegistrationState.PUBLISHED
                        || timerRegistrationState == TimerRegistrationState.ARMING
                        || timerRegistrationState == TimerRegistrationState.ARMED) {
                    Deadline updated = updatedDeadline;
                    int waitformore = this.waitformore;
                    if (!Deadline.MIN.equals(updated) && waitformore == 0) {
                        int more = this.more;
                        this.waitformore = more;
                        more <<= 1;
                        this.more = more;
                        if (more > 16) {
                            // The peer does not appear to process our CONNECTION_CLOSE frame.
                            updatedDeadline = Deadline.MIN;
                            finish = true;
                        } else {
                            updatedDeadline = updated.plusMillis(maxIdleTimeMs);
                        }
                        respond = true;
                    } else if (waitformore > 0) {
                        this.waitformore = waitformore - 1;
                    }
                }
            } finally {
                timerRegistrationLock.unlock();
            }

            if (respond) {
                handleIncoming(source, destConnId, headersType, buffer);
                if (finish) {
                    finishRoutes();
                }
            } else {
                dropIncoming(source, destConnId, headersType, buffer);
            }
        }

        @Override
        public final void onWriteError(Throwable t) {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                log(System.Logger.Level.DEBUG, "failed to write close packet", t);
            }
            finishRoutes();
        }

        public final void startTimer() {
            timerRegistrationLock.lock();
            try {
                if (timerRegistrationState != TimerRegistrationState.PUBLISHED) {
                    return;
                }
                timerRegistrationState = TimerRegistrationState.ARMING;
            } finally {
                timerRegistrationLock.unlock();
            }

            Throwable armFailure = null;
            try {
                timer().offer(this);
            } catch (RuntimeException | Error failure) {
                armFailure = failure;
            }

            boolean cancelTimer = false;
            boolean completeRemoval = false;
            Throwable removalFailure = null;
            timerRegistrationLock.lock();
            try {
                if (armFailure != null) {
                    cancelTimer = true;
                    if (timerRegistrationState == TimerRegistrationState.REMOVE_AFTER_ARM) {
                        completeRemoval = completeRemovalAfterArm;
                        removalFailure = deferredRemovalFailure;
                    }
                    timerRegistrationState = TimerRegistrationState.DONE;
                } else if (timerRegistrationState == TimerRegistrationState.ARMING) {
                    timerRegistrationState = TimerRegistrationState.ARMED;
                } else if (timerRegistrationState == TimerRegistrationState.REMOVE_AFTER_ARM) {
                    timerRegistrationState = TimerRegistrationState.DONE;
                    cancelTimer = true;
                    completeRemoval = completeRemovalAfterArm;
                    removalFailure = deferredRemovalFailure;
                }
            } finally {
                timerRegistrationLock.unlock();
            }

            if (cancelTimer) {
                try {
                    timer().cancel(this);
                } catch (RuntimeException | Error cancelFailure) {
                    if (armFailure == null) {
                        armFailure = cancelFailure;
                    } else {
                        armFailure.addSuppressed(cancelFailure);
                    }
                }
            }
            if (completeRemoval) {
                completeRouteRemoval(removalFailure);
            }
            if (armFailure != null) {
                rethrow(armFailure);
            }
        }

        final void routesRemoved(Throwable removalFailure) {
            TimerDetachAction action = detachTimer(TimerDetachMode.REMOVE_ROUTES, removalFailure);
            if (action.cancelTimer) {
                try {
                    timer().cancel(this);
                } catch (RuntimeException | Error cancelFailure) {
                    if (removalFailure == null) {
                        removalFailure = cancelFailure;
                    } else {
                        removalFailure.addSuppressed(cancelFailure);
                    }
                }
            }
            if (action.completeRemoval) {
                completeRouteRemoval(removalFailure);
            }
        }

        private void cancelTransferredTimer() {
            TimerDetachAction action = detachTimer(TimerDetachMode.TRANSFER, null);
            if (action.cancelTimer) {
                timer().cancel(this);
            }
        }

        private TimerDetachAction detachTimer(TimerDetachMode mode, Throwable removalFailure) {
            timerRegistrationLock.lock();
            try {
                switch (timerRegistrationState) {
                case CREATED, PUBLISHED -> {
                    timerRegistrationState = TimerRegistrationState.DONE;
                    return mode.action(false);
                }
                case ARMING -> {
                    timerRegistrationState = TimerRegistrationState.REMOVE_AFTER_ARM;
                    if (mode == TimerDetachMode.REMOVE_ROUTES) {
                        completeRemovalAfterArm = true;
                        deferredRemovalFailure = removalFailure;
                    }
                    return TimerDetachAction.NONE;
                }
                case ARMED -> {
                    timerRegistrationState = TimerRegistrationState.DONE;
                    return mode.action(true);
                }
                case HANDLING -> {
                    timerRegistrationState = TimerRegistrationState.DONE;
                    return mode.action(false);
                }
                case REMOVE_AFTER_ARM -> {
                    if (mode == TimerDetachMode.REMOVE_ROUTES) {
                        completeRemovalAfterArm = true;
                        deferredRemovalFailure = removalFailure;
                    }
                    return TimerDetachAction.NONE;
                }
                case DONE -> {
                    return mode.action(false);
                }
                default -> throw new IllegalStateException("Unexpected timer state " + timerRegistrationState);
                }
            } finally {
                timerRegistrationLock.unlock();
            }
        }

        private void completeRouteRemoval(Throwable removalFailure) {
            if (removalFailure == null) {
                routeLifecycle.completeRemoval();
            } else {
                routeLifecycle.completeRemovalExceptionally(removalFailure);
            }
        }

        @Override
        public final Deadline deadline() {
            return deadline;
        }

        @Override
        public final Deadline handle() {
            timerRegistrationLock.lock();
            try {
                if (timerRegistrationState != TimerRegistrationState.ARMING
                        && timerRegistrationState != TimerRegistrationState.ARMED) {
                    return Deadline.MAX;
                }
                timerRegistrationState = TimerRegistrationState.HANDLING;
                Deadline nextDeadline = updatedDeadline;
                if (nextDeadline.isAfter(TimeSource.now())) {
                    timerRegistrationState = TimerRegistrationState.ARMED;
                    return nextDeadline;
                }
            } finally {
                timerRegistrationLock.unlock();
            }
            finishRoutes();
            return Deadline.MAX;
        }

        @Override
        public final Deadline refreshDeadline() {
            timerRegistrationLock.lock();
            try {
                if (timerRegistrationState == TimerRegistrationState.ARMED) {
                    deadline = updatedDeadline;
                } else {
                    deadline = Deadline.MAX;
                }
                return deadline;
            } finally {
                timerRegistrationLock.unlock();
            }
        }

        @Override
        public final long eventId() {
            return id;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "(" + identityTag(this) + ")";
        }

        @Override
        public final void processStatelessReset() {
            // the peer has sent us a stateless reset: no need to
            //   replay CloseConnectionFrame. Just remove this connection.
            finishRoutes();
        }

        public void shutdown() {
            finishRoutes();
        }

        private void finishRoutes() {
            removeConnection(this);
        }

        protected void handleIncoming(SocketAddress source, ByteBuffer idbytes,
                                      HeadersType headersType, ByteBuffer buffer) {
            dropIncoming(source, idbytes, headersType, buffer);
        }

        protected abstract void dropIncoming(SocketAddress source, ByteBuffer idbytes,
                                             HeadersType headersType, ByteBuffer buffer);

        final QuicPathManager.ClosingPath closingPath() {
            return closingPath;
        }

        final QuicEndpointRouteLifecycle routeLifecycle() {
            return routeLifecycle;
        }

        private enum TimerRegistrationState {
            CREATED,
            PUBLISHED,
            ARMING,
            ARMED,
            HANDLING,
            REMOVE_AFTER_ARM,
            DONE
        }

        private enum TimerDetachMode {
            REMOVE_ROUTES,
            TRANSFER;

            private TimerDetachAction action(boolean cancelTimer) {
                if (this == TRANSFER) {
                    return cancelTimer ? TimerDetachAction.CANCEL : TimerDetachAction.NONE;
                }
                return cancelTimer ? TimerDetachAction.CANCEL_AND_COMPLETE : TimerDetachAction.COMPLETE;
            }
        }

        private enum TimerDetachAction {
            NONE(false, false),
            CANCEL(true, false),
            COMPLETE(false, true),
            CANCEL_AND_COMPLETE(true, true);

            private final boolean cancelTimer;
            private final boolean completeRemoval;

            TimerDetachAction(boolean cancelTimer, boolean completeRemoval) {
                this.cancelTimer = cancelTimer;
                this.completeRemoval = completeRemoval;
            }
        }
    }

    /**
     * Represent a closing quic connection: if we receive any packet for this
     * connection we simply replay the packet(s) that contained the
     * ConnectionCloseFrame frame.
     * Packets are replayed in decreasing frequency. We reply to the
     * first packet, then to the third, then to the seventh, etc...
     * We stop replying after 16*16/2.
     */
    final class ClosingConnection extends ClosedConnection {

        private final ByteBuffer closePacket;

        ClosingConnection(List<QuicConnectionId> localConnectionIds,
                          List<QuicPacketReceiver.PeerResetToken> activeResetTokens,
                          long maxIdleTimeMs,
                          ByteBuffer closePacket,
                          QuicPathManager.ClosingPath closingPath,
                          QuicEndpointRouteLifecycle routeLifecycle) {
            super(localConnectionIds, activeResetTokens, maxIdleTimeMs, closingPath, routeLifecycle);
            this.closePacket = Objects.requireNonNull(closePacket);
        }

        @Override
        public void handleIncoming(SocketAddress source, ByteBuffer idbytes,
                                   HeadersType headersType, ByteBuffer buffer) {
            if (isClosed() || isChannelClosed()) {
                // don't respond with any more datagrams and instead just drop
                // the incoming ones since the channel is closed
                dropIncoming(source, idbytes, headersType, buffer);
                return;
            }
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                log(System.Logger.Level.DEBUG, "ClosingConnection(%s): sending closed packets", connectionIds());
            }
            Optional<QuicPathManager.SendPermit> reservation = closingPath().reserve(closePacket.remaining());
            if (reservation.isEmpty()) {
                dropIncoming(source, idbytes, headersType, buffer);
                return;
            }
            QuicPathManager.SendPermit permit = reservation.orElseThrow();
            pushDatagram(this, source, closePacket.asReadOnlyBuffer(), permit);
        }

        @Override
        protected void dropIncoming(SocketAddress source, ByteBuffer idbytes, HeadersType headersType, ByteBuffer buffer) {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                log(System.Logger.Level.DEBUG,
                    "ClosingConnection(%s): dropping %s packet",
                    connectionIds(),
                    headersType);
            }
        }
    }

    /**
     * Represent a draining quic connection: if we receive any packet for this
     * connection we simply ignore them.
     */
    final class DrainingConnection extends ClosedConnection {

        DrainingConnection(List<QuicConnectionId> localConnectionIds,
                           List<QuicPacketReceiver.PeerResetToken> activeResetTokens,
                           long maxIdleTimeMs,
                           QuicPathManager.ClosingPath closingPath,
                           QuicEndpointRouteLifecycle routeLifecycle) {
            super(localConnectionIds, activeResetTokens, maxIdleTimeMs, closingPath, routeLifecycle);
        }

        @Override
        public void dropIncoming(SocketAddress source, ByteBuffer idbytes, HeadersType headersType, ByteBuffer buffer) {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                log(System.Logger.Level.DEBUG,
                    "DrainingConnection(%s): dropping %s packet",
                    connectionIds(),
                    headersType);
            }
        }
    }

    private record StatelessResetToken(byte[] token) {
        StatelessResetToken(byte[] token) {
            this.token = token.clone();
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(token);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof StatelessResetToken other) {
                return Arrays.equals(token, other.token);
            }
            return false;
        }
    }

    private record PeerIssuedResetToken(StatelessResetToken token, SocketAddress peerAddress) {
        private PeerIssuedResetToken {
            Objects.requireNonNull(token);
            Objects.requireNonNull(peerAddress);
        }
    }
}
