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

import io.helidon.common.socket.SocketContext;
import io.helidon.http.Headers;

final class Http3FrameListenerUtil {
    private Http3FrameListenerUtil() {
    }

    static Http3FrameListener toSingleListener(List<Http3FrameListener> listeners) {
        if (listeners.isEmpty()) {
            return NoOpFrameListener.INSTANCE;
        }
        if (listeners.size() == 1) {
            return listeners.getFirst();
        }
        return new ListFrameListener(List.copyOf(listeners));
    }

    private static final class NoOpFrameListener implements Http3FrameListener {
        private static final NoOpFrameListener INSTANCE = new NoOpFrameListener();

        @Override
        public boolean enabled() {
            return false;
        }
    }

    private static final class ListFrameListener implements Http3FrameListener {
        private final List<Http3FrameListener> delegates;

        private ListFrameListener(List<Http3FrameListener> delegates) {
            this.delegates = delegates;
        }

        @Override
        public boolean enabled() {
            return delegates.stream().anyMatch(Http3FrameListener::enabled);
        }

        @Override
        public boolean rawDataEnabled() {
            return delegates.stream().anyMatch(Http3FrameListener::rawDataEnabled);
        }

        @Override
        public void frameHeader(SocketContext context,
                                long streamId,
                                long frameType,
                                long frameLength,
                                int encodedLength) {
            for (Http3FrameListener delegate : delegates) {
                if (delegate.enabled()) {
                    delegate.frameHeader(context, streamId, frameType, frameLength, encodedLength);
                }
            }
        }

        @Override
        public void rawFrameHeader(SocketContext context, long streamId, byte[] data) {
            for (Http3FrameListener delegate : delegates) {
                if (delegate.rawDataEnabled()) {
                    delegate.rawFrameHeader(context, streamId, data);
                }
            }
        }

        @Override
        public void frameData(SocketContext context, long streamId, int byteCount, boolean last) {
            for (Http3FrameListener delegate : delegates) {
                if (delegate.enabled()) {
                    delegate.frameData(context, streamId, byteCount, last);
                }
            }
        }

        @Override
        public void rawFrameData(SocketContext context, long streamId, byte[] data, boolean last) {
            for (Http3FrameListener delegate : delegates) {
                if (delegate.rawDataEnabled()) {
                    delegate.rawFrameData(context, streamId, data, last);
                }
            }
        }

        @Override
        public void streamType(SocketContext context, long streamId, Http3StreamType streamType, int encodedLength) {
            for (Http3FrameListener delegate : delegates) {
                if (delegate.enabled()) {
                    delegate.streamType(context, streamId, streamType, encodedLength);
                }
            }
        }

        @Override
        public void streamData(SocketContext context, long streamId, String label, int byteCount) {
            for (Http3FrameListener delegate : delegates) {
                if (delegate.enabled()) {
                    delegate.streamData(context, streamId, label, byteCount);
                }
            }
        }

        @Override
        public void rawStreamData(SocketContext context, long streamId, String label, byte[] data) {
            for (Http3FrameListener delegate : delegates) {
                if (delegate.rawDataEnabled()) {
                    delegate.rawStreamData(context, streamId, label, data);
                }
            }
        }

        @Override
        public void requestHeaders(SocketContext context,
                                   long streamId,
                                   String method,
                                   String scheme,
                                   String authority,
                                   String path,
                                   Headers headers) {
            for (Http3FrameListener delegate : delegates) {
                if (delegate.enabled()) {
                    delegate.requestHeaders(context, streamId, method, scheme, authority, path, headers);
                }
            }
        }

        @Override
        public void responseHeaders(SocketContext context, long streamId, int status, Headers headers) {
            for (Http3FrameListener delegate : delegates) {
                if (delegate.enabled()) {
                    delegate.responseHeaders(context, streamId, status, headers);
                }
            }
        }

        @Override
        public void trailers(SocketContext context, long streamId, Headers trailers) {
            for (Http3FrameListener delegate : delegates) {
                if (delegate.enabled()) {
                    delegate.trailers(context, streamId, trailers);
                }
            }
        }
    }
}
