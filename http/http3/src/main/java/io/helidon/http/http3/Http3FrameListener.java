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

import java.util.List;

import io.helidon.common.Api;
import io.helidon.common.socket.SocketContext;
import io.helidon.http.Headers;

/**
 * Listener for HTTP/3 frame events.
 */
@Api.Internal
public interface Http3FrameListener {
    /**
     * Create a composite listener.
     *
     * @param listeners listeners to compose
     * @return composite listener, or a no-op listener when the list is empty
     */
    static Http3FrameListener create(List<Http3FrameListener> listeners) {
        return Http3FrameListenerUtil.toSingleListener(listeners);
    }

    /**
     * Whether this listener currently consumes events.
     *
     * @return whether listener callbacks should be invoked
     */
    default boolean enabled() {
        return true;
    }

    /**
     * Whether this listener consumes raw protocol bytes.
     * Callers use this capability to avoid materializing payload data for metadata-only listeners.
     * Implementations should enable it only for an explicit unsafe diagnostic mode.
     *
     * @return whether raw byte callbacks should be invoked
     */
    default boolean rawDataEnabled() {
        return false;
    }

    /**
     * HTTP/3 frame header.
     *
     * @param context socket context
     * @param streamId stream identifier
     * @param frameType frame type
     * @param frameLength frame payload length
     * @param encodedLength encoded frame-header length
     */
    default void frameHeader(SocketContext context,
                             long streamId,
                             long frameType,
                             long frameLength,
                             int encodedLength) {
    }

    /**
     * Raw HTTP/3 frame header data.
     * The data contains the exact wire encoding and is provided only when
     * {@link #rawDataEnabled()} returns {@code true}.
     *
     * @param context socket context
     * @param streamId stream identifier
     * @param data encoded frame header
     */
    default void rawFrameHeader(SocketContext context, long streamId, byte[] data) {
    }

    /**
     * HTTP/3 frame payload metadata. A payload may be delivered in multiple callbacks.
     *
     * @param context socket context
     * @param streamId stream identifier
     * @param byteCount payload chunk size
     * @param last whether this is the final chunk of the frame payload
     */
    default void frameData(SocketContext context, long streamId, int byteCount, boolean last) {
    }

    /**
     * Raw HTTP/3 frame payload data. A payload may be delivered in multiple callbacks.
     * Data is provided only when {@link #rawDataEnabled()} returns {@code true}.
     *
     * @param context socket context
     * @param streamId stream identifier
     * @param data payload chunk
     * @param last whether this is the final chunk of the frame payload
     */
    default void rawFrameData(SocketContext context, long streamId, byte[] data, boolean last) {
    }

    /**
     * HTTP/3 unidirectional stream type.
     *
     * @param context socket context
     * @param streamId stream identifier
     * @param streamType stream type
     * @param encodedLength encoded stream-type length
     */
    default void streamType(SocketContext context, long streamId, Http3StreamType streamType, int encodedLength) {
    }

    /**
     * HTTP/3 stream-data metadata for data that is not an HTTP frame payload.
     *
     * @param context socket context
     * @param streamId stream identifier
     * @param label data description
     * @param byteCount stream-data size
     */
    default void streamData(SocketContext context, long streamId, String label, int byteCount) {
    }

    /**
     * Raw HTTP/3 stream data that is not an HTTP frame payload.
     * Data is provided only when {@link #rawDataEnabled()} returns {@code true}.
     *
     * @param context socket context
     * @param streamId stream identifier
     * @param label data description
     * @param data stream bytes
     */
    default void rawStreamData(SocketContext context, long streamId, String label, byte[] data) {
    }

    /**
     * HTTP/3 request headers.
     *
     * @param context socket context
     * @param streamId stream identifier
     * @param method request method
     * @param scheme request scheme, or an empty string when absent
     * @param authority request authority, or an empty string when absent
     * @param path request path, or an empty string when absent
     * @param headers request headers
     */
    default void requestHeaders(SocketContext context,
                                long streamId,
                                String method,
                                String scheme,
                                String authority,
                                String path,
                                Headers headers) {
    }

    /**
     * HTTP/3 response headers.
     *
     * @param context socket context
     * @param streamId stream identifier
     * @param status response status
     * @param headers response headers
     */
    default void responseHeaders(SocketContext context, long streamId, int status, Headers headers) {
    }

    /**
     * HTTP/3 trailer headers.
     *
     * @param context socket context
     * @param streamId stream identifier
     * @param trailers trailer headers
     */
    default void trailers(SocketContext context, long streamId, Headers trailers) {
    }
}
