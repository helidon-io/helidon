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
 * Signal that a structurally valid frame exceeds a local resource policy and its QUIC packet must be discarded.
 * Structural and mandatory packet-type validation must complete before the packet is discarded. Connection-state validation
 * associated with applying the rejected frame is not performed.
 */
@Api.Internal
public final class QuicPacketDiscardException extends QuicPacketDecodeException {
    @Serial
    private static final long serialVersionUID = 5380465033971368382L;
    /**
     * Frame type that triggered the discard policy.
     */
    private final long frameType;

    /**
     * Create a packet-discard signal.
     *
     * @param message description of the local policy that rejected the packet
     * @param frameType decoded frame type that triggered the policy
     */
    public QuicPacketDiscardException(String message, long frameType) {
        super(message);
        this.frameType = frameType;
    }

    /**
     * Returns the decoded frame type that triggered the policy.
     *
     * @return decoded frame type
     */
    public long frameType() {
        return frameType;
    }

    @Override
    public QuicPacketDiscardException fillInStackTrace() {
        return this;
    }
}
