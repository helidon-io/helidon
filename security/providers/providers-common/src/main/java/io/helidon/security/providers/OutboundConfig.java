/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.security.providers;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.helidon.common.CollectionsHelper;
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

    private final LinkedList<OutboundTarget> targets = new LinkedList<>();

    /**
     * Parse targets from provider configuration.
     *
     * @param providerConfig configuration object of current provider
     * @return new instance with targets from configuration
     */
    public static OutboundConfig parseTargets(Config providerConfig) {
        return from(providerConfig, null);
    }

    /**
     * Parse targets from provider configuration with possible default targets.
     * In case the configuration contains a target named "default", the defaults provided to this method will be ignored.
     *
     * @param providerConfig configuration object of current provider
     * @param defaults       default target configuration (e.g. known public endpoints that are expected static in time)
     * @return new instance with targets from configuration, defaults are first (unless overridden)
     */
    public static OutboundConfig parseTargets(Config providerConfig, OutboundTarget... defaults) {
        return from(providerConfig, defaults);
    }

    static OutboundConfig from(Config providerConfig, OutboundTarget[] defaults) {
        Config config = providerConfig.get(CONFIG_OUTBOUND);

        List<OutboundTarget> configuredTargets = config.mapList(OutboundTarget::from, CollectionsHelper.listOf());

        boolean useDefaults = configuredTargets.stream().noneMatch(targetConfig -> "default".equals(targetConfig.getName()))
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
        String transport = env.getTransport();
        String host = env.getTargetUri().getHost();
        String path = env.getPath().orElse(null);

        for (OutboundTarget outboundTarget : targets) {
            if (outboundTarget.matches(transport, host, path)) {
                return Optional.of(outboundTarget);
            }
        }

        return Optional.empty();
    }

    public List<OutboundTarget> getTargets() {
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
            if (names.contains(config.getName())) {
                throw new IllegalStateException("Duplicate name of a target: " + config.getName());
            }

            names.add(config.getName());
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
