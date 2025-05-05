/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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
package io.helidon.data.sql.datasource;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.data.sql.datasource.spi.DataSourceConfigProvider;

/**
 * {@link javax.sql.DataSource} configuration.
 * <p>This is the {@code data-source} configuration array member node of the {@code DataSource} configuration with {@code name}
 * and {@code provider.<provider>} nodes:
 * <pre>
 *    data-source:
 *       - name: something
 *         provider.ucp:
 *             # provider configuration
 *             username: "test"
 *             password: "changeit"
 *             ...
 * </pre>
 */
@Prototype.Blueprint
@Prototype.Configured("data-source")
@Prototype.RegistrySupport
interface DataSourceConfigBlueprint {

    /**
     * {@link javax.sql.DataSource} name.
     * Optional name to distinguish several data sources of the same type.
     * First available data source is returned when name is not set.
     *
     * @return the repository name
     */
    @Option.Configured
    @Option.Default("@default")
    String name();

    /**
     * Configuration of the used provider, such as UCP.
     *
     * @return provider configuration
     */
    @Option.Configured
    @Option.Provider(DataSourceConfigProvider.class)
    ProviderConfig provider();

}
