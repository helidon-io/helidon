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

package io.helidon.webserver.http;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import io.helidon.webserver.ServerLifecycle;

/**
 * Locates an {@link HttpService} for a matched request.
 * <p>
 * Service locators are registered with {@link HttpRules#register(HttpServiceLocator)} or
 * {@link HttpRules#register(String, HttpServiceLocator)}. The locator is invoked at request time after the locator's path
 * pattern matches. Path parameters from the locator path pattern are available from {@link ServerRequest#path()}.
 * <p>
 * Returning {@link Optional#empty()} means this locator has no service for the request and routing should continue with the
 * next available route.
 */
@FunctionalInterface
public interface HttpServiceLocator extends ServerLifecycle {

    /**
     * Create a service locator from a locating function.
     *
     * @param locator function to locate a service
     * @return service locator
     */
    static HttpServiceLocator create(Function<ServerRequest, Optional<HttpService>> locator) {
        Objects.requireNonNull(locator);
        return request -> Objects.requireNonNull(locator.apply(request),
                                                "HttpServiceLocator function must not return null");
    }

    /**
     * Locate an HTTP service for the current request.
     *
     * @param request server request
     * @return located service, or empty if this locator has no service for this request
     */
    Optional<HttpService> locate(ServerRequest request);
}
