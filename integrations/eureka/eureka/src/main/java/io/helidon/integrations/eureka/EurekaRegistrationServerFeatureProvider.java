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
package io.helidon.integrations.eureka;

import io.helidon.common.config.Config;
import io.helidon.webserver.spi.ServerFeatureProvider;

import static io.helidon.integrations.eureka.EurekaRegistrationServerFeature.EUREKA_ID;

/**
 * A {@link ServerFeatureProvider} that creates and installs a {@link EurekaRegistrationServerFeature}.
 *
 * <p>Most users will never need to programmatically interact with any of the classes in this package.</p>
 *
 * @see #create(Config, String)
 *
 * @see EurekaRegistrationServerFeature
 */
public final class EurekaRegistrationServerFeatureProvider implements ServerFeatureProvider<EurekaRegistrationServerFeature> {

    /**
     * Creates a new {@link EurekaRegistrationServerFeatureProvider}.
     *
     * @deprecated For {@link java.util.ServiceLoader} use only.
     */
    @Deprecated // For java.util.ServiceLoader use only.
    public EurekaRegistrationServerFeatureProvider() {
        super();
    }

    /**
     * Returns {@value EurekaRegistrationServerFeature#EUREKA_ID} when invoked.
     *
     * @return {@value EurekaRegistrationServerFeature#EUREKA_ID} when invoked
     *
     * @see io.helidon.common.config.ConfiguredProvider#configKey()
     */
    @Override // ServerFeatureProvider<EurekaRegistrationServerFeature> (ConfiguredProvider<EurekaRegistrationServerFeature>
    public String configKey() {
        return EUREKA_ID;
    }

    /**
     * Creates and returns a non-{@code null} {@link EurekaRegistrationServerFeature}, configured using the supplied
     * {@link Config}.
     *
     * <p>Following Helidon convention, this method returns a {@link EurekaRegistrationServerFeature} as if by executing
     * code with effects identical to those of the following:</p>
     *
     * <blockquote><pre>return {@link EurekaRegistrationConfig}.{@link EurekaRegistrationConfig#builder() builder()}
     *     .{@linkplain EurekaRegistrationConfig.BuilderBase#config(Config) config}(config)
     *     .{@linkplain EurekaRegistrationConfig.BuilderBase#name(String) name}(name)
     *     .{@linkplain EurekaRegistrationConfig.Builder#build() build}();</pre></blockquote>
     *
     * <p>The {@link Config} will be used to configure a {@link EurekaRegistrationConfig} prototype that describes both
     * the initial registration's details and its process. Most users will interact with this entire feature via
     * configuration only.</p>
     *
     * @param config a {@link Config} node; must not be {@code null}
     *
     * @param name the name of the configured implementation; must not be {@code null}
     *
     * @return a non-{@code null} {@link EurekaRegistrationServerFeature}
     *
     * @see io.helidon.common.config.ConfiguredProvider#create(Config, String)
     *
     * @see EurekaRegistrationConfig
     */
    @Override // ServerFeatureProvider<EurekaRegistrationServerFeature> (ConfiguredProvider<EurekaRegistrationServerFeature>
    public EurekaRegistrationServerFeature create(Config config, String name) {
        return EurekaRegistrationConfig.builder()
            .config(config)
            .name(name)
            .build();
    }

}
