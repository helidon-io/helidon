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

package io.helidon.security.providers.oidc.common.spi;

import java.util.Optional;
import java.util.function.Consumer;

import io.helidon.security.providers.oidc.common.TenantConfig;

/**
 * Configuration of a tenant.
 */
public interface TenantConfigFinder {
    /**
     * Default tenant id used when requesting configuration for unknown tenant.
     */
    String DEFAULT_TENANT_ID = "@default";

    /**
     * Open ID Configuration for this tenant.
     *
     * @param tenantId identified tenant, or {@link #DEFAULT_TENANT_ID}
     *                 if tenant was not identified, or default was chosen
     * @return open ID connect configuration, or empty optional in case we are missing configuration (this will fail the request
     * if the provider is not optional)
     */
    Optional<TenantConfig> config(String tenantId);

    /**
     * Register a change listener. When configuration is updated, call the consumer to remove the cached data for this tenant.
     *
     * @param tenantIdChangeConsumer consumer of tenant configuration changes
     */
    void onChange(Consumer<String> tenantIdChangeConsumer);
}
