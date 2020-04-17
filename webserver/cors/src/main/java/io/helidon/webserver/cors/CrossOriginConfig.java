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
 */

package io.helidon.webserver.cors;

import java.util.Arrays;
import java.util.function.Function;

import io.helidon.config.Config;

import static io.helidon.webserver.cors.Aggregator.PATHLESS_KEY;

/**
 * Represents information about cross origin request sharing.
 *
 * Applications can create instance in two ways:
 * <ul>
 *     <li>using a {@code Builder} explicitly
 *     <p>
 *     Obtain a suitable builder by:
 *     </p>
 *     <ul>
 *         <li>explicitly getting a builder using {@link #builder()},</li>
 *         <li>invoking the static {@link Builder#from} method and
 *         passing an existing instance of {@code CrossOriginConfig}; the resulting {@code Builder} is
 *         intialized using the configuration node provided, or</li>
 *         <li>obtaining a {@link Config} instance and invoking {@code Config.as}, passing {@code Builder#from}</li>
 *     </ul>
 *     and then invoke methods on the builder, finally invoking the builder's {@code build} method to create the instance.
 *     <li>invoking the static {@link #from} method, passing a config node containing the cross-origin information to be
 *     converted.
 *     </li>
 * </ul>
 *
 * @see MappedCrossOriginConfig
 *
 */
public class CrossOriginConfig {

    /**
     * Key for the node within the CORS config that contains the list of path information.
     */
    public static final String CORS_PATHS_CONFIG_KEY = "paths";

    /**
     * Header Access-Control-Allow-Headers.
     */
    public static final String ACCESS_CONTROL_ALLOW_HEADERS = "Access-Control-Allow-Headers";
    /**
     * Header Access-Control-Allow-Methods.
     */
    public static final String ACCESS_CONTROL_ALLOW_METHODS = "Access-Control-Allow-Methods";
    /**
     * Header Access-Control-Allow-Credentials.
     */
    public static final String ACCESS_CONTROL_ALLOW_CREDENTIALS = "Access-Control-Allow-Credentials";
    /**
     * Header Access-Control-Max-Age.
     */
    public static final String ACCESS_CONTROL_MAX_AGE = "Access-Control-Max-Age";
    /**
     * Header Access-Control-Expose-Headers.
     */
    public static final String ACCESS_CONTROL_EXPOSE_HEADERS = "Access-Control-Expose-Headers";
    /**
     * Header Access-Control-Allow-Origin.
     */
    public static final String ACCESS_CONTROL_ALLOW_ORIGIN = "Access-Control-Allow-Origin";
    /**
     * Header Access-Control-Request-Headers.
     */
    public static final String ACCESS_CONTROL_REQUEST_HEADERS = "Access-Control-Request-Headers";
    /**
     * Header Access-Control-Request-Method.
     */
    public static final String ACCESS_CONTROL_REQUEST_METHOD = "Access-Control-Request-Method";

    /**
     * Default cache expiration in seconds.
     */
    public static final long DEFAULT_AGE = 3600;

    private final String pathPrefix;
    private final boolean enabled;
    private final String[] allowOrigins;
    private final String[] allowHeaders;
    private final String[] exposeHeaders;
    private final String[] allowMethods;
    private final boolean allowCredentials;
    private final long maxAge;

    private CrossOriginConfig(Builder builder) {
        this.pathPrefix = builder.pathPrefix;
        this.enabled = builder.enabled;
        this.allowOrigins = builder.origins;
        this.allowHeaders = builder.allowHeaders;
        this.exposeHeaders = builder.exposeHeaders;
        this.allowMethods = builder.allowMethods;
        this.allowCredentials = builder.allowCredentials;
        this.maxAge = builder.maxAge;
    }

    /**
     * @return a new builder for basic cross origin config
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a new {@code CrossOriginConfig} instance using the provided config node.
     *
     * @param config node containing cross-origin information
     * @return new {@code Basic} instance based on the configuration
     */
    public static CrossOriginConfig from(Config config) {
        return Builder.from(config).build();
    }

