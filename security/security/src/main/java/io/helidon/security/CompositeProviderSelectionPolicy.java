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

package io.helidon.security;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import io.helidon.config.Config;
import io.helidon.security.spi.AuthenticationProvider;
import io.helidon.security.spi.AuthorizationProvider;
import io.helidon.security.spi.OutboundSecurityProvider;
import io.helidon.security.spi.ProviderSelectionPolicy;
import io.helidon.security.spi.SecurityProvider;

/**
 * A provider selection policy that supports composing multiple providers (current Authentication and Outbound)
 * into a single virtual security provider.
 * <p>
 * Example configuration:
 * <pre>
 * security.provider-policy {
 *  type = "COMPOSITE"
 *  # explicit name of this policy (to be used when this is not the default or when we want to explicitly reference it)
 *  name = "composite"
 *  # whether this is the default provider or not (if not, must be explicitly defined by name, if yes, it is returned)
 *  default = true
 *  authentication: [
 *  {
 *      name = "first"
 *      flag = "REQUIRED"
 *  },
 *  {
 *      name = "second"
 *  }]
 * outbound: [
 *  {
 *      name = "first"
 *  },
 *  {
 *      name = "second"
 *  }]
 * }
 * </pre>
 */
public class CompositeProviderSelectionPolicy implements ProviderSelectionPolicy {
    private final CompositeOutboundProvider outbound;
    private final CompositeAuthenticationProvider atn;
    private final CompositeAuthorizationProvider atz;
    private final Set<String> configuredOutbound = new HashSet<>();
    private final List<NamedProvider<OutboundSecurityProvider>> allOutbound = new LinkedList<>();
    private final boolean isDefault;
    private final String name;
    private final FirstProviderSelectionPolicy fallback;

    @SuppressWarnings("unchecked")
    private CompositeProviderSelectionPolicy(Providers providers, Builder builder) {
        this.fallback = new FirstProviderSelectionPolicy(providers);
        this.isDefault = builder.isDefault;
        this.name = builder.name;

        if (!builder.authenticators.isEmpty()) {
            List<CompositeAuthenticationProvider.Atn> parts = new LinkedList<>();
            builder.authenticators
                    .forEach(flaggedProvider -> parts.add(new CompositeAuthenticationProvider.Atn(
                            flaggedProvider,
                            providers.getProviders(AuthenticationProvider.class)
                                    .stream()
                                    .filter(np -> np.getName().equals(flaggedProvider.getProviderName()))
                                    .findFirst()
                                    .map(NamedProvider::getProvider)
                                    .orElseThrow(() -> new SecurityException(
                                            "Misconfigured composite provider selection policy. There is no authentication "
                                                    + "provider named " + flaggedProvider
                                                    .getProviderName() + " configured."))
                    )));

            atn = new CompositeAuthenticationProvider(parts);
        } else {
            atn = null;
        }

        if (!builder.authorizers.isEmpty()) {
            List<CompositeAuthorizationProvider.Atz> parts = new LinkedList<>();
            builder.authorizers
                    .forEach(flaggedProvider -> parts.add(new CompositeAuthorizationProvider.Atz(
                            flaggedProvider,
                            providers.getProviders(AuthorizationProvider.class)
                                    .stream()
                                    .filter(np -> np.getName().equals(flaggedProvider.getProviderName()))
                                    .findFirst()
                                    .map(NamedProvider::getProvider)
                                    .orElseThrow(() -> new SecurityException(
                                            "Misconfigured composite provider selection policy. There is no authorization "
                                                    + "provider named " + flaggedProvider
                                                    .getProviderName() + " configured."))
                    )));

            atz = new CompositeAuthorizationProvider(parts);
        } else {
            atz = null;
        }

        allOutbound.addAll(providers.getProviders(OutboundSecurityProvider.class));

        if (!builder.outbound.isEmpty()) {
            List<OutboundSecurityProvider> parts = new LinkedList<>();
            configuredOutbound.addAll(builder.outbound);
            builder.outbound
                    .forEach(name -> parts.add(
                            providers.getProviders(OutboundSecurityProvider.class)
                                    .stream()
                                    .filter(np -> np.getName().equals(name))
                                    .findFirst()
                                    .map(NamedProvider::getProvider)
                                    .orElseThrow(() -> new SecurityException(
                                            "Misconfigured composite provider selection policy. There is no outbound security "
                                                    + "provider "
                                                    + "provider named " + name + " configured."))
                    ));
            outbound = new CompositeOutboundProvider(parts);
        } else {
            outbound = null;
        }

    }

    /**
     * Builder for this selection policy.
     *
     * @return builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Load this policy from config. See {@link CompositeProviderSelectionPolicy} for example.
     *
     * @param config configuration instance
     * @return function as expected by {@link Security.Builder#providerSelectionPolicy(Function)}
     */
    public static Function<Providers, ProviderSelectionPolicy> fromConfig(Config config) {
        return builder().fromConfig(config).build();
    }

    @Override
    public <T extends SecurityProvider> Optional<T> selectProvider(Class<T> providerType) {
        if (isDefault) {
            if (null != atn && providerType.equals(AuthenticationProvider.class)) {
                return Optional.of(providerType.cast(atn));
            } else if (null != atz && providerType.equals(AuthorizationProvider.class)) {
                return Optional.of(providerType.cast(atz));
            }
        }

        return fallback.selectProvider(providerType);
    }

