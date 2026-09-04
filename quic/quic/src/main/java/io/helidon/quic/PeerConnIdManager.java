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

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import io.helidon.quic.QuicPacketReceiver.PeerResetToken;
import io.helidon.quic.frame.NewConnectionIDFrame;
import io.helidon.quic.frame.QuicFrame;
import io.helidon.quic.frame.RetireConnectionIDFrame;
import io.helidon.quic.packet.InitialPacket;

import static io.helidon.quic.QuicConnectionId.MAX_CONNECTION_ID_LENGTH;
import static io.helidon.quic.QuicTransportErrors.PROTOCOL_VIOLATION;

/**
 * Manages the connection ids advertised by a peer of a connection.
 * - Handles incoming NEW_CONNECTION_ID frames,
 * - produces outgoing RETIRE_CONNECTION_ID frames,
 * - registers received stateless reset tokens with the QuicEndpoint
 * Additionally on the client side:
 * - handles incoming transport parameters (preferred_address, stateless_reset_token)
 * - stores original and retry peer IDs
 * Voluntary connection ID switching is outside the initial HTTP/3 scope; this
 * manager switches only when protocol handling requires it.
 */
final class PeerConnIdManager {
    private static final System.Logger LOGGER = System.getLogger(PeerConnIdManager.class.getName());

    private final QuicConnectionImpl connection;
    private final boolean isClient;
    // the connection ids (there can be more than one) with which the peer identifies this connection.
    // the key of this Map is a (RFC defined) sequence number for the connection id
    private final NavigableMap<Long, PeerConnectionId> peerConnectionIds =
            Collections.synchronizedNavigableMap(new TreeMap<>());
    // the connection id sequence numbers that we haven't received yet.
    // We need to know which sequence numbers are retired, and which are not assigned yet
    private final NavigableSet<Long> gaps =
            Collections.synchronizedNavigableSet(new TreeSet<>());
    private final Map<Long, PeerConnectionId> retiringConnectionIds = new HashMap<>();
    // the connection id sequence numbers that are awaiting retirement.
    private final Deque<Long> toRetire = new ArrayDeque<>();
    private final Map<Long, PathCidBinding> pathBindings = new HashMap<>();
    private final Set<Long> leasedSequences = new HashSet<>();
    private final Map<Long, PeerResetToken> registeredResetTokens = new HashMap<>();
    private final ReentrantLock lock = new ReentrantLock();
    private List<PeerResetToken> frozenResetTokenRoutes;
    private volatile State state = State.INITIAL_PKT_NOT_RECEIVED_FROM_PEER;
    private QuicConnectionId clientSelectedDestConnId;
    private QuicConnectionId peerDecidedRetryConnId;
    // sequence number of active connection ID
    private long activeConnIdSeq = -1;
    private QuicConnectionId activeConnId;
    // the largest retirePriorTo value received across NEW_CONNECTION_ID frames
    private volatile long largestReceivedRetirePriorTo = -1; // -1 implies none received so far
    // the largest sequenceNumber value received across NEW_CONNECTION_ID frames
    private volatile long largestReceivedSequenceNumber;
    PeerConnIdManager(QuicConnectionImpl connection) {
        this.isClient = connection.isClientConnection();
        this.connection = connection;
    }

    /**
     * Returns the list of stateless reset tokens associated with active peer connection IDs.
     *
     * @return the list of stateless reset tokens associated with active peer connection IDs.
     */
    public List<PeerResetToken> activeResetTokens() {
        lock.lock();
        try {
            return List.copyOf(registeredResetTokens.values());
        } finally {
            lock.unlock();
        }
    }

    List<PeerResetToken> freezeResetTokenRoutes() {
        lock.lock();
        try {
            if (frozenResetTokenRoutes == null) {
                frozenResetTokenRoutes = List.copyOf(registeredResetTokens.values());
            }
            return frozenResetTokenRoutes;
        } finally {
            lock.unlock();
        }
    }

