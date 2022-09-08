/*
 * Copyright (c) 2018, 2022 Oracle and/or its affiliates.
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

package io.helidon.reactive.webserver;

import java.util.function.Consumer;

import io.helidon.common.http.ServerResponseHeaders;
import io.helidon.common.reactive.Single;

/**
 * Extends {@link io.helidon.common.http.ServerResponseHeaders} interface by adding HTTP response headers oriented constants and
 * convenient methods.
 * Use constants located in {@link io.helidon.common.http.Http.Header} as standard header names.
 *
 * <h2>Lifecycle</h2>
 * Headers can be muted until {@link #send() send} to the client. It is also possible to register a '{@link #beforeSend(Consumer)
 * before send}' function which can made 'last minute mutation'.
 * <p>
 * Headers are send together with HTTP status code also automatically just before first chunk of response data is send.
 *
 * @see io.helidon.common.http.Http.Header
 */
public interface ResponseHeaders extends ServerResponseHeaders {
    /**
     * Register a {@link Consumer} which is executed just before headers are send. {@code Consumer} can made 'last minute
     * changes' in headers.
     * <p>
     * Sending of headers to the client is postponed after all registered consumers are finished.
     * <p>
     * There is no guarantied execution order.
     *
     * @param headersConsumer a consumer which will be executed just before headers are send.
     */
    void beforeSend(Consumer<ResponseHeaders> headersConsumer);

    /**
     * Returns a {@link io.helidon.common.reactive.Single} which is completed when all headers are sent to the client.
     *
     * @return a single of the headers
     */
    Single<ResponseHeaders> whenSent();

    /**
     * Send headers and status code to the client. This instance become immutable after that
     * (all muting methods throws {@link IllegalStateException}).
     * <p>
     * It is non-blocking method returning a {@link io.helidon.common.reactive.Single}.
     *
     * @return a completion stage of sending process.
     */
    Single<ResponseHeaders> send();
}
