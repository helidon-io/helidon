/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
package io.helidon.servicecommon.rest;

import io.helidon.webserver.Routing;
import io.helidon.webserver.Service;

/**
 * Required behavior (primarily required by {@code HelidonRestCdiExtension} of service support implementations.
 */
public interface RestServiceSupport extends Service {

    /**
     * Configures service endpoint on the provided routing rules. This method
     * just adds the endpoint path (as defaulted or configured).
     *
     * @param defaultRules default routing rules (also accepts {@link io.helidon.webserver.Routing.Builder}
     * @param serviceEndpointRoutingRules actual rules (if different from default) for the service endpoint
     */
    void configureEndpoint(Routing.Rules defaultRules, Routing.Rules serviceEndpointRoutingRules);
}
