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
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import io.helidon.config.Config;

import static io.helidon.webserver.cors.CrossOriginHelper.normalize;
import static io.helidon.webserver.cors.CrossOriginHelper.parseHeader;

/**
 * Represents information about cross origin request sharing.
 */
public class CrossOriginConfig /* implements CrossOrigin */ {

    /**
     * Default cache expiration in seconds.
     */
    public static final long DEFAULT_AGE = 3600;
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
     * Key for the node within the CORS config that contains the list of path information.
     */
    public static final String CORS_PATHS_CONFIG_KEY = "paths";

    private final String[] allowOrigins;
    private final String[] allowHeaders;
    private final String[] exposeHeaders;
    private final String[] allowMethods;
    private final boolean allowCredentials;
    private final long maxAge;

    private CrossOriginConfig(Builder builder) {
        this.allowOrigins = builder.origins;
        this.allowHeaders = builder.allowHeaders;
        this.exposeHeaders = builder.exposeHeaders;
        this.allowMethods = builder.allowMethods;
        this.allowCredentials = builder.allowCredentials;
        this.maxAge = builder.maxAge;
    }

    /**
     *
     * @return a new builder for cross origin config
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     *
     * @return the allowed origins
     */
    public String[] allowOrigins() {
        return copyOf(allowOrigins);
    }

    /**
     *
     * @return the allowed headers
     */
    public String[] allowHeaders() {
        return copyOf(allowHeaders);
    }

    /**
     *
     * @return headers OK to expose in responses
     */
    public String[] exposeHeaders() {
        return copyOf(exposeHeaders);
    }

    /**
     *
     * @return allowed methods
     */
    public String[] allowMethods() {
        return copyOf(allowMethods);
    }

    /**
     *
     * @return allowed credentials
     */
    public boolean allowCredentials() {
        return allowCredentials;
    }

    /**
     *
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
    public static class Builder implements Setter<Builder>, io.helidon.common.Builder<CrossOriginConfig> {

        private static final String[] ALLOW_ALL = {"*"};

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
                    .allowCredentials(original.allowCredentials)
                    .allowHeaders(original.allowHeaders)
                    .allowMethods(original.allowMethods)
                    .allowOrigins(original.allowOrigins)
                    .exposeHeaders(original.exposeHeaders)
                    .maxAge(original.maxAge);
        }

        /**
         * Sets the allowOrigins.
         *
         * @param origins the origin value(s)
         * @return updated builder
         */
        @Override
        public Builder allowOrigins(String... origins) {
            this.origins = copyOf(origins);
            return this;
        }

        /**
         * Sets the allow headers.
         *
         * @param allowHeaders the allow headers value(s)
         * @return updated builder
         */
        @Override
        public Builder allowHeaders(String... allowHeaders) {
            this.allowHeaders = copyOf(allowHeaders);
            return this;
        }

        /**
         * Sets the expose headers.
         *
         * @param exposeHeaders the expose headers value(s)
         * @return updated builder
         */
        @Override
        public Builder exposeHeaders(String... exposeHeaders) {
            this.exposeHeaders = copyOf(exposeHeaders);
            return this;
        }

        /**
         * Sets the allow methods.
         *
         * @param allowMethods the allow method value(s)
         * @return updated builder
         */
        @Override
        public Builder allowMethods(String... allowMethods) {
            this.allowMethods = copyOf(allowMethods);
            return this;
        }

        /**
         * Sets the allow credentials flag.
         *
         * @param allowCredentials the allow credentials flag
         * @return updated builder
         */
        public Builder allowCredentials(boolean allowCredentials) {
            this.allowCredentials = allowCredentials;
            return this;
        }

        /**
         * Sets the maximum age.
         *
         * @param maxAge the maximum age
         * @return updated builder
         */
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

    /**
     * Functional interface for converting a Helidon config instance to a {@code CrossOriginConfig} instance.
     */
    public static class CrossOriginConfigMapper implements Function<Config, Map<String, CrossOriginConfig>> {

        @Override
        public Map<String, CrossOriginConfig> apply(Config config) {
            Map<String, CrossOriginConfig> result = new HashMap<>();
            int i = 0;
            do {
                Config item = config.get(Integer.toString(i++));
                if (!item.exists()) {
                    break;
                }
                Builder builder = new Builder();
                String path = item.get("path-prefix").as(String.class).orElse(null);
                item.get("allow-origins").asList(String.class).ifPresent(
                        s -> builder.allowOrigins(parseHeader(s).toArray(new String[]{})));
                item.get("allow-methods").asList(String.class).ifPresent(
                        s -> builder.allowMethods(parseHeader(s).toArray(new String[]{})));
                item.get("allow-headers").asList(String.class).ifPresent(
                        s -> builder.allowHeaders(parseHeader(s).toArray(new String[]{})));
                item.get("expose-headers").asList(String.class).ifPresent(
                        s -> builder.exposeHeaders(parseHeader(s).toArray(new String[]{})));
                item.get("allow-credentials").as(Boolean.class).ifPresent(builder::allowCredentials);
                item.get("max-age").as(Long.class).ifPresent(builder::maxAge);
                result.put(normalize(path), builder.build());
            } while (true);
            return result;
        }
    }
}
