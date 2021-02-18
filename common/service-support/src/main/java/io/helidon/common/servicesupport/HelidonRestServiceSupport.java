/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
 *
 */
package io.helidon.common.servicesupport;

import java.util.Objects;

import io.helidon.config.Config;
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
 *     Concrete implementations must implement {@link #postConfigureEndpoint(Routing.Rules)} to do any service-specific routing.
 *     See also the {@link Builder} information for possible additional overrides.
 * </p>
 *
 * @param <T> the concrete service support class
 * @param <B> the concrete {@code Builder} class for {@code T}
 */
public abstract class HelidonRestServiceSupport<T extends HelidonRestServiceSupport<T, B>, B extends HelidonRestServiceSupport.Builder<T, B>>
        implements Service {

    private final String context;
    private final CorsEnabledServiceHelper corsEnabledServiceHelper;

    protected HelidonRestServiceSupport(Builder<T, B> builder, String serviceName) {
        this.context = builder.context;
        corsEnabledServiceHelper = CorsEnabledServiceHelper.create(serviceName, builder.crossOriginConfig);
    }

    /**
     * Configures service endpoint on the provided routing rules. This method
     * just adds the endpoint path (as defaulted or configured).
     * This method is exclusive to
     * {@link #update(io.helidon.webserver.Routing.Rules)} (e.g. you should not
     * use both, as otherwise you would register the endpoint twice)
     *
     * @param rules routing rules (also accepts
     * {@link io.helidon.webserver.Routing.Builder}
     */
    public final void configureEndpoint(Routing.Rules rules) {
        // CORS first
        rules.any(context, corsEnabledServiceHelper.processor());
        postConfigureEndpoint(rules);
    }

    /**
     * Concrete implementations override this method to perform any service-specific routing set-up.
     *
     * @param rules {@code Routing.Rules} to be updated
     */
    protected abstract void postConfigureEndpoint(Routing.Rules rules);

    protected String context() {
        return context;
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
    public abstract static class Builder<T extends HelidonRestServiceSupport<T, B>, B extends Builder<T, B>>
            implements io.helidon.common.Builder<T> {

        private final Class<B> builderClass;
        private String context;
        private Config config = Config.empty();
        private CrossOriginConfig crossOriginConfig = null;

        protected Builder(Class<B> builderClass, String defaultContext) {
            this.builderClass = builderClass;
            this.context = defaultContext;
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

            webContextConfig(config)
                    .asString()
                    .ifPresent(this::webContext);

            config.get(CorsEnabledServiceHelper.CORS_CONFIG_KEY)
                    .as(CrossOriginConfig::create)
                    .ifPresent(this::crossOriginConfig);

            return me();
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
            if (path.startsWith("/")) {
                this.context = path;
            } else {
                this.context = "/" + path;
            }
            return me();
        }

        /**
         * Set the CORS config from the specified {@code CrossOriginConfig} object.
         *
         * @param crossOriginConfig {@code CrossOriginConfig} containing CORS set-up
         * @return updated builder instance
         */
        public B crossOriginConfig(CrossOriginConfig crossOriginConfig) {
            Objects.requireNonNull(crossOriginConfig, "CrossOriginConfig must be non-null");
            this.crossOriginConfig = crossOriginConfig;
            return me();
        }

        /**
         * Returns correctly-typed {@code this}.
         *
         * @return typed "this"
         */
        private B me() {
            return builderClass.cast(this);
        }

        protected Config webContextConfig(Config config) {
            return config.get("web-context");
        }
    }
}
