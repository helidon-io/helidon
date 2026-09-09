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

package io.helidon.messaging.spi;

import io.helidon.common.Api;
import io.helidon.config.Config;
import io.helidon.service.registry.Service;

/**
 * Service Registry contract for the outgoing capability of a connector provider.
 * <p>
 * The messaging runtime discovers the provider through {@link ConnectorProvider}; this contract also supports direct
 * lookup of providers that can create outgoing connectors.
 *
 */
@Api.Preview
@Service.Contract
public interface OutgoingConnectorProvider extends ConnectorProvider {
    /**
     * Create one unstarted outgoing connector.
     * <p>
     * The connector factory may validate and snapshot configuration, but must not acquire transport resources or
     * create threads. The messaging graph owns the returned connector and invokes its lifecycle.
     *
     * @param config effective binding configuration
     * @return outgoing connector
     */
    OutgoingConnector createOutgoingConnector(Config config);
}
