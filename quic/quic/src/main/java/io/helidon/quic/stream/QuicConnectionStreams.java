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

package io.helidon.quic.stream;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IllegalFormatException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;
import java.util.stream.Stream;

import io.helidon.common.Api;
import io.helidon.quic.MinimalFuture;
import io.helidon.quic.QuicConnectionImpl;
import io.helidon.quic.QuicStreamLimitException;
import io.helidon.quic.QuicTLSEngine;
import io.helidon.quic.QuicTLSEngine.KeySpace;
import io.helidon.quic.QuicTermination;
import io.helidon.quic.QuicTransportErrors;
import io.helidon.quic.QuicTransportException;
import io.helidon.quic.QuicTransportParameters;
import io.helidon.quic.QuicTransportParameters.ParameterId;
import io.helidon.quic.frame.MaxStreamDataFrame;
import io.helidon.quic.frame.MaxStreamsFrame;
import io.helidon.quic.frame.QuicFrame;
import io.helidon.quic.frame.ResetStreamFrame;
import io.helidon.quic.frame.StopSendingFrame;
import io.helidon.quic.frame.StreamDataBlockedFrame;
import io.helidon.quic.frame.StreamFrame;
import io.helidon.quic.frame.StreamsBlockedFrame;
import io.helidon.quic.packet.QuicPacketEncoder;
import io.helidon.quic.stream.QuicReceiverStream.ReceivingStreamState;
import io.helidon.quic.stream.QuicStream.StreamMode;
import io.helidon.quic.stream.QuicStream.StreamState;

import static io.helidon.quic.stream.QuicStreams.SRV_MASK;
import static io.helidon.quic.stream.QuicStreams.TYPE_MASK;
import static io.helidon.quic.stream.QuicStreams.UNI_MASK;
import static io.helidon.quic.stream.QuicStreams.isBidirectional;
import static io.helidon.quic.stream.QuicStreams.streamType;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * A helper class to help manage Quic streams in a Quic connection.
 */
@Api.Internal
public final class QuicConnectionStreams {
    private static final System.Logger LOGGER = System.getLogger(QuicConnectionStreams.class.getName());
    private static final int DEFAULT_MAX_SMALL_FRAGMENTS = 100;
    private static final int DEFAULT_STREAM_BUFFER_SIZE = 1 << 16;

    // These atomic long ids record the expected next stream ID that
    // should be allocated for the next stream of a given type.
    // The type of a stream is a number in [0..3], and is used
    // as an index in this list.
    private final List<AtomicLong> nextStreamID = List.of(
            new AtomicLong(), // 0: client initiated bidi
            new AtomicLong(SRV_MASK), // 1: server initiated bidi
            new AtomicLong(UNI_MASK),  // 2: client initiated uni
            new AtomicLong(UNI_MASK | SRV_MASK)); // 3: server initiated uni

    // the max uni streams that the current endpoint is allowed to initiate against the peer
    private final StreamCreationPermit localUniMaxStreamLimit = new StreamCreationPermit(0);
    // the max bidi streams that the current endpoint is allowed to initiate against the peer
    private final StreamCreationPermit localBidiMaxStreamLimit = new StreamCreationPermit(0);
    // the stream credit advertised to the remote peer and the credit recovered as those streams retire
    private final RemoteStreamCredit remoteUniStreamCredit = new RemoteStreamCredit();
    private final RemoteStreamCredit remoteBidiStreamCredit = new RemoteStreamCredit();

    private final StreamsContainer streams = new StreamsContainer();

    // A collection of senders which have available data ready to send, (or which possibly
    // are blocked and need to send STREAM_DATA_BLOCKED).
    // A stream stays in the queue until it is blocked or until it
    // has no more data available to send: when a stream has no more data available for sending it is not
    // put back in the queue. It will be put in the queue again when selectForSending is called.
    private final ReadyStreamCollection sendersReady;

    // A map that contains streams for which sending a RESET_STREAM frame was requested
    // and their corresponding error codes.
    // Once the frame has been sent (or has been scheduled to be sent) the stream removed from the map.
    private final ConcurrentMap<QuicSenderStream, Long> sendersReset = new ConcurrentHashMap<>();

    // A map that contains streams for which sending a MAX_STREAM_DATA frame was requested.
    // Once the frame has been sent (or has been scheduled to be sent) the stream removed from the map.
    private final ConcurrentMap<QuicReceiverStream, QuicFrame> receiversSend = new ConcurrentHashMap<>();

    // A queue of remote initiated streams that have not been acquired yet.
    // see pollNewRemoteStreams and addRemoteStreamListener
    private final ConcurrentLinkedQueue<QuicReceiverStream> newRemoteStreams = new ConcurrentLinkedQueue<>();
    // A set of listeners listening to new streams created by the peer
    private final Set<Predicate<? super QuicReceiverStream>> streamListeners = ConcurrentHashMap.newKeySet();
    // A lock to ensure consistency between invocation of streamListeners and
    // the content of the newRemoteStreams queue.
    private final Lock newRemoteStreamsLock = new ReentrantLock();
    // Accessed only while holding newRemoteStreamsLock.
    private boolean remoteStreamsTerminated;
    private final Lock localStreamCreationLock = new ReentrantLock();
    // Accessed only while holding localStreamCreationLock.
    private RuntimeException localStreamTerminationCause;

    // The connection to which the streams managed by this
    // instance of QuicConnectionStreams belong to.
    private final QuicConnectionImpl connection;
    private final int maxSmallFragments;
    private final int streamBufferSize;

    // will hold the highest limit from a STREAMS_BLOCKED frame that was sent by a peer for uni
    // streams. this indicates the peer isn't able to create any more uni streams, past this limit
    private final AtomicLong peerUniStreamsBlocked = new AtomicLong(-1);
    // will hold the highest limit from a STREAMS_BLOCKED frame that was sent by a peer for bidi
    // streams. this indicates the peer isn't able to create any more bidi streams, past this limit
    private final AtomicLong peerBidiStreamsBlocked = new AtomicLong(-1);
    // will hold the highest limit at which the local endpoint couldn't create a uni stream
    // and a STREAMS_BLOCKED was required to be sent. -1 indicates the local endpoint hasn't yet
    // been blocked for stream creation
    private final AtomicLong uniStreamsBlocked = new AtomicLong(-1);
    // will hold the highest limit at which the local endpoint couldn't create a bidi stream
    // and a STREAMS_BLOCKED was required to be sent. -1 indicates the local endpoint hasn't yet
    // been blocked for stream creation
    private final AtomicLong bidiStreamsBlocked = new AtomicLong(-1);
    // will hold the limit with which the local endpoint last sent a STREAMS_BLOCKED frame to the
    // peer for uni streams. -1 indicates no STREAMS_BLOCKED frame has been sent yet. A new
    // STREAMS_BLOCKED will be sent only if "uniStreamsBlocked" exceeds this
    // "lastUniStreamsBlockedSent"
    private final AtomicLong lastUniStreamsBlockedSent = new AtomicLong(-1);
    // will hold the limit with which the local endpoint last sent a STREAMS_BLOCKED frame to the
    // peer for bidi streams. -1 indicates no STREAMS_BLOCKED frame has been sent yet. A new
    // STREAMS_BLOCKED will be sent only if "bidiStreamsBlocked" exceeds this
    // "lastBidiStreamsBlockedSent"
    private final AtomicLong lastBidiStreamsBlockedSent = new AtomicLong(-1);
    // streams that have been blocked and aren't able to send data to the peer,
    // due to reaching flow control limit imposed on those streams by the peer.
    private final Set<Long> flowControlBlockedStreams = ConcurrentHashMap.newKeySet();

    // A QuicConnectionStream instance can be tied to a client connection
    // or a server connection.
    // If the connection is a client connection, then localFlag=0x00,
    //   localBidi=0x00, remoteBidi=0x01, localUni=0x02, remoteUni=0x03
    // If the connection is a server connection, then localFlag=0x01,
    //   localBidi=0x01, remoteBidi=0x00, localUni=0x03, remoteUni=0x02
    private final int localFlag;
    private final int localBidi;
    private final int remoteBidi;
    private final int localUni;
    private final int remoteUni;

    /**
     * Creates a new instance of {@code QuicConnectionStreams} for the
     * given connection. There is a 1-1 relationship between a
     * {@code QuicConnectionImpl} instance and a {@code QuicConnectionStreams}
     * instance.
     *
     * @param connection the connection to which the streams managed by this
     *                   instance of {@code QuicConnectionStreams} belong
     */
    private QuicConnectionStreams(QuicConnectionImpl connection) {
        this.connection = Objects.requireNonNull(connection);
        this.maxSmallFragments = DEFAULT_MAX_SMALL_FRAGMENTS;
        this.streamBufferSize = DEFAULT_STREAM_BUFFER_SIZE;
        // implicit null check for connection
        boolean isClient = connection.isClientConnection();
        localFlag = isClient ? 0 : SRV_MASK;
        localBidi = isClient ? 0 : SRV_MASK;
        remoteBidi = isClient ? SRV_MASK : 0;
        localUni = isClient ? UNI_MASK : UNI_MASK | SRV_MASK;
        remoteUni = isClient ? UNI_MASK | SRV_MASK : UNI_MASK;
        sendersReady = isClient
                ? new ReadyStreamQueue()
                : serverReadyStreams();
    }

