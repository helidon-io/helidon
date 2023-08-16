/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.webserver.http;

import io.helidon.webserver.ServerLifecycle;

/**
 * HTTP filter.
 */
public interface Filter extends ServerLifecycle {
    /**
     * Handle a request.
     * If request processing should be terminated, do not call {@link FilterChain#proceed()}.
     * If request processing should continue with next filter,
     * call {@link FilterChain#proceed()}.
     * {@link FilterChain#proceed()} may throw an exception. The following exceptions
     * mean the connection will close (and the request cannot finish):
     * <ul>
     *     <li>{@link io.helidon.webserver.CloseConnectionException}</li>
     *     <li>{@link java.io.UncheckedIOException}</li>
     * </ul>
     * Filters may throw {@link io.helidon.http.HttpException} (or its subclasses), all filters before the current
     * filter will not receive such an exception - it will be processed before the method
     * {@link FilterChain#proceed()} returns.
     *
     * @param chain to proceed with further filters (or routing if this is the last filter)
     * @param req   routing request
     * @param res   routing response
     */
    void filter(FilterChain chain, RoutingRequest req, RoutingResponse res);
}
