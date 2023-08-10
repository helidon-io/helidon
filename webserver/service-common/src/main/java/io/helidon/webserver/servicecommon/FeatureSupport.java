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
package io.helidon.webserver.servicecommon;

import java.util.Optional;

import io.helidon.webserver.http.HttpFeature;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.HttpService;

/**
 * Adds support for features that require a service to be exposed.
 * This covers configurability of the context root, CORS support etc.
 */
public interface FeatureSupport extends HttpFeature {

    /**
     * Configures service endpoint on the provided routing rules. This method
     * just adds the endpoint path (as defaulted or configured).
     *
     * @param defaultRouting default routing builder
     * @param featureRouting actual rules (if different from default) for the service endpoint
     */
    void setup(HttpRouting.Builder defaultRouting, HttpRouting.Builder featureRouting);

    /**
     * Configures service endpoint on the provided routing rules. This method
     * just adds the endpoint path (as defaulted or configured).
     *
     * @param routing routing used to register this service
     */
    @Override
    default void setup(HttpRouting.Builder routing) {
        setup(routing, routing);
    }

    /**
     * If this feature is represented by a service, return it here, to simplify implementation.
     * Otherwise you will need to implement
     * {@link #setup(HttpRouting.Builder, HttpRouting.Builder)}.
     *
     * @return service if implemented
     */
    default Optional<HttpService> service() {
        return Optional.empty();
    }

    /**
     * Web context of this service.
     *
     * @return context path to be registered
     */
    String context();

    /**
     * Web context of this service as configured (may not contain leading slash).
     *
     * @return context path exactly as configured
     */
    String configuredContext();

    /**
     * When a service is not enabled, it can be omitted from registration with server.
     *
     * @return {@code true} for enabled services
     */
    boolean enabled();
}