    /**
     * Creates a new stream manager for the given connection.
     *
     * @param connection the connection this stream manager belongs to
     * @return new connection stream manager
     */
    public static QuicConnectionStreams create(QuicConnectionImpl connection) {
        return new QuicConnectionStreams(connection);
    }

    /**
     * Returns the next unallocated stream ID that would be expected for a stream of the given type.
     * This method expects {@code streamType} to be a number in [0..3] but
     * does not check it.
     *
     * @param streamType The stream type, a number in [0..3]
     * @return next unallocated stream ID expected for a stream of the given type
     */
    public long peekNextStreamId(int streamType) {
        var id = nextStreamID.get(streamType & 0x03);
        return id.get();
    }

    /**
     * Creates a new locally initiated unidirectional stream.
     * <p>
     * If the stream cannot be created due to stream creation limit being reached, then this method
     * will return a {@code CompletableFuture} which will complete either when the {@code timeout}
     * has reached or the stream limit has been increased and the stream creation was successful.
     * If the stream creation doesn't complete within the specified timeout then the returned
     * {@code CompletableFuture} will complete exceptionally with a {@link QuicStreamLimitException}
     *
     * @param timeout the maximum duration to wait to acquire a permit for stream creation
     * @return a CompletableFuture whose result on successful completion will return the newly
     *        created {@code QuicSenderStream}
     */
    public CompletableFuture<QuicSenderStream> createNewLocalUniStream(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        @SuppressWarnings("unchecked")
        var streamCF = (CompletableFuture<QuicSenderStream>) createNewLocalStream(localUni,
                                                                                  StreamMode.WRITE_ONLY, timeout);
        return streamCF;
    }

    /**
     * Creates a new locally initiated bidirectional stream.
     * <p>
     * If the stream cannot be created due to stream creation limit being reached, then this method
     * will return a {@code CompletableFuture} which will complete either when the {@code timeout}
     * has reached or the stream limit has been increased and the stream creation was successful.
     * If the stream creation doesn't complete within the specified timeout then the returned
     * {@code CompletableFuture} will complete exceptionally with a {@link QuicStreamLimitException}
     *
     * @param timeout the maximum duration to wait to acquire a permit for stream creation
     * @return a CompletableFuture whose result on successful completion will return the newly
     *        created {@code QuicBidiStream}
     */
    public CompletableFuture<QuicBidiStream> createNewLocalBidiStream(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        @SuppressWarnings("unchecked")
        var streamCF = (CompletableFuture<QuicBidiStream>) createNewLocalStream(localBidi,
                                                                                StreamMode.READ_WRITE, timeout);
        return streamCF;
    }

    /**
     * Reserves credit for one locally initiated bidirectional stream.
     *
     * <p>The returned future waits until peer-advertised stream credit is available. Canceling it removes the pending
     * credit waiter. A completed reservation owns the credit until it is opened or closed.
     *
     * @return future completed with reserved bidirectional-stream credit
     */
    public CompletableFuture<QuicBidiStreamReservation> reserveNewLocalBidiStream() {
        StreamCreationPermit permit = localBidiMaxStreamLimit;
        long currentLimit;
        localStreamCreationLock.lock();
        try {
            if (localStreamTerminationCause != null) {
                return MinimalFuture.failedMinimalFuture(localStreamTerminationCause);
            }
            currentLimit = permit.currentLimit();
            if (permit.tryAcquire()) {
                return MinimalFuture.completedMinimalFuture(new QuicBidiStreamReservationImpl(this));
            }
        } finally {
            localStreamCreationLock.unlock();
        }

        announceStreamsBlocked(true, currentLimit);
        CompletableFuture<Boolean> acquisition = permit.tryAcquire(connection.quicInstance().executor());
        var reservationFuture = MinimalFuture.<QuicBidiStreamReservation>create();
        reservationFuture.whenComplete((_, _) -> {
            if (reservationFuture.isCancelled()) {
                acquisition.cancel(false);
            }
        });
        acquisition.whenComplete((acquired, failure) -> {
            if (failure != null) {
                reservationFuture.completeExceptionally(failure);
                return;
            }
            if (!acquired) {
                reservationFuture.completeExceptionally(new IllegalStateException("Stream credit was not acquired"));
                return;
            }
            localStreamCreationLock.lock();
            try {
                if (localStreamTerminationCause != null) {
                    permit.releaseAcquisition();
                    reservationFuture.completeExceptionally(localStreamTerminationCause);
                    return;
                }
                var reservation = new QuicBidiStreamReservationImpl(this);
                if (!reservationFuture.complete(reservation)) {
                    reservation.close();
                }
            } finally {
                localStreamCreationLock.unlock();
            }
        });
        return reservationFuture;
    }

