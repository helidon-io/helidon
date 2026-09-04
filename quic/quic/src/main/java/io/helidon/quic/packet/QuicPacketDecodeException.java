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

package io.helidon.quic.packet;

import java.io.Serial;

import io.helidon.common.Api;

/**
 * Unchecked failure to decode an unauthenticated or otherwise discardable QUIC packet.
 *
 * <p>This signal does not imply a connection close. Authenticated protocol violations use
 * {@link io.helidon.quic.QuicTransportException} instead.
 */
@Api.Internal
public sealed class QuicPacketDecodeException extends IllegalArgumentException
        permits QuicPacketDiscardException {
    @Serial
    private static final long serialVersionUID = -3810164094414044310L;

    QuicPacketDecodeException(String message) {
        super(message);
    }

    QuicPacketDecodeException(String message, Throwable cause) {
        super(message, cause);
    }
}
