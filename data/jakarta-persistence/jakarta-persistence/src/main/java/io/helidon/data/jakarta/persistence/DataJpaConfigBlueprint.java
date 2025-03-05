/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.data.jakarta.persistence;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.data.ProviderConfig;
import io.helidon.data.spi.ProviderConfigProvider;
import io.helidon.data.sql.common.SqlConfig;

/**
 * Configuration of Helidon Data for Jakarta Persistence.
 */
@Prototype.Blueprint
@Prototype.Configured(root = false, value = DataJpaSupportProviderService.PROVIDER_TYPE)
@Prototype.Provides(ProviderConfigProvider.class)
interface DataJpaConfigBlueprint extends SqlConfig, ProviderConfig {
    /**
     * Persistence provider class name.
     * Implementation of {@code jakarta.persistence.spi.PersistenceProvider}, e.g.
     * {@code org.eclipse.persistence.jpa.PersistenceProvider}
     * Allows proper persistence provider selection when multiple providers are available.
     *
     * @return fully qualified name of the persistence provider class
     */
    @Option.Configured
    Optional<String> providerClassName();

    /**
     * Path to database initialization script on classpath.
     *
     * @return database initialization script path
     */
    @Option.Configured
    Optional<String> initScript();

    /**
     * Path to database cleanup script on classpath.
     *
     * @return database cleanup script path
     */
    @Option.Configured
    Optional<String> dropScript();

    /**
     * Additional persistence unit or connection properties.
     *
     * @return the properties
     */
    @Option.Configured
    @Option.Singular("property")
    Map<String, String> properties();

    /**
     * Managed persistence entities.
     *
     * @return the entities list
     */
    @Option.Configured
    List<String> managedClasses();

    /**
     * Type of this provider.
     *
     * @return provider type - {@value DataJpaSupportProviderService#PROVIDER_TYPE}
     */
    @Override
    default String type() {
        return DataJpaSupportProviderService.PROVIDER_TYPE;
    }
}