    /**
     * @return the configured path prefix; defaults to a "match-everything" pattern
     */
    public String pathPrefix() {
        return pathPrefix;
    }

    /**
     * @return whether this cross-origin config is enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * @return the allowed origins
     */
    public String[] allowOrigins() {
        return copyOf(allowOrigins);
    }

    /**
     * @return the allowed headers
     */
    public String[] allowHeaders() {
        return copyOf(allowHeaders);
    }

    /**
     * @return headers OK to expose in responses
     */
    public String[] exposeHeaders() {
        return copyOf(exposeHeaders);
    }

    /**
     * @return allowed methods
     */
    public String[] allowMethods() {
        return copyOf(allowMethods);
    }

    /**
     * @return allowed credentials
     */
    public boolean allowCredentials() {
        return allowCredentials;
    }

    /**
     * @return maximum age
     */
    public long maxAge() {
        return maxAge;
    }

    private static String[] copyOf(String[] strings) {
        return strings != null ? Arrays.copyOf(strings, strings.length) : new String[0];
    }

    /**
     * Builder for {@link CrossOriginConfig}.
     */
    public static class Builder implements CorsSetter<Builder>, io.helidon.common.Builder<CrossOriginConfig>,
            Function<Config, Builder> {

        static final String[] ALLOW_ALL = {"*"};

        private String pathPrefix = PATHLESS_KEY; // not typically used except when inside a MappedCrossOriginConfig
        private boolean enabled = true;
        private String[] origins = ALLOW_ALL;
        private String[] allowHeaders = ALLOW_ALL;
        private String[] exposeHeaders;
        private String[] allowMethods = ALLOW_ALL;
        private boolean allowCredentials;
        private long maxAge = DEFAULT_AGE;

        private Builder() {
        }

        /**
         * Creates a new builder based on the values in an existing {@code CrossOriginConfig} object.
         *
         * @param original the existing cross-origin config object
         * @return new Builder initialized from the existing object's settings
         */
        public static Builder from(CrossOriginConfig original) {
            return new Builder()
                    .pathPrefix(original.pathPrefix)
                    .enabled(original.enabled)
                    .allowCredentials(original.allowCredentials)
                    .allowHeaders(original.allowHeaders)
                    .allowMethods(original.allowMethods)
                    .allowOrigins(original.allowOrigins)
                    .exposeHeaders(original.exposeHeaders)
                    .maxAge(original.maxAge);
        }

        /**
         * Creates a new {@code Builder}instance from the specified configuration.
         *
         * @param config node containing cross-origin information
         * @return new {@code Builder} initialized from the config
         */
        public static Builder from(Config config) {
            return Loader.Basic.builder(config);
        }

        @Override
        public Builder apply(Config config) {
            return from(config);
        }

        /**
         * Updates the path prefix for this cross-origin config.
         *
         * @param pathPrefix new path prefix
         * @return updated builder
         */
        public Builder pathPrefix(String pathPrefix) {
            this.pathPrefix = pathPrefix;
            return this;
        }

        String pathPrefix() {
            return pathPrefix;
        }

        @Override
        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        @Override
        public Builder allowOrigins(String... origins) {
            this.origins = copyOf(origins);
            return this;
        }

        @Override
        public Builder allowHeaders(String... allowHeaders) {
            this.allowHeaders = copyOf(allowHeaders);
            return this;
        }

        @Override
        public Builder exposeHeaders(String... exposeHeaders) {
            this.exposeHeaders = copyOf(exposeHeaders);
            return this;
        }

        @Override
        public Builder allowMethods(String... allowMethods) {
            this.allowMethods = copyOf(allowMethods);
            return this;
        }

        @Override
        public Builder allowCredentials(boolean allowCredentials) {
            this.allowCredentials = allowCredentials;
            return this;
        }

        @Override
        public Builder maxAge(long maxAge) {
            this.maxAge = maxAge;
            return this;
        }

        @Override
        public CrossOriginConfig build() {
            return new CrossOriginConfig(this);
        }
    }
}