    Optional<PathCidBinding> acquirePathBinding(InetSocketAddress peerAddress) {
        lock.lock();
        try {
            if (activeConnId == null) {
                return Optional.empty();
            }
            if (activeConnId.length() == 0) {
                return Optional.of(new PathCidBinding(-1, activeConnId, peerAddress));
            }
            for (Map.Entry<Long, PeerConnectionId> entry : peerConnectionIds.entrySet()) {
                long sequence = entry.getKey();
                if (sequence >= largestReceivedRetirePriorTo && leasedSequences.add(sequence)) {
                    PathCidBinding binding = new PathCidBinding(sequence, entry.getValue(), peerAddress);
                    pathBindings.put(sequence, binding);
                    return Optional.of(binding);
                }
            }
            return Optional.empty();
        } finally {
            lock.unlock();
        }
    }

    void retirePathBinding(PathCidBinding binding) {
        if (binding == null || binding.sequence < 0) {
            return;
        }
        lock.lock();
        try {
            if (!binding.valid) {
                return;
            }
            binding.valid = false;
            queueRetirement(binding.sequence);
        } finally {
            lock.unlock();
        }
    }

    boolean acquirePathUse(PathCidBinding binding) {
        if (binding == null || !binding.valid) {
            return false;
        }
        if (binding.used) {
            binding.activeUses.incrementAndGet();
            if (binding.valid) {
                return true;
            }
            int remaining = binding.activeUses.decrementAndGet();
            if (remaining == 0) {
                connection.runAppPacketSpaceTransmitter();
            }
            return false;
        }
        lock.lock();
        try {
            if (!binding.valid) {
                return false;
            }
            if (binding.activeUses.get() == 0 && !binding.used) {
                associateResetToken(binding);
            }
            binding.activeUses.incrementAndGet();
            return true;
        } finally {
            lock.unlock();
        }
    }

    void releasePathUse(PathCidBinding binding) {
        completePathUse(binding, false);
    }

    void commitPathUse(PathCidBinding binding) {
        completePathUse(binding, true);
    }

    void initialPathUsed(PathCidBinding binding) {
        lock.lock();
        try {
            if (!binding.used) {
                binding.used = true;
                associateResetToken(binding);
            }
        } finally {
            lock.unlock();
        }
    }

    private void completePathUse(PathCidBinding binding, boolean sent) {
        if (binding.used) {
            int previous = binding.activeUses.getAndDecrement();
            if (previous <= 0) {
                binding.activeUses.incrementAndGet();
                throw new IllegalStateException("Connection ID use already released: " + binding.sequence);
            }
            if (previous == 1 && !binding.valid) {
                connection.runAppPacketSpaceTransmitter();
            }
            return;
        }
        boolean wakeTransmitter;
        lock.lock();
        try {
            int previous = binding.activeUses.getAndDecrement();
            if (previous <= 0) {
                binding.activeUses.incrementAndGet();
                throw new IllegalStateException("Connection ID use already released: " + binding.sequence);
            }
            binding.used |= sent;
            if (previous == 1 && !binding.used) {
                forgetResetToken(binding.sequence);
            }
            wakeTransmitter = previous == 1 && toRetire.contains(binding.sequence);
        } finally {
            lock.unlock();
        }
        if (wakeTransmitter) {
            connection.runAppPacketSpaceTransmitter();
        }
    }

