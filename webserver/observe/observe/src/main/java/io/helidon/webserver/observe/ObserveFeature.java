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

package io.helidon.webserver.observe;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.Weighted;
import io.helidon.common.config.Config;
import io.helidon.common.config.GlobalConfig;
import io.helidon.http.Http;
import io.helidon.http.HttpException;
import io.helidon.webserver.cors.CorsSupport;
import io.helidon.webserver.http.HttpFeature;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.observe.spi.ObserveProvider;

/**
 * Support for all observe providers that are available (or configured).
 */
public class ObserveFeature implements HttpFeature, Weighted {
    private static final double WEIGHT = 80;

    private final List<ProviderSetup> providers;
    private final boolean enabled;
    private final String endpoint;
    private final double weight;

    private ObserveFeature(Builder builder, List<ProviderSetup> providerSetups) {
        this.enabled = builder.enabled;
        this.endpoint = builder.endpoint;
        this.weight = builder.weight;
        this.providers = providerSetups;
    }

    /**
     * A new builder to customize observe support.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create a new support with default configuration and an explicit list of providers.
     * This will not use providers discovered by {@link java.util.ServiceLoader}.
     *
     * @param providers providers to use
     * @return a new observe support
     */
    public static ObserveFeature create(ObserveProvider... providers) {
        return builder()
                .useSystemServices(false)
                .update(it -> {
                    for (ObserveProvider provider : providers) {
                        it.addProvider(provider);
                    }
                })
                .build();
    }

    /**
     * Create a new support with default configuration and a list of providers
     * discovered by {@link java.util.ServiceLoader}.
     *
     * @return a new observe support
     */
    public static ObserveFeature create() {
        return builder().build();
    }

    /**
     * Create a new support with custom configuration.
     *
     * @param config configuration to read observe config from
     * @return a new observe support
     */
    public static ObserveFeature create(Config config) {
        return builder().config(config).build();
    }

    @Override
    public void setup(HttpRouting.Builder routing) {
        if (enabled) {
            for (ProviderSetup provider : providers) {
                routing.register(provider.endpoint + "/*", provider.cors());
                provider.provider().register(provider.config(), provider.endpoint(), routing);
            }
        } else {
            routing.get(endpoint, (req, res) -> {
                throw new HttpException("Observe endpoint is disabled", Http.Status.SERVICE_UNAVAILABLE_503, true);
            });
        }
    }

    /**
     * Fluent API builder for {@link ObserveFeature}.
     */
    public static class Builder implements io.helidon.common.Builder<Builder, ObserveFeature> {
        private final HelidonServiceLoader.Builder<ObserveProvider> observeProviders =
                HelidonServiceLoader.builder(ServiceLoader.load(ObserveProvider.class));

        private double weight = WEIGHT;
        private CorsSupport corsSupport = CorsSupport.create();
        private boolean enabled = true;
        private String endpoint = "/observe";
        private Config config;

        private Builder() {
            config(GlobalConfig.config().get("observe"));
        }

        @Override
        public ObserveFeature build() {
            List<ProviderSetup> providerSetups;
            if (enabled) {
                List<ObserveProvider> observeProviders = this.observeProviders.build()
                        .asList();
                providerSetups = new ArrayList<>(observeProviders.size());
                for (ObserveProvider provider : observeProviders) {
                    Config providerConfig = config.get(provider.configKey());
                    boolean enabled = providerConfig.get("enabled").asBoolean().orElse(true);
                    if (!enabled) {
                        // disabled provider, ignore it
                        continue;
                    }
                    String endpoint = providerEndpoint(providerConfig.get("endpoint").asString()
                                                               .orElseGet(provider::defaultEndpoint));
                    CorsSupport cors = providerConfig.get("cors").map(CorsSupport::create).orElse(corsSupport);
                    providerSetups.add(new ProviderSetup(endpoint, providerConfig, cors, provider));
                }
            } else {
                providerSetups = List.of();
            }
            return new ObserveFeature(this, providerSetups);
        }

        /**
         * Whether to use services discovered by {@link java.util.ServiceLoader}.
         *
         * @param useServices set to {@code false} to disable discovery
         * @return updated builder
         */
        public Builder useSystemServices(boolean useServices) {
            observeProviders.useSystemServiceLoader(useServices);
            return this;
        }

        /**
         * Add a provider.
         *
         * @param provider provider to use
         * @return updated builder
         */
        public Builder addProvider(ObserveProvider provider) {
            observeProviders.addService(provider);
            return this;
        }

        /**
         * Update this builder from configuration.
         *
         * @param config config on the node of observe support
         * @return updated builder
         */
        public Builder config(Config config) {
            // use config to set up defaults
            config.get("cors").map(CorsSupport::create).ifPresent(this::corsSupport);
            config.get("enabled").asBoolean().ifPresent(this::enabled);
            config.get("endpoint").asString().ifPresent(this::endpoint);
            config.get("weight").asDouble().ifPresent(this::weight);
            // the next sections are obtained at time of build, as they require the known observe providers
            this.config = config;

            return this;
        }

        /**
         * Change the weight of this feature. This may change the order of registration of this feature.
         * By default observability weight is {@value #WEIGHT} so it is registered after routing.
         *
         * @param weight weight to use
         * @return updated builder
         */
        private Builder weight(double weight) {
            this.weight = weight;
            return this;
        }

        /**
         * Cors support inherited by each observe provider, unless explicitly configured.
         *
         * @param cors cors support to use
         * @return updated builder
         */
        public Builder corsSupport(CorsSupport cors) {
            this.corsSupport = cors;
            return this;
        }

        /**
         * Whether the observe support is enabled.
         *
         * @param enabled set to {@code false} to disable observe feature
         * @return updated builder
         */
        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        /**
         * Root endpoint to use for observe providers. By default all observe endpoint are under this root endpoint.
         * <p>
         * Example:
         * <br>
         * If root endpoint is {@code /observe} (the default), and default health endpoint is {@code health} (relative),
         * health endpoint would be {@code /observe/health}.
         *
         * @param endpoint endpoint to use
         * @return updated builder
         */
        public Builder endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        private String providerEndpoint(String endpoint) {
            if (endpoint.startsWith("/")) {
                return endpoint;
            }
            return this.endpoint + "/" + endpoint;
        }
    }

    private record ProviderSetup(String endpoint, Config config, CorsSupport cors, ObserveProvider provider) {
    }
}
