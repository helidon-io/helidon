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

import java.util.concurrent.CompletableFuture;

import io.helidon.common.Api;

/**
 * Client-side QUIC connection that has not necessarily completed its handshake.
 */
@Api.Internal
public interface QuicClientConnection extends QuicConnection {

    /**
     * Starts the QUIC handshake.
     *
     * @return future completed when the handshake completes
     */
    CompletableFuture<Void> startHandshake();
}
