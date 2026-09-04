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

import java.io.Serial;
import java.util.Objects;

import io.helidon.common.Api;

/**
 * Used internally to indicate Quic stream limit has been reached.
 */
@Api.Internal
public final class QuicStreamLimitException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 4181770819022847041L;

    /**
     * Creates an exception signaling that the stream limit has been reached.
     *
     * @param message details about the limit violation
     */
    public QuicStreamLimitException(String message) {
        super(Objects.requireNonNull(message, "message"));
    }
}
