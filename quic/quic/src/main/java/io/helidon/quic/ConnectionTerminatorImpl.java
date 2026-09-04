/*
 * Copyright (c) 2024, 2026 Oracle and/or its affiliates.
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

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import io.helidon.quic.QuicConnectionImpl.HandshakeFlow;
import io.helidon.quic.QuicConnectionImpl.ProtectionRecord;
import io.helidon.quic.QuicTLSEngine.KeySpace;
import io.helidon.quic.frame.ConnectionCloseFrame;
import io.helidon.quic.frame.QuicFrame;
import io.helidon.quic.packet.QuicPacket;

import static io.helidon.quic.QuicConnectionImpl.QuicConnectionState.CLOSED;
import static io.helidon.quic.QuicConnectionImpl.QuicConnectionState.CLOSING;
import static io.helidon.quic.QuicConnectionImpl.QuicConnectionState.DRAINING;
import static io.helidon.quic.QuicTransportErrors.NO_ERROR;

final class ConnectionTerminatorImpl implements ConnectionTerminator {
    private static final System.Logger LOGGER = System.getLogger(ConnectionTerminatorImpl.class.getName());
    private static final ScopedValue<TerminationScope> COMPLETING_TERMINATION = ScopedValue.newInstance();
    private static final List<KeySpace> HANDSHAKE_CLOSE_KEY_SPACES =
            List.of(KeySpace.ONE_RTT, KeySpace.HANDSHAKE, KeySpace.INITIAL);

    private final QuicConnectionImpl connection;
    private final AtomicReference<QuicTermination> termination = new AtomicReference<>();
    private final AtomicBoolean cleanupStarted = new AtomicBoolean();
    private final CompletableFuture<QuicTermination> futureTermination = MinimalFuture.<QuicTermination>create();
    private final CompletableFuture<Void> cleanupComplete = MinimalFuture.create();

    ConnectionTerminatorImpl(QuicConnectionImpl connection) {
        this.connection = Objects.requireNonNull(connection, "connection");
    }

    static boolean isCompletingTermination(QuicInstance instance) {
        for (TerminationScope scope = currentTerminationScope(); scope != null; scope = scope.parent()) {
            if (scope.instance() == instance) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void peerPacketProcessed() {
        this.connection.idleTimeoutManager().peerPacketProcessed();
    }

    @Override
    public void ackElicitingPacketSent() {
        this.connection.idleTimeoutManager().ackElicitingPacketSent();
    }

    @Override
    public boolean tryReserveForUse() {
        return this.connection.idleTimeoutManager().tryReserveForUse();
    }

    @Override
    public void appLayerMaxIdle(Duration maxIdle, Supplier<Boolean> trafficGenerationCheck) {
        this.connection.idleTimeoutManager().appLayerMaxIdle(maxIdle, trafficGenerationCheck);
    }

    @Override
    public void terminate(QuicCloseCommand command) {
        Objects.requireNonNull(command, "command");
        if (!cleanupStarted.compareAndSet(false, true)) {
            return;
        }
        try {
            doTerminate(command);
        } catch (RuntimeException | Error failure) {
            log(System.Logger.Level.ERROR, "Failed to terminate QUIC connection", failure);
            terminationFailed(failure);
            if (failure instanceof Error error) {
                throw error;
            }
        }
    }

    QuicTermination termination() {
        return this.termination.get();
    }

    void incomingConnectionCloseFrame(KeySpace keySpace, ConnectionCloseFrame frame) {
        Objects.requireNonNull(keySpace, "keySpace");
        Objects.requireNonNull(frame);
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            log(System.Logger.Level.DEBUG, "Received close frame: %s", frame);
        }
        if (!cleanupStarted.compareAndSet(false, true)) {
            return;
        }
        try {
            drain(keySpace, frame);
        } catch (RuntimeException | Error failure) {
            if (!cleanupComplete.isDone()) {
                try {
                    QuicTermination selected = termination.get();
                    silentTerminate(selected == null
                                            ? QuicTermination.local(QuicCloseCommand.transport(failure), null)
                                            : selected);
                } catch (Throwable cleanupFailure) {
                    if (failure != cleanupFailure) {
                        failure.addSuppressed(cleanupFailure);
                    }
                }
            }
            terminationFailed(failure);
            throw failure;
        }
    }

    void incomingStatelessReset() {
        // if local endpoint is a client, then our peer is a server
        boolean peerIsServer = connection.isClientConnection();
        var label = "quic:" + connection.uniqueId();
        if (!cleanupStarted.compareAndSet(false, true)) {
            return;
        }
        QuicTermination termination = QuicTermination.peerStatelessReset("stateless reset from peer ("
                                                                                  + (peerIsServer ? "server" : "client")
                                                                                  + ") on " + label);
        try {
            silentTerminate(termination);
        } catch (RuntimeException | Error failure) {
            log(System.Logger.Level.ERROR, "Failed to process peer stateless reset", failure);
            terminationFailed(failure);
            if (failure instanceof Error error) {
                throw error;
            }
        }
    }

    CompletableFuture<QuicTermination> futureTermination() {
        return this.futureTermination;
    }

    CompletableFuture<Void> cleanupComplete() {
        return cleanupComplete;
    }

    /**
     * Returns a {@link ByteBuffer} which contains an encrypted QUIC packet containing
     * a {@linkplain ConnectionCloseFrame CONNECTION_CLOSE frame}. The CONNECTION_CLOSE
     * frame will have a frame type of {@code 0x1c} and error code of {@code NO_ERROR}.
     * <p>
     * This method should only be invoked when the {@link QuicEndpoint} is being closed
     * and the endpoint wants to send out a {@code CONNECTION_CLOSE} frame on a best-effort
     * basis (in a fire and forget manner).
     *
     * @return the datagram containing the QUIC packet with a CONNECTION_CLOSE frame
     * @throws QuicKeyUnavailableException
     * @throws QuicTransportException
     */
    ByteBuffer makeConnectionCloseDatagram(QuicPathManager.SendPermit permit)
            throws QuicKeyUnavailableException, QuicTransportException {
        ConnectionCloseFrame connCloseFrame = ConnectionCloseFrame.create(NO_ERROR.code(),
                                                                          QuicFrame.CONNECTION_CLOSE, null);
        KeySpace keySpace = connection.tlsEngine().currentSendKeySpace();
        // we don't want the connection's ByteBuffer pooling infrastructure
        // (through the QuicConnectionImpl::allocateDatagramForEncryption) for
        // this packet, so we use a simple custom allocator.
        Function<QuicPacket, ByteBuffer> allocator = (pkt) -> ByteBuffer.allocate(pkt.size());
        Optional<QuicConnectionId> destinationConnectionId = connection.destinationConnectionId(keySpace, permit);
        if (destinationConnectionId.isEmpty()) {
            throw new QuicKeyUnavailableException("No destination connection ID available for closing path", keySpace);
        }
        QuicPacket packet = connection.newQuicPacket(keySpace,
                                                     destinationConnectionId.orElseThrow(),
                                                     List.of(connCloseFrame));
        ProtectionRecord encrypted = ProtectionRecord.single(packet, allocator)
                .encrypt(connection.codingContext());
        ByteBuffer datagram = encrypted.datagram();
        int firstPacketOffset = encrypted.firstPacketOffset();
        // flip the datagram
        datagram.limit(datagram.position());
        datagram.position(firstPacketOffset);
        return datagram;
    }

    private static String logMessage(QuicTermination termination) {
        return termination.logMessage();
    }

    private void doTerminate(QuicCloseCommand command) {
        if (command.kind() == QuicCloseCommand.Kind.SILENT) {
            silentTerminate(QuicTermination.local(command, null));
            return;
        }
        ConnectionCloseFrame frame;
        KeySpace keySpace = command.keySpace().orElse(null);
        if (keySpace == null) {
            keySpace = connection.tlsEngine().currentSendKeySpace();
        }
        long errorCode = command.errorCode().orElseThrow();
        String peerDetail = command.peerDetail().orElse("");
        if (command.layer() == QuicCloseCommand.Layer.APPLICATION) {
            frame = ConnectionCloseFrame.create(errorCode, peerDetail); // 0x1d
        } else {
            frame = ConnectionCloseFrame.create(errorCode, command.frameType().orElse(0), peerDetail); // 0x1c
        }
        immediateClose(frame, keySpace, command);
    }

    /**
     * Called only when the connection is expected to be discarded without being required
     * to inform the peer.
     * Discards all state, no CONNECTION_CLOSE is sent, nor does the connection enter closing
     * or discarding state.
     */
    private void silentTerminate(QuicTermination termination) {
        // mark the connection state as closed (we don't enter closing or draining state
        // during silent termination)
        if (!connection.withStreamDispatchLock(() -> markClosed(termination))) {
            // previously already closed
            return;
        }
        Throwable cleanupFailure = attemptCleanup(null, () -> connection.idleTimeoutManager().shutdown());
        try {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                log(System.Logger.Level.DEBUG, "silently terminating connection due to: %s",
                          logMessage(termination));
            }
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                String message = connection.loggableState();
                if (message != null) {
                    log(System.Logger.Level.DEBUG, "connection state: %s", message);
                }
            }
        } finally {
            cleanupConnection(termination, true, cleanupFailure);
        }
    }

    private void unregisterConnFromEndpoint() {
        QuicEndpoint endpoint = this.connection.endpoint();
        if (endpoint == null) {
            // this can happen if the connection is being terminated before
            // an endpoint has been established (which is OK)
            return;
        }
        endpoint.removeConnection(this.connection);
    }

    private void immediateClose(ConnectionCloseFrame closeFrame,
                                KeySpace keySpace,
                                QuicCloseCommand command) {
        QuicPathManager.SendPermit validatedPermit = null;
        int firstKeySpaceIndex = -1;
        if (command.delivery() == QuicCloseCommand.Delivery.VALIDATED_HANDSHAKE_LEVELS) {
            for (int i = 0; i < HANDSHAKE_CLOSE_KEY_SPACES.size(); i++) {
                KeySpace candidate = HANDSHAKE_CLOSE_KEY_SPACES.get(i);
                if (!connection.tlsEngine().keysAvailable(candidate)) {
                    continue;
                }
                Optional<QuicPathManager.SendPermit> reservation =
                        connection.pathManager().reserveValidated(connection.maxDatagramSize());
                if (reservation.isEmpty()) {
                    break;
                }
                QuicPathManager.SendPermit permit = reservation.orElseThrow();
                boolean retained = false;
                try {
                    if (connection.destinationConnectionId(candidate, permit).isEmpty()) {
                        continue;
                    }
                    validatedPermit = permit;
                    firstKeySpaceIndex = i;
                    keySpace = candidate;
                    retained = true;
                    break;
                } finally {
                    if (!retained) {
                        permit.release();
                    }
                }
            }
            if (validatedPermit == null) {
                silentTerminate(QuicTermination.local(command.silentFallback(), null));
                return;
            }
        }
        QuicTermination termination = QuicTermination.local(command, keySpace);
        String logMsg = logMessage(termination);
        // if the connection has already been closed (for example: through silent termination)
        // then the local state of the connection is already discarded and thus
        // there's nothing more we can do with the connection.
        if (!connection.withStreamDispatchLock(() -> {
            if (connection.stateHandle().isMarked(CLOSED)) {
                return false;
            }
            return markClosing(termination);
        })) {
            if (validatedPermit != null) {
                validatedPermit.release();
            }
            // has previously already gone into closing state
            return;
        }
        Throwable cleanupFailure = attemptCleanup(null, () -> connection.idleTimeoutManager().shutdown());
        boolean unregister = false;
        try {
            if (connection.stateHandle().draining()) {
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    log(System.Logger.Level.DEBUG,
                              "skipping immediate close, since connection is already in draining state");
                }
                return;
            }
            String closeCodeHex = (termination.layer() == QuicTermination.Layer.APPLICATION ? "(app layer) " : "")
                    + "0x" + Long.toHexString(closeFrame.errorCode());
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                log(System.Logger.Level.DEBUG, "entering closing state, code %s - %s", closeCodeHex, logMsg);
            }
            if (command.delivery() == QuicCloseCommand.Delivery.VALIDATED_HANDSHAKE_LEVELS) {
                boolean sent = false;
                for (int i = firstKeySpaceIndex; i < HANDSHAKE_CLOSE_KEY_SPACES.size(); i++) {
                    KeySpace candidate = HANDSHAKE_CLOSE_KEY_SPACES.get(i);
                    QuicPathManager.SendPermit permit;
                    if (i == firstKeySpaceIndex) {
                        permit = validatedPermit;
                        validatedPermit = null;
                    } else {
                        if (!connection.tlsEngine().keysAvailable(candidate)) {
                            continue;
                        }
                        Optional<QuicPathManager.SendPermit> reservation =
                                connection.pathManager().reserveValidated(connection.maxDatagramSize());
                        if (reservation.isEmpty()) {
                            continue;
                        }
                        permit = reservation.orElseThrow();
                    }
                    try {
                        sent |= pushConnectionCloseFrame(candidate, closeFrame, permit);
                    } catch (QuicKeyUnavailableException e) {
                        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                            log(System.Logger.Level.DEBUG,
                                "Skipping handshake-timeout close after %s keys became unavailable: %s",
                                candidate.text(),
                                e.getMessage());
                        }
                    } catch (RuntimeException e) {
                        log(System.Logger.Level.ERROR,
                            "Failed to send handshake-timeout CONNECTION_CLOSE in " + candidate.text(),
                            e);
                    }
                }
                unregister = !sent;
            } else if (!pushConnectionCloseFrame(keySpace, closeFrame)) {
                unregister = true;
            }
        } catch (RuntimeException e) {
            log(System.Logger.Level.ERROR,
                      "Removing connection from endpoint after failure to send CONNECTION_CLOSE", e);
            // we failed to send a CONNECTION_CLOSE frame. this implies that the QuicEndpoint
            // won't detect that the QuicConnectionImpl has transitioned to closing connection
            // and thus won't remap it to closing. we thus discard such connection from the
            // endpoint.
            unregister = true;
        } finally {
            if (validatedPermit != null) {
                validatedPermit.release();
            }
            cleanupConnection(termination, unregister, cleanupFailure);
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                log(System.Logger.Level.DEBUG, "connection has now transitioned to closing state");
            }
        }
    }

    private void drain(KeySpace keySpace, ConnectionCloseFrame incomingFrame) {
        boolean isAppLayerClose = incomingFrame.variant();
        String reason = incomingFrame.reasonString().orElse("");
        String peer = connection.isClientConnection() ? "server" : "client";
        String msg = "Connection closed by "
                + peer
                + " peer: "
                + (isAppLayerClose ? "application" : "transport")
                + " error 0x"
                + Long.toHexString(incomingFrame.errorCode());
        QuicTermination termination = QuicTermination.peerConnectionClose(
                isAppLayerClose ? QuicTermination.Layer.APPLICATION : QuicTermination.Layer.TRANSPORT,
                incomingFrame.errorCode(),
                isAppLayerClose ? OptionalLong.empty() : OptionalLong.of(incomingFrame.errorFrameType()),
                keySpace,
                reason,
                msg);
        if (!connection.withStreamDispatchLock(() -> {
            if (connection.stateHandle().isMarked(CLOSED)) {
                return false;
            }
            return markDraining(termination);
        })) {
            // has previously already gone into draining state
            return;
        }
        Throwable cleanupFailure = attemptCleanup(null, () -> connection.idleTimeoutManager().shutdown());
        try {
            String closeCodeString;
            if (isAppLayerClose) {
                try {
                    String formatted = connection.appErrorToString(incomingFrame.errorCode());
                    closeCodeString = "[app]" + formatted;
                } catch (RuntimeException formatterFailure) {
                    closeCodeString = "[app]0x" + Long.toHexString(incomingFrame.errorCode());
                    log(System.Logger.Level.ERROR,
                        "Failed to format peer application close code " + closeCodeString,
                        formatterFailure);
                }
            } else {
                closeCodeString = QuicTransportErrors.text(incomingFrame.errorCode());
            }
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                log(System.Logger.Level.DEBUG,
                    "entering draining state, connection closed by %s peer: %s (reason %s bytes)",
                    peer,
                    closeCodeString,
                    incomingFrame.reasonLength());
            }
            if (connection.quicConfig().unsafeRawData()
                    && !reason.isEmpty()
                    && LOGGER.isLoggable(System.Logger.Level.TRACE)) {
                log(System.Logger.Level.TRACE, "UNSAFE raw peer close reason: %s", reason);
            }
            // RFC-9000, section 10.2.2:
            // An endpoint that receives a CONNECTION_CLOSE frame MAY send a single packet containing
            // a CONNECTION_CLOSE frame before entering the draining state, using a NO_ERROR code if
            // appropriate. An endpoint MUST NOT send further packets.
            // if we had previously marked our state as closing, then that implies
            // we would have already sent a connection close frame. we won't send
            // another when draining in such a case.
            if (markClosing(termination)) {
                boolean remapped = false;
                try {
                    if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                        log(System.Logger.Level.DEBUG, "sending CONNECTION_CLOSE frame before entering draining state");
                    }
                    ConnectionCloseFrame outgoingFrame =
                            ConnectionCloseFrame.create(NO_ERROR.code(), incomingFrame.typeField(), null);
                    KeySpace currentKeySpace = connection.tlsEngine().currentSendKeySpace();
                    remapped = pushConnectionCloseFrame(currentKeySpace, outgoingFrame);
                } catch (Exception e) {
                    // just log and ignore, since sending the CONNECTION_CLOSE when entering
                    // draining state is optional
                    log(System.Logger.Level.ERROR,
                              "Failed to send CONNECTION_CLOSE frame when entering draining state", e);
                }
                if (!remapped) {
                    connection.endpoint().draining(connection);
                }
            }
        } finally {
            cleanupConnection(termination, false, cleanupFailure);
        }
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            log(System.Logger.Level.DEBUG, "connection has now transitioned to draining state");
        }
    }

    private void cleanupConnection(QuicTermination termination,
                                   boolean unregister,
                                   Throwable failure) {
        ScopedValue.where(COMPLETING_TERMINATION,
                          new TerminationScope(connection.quicInstance(), currentTerminationScope()))
                .run(() -> cleanupConnectionInScope(termination, unregister, failure));
    }

    private void cleanupConnectionInScope(QuicTermination termination,
                                          boolean unregister,
                                          Throwable failure) {
        failure = attemptCleanup(failure, this::failHandshakeCFs);
        if (unregister) {
            failure = attemptCleanup(failure, this::unregisterConnFromEndpoint);
        }
        failure = attemptCleanup(failure, connection::closeReassemblyBudgets);
        failure = attemptCleanup(failure, () -> connection.packetNumberSpaces().close());
        failure = attemptCleanup(failure, connection::stopPathValidation);
        failure = attemptCleanup(failure, () -> connection.pathManager().close());
        failure = attemptCleanup(failure, connection::closeIncoming);
        failure = attemptCleanup(failure, () -> connection.terminateStreams(termination));
        if (failure != null) {
            completeTermination(termination, failure);
        }
        if (failure instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        if (failure != null) {
            throw new IllegalStateException("Connection cleanup failed", failure);
        }
        completeTermination(termination, null);
    }

    private static Throwable attemptCleanup(Throwable previousFailure, Runnable cleanup) {
        try {
            cleanup.run();
        } catch (Throwable failure) {
            if (previousFailure == null) {
                return failure;
            }
            if (previousFailure != failure) {
                previousFailure.addSuppressed(failure);
            }
        }
        return previousFailure;
    }

    private void terminationFailed(Throwable failure) {
        ScopedValue.where(COMPLETING_TERMINATION,
                          new TerminationScope(connection.quicInstance(), currentTerminationScope())).run(() -> {
            if (cleanupComplete.isDone()) {
                return;
            }
            // Make sure we do fail the handshake CompletableFuture(s) even when
            // connection termination itself failed. That way dependent tasks do
            // not keep waiting forever.
            Throwable cleanupFailure = attemptCleanup(failure, () -> failHandshakeCFs(failure));
            completeTermination(null, cleanupFailure);
        });
    }

    private void completeTermination(QuicTermination termination, Throwable failure) {
        if (failure == null) {
            cleanupComplete.complete(null);
            futureTermination.complete(termination);
        } else {
            cleanupComplete.completeExceptionally(failure);
            futureTermination.completeExceptionally(failure);
        }
    }

    private static TerminationScope currentTerminationScope() {
        return COMPLETING_TERMINATION.isBound() ? COMPLETING_TERMINATION.get() : null;
    }

    private void failHandshakeCFs() {
        QuicTermination termination = this.termination.get();
        failHandshakeCFs(termination.closeCause());
    }

    private void failHandshakeCFs(Throwable cause) {
        HandshakeFlow handshakeFlow = connection.handshakeFlow();
        handshakeFlow.failHandshakeCFs(cause);
    }

    private boolean markClosing(QuicTermination termination) {
        return mark(CLOSING, termination);
    }

    private boolean markDraining(QuicTermination termination) {
        return mark(DRAINING, termination);
    }

    private boolean markClosed(QuicTermination termination) {
        return mark(CLOSED, termination);
    }

    private boolean mark(int mask, QuicTermination termination) {
        this.termination.compareAndSet(null, termination);
        boolean marked = this.connection.stateHandle().mark(mask);
        return marked;
    }

    /**
     * CONNECTION_CLOSE frame is not congestion controlled (RFC-9002 section 3
     * and RFC-9000 section 12.4, table 3), nor is it queued or scheduled for sending.
     * This method constructs a {@link QuicPacket} containing the {@code frame} and immediately
     * {@link QuicConnectionImpl#pushDatagram(ProtectionRecord) pushes the datagram} through
     * the connection.
     *
     * @param keySpace the KeySpace to use for sending the packet
     * @param frame    the CONNECTION_CLOSE frame
     * @throws QuicKeyUnavailableException if the keys for the KeySpace aren't available
     * @throws QuicTransportException      for any QUIC transport exception when sending the packet
     */
    private boolean pushConnectionCloseFrame(KeySpace keySpace,
                                             ConnectionCloseFrame frame)
            throws QuicKeyUnavailableException, QuicTransportException {
        Optional<QuicPathManager.SendPermit> reservation =
                connection.pathManager().reserve(connection.maxDatagramSize());
        if (reservation.isEmpty()) {
            return false;
        }
        return pushConnectionCloseFrame(keySpace, frame, reservation.orElseThrow());
    }

    private boolean pushConnectionCloseFrame(KeySpace keySpace,
                                             ConnectionCloseFrame frame,
                                             QuicPathManager.SendPermit permit)
            throws QuicKeyUnavailableException, QuicTransportException {
        // ConnectionClose frame is allowed in Initial, Handshake, 0-RTT, 1-RTT spaces.
        // for Initial and Handshake space, the frame is expected to be of type 0x1c.
        // see RFC-9000, section 12.4, Table 3 for additional details
        ConnectionCloseFrame toSend = switch (keySpace) {
            case ONE_RTT, ZERO_RTT -> frame;
            case INITIAL, HANDSHAKE -> {
                // RFC 9000 - section 10.2.3:
                // A CONNECTION_CLOSE of type 0x1d MUST be replaced by a CONNECTION_CLOSE
                // of type 0x1c when sending the frame in Initial or Handshake packets.
                // Otherwise, information about the application state might be revealed.
                // Endpoints MUST clear the value of the Reason Phrase field and SHOULD
                // use the APPLICATION_ERROR code when converting to a CONNECTION_CLOSE
                // of type 0x1c.
                yield frame.clearApplicationState();
            }
            default -> {
                throw new IllegalStateException("cannot send a connection close frame"
                                                        + " in keyspace: " + keySpace.text());
            }
        };
        boolean transferred = false;
        try {
            Optional<QuicConnectionId> destinationConnectionId = connection.destinationConnectionId(keySpace, permit);
            if (destinationConnectionId.isEmpty()) {
                return false;
            }
            QuicPacket packet = connection.newQuicPacket(keySpace,
                                                         destinationConnectionId.orElseThrow(),
                                                         List.of(toSend));
            if (packet.size() > permit.size()) {
                return false;
            }
            ProtectionRecord protectionRecord = ProtectionRecord.single(packet,
                                                                        permit.destination(),
                                                                        permit,
                                                                        connection::allocateDatagramForEncryption);
            // while sending the packet containing the CONNECTION_CLOSE frame, the pushDatagram will
            // remap the QuicConnectionImpl in QuicEndpoint.
            transferred = true;
            connection.pushDatagram(protectionRecord);
            return true;
        } finally {
            if (!transferred) {
                permit.release();
            }
        }
    }

    private void log(System.Logger.Level level, String format, Object... arguments) {
        if (arguments.length == 0) {
            connection.log(LOGGER, level, "%s", format);
        } else {
            connection.log(LOGGER, level, format, arguments);
        }
    }

    private void log(System.Logger.Level level, String message, Throwable throwable) {
        connection.log(LOGGER, level, "%s", throwable, message);
    }

    private record TerminationScope(QuicInstance instance, TerminationScope parent) {
    }
}
