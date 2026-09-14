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

package io.helidon.webserver.http1.spi;

import io.helidon.common.Api;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.http.RoutingRequest;
import io.helidon.webserver.http.RoutingResponse;

/**
 * HTTP routing support for an HTTP/1 protocol upgrade that must run HTTP filters
 * and route policies before the protocol switch.
 */
@Api.Internal
public interface Http1UpgradeRouting {
    /**
     * Route a prepared HTTP/1 upgrade through filters and protocol-upgrade policies.
     *
     * @param ctx routed connection context
     * @param request routing request
     * @param response routing response used for filters and upgrade policies
     * @param upgradeResponse narrow response exposed to the upgrade implementation
     * @param upgrade prepared upgrade
     * @return upgrade result
     */
    Http1UpgradeResult routeUpgrade(ConnectionContext ctx,
                                    RoutingRequest request,
                                    RoutingResponse response,
                                    Http1UpgradeResponse upgradeResponse,
                                    Http1RoutedUpgrade upgrade);
}
