/*
 * Copyright (c) 2021, 2022 Oracle and/or its affiliates.
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

import java.util.Objects;
import java.util.logging.Logger;

import io.helidon.config.Config;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;
import io.helidon.webserver.Routing;
import io.helidon.webserver.Service;
import io.helidon.webserver.cors.CorsEnabledServiceHelper;
import io.helidon.webserver.cors.CrossOriginConfig;

/**
 * Common base implementation for {@linkplain Service service} support classes which involve REST endpoints.
 * <p>
 *     This base class takes care of some tasks common to many services, using config and other settings in the builder:
 *     <ul>
 *         <li>Setting up the endpoint path (web context) for the service, using settings in the builder and config.</li>
 *         <li>Providing automatic CORS support (and the ability to control it via config).</li>
 *     </ul>
 *
 * <p>
 *     Concrete implementations must implement {@link #postConfigureEndpoint(Routing.Rules, Routing.Rules)} to do any
 *     service-specific routing.
 *     See also the {@link Builder} information for possible additional overrides.
 * </p>
 *
 */
public abstract class HelidonRestServiceSupport implements RestServiceSupport {

    private final String context;
    private final CorsEnabledServiceHelper corsEnabledServiceHelper;
    private final Logger logger;
    private int webServerCount;

    /**
     * Shared initialization for new service support instances.
     *
     * @param logger the {@code Logger} for the concrete service support instance
     * @param builder builder for the service support instance.
     * @param serviceName name of the service
     */
    protected HelidonRestServiceSupport(Logger logger, Builder<?, ?> builder, String serviceName) {
        this(logger, builder.restServiceSettingsBuilder.build(), serviceName);
    }

    /**
     * Creates a new service support instance with the specified logger, REST settings, and service name.
     *
     * @param logger subclass-specific logger to use
     * @param restServiceSettings REST service settings to use
     * @param serviceName service name for the REST service
     */
    protected HelidonRestServiceSupport(Logger logger, RestServiceSettings restServiceSettings, String serviceName) {
        this.logger = logger;
        corsEnabledServiceHelper = CorsEnabledServiceHelper.create(serviceName, restServiceSettings.crossOriginConfig());
        context = (restServiceSettings.webContext().startsWith("/") ? "" : "/") + restServiceSettings.webContext();
    }

    /**
     * Configures service endpoint on the provided routing rules. This method
     * just adds the endpoint path (as defaulted or configured).
     * This method is exclusive to
     * {@link #update(io.helidon.webserver.Routing.Rules)} (e.g. you should not
     * use both, as otherwise you would register the endpoint twice)
     *
     * @param defaultRules default routing rules (also accepts {@link io.helidon.webserver.Routing.Builder}
     * @param serviceEndpointRoutingRules actual rules (if different from default) for the service endpoint
     */
    @Override
    public final void configureEndpoint(Routing.Rules defaultRules, Routing.Rules serviceEndpointRoutingRules) {
        defaultRules.onNewWebServer(webserver -> {
            webServerStarted();
            webserver.whenShutdown()
                    .thenRun(this::webServerStopped);
        });
        // CORS first
        defaultRules.any(context, corsEnabledServiceHelper.processor());
        if (defaultRules != serviceEndpointRoutingRules) {
            serviceEndpointRoutingRules.any(context, corsEnabledServiceHelper.processor());
        }
        postConfigureEndpoint(defaultRules, serviceEndpointRoutingRules);
    }

    /**
     * Concrete implementations override this method to perform any service-specific routing set-up.
     *
     * @param defaultRules default {@code Routing.Rules} to be updated
     * @param serviceEndpointRoutingRules actual rules (if different from the default ones) to be updated for the service endpoint
     */
    protected abstract void postConfigureEndpoint(Routing.Rules defaultRules, Routing.Rules serviceEndpointRoutingRules);

    private void webServerStarted() {
        webServerCount++;
    }

    private void webServerStopped() {
        if (--webServerCount == 0) {
            onShutdown();
        }
    }

    /**
     * Logic to run when the service is shut down.
     */
    protected void onShutdown() {
    }

    /**
     *
     * @return web context
     */
    protected String context() {
        return context;
    }

    /**
     *
     * @return logger in use by the service
     */
    protected Logger logger() {
        return logger;
    }

    /**
     * Abstract implementation of a {@code Builder} for the service.
     * <p>
     *     Concrete implementations may override any of the {@code Builder} methods, particularly
     *     {@link Builder#config(Config)} (to load service-specific values from config into the service-specific {@code Builder}).
     *     Such overrides should invoke {@code super.xxx(...)} to take advantage of the common behavior implemented here.
     * </p>
     *
     * @param <T> type of the concrete service
     * @param <B> type of the concrete builder for the service
     */
    @Configured
    public abstract static class Builder<B extends Builder<B, T>, T extends HelidonRestServiceSupport>
            implements io.helidon.common.Builder<B, T> {

        private Config config = Config.empty();
        private RestServiceSettings.Builder restServiceSettingsBuilder = RestServiceSettings.builder();

        /**
         * Creates a new builder using the provided class and default web context.
         *
         * @param defaultContext default web context for the service
         */
        protected Builder(String defaultContext) {
            restServiceSettingsBuilder.webContext(defaultContext);
        }

        /**
         * Sets the configuration to be used by this builder.
         * <p>
         *     Concrete builder implementations may override this method but should invoke {@code super.config(config)} to
         *     benefit from the common routing set-up.
         * </p>
         *
         * @param config the Helidon config instance
         * @return updated builder instance
         */
        public B config(Config config) {
            this.config = config;

            restServiceSettingsBuilder.config(config);

            config.get(CorsEnabledServiceHelper.CORS_CONFIG_KEY)
                    .as(CrossOriginConfig::create)
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
         */
        public B crossOriginConfig(CrossOriginConfig crossOriginConfig) {
            Objects.requireNonNull(crossOriginConfig, "CrossOriginConfig must be non-null");
            restServiceSettingsBuilder.crossOriginConfig(crossOriginConfig);
            return identity();
        }

        /**
         * Set the CORS config from the specified {@code CrossOriginConfig} object.
         *
         * @param crossOriginConfigBuilder {@code CrossOriginConfig.Builder} containing CORS set-up
         * @return updated builder instance
         */
        public B crossOriginConfig(CrossOriginConfig.Builder crossOriginConfigBuilder) {
            Objects.requireNonNull(crossOriginConfigBuilder, "CrossOriginConfig.Builder must be non-null");
            restServiceSettingsBuilder.crossOriginConfig(crossOriginConfigBuilder);
            return identity();
        }

        /**
         * Sets the builder for the REST service settings. This will replace any values
         * configured on this instance and will ONLY use values defined in the provided supplier.
         *
         * @param restServiceSettingsBuilder builder for REST service settings
         * @return updated builder
         */
        @ConfiguredOption(mergeWithParent = true, type = RestServiceSettings.class)
        public B restServiceSettings(RestServiceSettings.Builder restServiceSettingsBuilder) {
            this.restServiceSettingsBuilder = restServiceSettingsBuilder;
            return identity();
        }

        /**
         * Returns the web-context {@code Config} node from the provided config.
         *
         * @param config config to query for web-context
         * @return {@code Config} node for web-context
         */
        protected Config webContextConfig(Config config) {
            return config.get("web-context");
        }
    }
}