    /**
     * Runs the APPLICATION space packet transmitter, if necessary, to potentially trigger
     * sending a MAX_STREAMS frame to the peer, upon receiving the STREAMS_BLOCKED {@code frame}
     * from that peer.
     *
     * @param frame the STREAMS_BLOCKED frame that was received from the peer
     */
    public void peerStreamsBlocked(StreamsBlockedFrame frame) {
        boolean bidi = frame.isBidi();
        long blockedOnLimit = frame.maxStreams();
        AtomicLong blockedState = bidi ? this.peerBidiStreamsBlocked : this.peerUniStreamsBlocked;
        RemoteStreamCredit streamCredit = bidi ? remoteBidiStreamCredit : remoteUniStreamCredit;
        long currentLimit = streamCredit.currentLimit();
        clearPeerStreamsBlockedBelow(blockedState, currentLimit);
        if (blockedOnLimit != currentLimit) {
            return;
        }
        boolean runTransmitter = false;
        long prevBlockedLimit = blockedState.get();
        while (blockedOnLimit > prevBlockedLimit) {
            if (blockedState.compareAndSet(prevBlockedLimit, blockedOnLimit)) {
                currentLimit = streamCredit.currentLimit();
                if (blockedOnLimit != currentLimit) {
                    blockedState.compareAndSet(blockedOnLimit, -1);
                } else {
                    runTransmitter = true;
                }
                break;
            }
            prevBlockedLimit = blockedState.get();
        }
        if (runTransmitter) {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                log(System.Logger.Level.DEBUG, "requesting packet transmission in response to receiving "
                        + (bidi ? "bidi" : "uni") + " STREAMS_BLOCKED from peer,"
                        + " blocked with limit " + blockedOnLimit);
            }
            this.connection.runAppPacketSpaceTransmitter();
        }
    }

    /**
     * Gets or opens a remotely initiated stream with the given stream ID.
     * Creates all streams with lower IDs if needed.
     *
     * @param streamId  the stream ID
     * @param frameType type of the frame received, used in exceptions
     * @return a remotely initiated stream with the given stream ID, or an empty optional if the stream was already closed
     * @throws IllegalArgumentException if the streamID is of the wrong type for
     *                                 a remote stream.
     * @throws QuicTransportException   if the streamID is higher than allowed
     */
    public Optional<QuicStream> ensureRemoteStream(long streamId, long frameType)
            throws QuicTransportException {
        return ensureRemoteStream(streamId, frameType, null);
    }

    /**
     * Processes the first STREAM frame for a remotely initiated stream, creating all streams with lower IDs if needed.
     * The frame is applied to the target stream before any newly created streams are offered to remote-stream listeners.
     *
     * @param frame received STREAM frame
     * @throws IllegalArgumentException if the stream ID is of the wrong type for a remote stream
     * @throws QuicTransportException   if the stream ID is higher than allowed or the frame is invalid
     */
    public void processInitialRemoteStreamFrame(StreamFrame frame) throws QuicTransportException {
        Objects.requireNonNull(frame, "frame");
        ensureRemoteStream(frame.streamId(), frame.typeField(), frame);
    }

    private Optional<QuicStream> ensureRemoteStream(long streamId, long frameType, StreamFrame initialFrame)
            throws QuicTransportException {
        int streamType = streamType(streamId);
        if ((streamId & SRV_MASK) == localFlag) {
            throw new IllegalArgumentException("bad remote stream type %s for stream %s"
                                                       .formatted(streamType, streamId));
        }
        boolean bidi = isBidirectional(streamId);
        long maxStreamLimit = bidi ? this.remoteBidiStreamCredit.currentLimit()
                : this.remoteUniStreamCredit.currentLimit();
        if (maxStreamLimit <= (streamId >> 2)) {
            throw new QuicTransportException("stream ID %s exceeds the number of allowed streams(%s)"
                                                     .formatted(streamId, maxStreamLimit),
                                             QuicTLSEngine.KeySpace.ONE_RTT,
                                             frameType,
                                             QuicTransportErrors.STREAM_LIMIT_ERROR,
                                             streamId);
        }

        newRemoteStreamsLock.lock();
        try {
            if (remoteStreamsTerminated) {
                return Optional.empty();
            }
            var id = nextStreamID.get(streamType);
            long nextId = id.get();
            if (nextId > streamId) {
                // already created
                QuicStream stream = streams.get(streamId);
                if (stream != null && initialFrame != null) {
                    receiverImpl(stream).processIncomingFrame(initialFrame);
                }
                return Optional.ofNullable(stream);
            }
            // id must not be modified outside newRemoteStreamsLock
            id.getAndSet(streamId + 4);

            AbstractQuicStream stream = null;
            List<QuicReceiverStream> impliedRemoteStreams = null;
            for (long i = nextId; i <= streamId; i += 4) {
                stream = QuicStreams.createStream(connection, i, maxSmallFragments, streamBufferSize);
                register(i, stream, initialFrame == null);
                if (initialFrame != null && i < streamId) {
                    if (impliedRemoteStreams == null) {
                        impliedRemoteStreams = new ArrayList<>();
                    }
                    impliedRemoteStreams.add((QuicReceiverStream) stream);
                }
            }
            if (initialFrame != null) {
                receiverImpl(stream).processIncomingFrame(initialFrame);
                if (impliedRemoteStreams != null) {
                    newRemoteStreams.addAll(impliedRemoteStreams);
                }
                newRemoteStreams.add((QuicReceiverStream) stream);
                acceptRemoteStreamsLocked();
            }
            return Optional.of(stream);
        } finally {
            newRemoteStreamsLock.unlock();
        }
    }

    /**
     * Finds a stream with the given stream ID.
     *
     * @param streamId a stream ID
     * @return the stream with the given stream ID if found
     */
    public Optional<QuicStream> findStream(long streamId) {
        return Optional.ofNullable(streams.get(streamId));
    }

    /**
     * Adds a listener that will be invoked when a remote stream is
     * created.
     *
     * <p>The listener is first offered remote streams that are already open and unclaimed. Each offered stream is either a
     * {@link QuicBidiStream} or a {@link QuicReceiverStream}, according to its
     * {@linkplain QuicStreams#streamType(long) stream type}. Returning {@code true} claims the stream; returning
     * {@code false} leaves it available to another listener.
     *
     * @param streamConsumer the listener
     * @return {@code true} if the listener was registered, {@code false} if stream delivery was terminated
     */
    public boolean addRemoteStreamListener(Predicate<? super QuicReceiverStream> streamConsumer) {
        newRemoteStreamsLock.lock();
        try {
            if (remoteStreamsTerminated) {
                return false;
            }
            streamListeners.add(streamConsumer);
            try {
                acceptRemoteStreamsLocked();
            } catch (RuntimeException | Error e) {
                streamListeners.remove(streamConsumer);
                throw e;
            }
            return !remoteStreamsTerminated;
        } finally {
            newRemoteStreamsLock.unlock();
        }
    }

    /**
     * Removes a listener previously added with {@link #addRemoteStreamListener(Predicate)}.
     *
     * @param streamConsumer listener to remove
     * @return {@code true} if the listener was found and removed, {@code false} otherwise
     */
    public boolean removeRemoteStreamListener(Predicate<? super QuicReceiverStream> streamConsumer) {
        newRemoteStreamsLock.lock();
        try {
            return streamListeners.remove(streamConsumer);
        } finally {
            newRemoteStreamsLock.unlock();
        }
    }

    /**
     * Returns all currently active {@link QuicStream} instances in the connection.
     *
     * @return stream of all currently active streams in the connection
     */
    public Stream<? extends QuicStream> quicStreams() {
        return streams.all();
    }

    /**
     * Returns whether there is some data to send.
     *
     * <p>Note: This method may return true in the case where a
     *        STREAM_DATA_BLOCKED frame needs to be sent, even if no
     *        other data is available.
     *
     * @return {@code true} if there is some data to send
     */
    public boolean hasAvailableData() {
        return !sendersReady.isEmpty();
    }

    /**
     * Returns whether there are control frames to send.
     * Typically, these are STREAMS_BLOCKED, MAX_STREAMS, RESET_STREAM, STOP_SENDING, and
     * MAX_STREAM_DATA.
     *
     * @return {@code true} if there are control frames to send
     */
    public boolean hasControlFrames() {
        return !sendersReset.isEmpty() || !receiversSend.isEmpty()
                // either of these implies we can send a MAX_STREAMS frame
                || shouldSendMaxStreams(false) || shouldSendMaxStreams(true)
                // either of these imply we should send a STREAMS_BLOCKED frame
                || uniStreamsBlocked.get() > lastUniStreamsBlockedSent.get()
                || bidiStreamsBlocked.get() > lastBidiStreamsBlockedSent.get();
    }

    /**
     * Returns whether the given {@code streamId} indicates a stream that has a receiving part.
     * In other words, returns {@code true} if the given stream is either
     * bidirectional or peer-initiated.
     *
     * @param streamId a stream ID
     * @return {@code true} if the given {@code streamId} indicates a stream that has a receiving part
     */
    public boolean isReceivingStream(long streamId) {
        return !isLocalUni(streamId);
    }

    /**
     * Returns whether the given {@code streamId} indicates a stream that has a sending part.
     * In other words, returns {@code true} if the given stream is either
     * bidirectional or local-initiated.
     *
     * @param streamId a stream ID
     * @return {@code true} if the given {@code streamId} indicates a stream that has a sending part
     */
    public boolean isSendingStream(long streamId) {
        return !isRemoteUni(streamId);
    }

    /**
     * Returns whether the given {@code streamId} indicates a local unidirectional stream.
     *
     * @param streamId a stream ID
     * @return {@code true} if the given {@code streamId} indicates a local unidirectional stream
     */
    public boolean isLocalUni(long streamId) {
        return streamType(streamId) == localUni;
    }

    /**
     * Returns whether the given {@code streamId} indicates a local bidirectional stream.
     *
     * @param streamId a stream ID
     * @return {@code true} if the given {@code streamId} indicates a local bidirectional stream
     */
    public boolean isLocalBidi(long streamId) {
        return streamType(streamId) == localBidi;
    }

    /**
     * Returns whether the given {@code streamId} indicates a peer initiated unidirectional stream.
     *
     * @param streamId a stream ID
     * @return {@code true} if the given {@code streamId} indicates a peer initiated unidirectional stream
     */
    public boolean isRemoteUni(long streamId) {
        return streamType(streamId) == remoteUni;
    }

    /**
     * Returns whether the given {@code streamId} indicates a peer initiated bidirectional stream.
     *
     * @param streamId a stream ID
     * @return {@code true} if the given {@code streamId} indicates a peer initiated bidirectional stream
     */
    public boolean isRemoteBidi(long streamId) {
        return streamType(streamId) == remoteBidi;
    }

    /**
     * Mark the stream whose ID is encoded in the given
     * {@code ResetStreamFrame} as needing a RESET_STREAM frame to be sent.
     * It will put the stream and the frame in the {@code sendersReset} map.
     *
     * @param streamId  the id of the stream that should be reset
     * @param errorCode the application error code
     */
    public void requestResetStream(long streamId, long errorCode) {
        var stream = senderImpl(streams.get(streamId));
        if (stream == null) {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                logStreamDebug(streamId, "Can't reset stream: no such stream");
            }
            return;
        }
        sendersReset.putIfAbsent(stream, errorCode);
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            logStreamDebug(streamId, "Reset stream scheduled");
        }
    }

    /**
     * Mark the stream whose ID is encoded in the given
     * {@code MaxStreamDataFrame} as needing a MAX_STREAM_DATA frame to be sent.
     * It will put the stream and the frame in the {@code receiversSend} map.
     *
     * @param maxStreamDataFrame the MAX_STREAM_DATA frame to send
     */
    public void requestSendMaxStreamData(MaxStreamDataFrame maxStreamDataFrame) {
        Objects.requireNonNull(maxStreamDataFrame, "maxStreamDataFrame");
        long streamId = maxStreamDataFrame.streamID();
        var stream = streams.get(streamId);
        if (stream == null) {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                logStreamDebug(streamId, "Can't send MaxStreamDataFrame: no such stream");
            }
            return;
        }
        if (stream instanceof QuicReceiverStream receiver) {
            // don't replace a stop sending frame, and don't replace
            // a max stream data frame if it has a bigger max stream data
            receiversSend.compute(receiver, (s, frame) -> {
                if (frame instanceof StopSendingFrame stopSendingFrame) {
                    // no need to send max data frame if we are requesting
                    // stop sending
                    return frame;
                }
                if (frame instanceof MaxStreamDataFrame maxFrame) {
                    if (maxFrame.maxStreamData() > maxStreamDataFrame.maxStreamData()) {
                        // send the frame that has the greater max data
                        return maxFrame;
                    } else {
                        return maxStreamDataFrame;
                    }
                }
                return maxStreamDataFrame;
            });
        } else {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                logStreamDebug(streamId, "Can't send %s: not a receiver stream",
                               maxStreamDataFrame.getClass());
            }
        }
    }

    /**
     * Mark the stream whose ID is encoded in the given
     * {@code StopSendingFrame} as needing a STOP_SENDING frame to be sent.
     * It will put the stream and the frame in the {@code receiversSend} map.
     *
     * @param stopSendingFrame the STOP_SENDING frame to send
     */
    public void scheduleStopSendingFrame(StopSendingFrame stopSendingFrame) {
        Objects.requireNonNull(stopSendingFrame, "stopSendingFrame");
        long streamId = stopSendingFrame.streamID();
        var stream = streams.get(streamId);
        if (stream == null) {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                logStreamDebug(streamId, "Can't send STOP_SENDING: no such stream");
            }
            return;
        }
        if (stream instanceof QuicReceiverStream receiver) {
            // don't need to check if we already have a frame registered:
            // stop sending takes precedence.
            receiversSend.put(receiver, stopSendingFrame);
        } else {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                logStreamDebug(streamId, "Can't send %s: not a receiver stream",
                               stopSendingFrame.getClass());
            }
        }
    }

    /**
     * Called when the RESET_STREAM frame is acknowledged by the peer.
     *
     * @param reset the RESET_STREAM frame
     */
    public void streamResetAcknowledged(ResetStreamFrame reset) {
        Objects.requireNonNull(reset, "reset");
        long streamId = reset.streamId();
        var stream = streams.get(streamId);
        if (stream == null) {
            return;
        }
        var sender = senderImpl(stream);
        if (sender != null) {
            sender.resetAcknowledged(reset.finalSize());
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                logStreamDebug(streamId, "acknowledged reset");
            }
        }
    }

    /**
     * Called when the final STREAM frame is acknowledged by the peer.
     *
     * @param streamFrame the final STREAM frame
     */
    public void streamDataSentAcknowledged(StreamFrame streamFrame) {
        long streamId = streamFrame.streamId();
        var stream = streams.get(streamId);
        if (stream == null) {
            return;
        }
        var sender = senderImpl(stream);
        if (sender != null) {
            sender.dataAcknowledged(streamFrame.offset() + streamFrame.dataLength());
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                logStreamDebug(streamId, "acknowledged data");
            }
        }
    }

    /**
     * Called when new local transport parameters are available.
     *
     * @param params the new local transport parameters
     */
    public void newLocalTransportParameters(QuicTransportParameters params) {
        // the limit imposed on the remote peer by the local endpoint
        long newRemoteUniMax = params.intParameter(ParameterId.initial_max_streams_uni);
        this.remoteUniStreamCredit.initialize(newRemoteUniMax);
        clearPeerStreamsBlockedBelow(peerUniStreamsBlocked, remoteUniStreamCredit.currentLimit());
        long newRemoteBidiMax = params.intParameter(ParameterId.initial_max_streams_bidi);
        this.remoteBidiStreamCredit.initialize(newRemoteBidiMax);
        clearPeerStreamsBlockedBelow(peerBidiStreamsBlocked, remoteBidiStreamCredit.currentLimit());
        streams.all().forEach(s -> newInitialLocalParameters(s, params));
    }

    /**
     * Called when new peer transport parameters are available.
     *
     * @param params the new local transport parameters
     */
    public void newPeerTransportParameters(QuicTransportParameters params) {
        // the limit imposed on the local endpoint by the remote peer
        long localUniMaxStreams = params.intParameter(ParameterId.initial_max_streams_uni);
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            log(System.Logger.Level.DEBUG, "increasing localUniMaxStreamLimit to initial_max_streams_uni: "
                    + localUniMaxStreams);
        }
        this.localUniMaxStreamLimit.tryIncreaseLimitTo(localUniMaxStreams);
        long localBidiMaxStreams = params.intParameter(ParameterId.initial_max_streams_bidi);
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            log(System.Logger.Level.DEBUG, "increasing localBidiMaxStreamLimit to initial_max_streams_bidi: "
                    + localBidiMaxStreams);
        }
        this.localBidiMaxStreamLimit.tryIncreaseLimitTo(localBidiMaxStreams);
        // set initial parameters on streams
        streams.all().forEach(s -> newInitialPeerParameters(s, params));
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            log(System.Logger.Level.DEBUG, "all streams updated (%s)", streams.streams.size());
        }
    }

    /**
     * Set max stream data for a stream.
     * Called when a {@link io.helidon.quic.frame.MaxStreamDataFrame
     * MaxStreamDataFrame} is received.
     *
     * @param stream        the stream
     * @param maxStreamData the max data that the peer is willing to accept on this stream
     */
    public void updateMaxStreamData(QuicSenderStream stream, long maxStreamData) {
        var sender = senderImpl(stream);
        if (sender != null) {
            long newFinalizedLimit = sender.updateMaxStreamData(maxStreamData);
            // if the connection was tracking this stream as blocked due to flow control
            // and if this new MAX_STREAM_DATA limit unblocked this stream, then
            // stop tracking the stream.
            if (newFinalizedLimit == maxStreamData) { // the proposed limit was accepted
                if (!sender.isBlocked()) {
                    untrackBlockedStream(stream.streamId());
                }
            }
        }
    }

    /**
     * This method is called when a {@link
     * io.helidon.quic.frame.StopSendingFrame} is received
     * from the peer.
     *
     * @param stream    the stream for which stop sending was requested
     *                 by the peer
     * @param errorCode the error code
     */
    public void stopSendingReceived(QuicSenderStream stream, long errorCode) {
        var sender = senderImpl(stream);
        if (sender != null) {
            // if the stream was being tracked as blocked from sending data,
            // due to flow control limits imposed by the peer, then we now
            // stop tracking it since the peer no longer wants us to send data
            // on this stream.
            untrackBlockedStream(stream.streamId());
            sender.stopSendingReceived(errorCode);
        }
    }

    /**
     * Called when the receiving part or the sending part of a stream
     * reaches a terminal state.
     *
     * @param streamId the id of the stream
     * @param state    the terminal state
     */
    public void notifyTerminalState(long streamId, StreamState state) {
        var stream = streams.get(streamId);
        if (stream != null) {
            removeStream(streamId, stream);
        }
    }

    /**
     * Called when the connection is closed by the higher level
     * protocol.
     *
     * @param termination selected connection termination
     */
    public void terminate(QuicTermination termination) {
        RuntimeException closeCause = termination.closeCause();
        localStreamCreationLock.lock();
        try {
            if (localStreamTerminationCause == null) {
                localStreamTerminationCause = closeCause;
                localUniMaxStreamLimit.terminate(closeCause);
                localBidiMaxStreamLimit.terminate(closeCause);
            }
        } finally {
            localStreamCreationLock.unlock();
        }
        newRemoteStreamsLock.lock();
        try {
            remoteStreamsTerminated = true;
            streamListeners.clear();
            newRemoteStreams.clear();
        } finally {
            newRemoteStreamsLock.unlock();
        }
        // make sure all active streams are woken up when we close a connection
        streams.all().forEach((stream) -> {
            if (stream instanceof QuicSenderStream) {
                var sender = senderImpl(stream);
                try {
                    sender.terminate(termination);
                } catch (Throwable t) {
                    if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                        logStreamDebug(sender.streamId(), "failed to close sender stream: %s", t);
                    }
                }
            }
            if (stream instanceof QuicReceiverStream) {
                var receiver = receiverImpl(stream);
                try {
                    receiver.terminate(termination);
                } catch (Throwable t) {
                    // log and ignore
                    if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                        logStreamDebug(receiver.streamId(), "failed to close receiver stream: %s", t);
                    }
                }
            }
        });
    }

    /**
     * This method is called by when a stream has data available for sending.
     *
     * @param streamId the stream id of the stream which is ready
     * @see QuicConnectionImpl#streamDataAvailableForSending
     */
    public void enqueueForSending(long streamId) {
        var stream = streams.get(streamId);
        if (stream == null) {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                logStreamDebug(streamId, "WARNING: stream not found");
            }
            return;
        }
        if (stream instanceof QuicSenderStream sender) {
            // The client queue permits repeated entries for round-robin dispatch.
            // The server queue coalesces repeated producer and scheduler notifications.
            sendersReady.add(sender);
        } else {
            String msg = String.format("Stream %s not a sending or bidi stream: %s",
                                       streamId, stream.getClass().getName());
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                logStreamDebug(streamId, "WARNING: not a sending or bidi stream: %s",
                               stream.getClass().getName());
            }
            throw new AssertionError(msg);
        }
    }

    /**
     * This method is called when a sender has data available for sending.
     *
     * @param sender the sender which is ready
     * @see QuicConnectionImpl#streamDataAvailableForSending
     */
    public void enqueueForSending(QuicSenderStreamImpl sender) {
        Objects.requireNonNull(sender, "sender");
        if (sender.connection() != connection) {
            throw new IllegalArgumentException("Stream belongs to another connection");
        }
        if (sender.sendingState().isSending()) {
            sendersReady.add(sender);
        }
    }

    /**
     * If there are any streams in this connection that have been blocked from sending
     * data due to flow control limit on that stream, then this method enqueues a
     * {@code STREAM_DATA_BLOCKED} frame to be sent for each such stream.
     */
    public void enqueueStreamDataBlocked() {
        connection.streamDataAvailableForSending(this.flowControlBlockedStreams);
    }

    /**
     * Called when a StreamFrame is received.
     *
     * @param stream the stream for which the StreamFrame was received
     * @param frame  the stream frame
     * @throws QuicTransportException if an error occurred processing the frame
     */
    public void processIncomingFrame(QuicStream stream, StreamFrame frame) throws QuicTransportException {
        var receiver = receiverImpl(stream);
        receiver.processIncomingFrame(frame);
    }

    /**
     * Called when a ResetStreamFrame is received.
     *
     * @param stream the stream for which the ResetStreamFrame was received
     * @param frame  the reset stream frame
     * @throws QuicTransportException if an error occurred processing the frame
     */
    public void processIncomingFrame(QuicStream stream, ResetStreamFrame frame) throws QuicTransportException {
        var receiver = receiverImpl(stream);
        receiver.processIncomingResetFrame(frame);
    }

    /**
     * Called when a StreamDataBlockedFrame is received.
     *
     * @param stream the stream for which the StreamDataBlockedFrame was received
     * @param frame  the stream-data-blocked frame
     */
    public void processIncomingFrame(QuicStream stream, StreamDataBlockedFrame frame) {
        QuicReceiverStreamImpl rcvrStream = receiverImpl(stream);
        rcvrStream.processIncomingFrame(frame);
    }

    /**
     * Tries to increase the locally allowed stream creation limit from a received MAX_STREAMS frame.
     *
     * @param maxStreamsFrame received MAX_STREAMS frame
     * @return {@code true} if the limit increased, {@code false} otherwise
     */
    public boolean tryIncreaseStreamLimit(MaxStreamsFrame maxStreamsFrame) {
        StreamCreationPermit permit = maxStreamsFrame.isBidi()
                ? localBidiMaxStreamLimit : localUniMaxStreamLimit;
        long newLimit = maxStreamsFrame.maxStreams();
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            if (maxStreamsFrame.isBidi()) {
                log(System.Logger.Level.DEBUG, "increasing localBidiMaxStreamLimit limit to: " + newLimit);
            } else {
                log(System.Logger.Level.DEBUG, "increasing localUniMaxStreamLimit limit to: " + newLimit);
            }
        }
        return permit.tryIncreaseLimitTo(newLimit);
    }

    /**
     * Returns the next advertised MAX_STREAMS value for the selected directionality.
     *
     * @param bidi {@code true} for bidirectional streams, {@code false} for unidirectional streams
     * @return next MAX_STREAMS limit to advertise
     */
    public long nextMaxStreamsLimit(boolean bidi) {
        return nextMaxStreamsLimit(bidi, false);
    }

    /**
     * Returns the next advertised MAX_STREAMS value for the selected directionality.
     *
     * @param bidi        {@code true} for bidirectional streams, {@code false} for unidirectional streams
     * @param peerBlocked whether the peer reported that it is blocked on the current limit
     * @return next MAX_STREAMS limit to advertise
     */
    public long nextMaxStreamsLimit(boolean bidi, boolean peerBlocked) {
        return bidi ? remoteBidiStreamCredit.nextLimit(peerBlocked)
                : remoteUniStreamCredit.nextLimit(peerBlocked);
    }

    /**
     * Returns whether any stream on this connection is blocked from sending data due to flow control limit.
     *
     * @return {@code true} if any stream on this connection is blocked from sending data due to flow control limit
     */
    public boolean hasBlockedStreams() {
        return !this.flowControlBlockedStreams.isEmpty();
    }

    /**
     * Returns whether a ready stream can dispatch a FIN without connection flow-control credit.
     *
     * @return whether zero-credit stream work is ready
     */
    public boolean hasZeroCreditSendableData() {
        return sendersReady.hasPendingZeroLengthEndOfStream();
    }

    /**
     * Package available data in {@link StreamFrame} instances and add them
     * to the provided frames list. Additional frames, like connection control frames
     * {@code STREAMS_BLOCKED}, {@code MAX_STREAMS} or stream flow control frames like
     * {@code STREAM_DATA_BLOCKED} may also be added if space allows. The {@link StreamDataBlockedFrame}
     * is added only once for a given stream, until the stream becomes ready again.
     *
     * @param encoder           the {@link QuicPacketEncoder}, used if anything is quic version
     *                         dependent.
     * @param maxSize           the cumulated maximum size of all the frames
     * @param maxConnectionData the maximum number of stream data bytes that can
     *                         be packaged to respect connection flow control
     *                         constraints
     * @param frames            a list of frames in which to add the packaged data
     * @return the total number of stream data bytes packaged in the created
     *        frames. This will not exceed the given {@code maxConnectionData}.
     * @throws QuicTransportException if packaging stream data violates transport constraints
     * @implSpec The total cumulated size of the returned frames must not exceed {@code maxSize}.
     *        The total cumulated lengths of the returned frames must not exceed {@code maxConnectionData}.
     */
    public long produceFramesToSend(QuicPacketEncoder encoder, long maxSize,
                                    long maxConnectionData, List<QuicFrame> frames)
            throws QuicTransportException {
        long remaining = maxSize;
        long produced = 0;
        ArrayList<Long> zeroCreditDeferred = null;
        int zeroCreditStreamsRemaining = -1;
        try {
            remaining -= checkResetAndOtherControls(frames, remaining);
            // scan the streams and compose a list of frames - possibly including
            // stream data blocked frames,
            QuicSenderStreamImpl sender;
            NEXT_STREAM:
            while (remaining > 0 && (maxConnectionData != 0 || zeroCreditStreamsRemaining != 0)) {
                sender = senderImpl(sendersReady.poll());
                if (sender == null) {
                    break;
                }
                long streamId = sender.streamId();
                if (maxConnectionData == 0) {
                    if (zeroCreditStreamsRemaining < 0) {
                        zeroCreditStreamsRemaining = sendersReady.size() + 1;
                    }
                    zeroCreditStreamsRemaining--;
                }
                boolean stillReady = true;
                try {
                    do {
                        if (remaining == 0) {
                            break;
                        }
                        var state = sender.sendingState();
                        switch (state) {
                        case SEND -> {
                            if (maxConnectionData == 0 && !sender.hasPendingZeroLengthEndOfStream()) {
                                stillReady = false;
                                if (zeroCreditDeferred == null) {
                                    zeroCreditDeferred = new ArrayList<>();
                                }
                                zeroCreditDeferred.add(streamId);
                                continue NEXT_STREAM;
                            }
                            long offset = sender.dataSent();
                            int headerSize = StreamFrame.headerSize(encoder, streamId, offset, remaining);
                            if (headerSize >= remaining) {
                                break NEXT_STREAM;
                            }
                            long maxControlled = Math.min(maxConnectionData, remaining - headerSize);
                            int maxData = (int) Math.min(Integer.MAX_VALUE, maxControlled);
                            if (maxData < 0
                                    || (maxData == 0 && !sender.hasPendingZeroLengthEndOfStream())) {
                                break NEXT_STREAM;
                            }
                            ByteBuffer buffer = sender.poll(maxData);
                            if (buffer != null) {
                                int length = buffer.remaining();
                                long streamSize = sender.streamSize();
                                boolean fin = streamSize >= 0 && streamSize == offset + length;
                                if (fin) {
                                    stillReady = false;
                                }
                                if (length > 0 || fin) {
                                    StreamFrame frame = StreamFrame.createOwned(streamId, offset, length, fin, buffer);
                                    int size = frame.size();
                                    if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                                        logStreamDebug(streamId, "Adding StreamFrame: %s", frame);
                                    }
                                    frames.add(frame);
                                    remaining -= size;
                                    produced += length;
                                    maxConnectionData -= length;
                                }
                            }
                            var blocked = sender.isBlocked();
                            if (blocked) {
                                // track this stream as blocked due to flow control
                                trackBlockedStream(streamId);
                                var dataBlocked = StreamDataBlockedFrame.create(streamId, sender.dataSent());
                                // This might produce multiple StreamDataBlocked frames
                                // if the stream was added to sendersReady multiple times, so
                                // we check before actually sending a STREAM_DATA_BLOCKED frame
                                if (!frames.contains(dataBlocked)) {
                                    var fdbSize = dataBlocked.size();
                                    if (dataBlocked.size() > remaining) {
                                        // keep the stream in the ready list if we haven't been
                                        // able to generate the StreamDataBlockedFrame
                                        break NEXT_STREAM;
                                    }
                                    if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                                        logStreamDebug(streamId, "sender is blocked: %s", dataBlocked);
                                    }
                                    frames.add(dataBlocked);
                                    remaining -= fdbSize;
                                }
                                stillReady = false;
                                continue NEXT_STREAM;
                            }
                            if (buffer == null) {
                                stillReady = sender.available() != 0;
                                continue NEXT_STREAM;
                            }
                        }
                        case DATA_SENT, DATA_RECVD, RESET_SENT, RESET_RECVD -> {
                            stillReady = false;
                            continue NEXT_STREAM;
                        }
                        case READY -> {
                            String msg = "stream:%s: illegal state %s".formatted(streamId, state);
                            throw new IllegalStateException(msg);
                        }
                        default -> throw new IllegalStateException("Unknown sending state: " + state);
                        }
                        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                            logStreamDebug(streamId,
                                           "packageStreamData: remaining:%s, maxConnectionData:%s, produced:%s",
                                           remaining, maxConnectionData, produced);
                        }
                    } while (remaining > 0 && maxConnectionData > 0);
                } catch (RuntimeException | AssertionError x) {
                    stillReady = false;
                    throw transportFailure(x, streamId);
                } finally {
                    if (stillReady) {
                        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                            logStreamDebug(streamId, "is still ready");
                        }
                        enqueueForSending(streamId);
                    } else {
                        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                            logStreamDebug(streamId, "is no longer ready");
                        }
                    }
                }
                if (remaining == 0) {
                    break;
                }
            }
        } catch (RuntimeException | AssertionError x) {
            log(System.Logger.Level.ERROR, "Failed to compose frames", x);
            throw transportFailure(x, -1);
        } finally {
            if (zeroCreditDeferred != null) {
                zeroCreditDeferred.forEach(this::enqueueForSending);
            }
        }
        return produced;
    }

    private static QuicTransportException transportFailure(Throwable failure, long streamId) {
        if (failure instanceof QuicTransportException transportException) {
            return transportException;
        }
        if (streamId >= 0) {
            return new QuicTransportException("Failed to compose frames for stream " + streamId,
                                              KeySpace.ONE_RTT,
                                              0,
                                              QuicTransportErrors.INTERNAL_ERROR.code(),
                                              failure,
                                              streamId);
        }
        return new QuicTransportException("Failed to compose frames",
                                          KeySpace.ONE_RTT,
                                          0,
                                          QuicTransportErrors.INTERNAL_ERROR.code(),
                                          failure);
    }

    /**
     * Completes dispatch receipts for stream frames handed to the connection packet path.
     *
     * @param frames frames handed off for packet dispatch
     */
    public void streamFramesDispatched(List<QuicFrame> frames) {
        Objects.requireNonNull(frames, "frames");
        for (QuicFrame frame : frames) {
            if (frame instanceof StreamFrame streamFrame) {
                QuicSenderStreamImpl sender = senderImpl(streams.get(streamFrame.streamId()));
                if (sender != null) {
                    sender.markDispatched(streamFrame.offset() + streamFrame.dataLength(), streamFrame.isLast());
                }
            }
        }
    }

    /**
     * Tracks a stream, belonging to this connection, as being blocked from sending data
     * due to flow control limit.
     *
     * @param streamId the stream id
     */
    void trackBlockedStream(long streamId) {
        this.flowControlBlockedStreams.add(streamId);
    }

    /**
     * Stops tracking a stream, belonging to this connection, that may have been previously
     * tracked as being blocked due to flow control limit.
     *
     * @param streamId the stream id
     */
    void untrackBlockedStream(long streamId) {
        this.flowControlBlockedStreams.remove(streamId);
    }

    /**
     * {@return the sender part implementation of the given stream, or {@code null}}
     * This method returns null if the given stream doesn't have a sending part
     * (that is, if it is a unidirectional peer initiated stream).
     *
     * @param stream a sending or bidirectional stream
     */
    static QuicSenderStreamImpl senderImpl(QuicStream stream) {
        if (stream instanceof QuicSenderStreamImpl sender) {
            return sender;
        } else if (stream instanceof QuicBidiStreamImpl bidi) {
            return bidi.senderPart();
        }
        return null;
    }

    /**
     * {@return the receiver part implementation of the given stream, or {@code null}}
     * This method returns null if the given stream doesn't have a receiver part
     * (that is, if it is a unidirectional local initiated stream).
     *
     * @param stream a receiving or bidirectional stream
     */
    QuicReceiverStreamImpl receiverImpl(QuicStream stream) {
        if (stream instanceof QuicReceiverStreamImpl receiver) {
            return receiver;
        } else if (stream instanceof QuicBidiStreamImpl bidi) {
            return bidi.receiverPart();
        }
        return null;
    }

    private static String formatMessage(String format, Object... args) {
        if (args == null || args.length == 0) {
            return String.valueOf(format);
        }
        try {
            return String.format(Locale.ROOT, format, args);
        } catch (IllegalFormatException _) {
            return format + " " + Arrays.toString(args);
        }
    }

    private static boolean tryIncreaseLimitTo(AtomicLong limit, long newLimit) {
        long currentLimit = limit.get();
        while (currentLimit < newLimit) {
            if (limit.compareAndSet(currentLimit, newLimit)) {
                return true;
            }
            currentLimit = limit.get();
        }
        return false;
    }

    private static void clearPeerStreamsBlockedBelow(AtomicLong blockedState, long newLimit) {
        long blockedOnLimit = blockedState.get();
        while (blockedOnLimit >= 0 && blockedOnLimit < newLimit) {
            if (blockedState.compareAndSet(blockedOnLimit, -1)) {
                return;
            }
            blockedOnLimit = blockedState.get();
        }
    }

    private void logStreamDebug(long streamId, String message) {
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            connection.log(LOGGER, System.Logger.Level.DEBUG, "%d: %s", streamId, message);
        }
    }

    private void logStreamDebug(long streamId, String format, Object... args) {
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            logStreamDebug(streamId, formatMessage(format, args));
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

    private void register(long streamId, AbstractQuicStream stream) {
        register(streamId, stream, true);
    }

    private void register(long streamId, AbstractQuicStream stream, boolean publishRemoteStream) {
        streams.put(streamId, stream);
        Optional<QuicTransportParameters> peerParameters = connection.peerTransportParameters();
        if (peerParameters.isPresent()) {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                logStreamDebug(streamId, "setting initial peer parameters");
            }
            newInitialPeerParameters(stream, peerParameters.orElseThrow());
        }
        Optional<QuicTransportParameters> localParameters = connection.localTransportParameters();
        if (localParameters.isPresent()) {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                logStreamDebug(streamId, "setting initial local parameters");
            }
            newInitialLocalParameters(stream, localParameters.orElseThrow());
        }
        if (stream instanceof QuicReceiverStream receiver && stream.isRemoteInitiated()) {
            if (!remoteStreamsTerminated && publishRemoteStream) {
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    logStreamDebug(streamId, "accepting remote stream");
                }
                newRemoteStreams.add(receiver);
                acceptRemoteStreamsLocked();
            }
        }
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            logStreamDebug(streamId, "new stream %s registered", stream.mode());
        }
    }

    private void acceptRemoteStreamsLocked() {
        for (var listener : streamListeners) {
            var iterator = newRemoteStreams.iterator();
            while (iterator.hasNext()) {
                var stream = iterator.next();
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    logStreamDebug(stream.streamId(), "invoking remote stream listener");
                }
                if (listener.test(stream)) {
                    iterator.remove();
                }
            }
        }
    }

    private CompletableFuture<? extends QuicStream> createNewLocalStream(
            int localType, StreamMode mode, Duration timeout) {
        boolean bidi = isBidirectional(localType);
        StreamCreationPermit permit = bidi ? this.localBidiMaxStreamLimit
                : this.localUniMaxStreamLimit;
        CompletableFuture<Boolean> permitAcquisitionCF;
        long currentLimit;
        boolean acquired;
        localStreamCreationLock.lock();
        try {
            if (localStreamTerminationCause != null) {
                return MinimalFuture.failedMinimalFuture(localStreamTerminationCause);
            }
            currentLimit = permit.currentLimit();
            acquired = permit.tryAcquire();
        } finally {
            localStreamCreationLock.unlock();
        }
        if (acquired) {
            permitAcquisitionCF = MinimalFuture.completedMinimalFuture(true);
        } else {
            // stream limit reached, request sending a STREAMS_BLOCKED frame
            announceStreamsBlocked(bidi, currentLimit);
            if (timeout.isPositive()) {
                Executor executor = this.connection.quicInstance().executor();
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    log(System.Logger.Level.DEBUG, "stream creation limit = " + permit.currentLimit()
                            + " reached; waiting for it to increase, timeout=" + timeout);
                }
                permitAcquisitionCF = permit.tryAcquire(timeout.toNanos(), NANOSECONDS, executor);
            } else {
                permitAcquisitionCF = MinimalFuture.completedMinimalFuture(false);
            }
        }
        CompletableFuture<? extends AbstractQuicStream> streamCF =
                permitAcquisitionCF.thenCompose((acq) -> {
                    if (!acq) {
                        String msg = "Stream limit = " + permit.currentLimit()
                                + " reached for locally initiated "
                                + (bidi ? "bidi" : "uni") + " streams";
                        return MinimalFuture.failedMinimalFuture(new QuicStreamLimitException(msg));
                    }
                    try {
                        return MinimalFuture.completedMinimalFuture(openReservedLocalStream(localType, mode));
                    } catch (RuntimeException | Error failure) {
                        permit.releaseAcquisition();
                        return MinimalFuture.failedMinimalFuture(failure);
                    }
                });
        return streamCF;
    }

    private AbstractQuicStream openReservedLocalStream(int localType, StreamMode mode) {
        localStreamCreationLock.lock();
        try {
            return openReservedLocalStreamLocked(localType, mode);
        } finally {
            localStreamCreationLock.unlock();
        }
    }

    QuicBidiStream openReservedLocalBidiStream() {
        return (QuicBidiStream) openReservedLocalStream(localBidi, StreamMode.READ_WRITE);
    }

    void releaseLocalBidiStreamReservation() {
        localBidiMaxStreamLimit.releaseAcquisition();
    }

    private AbstractQuicStream openReservedLocalStreamLocked(int localType, StreamMode mode) {
        if (localStreamTerminationCause != null) {
            throw localStreamTerminationCause;
        }
        AtomicLong nextStreamId = nextStreamID.get(localType);
        long streamId = nextStreamId.get();
        AbstractQuicStream stream = null;
        try {
            stream = QuicStreams.createStream(connection,
                                              streamId,
                                              maxSmallFragments,
                                              streamBufferSize);
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                var strtype = (localType & UNI_MASK) == UNI_MASK ? "uni" : "bidi";
                logStreamDebug(streamId, "created new local %s stream type:%s, mode:%s",
                               strtype, localType, mode);
            }
            register(streamId, stream);
            nextStreamId.addAndGet(4);
            return stream;
        } catch (RuntimeException | Error failure) {
            if (stream != null) {
                streams.remove(streamId, stream);
            }
            throw failure;
        }
    }

    /**
     * Runs the APPLICATION space packet transmitter, if necessary,
     * to potentially trigger sending a STREAMS_BLOCKED frame to the peer.
     *
     * @param bidi           true if the local endpoint is blocked for bidi streams, false for uni streams
     * @param blockedOnLimit the stream creation limit due to which the local endpoint is
     *                      currently blocked
     */
    private void announceStreamsBlocked(boolean bidi, long blockedOnLimit) {
        boolean runTransmitter = false;
        if (bidi) {
            long prevBlockedLimit = this.bidiStreamsBlocked.get();
            while (blockedOnLimit > prevBlockedLimit) {
                if (this.bidiStreamsBlocked.compareAndSet(prevBlockedLimit, blockedOnLimit)) {
                    runTransmitter = true;
                    break;
                }
                prevBlockedLimit = this.bidiStreamsBlocked.get();
            }
        } else {
            long prevBlockedLimit = this.uniStreamsBlocked.get();
            while (blockedOnLimit > prevBlockedLimit) {
                if (this.uniStreamsBlocked.compareAndSet(prevBlockedLimit, blockedOnLimit)) {
                    runTransmitter = true;
                    break;
                }
                prevBlockedLimit = this.uniStreamsBlocked.get();
            }
        }
        if (runTransmitter) {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                log(System.Logger.Level.DEBUG, "requesting packet transmission to send " + (bidi ? "bidi" : "uni")
                        + " STREAMS_BLOCKED with limit " + blockedOnLimit);
            }
            this.connection.runAppPacketSpaceTransmitter();
        }
    }

    /**
     * Removes a stream from the stream map after its state has been
     * switched to DATA_RECVD or RESET_RECVD.
     *
     * @param streamId the stream id
     * @param stream   the stream instance
     */
    private void removeStream(long streamId, QuicStream stream) {
        // if we were tracking this stream as blocked due to flow control, then
        // stop tracking the stream.
        untrackBlockedStream(streamId);
        if (stream instanceof AbstractQuicStream astream) {
            if (astream.isDone()) {
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    logStreamDebug(stream.streamId(), "Removing stream (%s)",
                                   stream.getClass().getSimpleName());
                }
                streams.remove(streamId, astream);
                if (stream.isRemoteInitiated()) {
                    // the queue is not expected to contain many elements.
                    newRemoteStreams.remove(stream);
                    if (shouldSendMaxStreams(stream.isBidirectional())) {
                        this.connection.runAppPacketSpaceTransmitter();
                    }
                }
            } else {
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    logStreamDebug(stream.streamId(), "Can't remove stream yet: %s is %s",
                                   stream.getClass().getSimpleName(),
                                   stream.state());
                }
            }
        }
    }

    /**
     * Called to set initial peer parameters on a stream.
     *
     * @param stream the stream on which parameters might be set
     * @param params the peer transport parameters
     */
    private void newInitialPeerParameters(QuicStream stream, QuicTransportParameters params) {
        long streamId = stream.streamId();
        if (isLocalUni(stream.streamId())) {
            if (params.isPresent(ParameterId.initial_max_stream_data_uni)) {
                long maxData = params.intParameter(ParameterId.initial_max_stream_data_uni);
                senderImpl(stream).updateMaxStreamData(maxData);
            }
        } else if (isLocalBidi(streamId)) {
            // remote for the peer is local for us
            if (params.isPresent(ParameterId.initial_max_stream_data_bidi_remote)) {
                long maxData = params.intParameter(ParameterId.initial_max_stream_data_bidi_remote);
                senderImpl(stream).updateMaxStreamData(maxData);
            }
        } else if (isRemoteBidi(streamId)) {
            // local for the peer is remote for us
            if (params.isPresent(ParameterId.initial_max_stream_data_bidi_local)) {
                long maxData = params.intParameter(ParameterId.initial_max_stream_data_bidi_local);
                senderImpl(stream).updateMaxStreamData(maxData);
            }
        }
    }

    /**
     * Called to set initial local parameters on a stream.
     *
     * @param stream the stream on which parameters might be set
     * @param params the peer transport parameters
     */
    private void newInitialLocalParameters(QuicStream stream, QuicTransportParameters params) {
        long streamId = stream.streamId();
        if (isRemoteUni(stream.streamId())) {
            if (params.isPresent(ParameterId.initial_max_stream_data_uni)) {
                long maxData = params.intParameter(ParameterId.initial_max_stream_data_uni);
                receiverImpl(stream).updateMaxStreamData(maxData);
            }
        } else if (isLocalBidi(streamId)) {
            if (params.isPresent(ParameterId.initial_max_stream_data_bidi_local)) {
                long maxData = params.intParameter(ParameterId.initial_max_stream_data_bidi_local);
                receiverImpl(stream).updateMaxStreamData(maxData);
            }
        } else if (isRemoteBidi(streamId)) {
            if (params.isPresent(ParameterId.initial_max_stream_data_bidi_remote)) {
                long maxData = params.intParameter(ParameterId.initial_max_stream_data_bidi_remote);
                receiverImpl(stream).updateMaxStreamData(maxData);
            }
        }
    }

    /**
     * Checks whether any stream needs to have a STOP_SENDING, RESET_STREAM or any connection
     * control frames like STREAMS_BLOCKED, MAX_STREAMS sent and adds the frame to the list
     * if there's room.
     *
     * @param frames    list of frames
     * @param remaining maximum number of bytes that can be added by this method
     * @return number of bytes actually added
     */
    private long checkResetAndOtherControls(List<QuicFrame> frames, long remaining) {
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            log(System.Logger.Level.DEBUG, "checking reset and other control frames...");
        }
        long added = 0;
        // check STREAMS_BLOCKED, only send it if the local endpoint is blocked on a limit
        // for which we haven't yet sent a STREAMS_BLOCKED
        long uniStreamsBlockedLimit = this.uniStreamsBlocked.get();
        long lastUniStreamsBlockedSent = this.lastUniStreamsBlockedSent.get();
        if (uniStreamsBlockedLimit != -1 && uniStreamsBlockedLimit > lastUniStreamsBlockedSent) {
            StreamsBlockedFrame frame = StreamsBlockedFrame.create(false, uniStreamsBlockedLimit);
            int size = frame.size();
            if (size > remaining - added) {
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    log(System.Logger.Level.DEBUG, "Not enough space to add a STREAMS_BLOCKED frame for uni streams");
                }
            } else {
                frames.add(frame);
                added += size;
                // now that we are sending a STREAMS_BLOCKED frame, keep track of the limit
                // that we sent it with
                this.lastUniStreamsBlockedSent.set(frame.maxStreams());
            }
        }
        long bidiStreamsBlockedLimit = this.bidiStreamsBlocked.get();
        long lastBidiStreamsBlockedSent = this.lastBidiStreamsBlockedSent.get();
        if (bidiStreamsBlockedLimit != -1 && bidiStreamsBlockedLimit > lastBidiStreamsBlockedSent) {
            StreamsBlockedFrame frame = StreamsBlockedFrame.create(true, bidiStreamsBlockedLimit);
            int size = frame.size();
            if (size > remaining - added) {
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    log(System.Logger.Level.DEBUG, "Not enough space to add a STREAMS_BLOCKED frame for bidi streams");
                }
            } else {
                frames.add(frame);
                added += size;
                // now that we are sending a STREAMS_BLOCKED frame, keep track of the limit
                // that we sent it with
                this.lastBidiStreamsBlockedSent.set(frame.maxStreams());
            }
        }
        // check STOP_SENDING and MAX_STREAM_DATA
        var rcvIterator = receiversSend.entrySet().iterator();
        while (rcvIterator.hasNext()) {
            var entry = rcvIterator.next();
            var frame = entry.getValue();
            if (frame.size() > remaining - added) {
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    logStreamDebug(entry.getKey().streamId(), "not enough space for %s", frame);
                }
                break;
            }
            var receiver = receiverImpl(entry.getKey());
            var size = checkSendControlFrame(receiver, frame, frames);
            if (size > 0) {
                added += size;
            }
            rcvIterator.remove();
        }

        // check RESET_STREAM
        var sndIterator = sendersReset.entrySet().iterator();
        while (sndIterator.hasNext()) {
            Map.Entry<QuicSenderStream, Long> entry = sndIterator.next();
            var sender = senderImpl(entry.getKey());
            long finalSize = sender.dataSent();
            ResetStreamFrame frame = ResetStreamFrame.create(sender.streamId(), entry.getValue(), finalSize);
            int size = frame.size();
            if (size > remaining - added) {
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    logStreamDebug(sender.streamId(), "not enough space for ResetFrame");
                }
                break;
            }
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                logStreamDebug(sender.streamId(), "Adding ResetFrame");
            }
            frames.add(frame);
            added += size;
            sender.resetSent();
            sndIterator.remove();
        }

        if (remaining - added > 18) {
            // add MAX_STREAMS if necessary
            added += addMaxStreamsFrame(frames, false);
            added += addMaxStreamsFrame(frames, true);
        }
        return added;
    }

    private boolean shouldSendMaxStreams(boolean bidi) {
        boolean rcvdStreamsBlocked = bidi
                ? this.peerBidiStreamsBlocked.get() != -1
                : this.peerUniStreamsBlocked.get() != -1;
        return connection.nextMaxStreamsLimit(bidi, rcvdStreamsBlocked) > 0;
    }

    private long addMaxStreamsFrame(List<QuicFrame> frames, boolean bidi) {
        AtomicLong blockedState = bidi ? peerBidiStreamsBlocked : peerUniStreamsBlocked;
        long blockedOnLimit = blockedState.get();
        boolean peerBlocked = blockedOnLimit != -1;
        long newMaxStreamsLimit = connection.nextMaxStreamsLimit(bidi, peerBlocked);
        if (newMaxStreamsLimit == 0) {
            if (peerBlocked) {
                RemoteStreamCredit streamCredit = bidi ? remoteBidiStreamCredit : remoteUniStreamCredit;
                clearPeerStreamsBlockedBelow(blockedState, streamCredit.currentLimit());
            }
            return 0;
        }
        RemoteStreamCredit streamCredit = bidi ? remoteBidiStreamCredit : remoteUniStreamCredit;
        boolean limitIncreased = streamCredit.tryAdvanceLimit(newMaxStreamsLimit);
        if (!limitIncreased) {
            clearPeerStreamsBlockedBelow(blockedState, streamCredit.currentLimit());
            return 0;
        }
        MaxStreamsFrame frame = MaxStreamsFrame.create(bidi, newMaxStreamsLimit);
        frames.add(frame);
        // Keep a concurrent STREAMS_BLOCKED update for this new limit (or a higher one).
        clearPeerStreamsBlockedBelow(blockedState, newMaxStreamsLimit);
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            log(System.Logger.Level.DEBUG, "Increasing max remote %s streams to %s",
                      bidi ? "bidi" : "uni", newMaxStreamsLimit);
        }
        return frame.size();
    }

    /**
     * Checks whether the given stream is recorded as needing a control
     * frame to be sent, and if so, add that frame to the list.
     *
     * @param receiver the receiver part of the stream
     * @param frame    the frame to send
     * @param frames   list of frames
     * @return size of the added frame, or zero if no frame was added
     * <p>Note: Typically, the control frame that is sent is either a MAX_STREAM_DATA
     *        or a STOP_SENDING frame
     */
    private long checkSendControlFrame(QuicReceiverStreamImpl receiver,
                                       QuicFrame frame,
                                       List<QuicFrame> frames) {
        if (frame == null) {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                logStreamDebug(receiver.streamId(), "no receiver frame to send");
            }
            return 0;
        }
        if (frame instanceof MaxStreamDataFrame maxStreamDataFrame) {
            if (receiver.receivingState() == ReceivingStreamState.RECV) {
                // if we know the final size, no point in increasing max data
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    logStreamDebug(receiver.streamId(), "Adding MaxStreamDataFrame");
                }
                frames.add(frame);
                receiver.updateMaxStreamData(maxStreamDataFrame.maxStreamData());
                return frame.size();
            }
            return 0;
        } else if (frame instanceof StopSendingFrame) {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                logStreamDebug(receiver.streamId(), "Adding StopSendingFrame");
            }
            frames.add(frame);
            return frame.size();
        } else {
            throw new InternalError("Should not reach here - not a control frame: " + frame);
        }
    }

    private static boolean hasPendingZeroLengthEndOfStream(Iterable<? extends QuicSenderStream> senders) {
        for (QuicSenderStream sender : senders) {
            QuicSenderStreamImpl implementation = senderImpl(sender);
            if (implementation != null && implementation.hasPendingZeroLengthEndOfStream()) {
                return true;
            }
        }
        return false;
    }

    // Package access supports direct queue regression and benchmark coverage.
    static ReadyStreamCollection serverReadyStreams() {
        return new ReadyStreamDistinctQueue();
    }

    interface ReadyStreamCollection {
        boolean isEmpty();

        int size();

        boolean hasPendingZeroLengthEndOfStream();

        void add(QuicSenderStream sender);

        QuicStream poll();
    }

    //This queue is used to ensure fair sending of stream data: the packageStreamData method
    // will pop and push streams from/to this queue in a round-robin fashion so that one stream
    // doesn't starve all the others.
    private static class ReadyStreamQueue implements ReadyStreamCollection {
        private final ConcurrentLinkedQueue<QuicSenderStream> queue = new ConcurrentLinkedQueue<>();

        public boolean isEmpty() {
            return queue.isEmpty();
        }

        public int size() {
            return queue.size();
        }

        public boolean hasPendingZeroLengthEndOfStream() {
            return QuicConnectionStreams.hasPendingZeroLengthEndOfStream(queue);
        }

        public void add(QuicSenderStream sender) {
            queue.add(sender);
        }

        public QuicStream poll() {
            return queue.poll();
        }
    }

    // Server response streams can become ready concurrently. Stream retirement credit is
    // accounted independently of stream ID order, so enqueue order provides fair dispatch
    // without the traversal, boxing, and node allocation of a sorted map. The marker keeps
    // repeated writer notifications from adding the same stream more than once.
    private static class ReadyStreamDistinctQueue implements ReadyStreamCollection {
        private final ConcurrentLinkedQueue<QuicSenderStream> queue = new ConcurrentLinkedQueue<>();

        public boolean isEmpty() {
            return queue.isEmpty();
        }

        public int size() {
            return queue.size();
        }

        public boolean hasPendingZeroLengthEndOfStream() {
            return QuicConnectionStreams.hasPendingZeroLengthEndOfStream(queue);
        }

        public void add(QuicSenderStream sender) {
            QuicSenderStreamImpl implementation = senderImpl(sender);
            if (implementation != null && implementation.markReadyForSending()) {
                queue.add(sender);
            }
        }

        public QuicStream poll() {
            QuicSenderStream sender = queue.poll();
            if (sender != null) {
                senderImpl(sender).clearReadyForSending();
            }
            return sender;
        }
    }

    static final class RemoteStreamCredit {
        private static final long UNINITIALIZED = -1;

        private final AtomicLong initialLimit = new AtomicLong(UNINITIALIZED);
        private final AtomicLong advertisedLimit = new AtomicLong();
        private final AtomicLong retiredStreams = new AtomicLong();

        void initialize(long limit) {
            tryIncreaseLimitTo(initialLimit, limit);
            tryIncreaseLimitTo(advertisedLimit, limit);
        }

        long currentLimit() {
            return advertisedLimit.get();
        }

        void streamRetired() {
            retiredStreams.incrementAndGet();
        }

        long nextLimit(boolean peerBlocked) {
            long initialLimit = this.initialLimit.get();
            if (initialLimit == UNINITIALIZED) {
                return 0;
            }
            long desiredLimit = Math.min(QuicConnectionImpl.MAX_STREAMS_VALUE_LIMIT,
                                         initialLimit + retiredStreams.get());
            long currentLimit = advertisedLimit.get();
            long availableCredit = desiredLimit - currentLimit;
            if (availableCredit <= 0) {
                return 0;
            }
            // Ordinarily replenish after 25% of the window is consumed. A blocked peer needs any
            // available credit immediately, even when it is below that batching threshold.
            return peerBlocked || availableCredit > (initialLimit >> 2) ? desiredLimit : 0;
        }

        boolean tryAdvanceLimit(long newLimit) {
            return tryIncreaseLimitTo(advertisedLimit, newLimit);
        }
    }

    // Provides a limited view over a ConcurrentHashMap. A successful remove is also the
    // linearization point at which remotely initiated stream credit is retired.
    private final class StreamsContainer {
        // A map of <Stream ID, Quic Stream>
        private final ConcurrentMap<Long, AbstractQuicStream> streams = new ConcurrentHashMap<>();

        AbstractQuicStream get(long streamId) {
            return streams.get(streamId);
        }

        boolean remove(long streamId, AbstractQuicStream stream) {
            if (!streams.remove(streamId, stream)) {
                return false;
            }
            int streamType = (int) (stream.streamId() & TYPE_MASK);
            if (streamType == remoteBidi) {
                remoteBidiStreamCredit.streamRetired();
            } else if (streamType == remoteUni) {
                remoteUniStreamCredit.streamRetired();
            }
            return true;
        }

        AbstractQuicStream put(long streamId, AbstractQuicStream stream) {
            return streams.put(streamId, stream);
        }

        Stream<AbstractQuicStream> all() {
            return streams.values().stream();
        }

    }
}
