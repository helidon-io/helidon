/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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

import io.helidon.common.Api;

/**
 * Supplies contextual 1-RTT information that's available in the QUIC implementation of the
 * {@code java.net.http} module, to the QUIC TLS layer in the {@code java.base} module.
 */
@Api.Internal
public interface QuicOneRttContext {

    /**
     * Returns the largest packet number acknowledged by the peer in the 1-RTT packet space.
     *
     * @return the largest packet number acknowledged by the peer in the 1-RTT packet space
     */
    long largestPeerAcknowledgedPacketNumber();
}