    @Override
    public List<OutboundSecurityProvider> selectOutboundProviders() {
        LinkedList<OutboundSecurityProvider> result = new LinkedList<>();

        // only add the ones we are not composing
        allOutbound.stream()
                .filter(np -> !configuredOutbound.contains(np.getName()))
                .forEach(np -> result.add(np.getProvider()));

        if (null != outbound) {
            if (isDefault) {
                result.addFirst(outbound);
            } else {
                result.addLast(outbound);
            }
        }

        return result;
    }

    @Override
    public <T extends SecurityProvider> Optional<T> selectProvider(Class<T> providerType, String requestedName) {
        if (name.equals(requestedName)) {
            if (null != atn && providerType.equals(AuthenticationProvider.class)) {
                return Optional.of(providerType.cast(atn));
            } else if (null != atz && providerType.equals(AuthorizationProvider.class)) {
                return Optional.of(providerType.cast(atz));
            } else if (null != outbound && providerType.equals(OutboundSecurityProvider.class)) {
                return Optional.of(providerType.cast(outbound));
            }
        }

        return fallback.selectProvider(providerType, requestedName);
    }

    /**
     * Fluent API builder to create {@link CompositeProviderSelectionPolicy}.
     * Invoke {@link #build()} to get a function to be sent to {@link Security.Builder#providerSelectionPolicy(Function)}.
     */
    public static class Builder implements io.helidon.common.Builder<Function<Providers, ProviderSelectionPolicy>> {
        private final List<FlaggedProvider> authenticators = new LinkedList<>();
        private final List<FlaggedProvider> authorizers = new LinkedList<>();
        private final List<String> outbound = new LinkedList<>();
        private String name = "composite";
        private boolean isDefault = true;

        private Builder() {
        }

        /**
         * Name of this provider to use for explicit provider configuration.
         * The same name is used for authentication, authorization and outbound security.
         *
         * @param name name of the virtual provider create by this policy
         * @return updated builder instance
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * If set to true (default value) then this policy returns a virtual
         * provider combining the configured providers.
         * If set to false, the virtual provider is returned only when explicitly
         * called by {@link #name}.
         *
         * @param isDefault whether the composite provider is the default
         * @return updated builder instance
         */
        public Builder isDefault(boolean isDefault) {
            this.isDefault = isDefault;
            return this;
        }

        /**
         * Add a required provider to this composite provider.
         *
         * @param namedProvider name of the provider as configured with security
         * @return updated builder instance
         */
        public Builder addAuthenticationProvider(String namedProvider) {
            authenticators.add(new FlaggedProvider(CompositeProviderFlag.REQUIRED, namedProvider));
            return this;
        }

        /**
         * Add a provider to this composite policy.
         *
         * @param namedProvider name of the provider as configured with security
         * @param flag          to indicate how to handle provider's response
         * @return updated builder instance
         */
        public Builder addAuthenticationProvider(String namedProvider, CompositeProviderFlag flag) {
            authenticators.add(new FlaggedProvider(flag, namedProvider));
            return this;
        }

        /**
         * Add a required provider to this composite provider.
         *
         * @param namedProvider name of the provider as configured with security
         * @return updated builder instance
         */
        public Builder addAuthorizationProvider(String namedProvider) {
            authorizers.add(new FlaggedProvider(CompositeProviderFlag.REQUIRED, namedProvider));
            return this;
        }

        /**
         * Add a provider to this composite policy.
         *
         * @param namedProvider name of the provider as configured with security
         * @param flag          to indicate how to handle provider's response
         * @return updated builder instance
         */
        public Builder addAuthorizationProvider(String namedProvider, CompositeProviderFlag flag) {
            authorizers.add(new FlaggedProvider(flag, namedProvider));
            return this;
        }

        /**
         * Add a provider to this composite policy.
         *
         * @param namedProvider name of the provider as configured with security
         * @return updated builder instance
         */
        public Builder addOutboundProvider(String namedProvider) {
            outbound.add(namedProvider);
            return this;
        }

        /**
         * Update fields from configuration.
         *
         * @param config Configuration
         * @return updated builder instance
         */
        public Builder fromConfig(Config config) {
            config.get("name").value().ifPresent(this::name);
            config.get("default").asOptional(Boolean.class).ifPresent(this::isDefault);
            config.get("authentication").mapOptionalList(FlaggedProvider::fromConfig)
                    .ifPresent(this.authenticators::addAll);
            config.get("authorization").mapOptionalList(FlaggedProvider::fromConfig)
                    .ifPresent(this.authorizers::addAll);
            config.get("outbound").nodeList()
                    .ifPresent(configs -> configs.forEach(outConfig -> addOutboundProvider(outConfig.get("name").asString())));

            return this;
        }

        /**
         * Build a function to create an instance of this provider as expected by
         * {@link Security.Builder#providerSelectionPolicy(Function)}.
         *
         * @return function to build this policy
         */
        @Override
        public Function<Providers, ProviderSelectionPolicy> build() {
            return providers -> new CompositeProviderSelectionPolicy(providers, this);
        }
    }

    static class FlaggedProvider {
        private final CompositeProviderFlag flag;
        private final String providerName;

        FlaggedProvider(CompositeProviderFlag flag, String providerName) {
            this.flag = flag;
            this.providerName = providerName;
        }

        /**
         * Load an instance from configuration.
         *
         * @param config configuration instance
         * @return instance configured from config
         */
        static FlaggedProvider fromConfig(Config config) {
            String name = config.get("name").asString();
            CompositeProviderFlag flag = config.get("flag").as(CompositeProviderFlag.class, CompositeProviderFlag.REQUIRED);

            return new FlaggedProvider(flag, name);
        }

        String getProviderName() {
            return providerName;
        }

        CompositeProviderFlag getFlag() {
            return flag;
        }
    }
}
