/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.webserver;

import java.util.LinkedList;
import java.util.List;

import io.helidon.config.Config;
import io.helidon.tracing.config.TracingConfig;

/**
 * Traced system configuration for web server for a specific path.
 */
public interface PathTracingConfig {
    /**
     * Create a new traced path configuration from {@link io.helidon.config.Config}.
     * @param config config of a path
     * @return traced path configuration
     */
    static PathTracingConfig create(Config config) {
        return builder().config(config).build();
    }

    /**
     * Create a new builder to configure traced path configuration.
     *
     * @return a new builder instance
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Path this configuration should configure.
     * @return path on the web server
     * @see io.helidon.webserver.Routing.Builder#any(String, Handler...)
     */
    String path();

    /**
     * Method(s) this configuration should be valid for. This can be used to restrict the configuration
     * only to specific HTTP methods (such as {@code GET} or {@code POST}).
     *
     * @return list of methods, if empty, this configuration is valid for any method
     */
    List<String> methods();

    /**
     * Associated configuration of tracing valid for the configured path and (possibly) methods.
     *
     * @return traced system configuration
     */
    TracingConfig tracedConfig();

    /**
     * Fluent API builder for {@link PathTracingConfig}.
     */
    final class Builder implements io.helidon.common.Builder<PathTracingConfig> {
        private final List<String> methods = new LinkedList<>();
        private String path;
        private TracingConfig tracedConfig;

        private Builder() {
        }

        @Override
        public PathTracingConfig build() {
            // immutable
            final String finalPath = path;
            final List<String> finalMethods = new LinkedList<>(methods);
            final TracingConfig finalTracingConfig = tracedConfig;

            return new PathTracingConfig() {
                @Override
                public String path() {
                    return finalPath;
                }

                @Override
                public List<String> methods() {
                    return finalMethods;
                }

                @Override
                public TracingConfig tracedConfig() {
                    return finalTracingConfig;
                }

                @Override
                public String toString() {
                    return path + "(" + finalMethods + "): " + finalTracingConfig;
                }
            };
        }

        /**
         * Update this builder from provided {@link io.helidon.config.Config}.
         *
         * @param config config to update this builder from
         * @return updated builder instance
         */
        public Builder config(Config config) {
            path(config.get("path").asString().get());
            List<String> methods = config.get("methods").asList(String.class).orElse(null);
            if (null != methods) {
                methods(methods);
            }
            tracingConfig(TracingConfig.create(config));

            return this;
        }

        /**
         * Path to register the traced configuration on.
         *
         * @param path path as understood by {@link io.helidon.webserver.Routing.Builder} of web server
         * @return updated builder instance
         */
        public Builder path(String path) {
            this.path = path;
            return this;
        }

        /**
         * HTTP methods to restrict registration of this configuration on web server.
         * @param methods list of methods to use, empty means all methods
         * @return updated builder instance
         */
        public Builder methods(List<String> methods) {
            this.methods.clear();
            this.methods.addAll(methods);
            return this;
        }

        /**
         * Add a new HTTP method to restrict this configuration for.
         *
         * @param method method to add to the list of supported methods
         * @return updated builder instance
         */
        public Builder addMethod(String method) {
            this.methods.add(method);
            return this;
        }

        /**
         * Configuration of a traced system to use on this path and possibly method(s).
         *
         * @param tracedConfig configuration of components, spans and span logs
         * @return updated builder instance
         */
        public Builder tracingConfig(TracingConfig tracedConfig) {
            this.tracedConfig = tracedConfig;
            return this;
        }
    }
}
