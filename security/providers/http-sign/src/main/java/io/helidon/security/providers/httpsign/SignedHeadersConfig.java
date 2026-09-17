/*
 * Copyright (c) 2018, 2026 Oracle and/or its affiliates.
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

import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import io.helidon.config.Config;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;

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
 *      method = "GET"
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
     * Special header {@value} is used for method and request target, including query when present.
     */
    public static final String REQUEST_TARGET = "(request-target)";

    private static final System.Logger LOGGER = System.getLogger(SignedHeadersConfig.class.getName());
    private static final Set<String> KNOWN_METHODS = Set.of("GET", "POST", "QUERY", "PUT", "DELETE", "HEAD", "PATCH", "OPTIONS",
                                                           "TRACE", "CONNECT");

    private final HeadersConfig defaultConfig;
    private final Map<String, HeadersConfig> methodConfigs;

    private SignedHeadersConfig(Builder builder) {
        this.defaultConfig = builder.defaultConfig;
        this.methodConfigs = Map.copyOf(builder.methodConfigs);
    }

    /**
     * Load header configuration from config.
     * Non-uppercase known HTTP methods also configure their uppercase names for compatibility and log a warning.
     * An explicit uppercase configuration takes precedence over this compatibility configuration. Otherwise, the last
     * non-uppercase configuration for a known method supplies its uppercase configuration. Custom methods retain exact case.
     * Use uppercase names for known HTTP methods; this compatibility will be removed in a future major version.
     *
     * @param config config instance, expecting object array as children
     * @return signed headers configuration loaded from config
     */
    public static SignedHeadersConfig create(Config config) {
        Builder builder = builder();
        Map<String, HeadersConfig> uppercaseConfigs = new HashMap<>();
        config.asNodeList().get().forEach(methodConfig -> {
            HeadersConfig mc = HeadersConfig.create(methodConfig);

            Config methodNode = methodConfig.get("method");
            methodNode
                    .asString()
                    .ifPresentOrElse(method -> {
                        builder.config(method, mc);
                        String uppercase = method.toUpperCase(Locale.ROOT);
                        if (!method.equals(uppercase)
                                && KNOWN_METHODS.contains(uppercase)
                                && method.chars().allMatch(character -> character < 128)) {
                            uppercaseConfigs.put(uppercase, mc);
                            LOGGER.log(Level.WARNING,
                                       "Configuration key \"{0}\" uses non-uppercase HTTP method \"{1}\". Use \"{2}\" instead. "
                                               + "Automatic uppercasing will be removed in a future major version; "
                                               + "the configured value will then be matched case-sensitively.",
                                       methodNode.key(), method, uppercase);
                        }
                    }, () -> builder.defaultConfig(mc));
        });
        uppercaseConfigs.forEach(builder.methodConfigs::putIfAbsent);

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

    /**
     * Headers configured for a method with optional headers matched against actual transport headers.
     *
     * @param method method (such as GET)
     * @param transportHeaders actual headers received on the transport
     * @return list of headers that must be signed
     */
    public List<String> headers(String method, Map<String, List<String>> transportHeaders) {
        return methodConfig(method).getHeaders(transportHeaders);
    }

    /**
     * Headers configured for a method.
     *
     * @param method method (such as GET)
     * @return list of headers
     */
    public List<String> headers(String method) {
        return new ArrayList<>(methodConfig(method).always);
    }

    private HeadersConfig methodConfig(String method) {
        return methodConfigs.getOrDefault(method, defaultConfig);
    }

    /**
     * Fluent API builder to create {@link SignedHeadersConfig} instances.
     * Call {@link #build()} to create a new instance.
     */
    public static final class Builder implements io.helidon.common.Builder<Builder, SignedHeadersConfig> {
        private static final HeadersConfig DEFAULT_HEADERS = HeadersConfig.create(List.of("date"));

        private final Map<String, HeadersConfig> methodConfigs = new HashMap<>();
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
         * "date" and "host" headers and method "GET" to require "(request-target)", GET will NOT require "date" and
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
         * @param method exact method name
         * @param config configuration of method
         * @return updated builder instance
         */
        public Builder config(String method, HeadersConfig config) {
            Objects.requireNonNull(method);
            Objects.requireNonNull(config);
            this.methodConfigs.put(method, config);
            return this;
        }
    }

    /**
     * Configuration of headers to be signed.
     */
    @Configured
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
        @ConfiguredOption(key = "always", type = String.class, kind = ConfiguredOption.Kind.LIST,
                          description = "Headers that must be signed (and signature validation or creation should fail if not "
                                  + "signed or present)")
        @ConfiguredOption(key = "if-present", type = String.class, kind = ConfiguredOption.Kind.LIST,
                          description = "Headers that must be signed if present in request.")
        @ConfiguredOption(key = "method", type = String.class,
                          description = "Exact HTTP method this header configuration is bound to. "
                                  + "Non-uppercase known HTTP methods also configure their uppercase names and log a warning; "
                                  + "an explicit uppercase configuration takes precedence. Use uppercase names for known HTTP "
                                  + "methods; this compatibility will be removed in a future major version. "
                                  + "Custom methods retain exact case. "
                                  + "If not present, it is considered default header configuration.")
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
