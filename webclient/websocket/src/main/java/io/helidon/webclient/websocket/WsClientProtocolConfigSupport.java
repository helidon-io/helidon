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

package io.helidon.webclient.websocket;

import io.helidon.builder.api.Prototype;

final class WsClientProtocolConfigSupport {
    private WsClientProtocolConfigSupport() {
    }

    static class BuilderDecorator implements Prototype.BuilderDecorator<WsClientProtocolConfig.BuilderBase<?, ?>> {
        @Override
        public void decorate(WsClientProtocolConfig.BuilderBase<?, ?> builder) {
            long maxFrameSize;
            try {
                maxFrameSize = builder.maxFrameSize().toBytes();
            } catch (ArithmeticException e) {
                throw new IllegalArgumentException("Maximum WebSocket frame size exceeds Integer.MAX_VALUE bytes", e);
            }
            if (maxFrameSize <= 0) {
                throw new IllegalArgumentException("Maximum WebSocket frame size must be greater than 0");
            }
            if (maxFrameSize > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Maximum WebSocket frame size exceeds Integer.MAX_VALUE bytes");
            }
        }
    }
}
