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
import io.helidon.service.registry.Service;

/**
 * Stateless factory for one connector type.
 * <p>
 * Providers are discovered through the service registry and may be shared by multiple messaging graphs. A provider
 * must not retain connector instances or transport resources. Each successful connector factory invocation returns a
 * new binding whose lifecycle is owned by its messaging graph.
 *
 */
@Api.Preview
@Service.Contract
public interface ConnectorProvider {
    /**
     * Connector type used to select this provider.
     * <p>
     * The type must be unique among the providers installed in an application. Helidon-provided connectors use the
     * {@code helidon-} prefix; third-party providers should use a similarly distinctive namespace.
     *
     * @return connector type
     */
    String connectorType();
}
