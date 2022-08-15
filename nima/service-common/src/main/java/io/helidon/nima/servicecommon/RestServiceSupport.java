/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
package io.helidon.nima.servicecommon;

import io.helidon.nima.webserver.http.HttpRules;
import io.helidon.nima.webserver.http.HttpService;

/**
 * Required behavior (primarily required by {@code HelidonRestCdiExtension}) of service support implementations.
 */
public interface RestServiceSupport extends HttpService {

    /**
     * Configures service endpoint on the provided routing rules. This method
     * just adds the endpoint path (as defaulted or configured).
     *
     * @param defaultRules                default routing rules (also accepts
     *                                    {@link io.helidon.nima.webserver.http.HttpRouting.Builder}
     * @param serviceEndpointRoutingRules actual rules (if different from default) for the service endpoint
     */
    void configureEndpoint(HttpRules defaultRules, HttpRules serviceEndpointRoutingRules);

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
