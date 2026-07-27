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

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.Size;

/**
 * WebSocket protocol configuration available from a {@link WsSession}.
 */
@Prototype.Blueprint(decorator = WsProtocolConfigSupport.BuilderDecorator.class)
@Prototype.Configured
@Prototype.IncludeDefaultMethods
interface WsProtocolConfigBlueprint {
    /**
     * Maximum size of a WebSocket message buffered for delivery to a listener.
     * Binary message size is measured in bytes. Text message size is approximated using the number of UTF-16 code units
     * in each decoded string fragment, as reported by {@link String#length()}, and may be smaller than the UTF-8 payload
     * size.
     * This setting does not limit messages delivered to listeners as fragments, readers, or input streams.
     * The value must be between {@code 0 B} and {@link Integer#MAX_VALUE} bytes, inclusive. A value of {@code 0 B}
     * disables buffering of non-empty messages while allowing empty messages.
     * Default is 1 MiB.
     *
     * @return maximum size of a buffered message
     */
    @Option.Configured
    @Option.Default("1 MiB")
    default Size maxBufferedMessageSize() {
        return Size.create(1, Size.Unit.MIB);
    }
}
