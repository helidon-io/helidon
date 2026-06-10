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
 * Service locators are registered with {@link HttpRules#registerLocator(HttpServiceLocator)} or
 * {@link HttpRules#registerLocator(String, HttpServiceLocator)}. The locator is invoked at request time after the locator's
 * path pattern matches. Path parameters from the locator path pattern are available from {@link ServerRequest#path()}.
 * <p>
 * Returning {@link Optional#empty()} means this locator has no service for the request and routing should continue with the
 * next available route.
 * <p>
 * WebServer caches located services by service instance identity until the server stops. Locators must return a stable,
 * bounded set of service instances. Return stable service instances and override {@link #maxServiceCacheSize()} when a
 * locator intentionally exposes more stable identities than the default.
 */
@FunctionalInterface
public interface HttpServiceLocator extends ServerLifecycle {
    /**
     * Default maximum number of service instances cached by one locator.
     */
    int DEFAULT_MAX_SERVICE_CACHE_SIZE = 1024;

    /**
     * Create a service locator from a locating function.
     *
     * @param locator function to locate a service
     * @return service locator
     */
    static HttpServiceLocator create(Function<ServerRequest, Optional<HttpService>> locator) {
        Objects.requireNonNull(locator);
        return request -> Objects.requireNonNull(locator.apply(Objects.requireNonNull(request, "request")),
                                                "HttpServiceLocator function must not return null");
    }

    /**
     * Locate an HTTP service for the current request.
     *
     * @param request server request, never {@code null}
     * @return located service, or empty if this locator has no service for this request
     */
    Optional<HttpService> locate(ServerRequest request);

    /**
     * Maximum number of distinct service instances this locator may cache.
     * <p>
     * The value must be greater than zero.
     *
     * @return maximum number of cached service instances, defaults to {@value #DEFAULT_MAX_SERVICE_CACHE_SIZE}
     */
    default int maxServiceCacheSize() {
        return DEFAULT_MAX_SERVICE_CACHE_SIZE;
    }
}
