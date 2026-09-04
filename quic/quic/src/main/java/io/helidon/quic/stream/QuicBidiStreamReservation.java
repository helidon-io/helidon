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

package io.helidon.quic.stream;

import io.helidon.common.Api;

/**
 * Reserved credit for one locally initiated bidirectional QUIC stream.
 *
 * <p>A reservation owns stream credit until {@link #open()} succeeds or the reservation is
 * {@linkplain #close() closed}. Callers that do not open the stream must close the reservation so another stream can
 * use the credit.
 */
@Api.Internal
public interface QuicBidiStreamReservation extends AutoCloseable {

    /**
     * Creates the stream represented by this reservation.
     *
     * <p>This method may be invoked at most once. The stream identifier is allocated only when this invocation wins a
     * race with {@link #close()}.
     *
     * @return newly created bidirectional stream
     * @throws IllegalStateException if this reservation was already opened or closed
     */
    QuicBidiStream open();

    /**
     * Releases unused stream credit.
     *
     * <p>This operation is idempotent. It has no effect after {@link #open()} starts creating the stream.
     */
    @Override
    void close();
}
