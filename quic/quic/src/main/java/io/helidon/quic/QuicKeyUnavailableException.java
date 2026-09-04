/*
 * Copyright (c) 2024, 2026 Oracle and/or its affiliates.
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

import java.io.Serial;
import java.util.Objects;

import io.helidon.common.Api;
import io.helidon.quic.QuicTLSEngine.KeySpace;

/**
 * Thrown when an operation on {@link QuicTLSEngine} doesn't have the necessary
 * QUIC keys for encrypting or decrypting packets. This can either be because
 * the keys aren't available for a particular {@linkplain KeySpace keyspace} or
 * the keys for the {@code keyspace} have been discarded.
 */
@Api.Internal
public final class QuicKeyUnavailableException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 8553365136999153478L;

    /**
     * Creates a new exception describing missing keys for the given key space.
     *
     * @param message  detail message
     * @param keySpace key space for which keys were unavailable
     */
    public QuicKeyUnavailableException(String message, KeySpace keySpace) {
        super(Objects.requireNonNull(keySpace, "keySpace").text()
                      + " keyspace: "
                      + Objects.requireNonNull(message, "message"));
    }
}
