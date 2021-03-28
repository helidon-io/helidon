/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.security.providers.httpsign;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import io.helidon.config.Config;

/**
 * Configuration of required and "if-present" headers to be signed.
 * <p>
 * Example for configuration based approach:
 * <pre>
 * sign-headers: [
 *  # request may sign headers not specified here - only specify the ones that MUST be signed
 *  {
 *      # if method is not defined, then this is the default config
 *      # MUST be present and signed
 *      always = ["date"]
 *  }
 *  {
 *      method = "get"
 *      # MUST be present and signed
 *      always = ["date", "(request-target)", "host"]
 *      # MUST be signed IF present
 *      if-present = ["authorization"]
 *  }
 * ]
 * </pre>
 */
public final class SignedHeadersConfig {
    /**
     * Special header {@value} is used for method and path combination.
     */
    public static final String REQUEST_TARGET = "(request-target)";

    private final HeadersConfig defaultConfig;
    private final Map<String, HeadersConfig> methodConfigs;

    private SignedHeadersConfig(Builder builder) {
        this.defaultConfig = builder.defaultConfig;
        this.methodConfigs = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        methodConfigs.putAll(builder.methodConfigs);
    }

    /**
     * Load header configuration from config.
     *
     * @param config config instance, expecting object array as children
     * @return signed headers configuration loaded from config
     */
    public static SignedHeadersConfig create(Config config) {
        Builder builder = builder();
        config.asNodeList().get().forEach(methodConfig -> {
            HeadersConfig mc = HeadersConfig.create(methodConfig);

            methodConfig.get("method")
                    .asString()
                    .ifPresentOrElse(method -> builder.config(method, mc),
                                     () -> builder.defaultConfig(mc));
        });

        return builder.build();
    }

    /**
     * Builder to create a new instance.
     *
     * @return new builder
     */
    public static Builder builder() {
        return new Builder().defaultConfig(HeadersConfig.create());
    }

    public List<String> headers(String method, Map<String, List<String>> transportHeaders) {
        return methodConfigs.getOrDefault(method, defaultConfig).getHeaders(transportHeaders);
    }

    public List<String> headers(String method) {
        return new ArrayList<>(methodConfigs.getOrDefault(method, defaultConfig).always);
    }

    /**
     * Fluent API builder to create {@link SignedHeadersConfig} instances.
     * Call {@link #build()} to create a new instance.
     */
    public static final class Builder implements io.helidon.common.Builder<SignedHeadersConfig> {
        private static final HeadersConfig DEFAULT_HEADERS = HeadersConfig.create(List.of("date"));

        private final Map<String, HeadersConfig> methodConfigs = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        private HeadersConfig defaultConfig = DEFAULT_HEADERS;

        private Builder() {
        }

        @Override
        public SignedHeadersConfig build() {
            return new SignedHeadersConfig(this);
        }

        /**
         * Default configuration is used by methods that do not have an explicit configuration.
         * <p>
         * <strong>Configuration is not cumulative - e.g. if you configure default to require
         * "date" and "host" headers and method "get" to require "(request-target)", get will NOT require "date" and
         * "host"</strong>
         *
         * @param config configuration of method (e.g. headers that must always be signed and headers
         *               to be signed when available in request)
         * @return updated builder instance
         */
        public Builder defaultConfig(HeadersConfig config) {
            this.defaultConfig = config;
            return this;
        }

        /**
         * Configuration of a single method (see {@link io.helidon.security.SecurityEnvironment#method()} to set required and
         * "if-present" headers to be signed (or to be expected in inbound signature).
         *
         * @param method method name (methods are case-insensitive)
         * @param config configuration of method
         * @return updated builder instance
         */
        public Builder config(String method, HeadersConfig config) {
            this.methodConfigs.put(method, config);
            return this;
        }
    }

    /**
     * Configuration of headers to be signed.
     */
    public static final class HeadersConfig {
        private final List<String> always;
        private final List<String> ifPresent;

        private HeadersConfig(List<String> requiredHeaders, List<String> ifPresentHeaders) {
            this.always = new ArrayList<>(requiredHeaders);
            this.ifPresent = new LinkedList<>(ifPresentHeaders);
        }

        /**
         * Create a config with no signed headers (e.g. signatures disabled)
         *
         * @return instance with no required headers
         */
        public static HeadersConfig create() {
            return create(List.of());
        }

        /**
         * Create a config with required headers only (e.g. no "if-present" headers).
         *
         * @param requiredHeaders headers that must be signed
         * @return instance with required headers
         */
        public static HeadersConfig create(List<String> requiredHeaders) {
            return create(requiredHeaders, List.of());
        }

        /**
         * Create a new instance with both required headers and headers that are signed only if present in request.
         *
         * @param requiredHeaders  headers that must be signed (and signature validation or creation should fail if not signed or
         *                         present)
         * @param ifPresentHeaders headers that must be signed if present in request
         * @return instance with required and "if-present" headers
         */
        public static HeadersConfig create(List<String> requiredHeaders, List<String> ifPresentHeaders) {
            return new HeadersConfig(requiredHeaders, ifPresentHeaders);
        }

        /**
         * Create a new instance from configuration.
         *
         * @param config configuration located at header config
         * @return instance configured from config
         */
        public static HeadersConfig create(Config config) {
            return create(config.get("always").asList(String.class).orElse(List.of()),
                          config.get("if-present").asList(String.class).orElse(List.of()));
        }

        List<String> getHeaders(Map<String, List<String>> transportHeaders) {
            List<String> result = new ArrayList<>(always);

            ifPresent.stream().filter(transportHeaders::containsKey).forEach(result::add);

            return result;
        }
    }

}
