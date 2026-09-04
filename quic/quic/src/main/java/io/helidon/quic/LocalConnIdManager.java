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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;

import io.helidon.quic.frame.NewConnectionIDFrame;
import io.helidon.quic.frame.QuicFrame;
import io.helidon.quic.frame.RetireConnectionIDFrame;
import io.helidon.quic.packet.QuicPacket;

import static io.helidon.quic.QuicTransportErrors.PROTOCOL_VIOLATION;

/**
 * Manages the connection ids advertised by the local endpoint of a connection.
 * - Produces outgoing NEW_CONNECTION_ID frames,
 * - handles incoming RETIRE_CONNECTION_ID frames,
 * - registers produced connection IDs with the QuicEndpoint
 * Handshake connection ID is created and registered by QuicConnection.
 */
final class LocalConnIdManager {
    private static final System.Logger LOGGER = System.getLogger(LocalConnIdManager.class.getName());

    private final QuicConnectionImpl connection;
    private final ReentrantLock lock = new ReentrantLock();
    // the connection ids (there can be more than one) with which the endpoint identifies this connection.
    // the key of this Map is a (RFC defined) sequence number for the connection id
    private final NavigableMap<Long, QuicConnectionId> localConnectionIds =
            Collections.synchronizedNavigableMap(new TreeMap<>());
    private long nextConnectionIdSequence;
    private boolean closed; // when true, no more connection IDs are registered
    private List<QuicConnectionId> frozenConnectionIds;
    private Set<Long> deferredRetirements;

    LocalConnIdManager(QuicConnectionImpl connection, QuicConnectionId handshakeConnectionId) {
        this.connection = connection;
        this.localConnectionIds.put(nextConnectionIdSequence++, handshakeConnectionId);
    }

    public QuicFrame nextFrame(int remaining) {
        if (localConnectionIds.size() >= 2) {
            return null;
        }
        int cidlen = connection.endpoint().idFactory().connectionIdLength();
        if (cidlen == 0) {
            return null;
        }
        // frame:
        // type - 1 byte
        // sequence number - var int
        // retire prior to - 1 byte (always zero)
        // connection id: <length> + 1 byte
        // stateless reset token - 16 bytes
        int len = 19 + cidlen + VariableLengthEncoder.encodedSize(nextConnectionIdSequence);
        if (len > remaining) {
            return null;
        }
        NewConnectionIDFrame newCidFrame;
        QuicConnectionId cid = newConnectionId();
        byte[] token = statelessTokenFor(cid);
        lock.lock();
        try {
            if (closed) {
                return null;
            }
            long sequenceNumber = nextConnectionIdSequence;
            newCidFrame = NewConnectionIDFrame.create(sequenceNumber, 0,
                                                      cid.asReadOnlyBuffer(), ByteBuffer.wrap(token));
            this.localConnectionIds.put(sequenceNumber, cid);
            if (!this.connection.endpoint().addConnectionId(cid, connection)) {
                this.localConnectionIds.remove(sequenceNumber);
                return null;
            }
            nextConnectionIdSequence++;
            if (deferredRetirements != null) {
                var iterator = deferredRetirements.iterator();
                while (iterator.hasNext()) {
                    long deferredSequence = iterator.next();
                    QuicConnectionId deferred = localConnectionIds.get(deferredSequence);
                    if (deferred == null || connection.endpoint().removeConnectionId(deferred, connection)) {
                        localConnectionIds.remove(deferredSequence);
                        iterator.remove();
                    }
                }
                if (deferredRetirements.isEmpty()) {
                    deferredRetirements = null;
                }
            }
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                log(System.Logger.Level.DEBUG, "Sending NEW_CONNECTION_ID frame");
            }
            return newCidFrame;
        } finally {
            lock.unlock();
        }
    }

    public List<QuicConnectionId> connectionIds() {
        lock.lock();
        try {
            return frozenConnectionIds == null
                    ? List.copyOf(localConnectionIds.values())
                    : frozenConnectionIds;
        } finally {
            lock.unlock();
        }
    }

    boolean withConnectionIdLock(BooleanSupplier action) {
        lock.lock();
        try {
            return action.getAsBoolean();
        } finally {
            lock.unlock();
        }
    }

    void freezeConnectionIds() {
        lock.lock();
        try {
            closed = true;
            if (frozenConnectionIds == null) {
                frozenConnectionIds = List.copyOf(localConnectionIds.values());
            }
        } finally {
            lock.unlock();
        }
    }

    void handleRetireConnectionIdFrame(QuicConnectionId incomingPacketDestConnId,
                                       QuicPacket.PacketType packetType,
                                       RetireConnectionIDFrame retireFrame)
            throws QuicTransportException {
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            log(System.Logger.Level.DEBUG, "Received RETIRE_CONNECTION_ID frame: %s", retireFrame);
        }
        QuicTLSEngine.KeySpace keySpace = packetType.keySpace().orElseThrow();
        QuicConnectionId toRetire;
        boolean retired;
        lock.lock();
        try {
            long seqNumber = retireFrame.sequenceNumber();
            if (seqNumber >= nextConnectionIdSequence) {
                // RFC-9000, section 19.16: Receipt of a RETIRE_CONNECTION_ID frame containing a
                // sequence number greater than any previously sent to the peer MUST be treated
                // as a connection error of type PROTOCOL_VIOLATION
                throw new QuicTransportException("Invalid sequence number " + seqNumber
                                                         + " in RETIRE_CONNECTION_ID frame",
                                                 keySpace,
                                                 retireFrame.typeField(), PROTOCOL_VIOLATION);
            }
            toRetire = this.localConnectionIds.get(seqNumber);
            if (toRetire == null) {
                return;
            }
            if (toRetire.equals(incomingPacketDestConnId)) {
                // RFC-9000, section 19.16: The sequence number specified in a RETIRE_CONNECTION_ID
                // frame MUST NOT refer to the Destination Connection ID field of the packet in which
                // the frame is contained. The peer MAY treat this as a connection error of type
                // PROTOCOL_VIOLATION.
                throw new QuicTransportException("Invalid connection id in RETIRE_CONNECTION_ID frame",
                                                 keySpace,
                                                 retireFrame.typeField(), PROTOCOL_VIOLATION);
            }
            if (deferredRetirements != null && deferredRetirements.contains(seqNumber)) {
                return;
            }
            retired = this.connection.endpoint().removeConnectionId(toRetire, connection);
            if (retired) {
                this.localConnectionIds.remove(seqNumber);
                if (deferredRetirements != null) {
                    deferredRetirements.remove(seqNumber);
                    if (deferredRetirements.isEmpty()) {
                        deferredRetirements = null;
                    }
                }
            } else {
                if (deferredRetirements == null) {
                    deferredRetirements = new HashSet<>();
                }
                deferredRetirements.add(seqNumber);
            }
        } finally {
            lock.unlock();
        }
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            log(System.Logger.Level.DEBUG,
                (retired ? "retired" : "deferred retirement of") + " connection id " + toRetire);
        }
    }

    private void log(System.Logger.Level level, String format, Object... arguments) {
        if (arguments.length == 0) {
            connection.log(LOGGER, level, "%s", format);
        } else {
            connection.log(LOGGER, level, format, arguments);
        }
    }

    private QuicConnectionId newConnectionId() {
        return connection.endpoint().idFactory().newConnectionId();

    }

    private byte[] statelessTokenFor(QuicConnectionId cid) {
        return connection.endpoint().idFactory().statelessTokenFor(cid);
    }
}
