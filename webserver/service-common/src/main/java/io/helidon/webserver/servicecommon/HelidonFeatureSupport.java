/*
 * Copyright (c) 2022, 2026 Oracle and/or its affiliates.
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
package io.helidon.webserver.servicecommon;

import java.util.Objects;

import io.helidon.common.config.Config;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;
import io.helidon.cors.CrossOriginConfig;
import io.helidon.webserver.cors.CorsEnabledServiceHelper;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.HttpService;

/**
 * Common base implementation for {@linkplain HttpService service} support classes which involve REST endpoints.
 * <p>
 * This base class takes care of some tasks common to many services, using config and other settings in the builder:
 *     <ul>
 *         <li>Setting up the endpoint path (web context) for the service, using settings in the builder and config.</li>
 *         <li>Providing automatic CORS support (and the ability to control it via config).</li>
 *     </ul>
 *
 * <p>
 *     Concrete implementations must implement
 *     {@link #postSetup(HttpRouting.Builder, HttpRouting.Builder)} to do any service-specific routing.
 *     See also the {@link Builder} information for possible additional overrides.
 * </p>
 *
 * @deprecated feature specific CORS configuration is deprecated and will be removed; use either config based CORS setup
 *  (configuration key {@code cors}, or programmatic setup using the {@code io.helidon.webserver.cors.CorsFeature}
 *  server feature; reason for existence of this class was CORS configuration - as this is now not needed,
 *  this class will most likely be removed in a future version of Helidon; implement an
 *  {@link io.helidon.webserver.http.HttpFeature} directly instead
 */
@Deprecated(forRemoval = true, since = "4.4.0")
@SuppressWarnings("removal")
public abstract class HelidonFeatureSupport implements FeatureSupport {

    private final CorsEnabledServiceHelper corsEnabledServiceHelper;
    private final System.Logger logger;
    private final String configuredContext;
    private final boolean enabled;
    private String context;

    /**
     * Shared initialization for new service support instances.
     *
     * @param logger      the {@code Logger} for the concrete service support instance
     * @param builder     builder for the service support instance.
     * @param serviceName name of the service
     */
    protected HelidonFeatureSupport(System.Logger logger, Builder<?, ?> builder, String serviceName) {
        this(logger, builder.restServiceSettingsBuilder.build(), serviceName);
    }

    protected HelidonFeatureSupport(System.Logger logger, RestServiceSettings restServiceSettings, String serviceName) {
        this.logger = logger;
        this.corsEnabledServiceHelper = CorsEnabledServiceHelper.create(serviceName, restServiceSettings.crossOriginConfig());
        this.configuredContext = restServiceSettings.webContext();
        this.context = (restServiceSettings.webContext().startsWith("/") ? "" : "/") + restServiceSettings.webContext();
        this.enabled = restServiceSettings.enabled();
    }

    /**
     * Configures service endpoint on the provided routing rules. This method
     * just adds the endpoint path (as defaulted or configured).
     * This method is exclusive to
     * {@link #setup(io.helidon.webserver.http.HttpRouting.Builder)} (e.g. you should not
     * use both, as otherwise you would register the endpoint twice)
     *
     * @param defaultRouting default routing rules (also accepts
     *                       {@link io.helidon.webserver.http.HttpRouting.Builder}
     * @param featureRouting actual rules (if different from default) for the service endpoint
     */
    @Override
    public final void setup(HttpRouting.Builder defaultRouting, HttpRouting.Builder featureRouting) {
        // CORS first
        defaultRouting.any(corsEnabledServiceHelper.processor());
        if (defaultRouting != featureRouting) {
            featureRouting.any(corsEnabledServiceHelper.processor());
        }
        service().ifPresent(it -> featureRouting.register(context(), it));
        postSetup(defaultRouting, featureRouting);
    }

    @Override
    public String context() {
        return context;
    }

