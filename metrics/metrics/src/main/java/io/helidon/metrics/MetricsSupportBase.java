/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
package io.helidon.metrics;

import java.util.Objects;

import io.helidon.config.Config;
import io.helidon.webserver.Routing;
import io.helidon.webserver.Service;
import io.helidon.webserver.cors.CorsEnabledServiceHelper;
import io.helidon.webserver.cors.CrossOriginConfig;

import static io.helidon.webserver.cors.CorsEnabledServiceHelper.CORS_CONFIG_KEY;

/**
 * Common base implementation for metrics support classes.
 *
 * @param <T> the concrete metrics support class
 * @param <B> the concrete {@code Builder} for {@code T}
 */
public abstract class MetricsSupportBase<T extends MetricsSupportBase<T, B>, B extends MetricsSupportBase.Builder<T, B>>
        implements Service {

    private final String context;
    private final CorsEnabledServiceHelper corsEnabledServiceHelper;

    MetricsSupportBase(Builder<T, B> builder, String serviceName) {
        this.context = builder.context;
        corsEnabledServiceHelper = CorsEnabledServiceHelper.create(serviceName, builder.crossOriginConfig);
    }

    /**
     * Configure metrics endpoint on the provided routing rules. This method
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

    abstract void postConfigureEndpoint(Routing.Rules rules);

    String context() {
        return context;
    }

    abstract static class Builder<T extends MetricsSupportBase<T, B>, B extends Builder<T, B>> implements io.helidon.common.Builder<T> {

        private String context;
        private Config config = Config.empty();
        private CrossOriginConfig crossOriginConfig = null;

        Builder(String defaultContext) {
            this.context = defaultContext;
        }

        public B config(Config config) {
            this.config = config;

            getWebContextConfig(config)
                    .asString()
                    .ifPresent(this::webContext);

            config.get(CORS_CONFIG_KEY)
                    .as(CrossOriginConfig::create)
                    .ifPresent(this::crossOriginConfig);

            return me();
        }

        Config config() {
            return config;
        }

        /**
         * Set a new root context for REST API of metrics.
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
        public Builder<T, B> crossOriginConfig(CrossOriginConfig crossOriginConfig) {
            Objects.requireNonNull(crossOriginConfig, "CrossOriginConfig must be non-null");
            this.crossOriginConfig = crossOriginConfig;
            return me();
        }

        protected abstract B me();

        protected Config getWebContextConfig(Config config) {
            return config.get("web-context");
        }
    }
}
