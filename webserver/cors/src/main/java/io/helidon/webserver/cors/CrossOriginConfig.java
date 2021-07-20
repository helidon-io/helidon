/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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

import io.helidon.config.Config;

import static io.helidon.webserver.cors.Aggregator.PATHLESS_KEY;

/**
 * Represents information about cross origin request sharing.
 *
 * Applications can obtain a new instance in three ways:
 * <ul>
 *     <li>Use a {@code Builder} explicitly.
 *     <p>
 *     Obtain a suitable builder by:
 *     </p>
 *     <ul>
 *         <li>getting a new builder using the static {@link #builder()} method,</li>
 *         <li>initializing a builder from an existing {@code CrossOriginConfig} instance using the static
 *         {@link #builder(CrossOriginConfig)} method, or</li>
 *         <li>initializing a builder from a {@code Config} node, invoking {@link Config#as} using
 *         {@code corsConfig.as(CrossOriginConfig::builder).get()}</li>
 *     </ul>
 *     and then invoke methods on the builder as needed. Finally invoke the builder's {@code build} method to create the
 *     instance.
 *     <li>Invoke the static {@link #create(Config)} method, passing a config node containing the cross-origin information to be
 *     converted. This is a convenience method equivalent to creating a builder using the config node and then invoking {@code
 *     build()}.</li>
 *     <li>Invoke the static {@link #create()} method which returns a {@code CrossOriginConfig} instance which implements
 *     the default CORS behavior.</li>
 * </ul>
 *
 * @see MappedCrossOriginConfig
 *
 */
public class CrossOriginConfig {

    static final CrossOriginConfig CATCH_ALL = CrossOriginConfig.builder()
            .allowOrigins("*")
            .build();

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

    private final String pathPattern;
    private final boolean enabled;
    private final String[] allowOrigins;
    private final String[] allowHeaders;
    private final String[] exposeHeaders;
    private final String[] allowMethods;
    private final boolean allowCredentials;
    private final long maxAgeSeconds;

    private CrossOriginConfig(Builder builder) {
        this.pathPattern = builder.pathPattern;
        this.enabled = builder.enabled;
        this.allowOrigins = builder.origins;
        this.allowHeaders = builder.allowHeaders;
        this.exposeHeaders = builder.exposeHeaders;
        this.allowMethods = builder.allowMethods;
        this.allowCredentials = builder.allowCredentials;
        this.maxAgeSeconds = builder.maxAgeSeconds;
    }

    /**
     * @return a new builder for basic cross origin config
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a new {@code CrossOriginConfig.Builder} using the provided config node.
     * <p>
     *     Although this method is equivalent to {@code builder().config(config)} it conveniently combines those two steps for
     *     use as a method reference.
     * </p>
     *
     * @param config node containing cross-origin information
     * @return new {@code CrossOriginConfig.Builder} instance based on the configuration
     */
    public static Builder builder(Config config) {
        return Loader.Basic.applyConfig(builder(), config);
    }

    /**
     * Initializes a new {@code CrossOriginConfig.Builder} from the values in an existing {@code CrossOriginConfig} object.
     *
     * @param original the existing cross-origin config object
     * @return new Builder initialized from the existing object's settings
     */
    public static Builder builder(CrossOriginConfig original) {
        return new Builder()
                .pathPattern(original.pathPattern)
                .enabled(original.enabled)
                .allowCredentials(original.allowCredentials)
                .allowHeaders(original.allowHeaders)
                .allowMethods(original.allowMethods)
                .allowOrigins(original.allowOrigins)
                .exposeHeaders(original.exposeHeaders)
                .maxAgeSeconds(original.maxAgeSeconds);
    }

    /**
     * Creates a new {@code CrossOriginConfig} instance which represents the default CORS behavior.
     *
     * @return new default {@code CrossOriginConfig} instance
     */
    public static CrossOriginConfig create() {
        return builder().build();
    }

    /**
     * Creates a new {@code CrossOriginConfig} instance based on the provided configuration node.
     * @param corsConfig node containing CORS information
     * @return new {@code CrossOriginConfig} based on the configuration
     */
    public static CrossOriginConfig create(Config corsConfig) {
        return builder(corsConfig).build();
    }

    /**
     * @return the configured path expression; defaults to a "match-everything" pattern
     */
    public String pathPattern() {
        return pathPattern;
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
    public long maxAgeSeconds() {
        return maxAgeSeconds;
    }

    /**
     * Reports whether the specified HTTP method name matches this {@code CrossOriginConfig}.
     *
     * @param method HTTP method name to check
     * @return true if this {@code CrossOriginConfig} matches the specified method; false otherwise
     */
    public boolean matches(String method) {
        for (String allowMethod : allowMethods) {
            if (allowMethod.equalsIgnoreCase(method) || allowMethod.equals("*")) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return String.format("CrossOriginConfig{pathPattern=%s, enabled=%b, origins=%s, allowHeaders=%s, exposeHeaders=%s, "
                + "allowMethods=%s, allowCredentials=%b, maxAgeSeconds=%d", pathPattern, enabled,
                Arrays.toString(allowOrigins),
                Arrays.toString(allowHeaders), Arrays.toString(exposeHeaders), Arrays.toString(allowMethods),
                allowCredentials, maxAgeSeconds);
    }

    private static String[] copyOf(String[] strings) {
        return strings != null ? Arrays.copyOf(strings, strings.length) : new String[0];
    }

    /**
     * Builder for {@link CrossOriginConfig}.
     */
    public static class Builder implements CorsSetter<Builder>, io.helidon.common.Builder<CrossOriginConfig> {

        static final String[] ALLOW_ALL = {"*"};

        private String pathPattern = PATHLESS_KEY; // not typically used except when inside a MappedCrossOriginConfig
        private boolean enabled = true;
        private String[] origins = ALLOW_ALL;
        private String[] allowHeaders = ALLOW_ALL;
        private String[] exposeHeaders;
        private String[] allowMethods = ALLOW_ALL;
        private boolean allowCredentials;
        private long maxAgeSeconds = DEFAULT_AGE;

        private Builder() {
        }

        /**
         * Updates the path prefix for this cross-origin config.
         *
         * @param pathPattern new path prefix
         * @return updated builder
         */
        public Builder pathPattern(String pathPattern) {
            this.pathPattern = pathPattern;
            return this;
        }

        String pathPattern() {
            return pathPattern;
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
        public Builder maxAgeSeconds(long maxAgeSeconds) {
            this.maxAgeSeconds = maxAgeSeconds;
            return this;
        }

        /**
         * Augment or override existing settings using the provided {@code Config} node.
         *
         * @param corsConfig config node containing CORS information
         * @return updated builder
         */
        public Builder config(Config corsConfig) {
            Loader.Basic.applyConfig(this, corsConfig);
            return this;
        }

        @Override
        public CrossOriginConfig build() {
            return new CrossOriginConfig(this);
        }

        @Override
        public String toString() {
            return String.format("Builder{pathPattern=%s, enabled=%b, origins=%s, allowHeaders=%s, exposeHeaders=%s, "
                    + "allowMethods=%s, allowCredentials=%b, maxAgeSeconds=%d", pathPattern, enabled, Arrays.toString(origins),
                    Arrays.toString(allowHeaders), Arrays.toString(exposeHeaders), Arrays.toString(allowMethods),
                    allowCredentials, maxAgeSeconds);
        }
    }
}
