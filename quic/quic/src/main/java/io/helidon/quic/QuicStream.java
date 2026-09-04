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

package io.helidon.quic;

import io.helidon.common.Api;

/**
 * Application view of a QUIC stream.
 */
@Api.Incubating
public interface QuicStream {

    /**
     * QUIC stream identifier.
     *
     * @return stream identifier
     */
    long streamId();

    /**
     * Whether the stream was initiated by the QUIC client.
     *
     * @return {@code true} for a client-initiated stream
     */
    boolean clientInitiated();

    /**
     * Whether the stream was initiated by this endpoint.
     *
     * @return {@code true} for a locally initiated stream
     */
    boolean locallyInitiated();

    /**
     * Whether the stream is bidirectional.
     *
     * @return {@code true} for a bidirectional stream
     */
    boolean bidirectional();
}
