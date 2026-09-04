/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.http.http3;

import java.util.Objects;

import io.helidon.common.Api;
import io.helidon.quic.VariableLengthEncoder;
import io.helidon.quic.stream.QuicStreams;

/**
 * Immutable HTTP/3 GOAWAY value.
 */
@Api.Internal
public final class Http3GoAway {
    private final Type type;
    private final long identifier;

    private Http3GoAway(Type type, long identifier) {
        this.type = type;
        this.identifier = identifier;
    }

    /**
     * Create a server GOAWAY value identifying a client-initiated bidirectional request stream.
     *
     * @param streamId request stream identifier
     * @return GOAWAY value
     */
    public static Http3GoAway requestStream(long streamId) {
        requireVarInt(streamId);
        if (!QuicStreams.isClientInitiated(streamId) || !QuicStreams.isBidirectional(streamId)) {
            throw new IllegalArgumentException(
                    "HTTP/3 request-stream GOAWAY requires a client-initiated bidirectional stream ID: " + streamId);
        }
        return new Http3GoAway(Type.REQUEST_STREAM_ID, streamId);
    }

    /**
     * Create a client GOAWAY value identifying a server push.
     *
     * @param pushId push identifier
     * @return GOAWAY value
     */
    public static Http3GoAway pushId(long pushId) {
        requireVarInt(pushId);
        return new Http3GoAway(Type.PUSH_ID, pushId);
    }

    /**
     * GOAWAY identifier.
     *
     * @return request-stream or push identifier
     */
    public long identifier() {
        return identifier;
    }

    /**
     * GOAWAY identifier type.
     *
     * @return identifier type
     */
    public Type type() {
        return type;
    }

    /**
     * Whether this request-stream GOAWAY rejects the supplied request stream.
     *
     * @param streamId request stream identifier
     * @return whether the request stream is rejected
     */
    public boolean rejectsStream(long streamId) {
        return type == Type.REQUEST_STREAM_ID && streamId >= identifier;
    }

    /**
     * Whether this value can follow the previous GOAWAY value on the same connection.
     * GOAWAY identifiers may stay equal or decrease, but cannot increase or change type.
     *
     * @param previous previously observed or sent GOAWAY value
     * @return whether this value is a valid successor
     */
    public boolean isValidSuccessorOf(Http3GoAway previous) {
        Objects.requireNonNull(previous, "previous");
        return type == previous.type && identifier <= previous.identifier;
    }

    private static void requireVarInt(long identifier) {
        if (identifier < 0 || identifier > VariableLengthEncoder.MAX_ENCODED_INTEGER) {
            throw new IllegalArgumentException(
                    "HTTP/3 GOAWAY identifier must be a QUIC variable-length integer: " + identifier);
        }
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof Http3GoAway other)) {
            return false;
        }
        return identifier == other.identifier && type == other.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, identifier);
    }

    @Override
    public String toString() {
        return "Http3GoAway[type=" + type + ", identifier=" + identifier + ']';
    }

    /**
     * Meaning of a GOAWAY identifier for its sender.
     */
    public enum Type {
        /**
         * Server-sent identifier for a client-initiated bidirectional request stream.
         */
        REQUEST_STREAM_ID,
        /**
         * Client-sent push identifier.
         */
        PUSH_ID
    }
}
