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

package io.helidon.websocket;

import io.helidon.builder.api.Prototype;

final class WsProtocolConfigSupport {
    static final WsProtocolConfig DEFAULT = WsProtocolConfig.create();

    private WsProtocolConfigSupport() {
    }

    static class BuilderDecorator implements Prototype.BuilderDecorator<WsProtocolConfig.BuilderBase<?, ?>> {
        @Override
        public void decorate(WsProtocolConfig.BuilderBase<?, ?> builder) {
            long maxBufferedMessageSize;
            try {
                maxBufferedMessageSize = builder.maxBufferedMessageSize().toBytes();
            } catch (ArithmeticException e) {
                throw new IllegalArgumentException("Maximum buffered WebSocket message size exceeds Integer.MAX_VALUE bytes",
                                                   e);
            }
            if (maxBufferedMessageSize < 0) {
                throw new IllegalArgumentException("Maximum buffered WebSocket message size must not be negative");
            }
            if (maxBufferedMessageSize > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Maximum buffered WebSocket message size exceeds Integer.MAX_VALUE bytes");
            }
        }
    }
}
