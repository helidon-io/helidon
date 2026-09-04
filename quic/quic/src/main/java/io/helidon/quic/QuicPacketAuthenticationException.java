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

import java.io.Serial;
import java.util.Objects;

import io.helidon.common.Api;

/**
 * Unchecked signal that QUIC packet authentication or Retry integrity verification failed.
 */
@Api.Internal
public final class QuicPacketAuthenticationException extends IllegalArgumentException {
    @Serial
    private static final long serialVersionUID = 4691945113340461102L;

    /**
     * Creates an authentication-failure signal.
     *
     * @param message local diagnostic
     * @param cause   cryptographic provider failure
     */
    public QuicPacketAuthenticationException(String message, Throwable cause) {
        super(Objects.requireNonNull(message, "message"), Objects.requireNonNull(cause, "cause"));
    }
}