    /**
     * Produce a queued RETIRE_CONNECTION_ID frame, if it fits in the packet.
     *
     * @param remaining bytes remaining in the packet
     * @return a RetireConnectionIdFrame, or null if none is queued or remaining is too low
     */
    public QuicFrame nextFrame(int remaining, long destinationConnectionIdSequence) {
        lock.lock();
        try {
            int pending = toRetire.size();
            for (int i = 0; i < pending; i++) {
                Long seqNumToRetire = toRetire.poll();
                if (seqNumToRetire == null) {
                    return null;
                }
                PathCidBinding binding = pathBindings.get(seqNumToRetire);
                RetireConnectionIDFrame frame = RetireConnectionIDFrame.create(seqNumToRetire);
                if (seqNumToRetire == destinationConnectionIdSequence
                        || binding != null && binding.activeUses.get() != 0
                        || frame.size() > remaining) {
                    toRetire.add(seqNumToRetire);
                    continue;
                }
                completeRetirement(seqNumToRetire);
                return frame;
            }
            return null;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Save the client-selected original server connection ID.
     *
     * @param peerConnId the client-selected original server connection ID
     */
    void originalServerConnId(QuicConnectionId peerConnId) {
        lock.lock();
        try {
            var st = this.state;
            if (st != State.INITIAL_PKT_NOT_RECEIVED_FROM_PEER) {
                throw new IllegalStateException("Cannot associate a client selected peer id"
                                                        + " in current state " + st.text());
            }
            this.clientSelectedDestConnId = peerConnId;
            this.activeConnId = peerConnId;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the client-selected original server connection ID.
     *
     * @return the client-selected original server connection ID.
     */
    QuicConnectionId originalServerConnId() {
        lock.lock();
        try {
            var id = this.clientSelectedDestConnId;
            if (id == null) {
                throw new IllegalArgumentException("Original (peer) connection id not yet set");
            }
            return id;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Save the server-selected retry connection ID.
     *
     * @param peerConnId the server-selected retry connection ID
     */
    void retryConnId(QuicConnectionId peerConnId) {
        if (!isClient) {
            throw new IllegalStateException("Should not be used on the server");
        }
        lock.lock();
        try {
            var st = this.state;
            if (st != State.INITIAL_PKT_NOT_RECEIVED_FROM_PEER) {
                throw new IllegalStateException("Cannot associate a peer id, from retry packet,"
                                                        + " in current state " + st.text());
            }
            this.peerDecidedRetryConnId = peerConnId;
            this.activeConnId = peerConnId;
            this.state = State.RETRY_PKT_RECEIVED_FROM_PEER;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the connectionId the server included in the Source Connection ID field of a
     * Retry packet. May be null.
     *
     * @return the connection id sent in the server's retry packet
     */
    QuicConnectionId retryConnId() {
        lock.lock();
        try {
            return this.peerDecidedRetryConnId;
        } finally {
            lock.unlock();
        }
    }

    /**
     * The peer in its INITIAL packet would have sent a connection id representing itself. That
     * connection id may not be the same that we might have sent in the INITIAL packet. If it isn't
     * the same, then we switch the peer connection id, that we keep track off, to the one that
     * the peer has chosen.
     *
     * @param initialPacket the INITIAL packet from the peer
     */
    void finalizeHandshakePeerConnId(InitialPacket initialPacket) throws QuicTransportException {
        finalizeHandshakePeerConnId(initialPacket.sourceId());
    }

    void finalizeHandshakePeerConnId(QuicConnectionId sourceId) throws QuicTransportException {
        lock.lock();
        try {
            var st = this.state;
            if (st == State.PEER_CONN_ID_FINALIZED) {
                // we have already finalized the peer connection id, through a previous INITIAL
                // packet receipt (there can be more than one INITIAL packets).
                // now we just verify that this INITIAL packet too has the finalized peer connection
                // id and if it doesn't then we throw an exception
                QuicConnectionId handshakePeerConnId = this.peerConnectionIds.get(0L);
                if (!handshakePeerConnId.equals(sourceId)) {
                    throw new QuicTransportException("Invalid source connection id in INITIAL packet",
                                                     QuicTLSEngine.KeySpace.INITIAL, 0, PROTOCOL_VIOLATION);
                }
                return;
            }
            // this is the first INITIAL packet from the peer, so we finalize the peer connection id
            PeerConnectionId handshakePeerConnId = new PeerConnectionId(sourceId.bytes());
            // at this point we have either switched to a new peer connection id (chosen by the peer)
            // or have agreed to use the one we chose for the peer. In either case, we register this
            // as the handshake peer connection id with sequence number 0.
            // RFC-9000, section 5.1.1: The initial connection ID issued by an endpoint is sent in
            // the Source Connection ID field of the long packet header during the handshake.
            // The sequence number of the initial connection ID is 0.
            this.peerConnectionIds.put(0L, handshakePeerConnId);
            this.state = State.PEER_CONN_ID_FINALIZED;
            this.activeConnIdSeq = 0;
            this.activeConnId = handshakePeerConnId;
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                log(System.Logger.Level.DEBUG,
                    "finalized handshake peer connection ID (local length %s, peer length %s)",
                    connection.localConnectionId().orElseThrow().length(),
                    handshakePeerConnId.length());
            }
            if (connection.quicConfig().unsafeRawData()
                    && LOGGER.isLoggable(System.Logger.Level.TRACE)) {
                log(System.Logger.Level.TRACE,
                    "UNSAFE raw finalized handshake connection IDs (local %s, peer %s)",
                    connection.localConnectionId().orElseThrow().toHexString(),
                    handshakePeerConnId.toHexString());
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Save the connection ID from the preferred address QUIC transport parameter.
     *
     * @param preferredConnId              preferred connection ID
     * @param preferredStatelessResetToken preferred stateless reset token
     */
    void handlePreferredAddress(ByteBuffer preferredConnId,
                                byte[] preferredStatelessResetToken) {
        if (!isClient) {
            throw new IllegalStateException("Should not be used on the server");
        }
        lock.lock();
        try {
            PeerConnectionId peerConnId = new PeerConnectionId(preferredConnId,
                                                               preferredStatelessResetToken);
            // keep track of this peer connection id
            // RFC-9000, section 5.1.1:  If the preferred_address transport parameter is sent,
            // the sequence number of the supplied connection ID is 1
            this.peerConnectionIds.put(1L, peerConnId);
            largestReceivedSequenceNumber = 1;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Save the stateless reset token QUIC transport parameter.
     *
     * @param statelessResetToken stateless reset token
     */
    void handshakeStatelessResetToken(byte[] statelessResetToken) {
        if (!isClient) {
            throw new IllegalStateException("Should not be used on the server");
        }
        lock.lock();
        try {
            QuicConnectionId handshakeConnId = this.peerConnectionIds.get(0L);
            if (handshakeConnId == null) {
                throw new IllegalStateException("No handshake peer connection available");
            }
            // recreate the conn id with the stateless token
            PeerConnectionId connectionId = new PeerConnectionId(handshakeConnId.asReadOnlyBuffer(),
                                                                 statelessResetToken);
            this.peerConnectionIds.put(0L, connectionId);
            if (activeConnIdSeq == 0) {
                activeConnId = connectionId;
            }
            PathCidBinding binding = pathBindings.get(0L);
            if (binding != null) {
                binding.connectionId = connectionId;
                if (binding.used) {
                    associateResetToken(binding);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the active peer connection ID.
     *
     * @return the active peer connection ID.
     */
    QuicConnectionId peerConnectionId() {
        lock.lock();
        try {
            if (activeConnIdSeq < largestReceivedRetirePriorTo) {
                // stop using the old connection ID
                switchConnectionId();
            }
            return activeConnId;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Process the incoming NEW_CONNECTION_ID frame.
     *
     * @param newCid the NEW_CONNECTION_ID frame
     * @throws QuicTransportException if the frame violates the protocol
     */
    void handleNewConnectionIdFrame(NewConnectionIDFrame newCid)
            throws QuicTransportException {
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            log(System.Logger.Level.DEBUG, "Received NEW_CONNECTION_ID frame: %s", newCid);
        }
        // pre-checks
        long sequenceNumber = newCid.sequenceNumber();
        long retirePriorTo = newCid.retirePriorTo();
        if (retirePriorTo > sequenceNumber) {
            // RFC 9000, section 19.15: Receiving a value in the Retire Prior To field that is greater
            // than that in the Sequence Number field MUST be treated as a connection error of
            // type FRAME_ENCODING_ERROR
            throw new QuicTransportException("Invalid retirePriorTo " + retirePriorTo,
                                             QuicTLSEngine.KeySpace.ONE_RTT,
                                             newCid.typeField(), QuicTransportErrors.FRAME_ENCODING_ERROR);
        }
        ByteBuffer connectionId = newCid.connectionId();
        int connIdLength = connectionId.remaining();
        if (connIdLength < 1 || connIdLength > MAX_CONNECTION_ID_LENGTH) {
            // RFC-9000, section 19.15: Values less than 1 and greater than 20 are invalid and
            // MUST be treated as a connection error of type FRAME_ENCODING_ERROR
            throw new QuicTransportException("Invalid connection id length " + connIdLength,
                                             QuicTLSEngine.KeySpace.ONE_RTT,
                                             newCid.typeField(), QuicTransportErrors.FRAME_ENCODING_ERROR);
        }
        ByteBuffer statelessResetToken = newCid.statelessResetToken();
        lock.lock();
        try {
            // see if we have received any connection ids for this same sequence number.
            // this is possible if the packet containing the new connection id frame was retransmitted.
            // the connection id for such a (duplicate) sequence number is expected to be the same.
            // RFC-9000, section 19.15: if a sequence number is used for different connection IDs,
            // the endpoint MAY treat that receipt as a connection error of type PROTOCOL_VIOLATION
            QuicConnectionId previousConnIdForSeqNum = peerConnectionId(sequenceNumber);
            if (previousConnIdForSeqNum != null) {
                if (previousConnIdForSeqNum.matches(connectionId)) {
                    if (retirePriorTo > largestReceivedRetirePriorTo) {
                        retirePriorTo(retirePriorTo);
                        largestReceivedRetirePriorTo = retirePriorTo;
                    }
                    // frame with same sequence number and connection id, probably a retransmission.
                    // ignore this frame
                    if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                        log(System.Logger.Level.DEBUG, "Ignoring (duplicate) new connection id frame with"
                                + " sequence number %d", sequenceNumber);
                    }
                    return;
                }
                // mismatch, throw protocol violation error
                throw new QuicTransportException("Invalid connection id in (duplicated)"
                                                         + " new connection id frame with sequence number " + sequenceNumber,
                                                 QuicTLSEngine.KeySpace.ONE_RTT,
                                                 newCid.typeField(), PROTOCOL_VIOLATION);
            }
            if ((sequenceNumber <= largestReceivedSequenceNumber && !gaps.contains(sequenceNumber))
                    || sequenceNumber < largestReceivedRetirePriorTo) {
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    log(System.Logger.Level.DEBUG, "Ignoring (retired) new connection id frame with"
                            + " sequence number %d", sequenceNumber);
                }
                return;
            }
            long numConnIdsToAdd = Math.max(sequenceNumber - largestReceivedSequenceNumber, 0);
            long numCurrentActivePeerConnIds = this.peerConnectionIds.size() + this.gaps.size();
            // we can temporarily store up to 3x the active connection ID limit,
            // including active and retired IDs.
            long retainedConnectionIds = saturatingAdd(saturatingAdd(numCurrentActivePeerConnIds, numConnIdsToAdd),
                                                       toRetire.size());
            if (retainedConnectionIds > saturatingMultiply(this.connection.localActiveConnectionIdLimit(), 3)) {
                // RFC-9000, section 5.1.1: After processing a NEW_CONNECTION_ID frame and adding and
                // retiring active connection IDs, if the number of active connection IDs exceeds
                // the value advertised in its active_connection_id_limit transport parameter,
                // an endpoint MUST close the connection with an error of type CONNECTION_ID_LIMIT_ERROR
                throw new QuicTransportException("Connection id limit reached",
                                                 QuicTLSEngine.KeySpace.ONE_RTT, newCid.typeField(),
                                                 QuicTransportErrors.CONNECTION_ID_LIMIT_ERROR);
            }
            // end pre-checks
            // Insert gaps for the sequence numbers we haven't seen yet
            insertGaps(sequenceNumber);
            // Update the list of sequence numbers to retire
            retirePriorTo(retirePriorTo);
            // insert the new connection ID
            byte[] statelessResetTokenBytes = new byte[QuicConnectionImpl.RESET_TOKEN_LENGTH];
            statelessResetToken.get(statelessResetTokenBytes);
            PeerConnectionId newPeerConnId = new PeerConnectionId(connectionId, statelessResetTokenBytes);
            this.peerConnectionIds.putIfAbsent(sequenceNumber, newPeerConnId);
            // post-checks
            // now we can accurately check the number of active and retired connection IDs
            if (peerConnectionIds.size() + gaps.size()
                    > this.connection.localActiveConnectionIdLimit()) {
                // RFC-9000, section 5.1.1: After processing a NEW_CONNECTION_ID frame and adding and
                // retiring active connection IDs, if the number of active connection IDs exceeds
                // the value advertised in its active_connection_id_limit transport parameter,
                // an endpoint MUST close the connection with an error of type CONNECTION_ID_LIMIT_ERROR
                throw new QuicTransportException("Active connection id limit reached",
                                                 QuicTLSEngine.KeySpace.ONE_RTT, newCid.typeField(),
                                                 QuicTransportErrors.CONNECTION_ID_LIMIT_ERROR);
            }
            if (toRetire.size() > saturatingMultiply(this.connection.localActiveConnectionIdLimit(), 2)) {
                // RFC-9000, section 5.1.2:
                // An endpoint SHOULD limit the number of connection IDs it has retired locally for
                // which RETIRE_CONNECTION_ID frames have not yet been acknowledged.
                // An endpoint SHOULD allow for sending and tracking a number
                // of RETIRE_CONNECTION_ID frames of at least twice the value
                // of the active_connection_id_limit transport parameter
                throw new QuicTransportException("Retired connection id limit reached: " + toRetire,
                                                 QuicTLSEngine.KeySpace.ONE_RTT, newCid.typeField(),
                                                 QuicTransportErrors.CONNECTION_ID_LIMIT_ERROR);
            }
            if (this.largestReceivedRetirePriorTo < retirePriorTo) {
                this.largestReceivedRetirePriorTo = retirePriorTo;
            }
            if (this.largestReceivedSequenceNumber < sequenceNumber) {
                this.largestReceivedSequenceNumber = sequenceNumber;
            }
        } finally {
            lock.unlock();
        }
    }

    private QuicConnectionId peerConnectionId(long sequenceNum) {
        return this.peerConnectionIds.get(sequenceNum);
    }

    private void switchConnectionId() {
        // the caller is expected to retire the active connection id prior to calling this
        Map.Entry<Long, PeerConnectionId> entry = peerConnectionIds.ceilingEntry(largestReceivedRetirePriorTo);
        if (entry == null) {
            activeConnIdSeq = -1;
            activeConnId = null;
            return;
        }
        activeConnIdSeq = entry.getKey();
        activeConnId = entry.getValue();
        // link the peer issued stateless reset token to this connection
        PathCidBinding binding = pathBindings.get(activeConnIdSeq);
        if (binding != null && binding.used) {
            associateResetToken(binding);
        }

        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            log(System.Logger.Level.DEBUG, "Switching to connection ID %d", activeConnIdSeq);
        }
    }

    private void insertGaps(long sequenceNumber) {
        for (long i = largestReceivedSequenceNumber + 1; i < sequenceNumber; i++) {
            gaps.add(i);
        }
    }

    private void retirePriorTo(long priorTo) {
        // remove/retire (in preparation of sending a RETIRE_CONNECTION_ID frames)
        for (Iterator<Map.Entry<Long, PeerConnectionId>> iterator = peerConnectionIds.entrySet().iterator();
                iterator.hasNext();) {
            Map.Entry<Long, PeerConnectionId> entry = iterator.next();
            long seqNumToRetire = entry.getKey();
            if (seqNumToRetire >= priorTo) {
                break;
            }
            iterator.remove();
            retiringConnectionIds.put(seqNumToRetire, entry.getValue());
            queueRetirement(seqNumToRetire);
            PathCidBinding binding = pathBindings.get(seqNumToRetire);
            if (binding != null) {
                binding.valid = false;
            }
        }
        for (Iterator<Long> iterator = gaps.iterator(); iterator.hasNext();) {
            Long gap = iterator.next();
            if (gap >= priorTo) {
                return;
            }
            iterator.remove();
            queueRetirement(gap);
        }
    }

    private void queueRetirement(long sequence) {
        if (!toRetire.contains(sequence)) {
            toRetire.add(sequence);
        }
    }

    private void completeRetirement(long sequence) {
        PathCidBinding binding = pathBindings.remove(sequence);
        PeerConnectionId connectionId = peerConnectionIds.remove(sequence);
        if (connectionId == null) {
            connectionId = retiringConnectionIds.remove(sequence);
        }
        leasedSequences.remove(sequence);
        if (binding != null) {
            binding.valid = false;
        }
        if (connectionId != null) {
            forgetResetToken(sequence);
        }
        if (activeConnIdSeq == sequence) {
            Map.Entry<Long, PeerConnectionId> replacement = peerConnectionIds.firstEntry();
            activeConnIdSeq = replacement == null ? -1 : replacement.getKey();
            activeConnId = replacement == null ? null : replacement.getValue();
        }
    }

    private void associateResetToken(PathCidBinding binding) {
        if (frozenResetTokenRoutes != null) {
            return;
        }
        if (!(binding.connectionId instanceof PeerConnectionId connectionId)) {
            return;
        }
        connectionId.statelessResetToken().ifPresent(token -> {
            PeerResetToken registration = PeerResetToken.create(token, binding.peerAddress);
            PeerResetToken previous = registeredResetTokens.put(binding.sequence, registration);
            if (previous != null) {
                connection.endpoint().forgetStatelessResetToken(previous, connection);
            }
            connection.endpoint().associateStatelessResetToken(registration, connection);
        });
    }

    private void forgetResetToken(long sequence) {
        PeerResetToken registration = registeredResetTokens.remove(sequence);
        if (registration != null) {
            connection.endpoint().forgetStatelessResetToken(registration, connection);
        }
    }

    private static long saturatingAdd(long left, long right) {
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private static long saturatingMultiply(long value, int multiplier) {
        return value > Long.MAX_VALUE / multiplier ? Long.MAX_VALUE : value * multiplier;
    }

    private void log(System.Logger.Level level, String format, Object... arguments) {
        if (arguments.length == 0) {
            connection.log(LOGGER, level, "%s", format);
        } else {
            connection.log(LOGGER, level, format, arguments);
        }
    }

    static final class PathCidBinding {
        private final long sequence;
        private volatile QuicConnectionId connectionId;
        private final InetSocketAddress peerAddress;
        private volatile boolean valid = true;
        private volatile boolean used;
        private final AtomicInteger activeUses = new AtomicInteger();

        private PathCidBinding(long sequence,
                               QuicConnectionId connectionId,
                               InetSocketAddress peerAddress) {
            this.sequence = sequence;
            this.connectionId = connectionId;
            this.peerAddress = peerAddress;
        }

        long sequence() {
            return sequence;
        }

        QuicConnectionId connectionId() {
            return connectionId;
        }

        boolean valid() {
            return valid;
        }
    }

    private enum State {
        INITIAL_PKT_NOT_RECEIVED_FROM_PEER,
        RETRY_PKT_RECEIVED_FROM_PEER,
        PEER_CONN_ID_FINALIZED;

        private String text() {
            return name();
        }
    }
}
