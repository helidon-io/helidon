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
 * Failure of a supported QUIC application operation.
 */
@Api.Incubating
public class QuicException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 7275234535113160227L;

    /**
     * Creates a QUIC exception.
     *
     * @param message failure description
     */
    public QuicException(String message) {
        super(Objects.requireNonNull(message, "message"));
    }

    /**
     * Creates a QUIC exception.
     *
     * @param message failure description
     * @param cause original failure
     */
    public QuicException(String message, Throwable cause) {
        super(Objects.requireNonNull(message, "message"),
              Objects.requireNonNull(cause, "cause"));
    }
}
