/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates.
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

package io.helidon.security.providers.common;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.helidon.config.Config;
import io.helidon.security.EndpointConfig;
import io.helidon.security.ProviderRequest;
import io.helidon.security.SecurityEnvironment;
import io.helidon.security.spi.OutboundSecurityProvider;

/**
 * Configuration of outbound security targets.
 * This is a common implementation for outbound security providers that want to have configuration of enabled
 * endpoints based on transport (http/https etc.) and hosts (or ip addresses) with support for asterisk (*) wildcard.
 *
 * Use from {@link OutboundSecurityProvider#isOutboundSupported(ProviderRequest, SecurityEnvironment, EndpointConfig)}
 */
public final class OutboundConfig {
    /**
     * Configuration key (expected under provider configuration) that holds the object list of {@link OutboundTarget}s
     * configuration.
     */
    public static final String CONFIG_OUTBOUND = "outbound";
    /**
     * Property used for outbound calls with clients to disable registration/running of outbound security.
     * This may be used from clients that set up outbound security explicitly.
     * By default this property is assumed to be {@code false}, so outbound is enabled
     */
    public static final String PROPERTY_DISABLE_OUTBOUND = "io.helidon.security.client.disable";

    private final LinkedList<OutboundTarget> targets = new LinkedList<>();

    /**
     * Parse targets from provider configuration.
     *
     * @param providerConfig configuration object of current provider
     * @return new instance with targets from configuration
     */
    public static OutboundConfig create(Config providerConfig) {
        return createFromConfig(providerConfig, null);
    }

    /**
     * Parse targets from provider configuration with possible default targets.
     * In case the configuration contains a target named "default", the defaults provided to this method will be ignored.
     *
     * @param providerConfig configuration object of current provider
     * @param defaults       default target configuration (e.g. known public endpoints that are expected static in time)
     * @return new instance with targets from configuration, defaults are first (unless overridden)
     */
    public static OutboundConfig create(Config providerConfig, OutboundTarget... defaults) {
        return createFromConfig(providerConfig, defaults);
    }

    static OutboundConfig createFromConfig(Config providerConfig, OutboundTarget[] defaults) {
        Config config = providerConfig.get(CONFIG_OUTBOUND);

        List<OutboundTarget> configuredTargets = config.asList(OutboundTarget::create).orElse(List.of());

        boolean useDefaults = configuredTargets.stream().noneMatch(targetConfig -> "default".equals(targetConfig.name()))
                && (null != defaults);

        Builder builder = OutboundConfig.builder();

        if (useDefaults) {
            //first add default values
            Arrays.stream(defaults).forEach(builder::addTarget);
        }

        //then add configured values
        configuredTargets.forEach(builder::addTarget);

        return builder.build();
    }

    /**
     * New builder to programmatically build targets.
     *
     * @return new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Find target for current security request.
     * Example of implementation
     * of {@link OutboundSecurityProvider#isOutboundSupported(ProviderRequest, SecurityEnvironment, EndpointConfig)}:
     * {@code
     * return (null != outboundTargets) && outboundTargets.findTarget(request).isPresent();
     * }
     *
     * @param env request we are processing
     * @return TargetConfig wrapped in {@link Optional} valid for the request
     */
    public Optional<OutboundTarget> findTarget(SecurityEnvironment env) {
        String transport = env.transport();
        String host = env.targetUri().getHost();
        String path = env.path().orElse(null);
        String method = env.method();

        for (OutboundTarget outboundTarget : targets) {
            if (outboundTarget.matches(transport, host, path, method)) {
                return Optional.of(outboundTarget);
            }
        }

        return Optional.empty();
    }

    /**
     * Outbound targets configured for outbound handling.
     * Each target defines a protocol, host and path it is valid for (maybe using wildcards).
     * Additional configuration is usually added to a target for a specific provider.
     *
     * @return list of targets defined
     */
    public List<OutboundTarget> targets() {
        return targets;
    }

    /**
     * {@link OutboundConfig} builder when not reading it from configuration.
     */
    public static final class Builder implements io.helidon.common.Builder<OutboundConfig> {
        private final List<OutboundTarget> targets = new LinkedList<>();
        private final Set<String> names = new HashSet<>();

        private Builder() {
        }

        /**
         * Add a new target configuration.
         *
         * @param config target to add
         * @return updated builder instance
         */
        public Builder addTarget(OutboundTarget config) {
            if (names.contains(config.name())) {
                throw new IllegalStateException("Duplicate name of a target: " + config.name());
            }

            names.add(config.name());
            targets.add(config);

            return this;
        }

        /**
         * Build targets from this builder.
         *
         * @return new {@link OutboundConfig} instance
         */
        @Override
        public OutboundConfig build() {
            OutboundConfig result = new OutboundConfig();
            result.targets.addAll(this.targets);
            return result;
        }
    }
}