    @Override
    public String configuredContext() {
        return configuredContext;
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    protected void context(String context) {
        this.context = context;
    }

    /**
     * This can be used to register services, filters etc. on either the default rules (usually the main routing of the web
     * server)
     * and the feature routing (may be the same instance).
     * If {@link #service()} provides an instance, that instance will be correctly registered with the context root on
     * feature routing.
     *
     * @param defaultRouting default {@code HttpRules} to be updated
     * @param featureRouting actual rules (if different from the default ones) to be updated for the service endpoint
     */
    protected void postSetup(HttpRouting.Builder defaultRouting, HttpRouting.Builder featureRouting) {
    }

    protected System.Logger logger() {
        return logger;
    }

    /**
     * Abstract implementation of a {@code Builder} for the service.
     * <p>
     * Concrete implementations may override any of the {@code Builder} methods, particularly
     * {@link Builder#config(Config)} (to load service-specific values from config into the service-specific {@code Builder}).
     * Such overrides should invoke {@code super.xxx(...)} to take advantage of the common behavior implemented here.
     * </p>
     *
     * @param <T> type of the concrete service
     * @param <B> type of the concrete builder for the service
     */
    @Configured
    public abstract static class Builder<B extends Builder<B, T>, T extends HelidonFeatureSupport>
            implements io.helidon.common.Builder<B, T> {

        private Config config = Config.empty();
        private RestServiceSettings.Builder restServiceSettingsBuilder = RestServiceSettings.builder();

        protected Builder(String defaultContext) {
            restServiceSettingsBuilder.webContext(defaultContext);
        }

        /**
         * Sets the configuration to be used by this builder.
         * <p>
         * Concrete builder implementations may override this method but should invoke {@code super.config(config)} to
         * benefit from the common routing set-up.
         * </p>
         *
         * @param config the Helidon config instance
         * @return updated builder instance
         */
        public B config(Config config) {
            this.config = config;

            webContextConfig(config)
                    .asString()
                    .ifPresent(this::webContext);

            config.get(RestServiceSettings.Builder.ROUTING_NAME_CONFIG_KEY)
                    .asString()
                    .ifPresent(restServiceSettingsBuilder::routing);

            config.get(CorsEnabledServiceHelper.CORS_CONFIG_KEY)
                    .map(CrossOriginConfig::create)
                    .ifPresent(this::crossOriginConfig);

            return identity();
        }

        /**
         * Returns the config (if any) assigned for this builder.
         *
         * @return the Config
         */
        public Config config() {
            return config;
        }

        /**
         * Set the root context for the REST API of the service.
         *
         * @param path context to use
         * @return updated builder instance
         */
        @ConfiguredOption
        public B webContext(String path) {
            String context;
            if (path.startsWith("/")) {
                context = path;
            } else {
                context = "/" + path;
            }
            restServiceSettingsBuilder.webContext(context);
            return identity();
        }

        /**
         * Set the CORS config from the specified {@code CrossOriginConfig} object.
         *
         * @param crossOriginConfig {@code CrossOriginConfig} containing CORS set-up
         * @return updated builder instance
         * @deprecated feature specific CORS configuration is deprecated and will be removed; use either config based CORS setup
         *  (configuration key {@code cors}, or programmatic setup using the {@code io.helidon.webserver.cors.CorsFeature}
         *  server feature
         */
        @SuppressWarnings("removal")
        @Deprecated(forRemoval = true, since = "4.4.0")
        @ConfiguredOption
        public B crossOriginConfig(CrossOriginConfig crossOriginConfig) {
            Objects.requireNonNull(crossOriginConfig, "CrossOriginConfig must be non-null");
            restServiceSettingsBuilder.crossOriginConfig(crossOriginConfig);
            return identity();
        }

        /**
         * Sets the builder for the REST service settings.
         *
         * @param restServiceSettingsBuilder builder for REST service settings
         * @return updated builder
         */
        public B restServiceSettings(RestServiceSettings.Builder restServiceSettingsBuilder) {
            this.restServiceSettingsBuilder = restServiceSettingsBuilder;
            return identity();
        }

        protected Config webContextConfig(Config config) {
            return config.get("web-context");
        }
    }
}
