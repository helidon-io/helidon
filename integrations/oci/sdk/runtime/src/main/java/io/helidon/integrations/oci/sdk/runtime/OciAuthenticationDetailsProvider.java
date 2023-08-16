/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.integrations.oci.sdk.runtime;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Supplier;

import com.oracle.bmc.ConfigFileReader;
import com.oracle.bmc.Region;
import com.oracle.bmc.auth.AbstractAuthenticationDetailsProvider;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;
import com.oracle.bmc.auth.InstancePrincipalsAuthenticationDetailsProvider;
import com.oracle.bmc.auth.ResourcePrincipalAuthenticationDetailsProvider;
import com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider;
import com.oracle.bmc.auth.SimplePrivateKeySupplier;
import com.oracle.bmc.auth.StringPrivateKeySupplier;
import jakarta.inject.Provider;

/**
 * This (overridable) provider will provide the default implementation for {@link AbstractAuthenticationDetailsProvider}.
 *
 * @see OciExtension
 * @see OciConfig
 */
class OciAuthenticationDetailsProvider implements Provider<AbstractAuthenticationDetailsProvider> {
    static final System.Logger LOGGER = System.getLogger(OciAuthenticationDetailsProvider.class.getName());

    static final String KEY_AUTH_STRATEGY = "auth-strategy";
    static final String KEY_AUTH_STRATEGIES = "auth-strategies";
    static final String TAG_RESOURCE_PRINCIPAL_VERSION = "OCI_RESOURCE_PRINCIPAL_VERSION";
    static final String VAL_AUTO = "auto";
    static final String VAL_CONFIG = "config";
    static final String VAL_CONFIG_FILE = "config-file";
    // com.oracle.bmc.auth.InstancePrincipalsAuthenticationDetailsProvider
    static final String VAL_INSTANCE_PRINCIPALS = "instance-principals";
    // com.oracle.bmc.auth.ResourcePrincipalAuthenticationDetailsProvider
    static final String VAL_RESOURCE_PRINCIPAL = "resource-principal";

    // order is important here - see the tests and the docs
    static final List<String> ALL_STRATEGIES = List.of(VAL_INSTANCE_PRINCIPALS,
                                                       VAL_RESOURCE_PRINCIPAL,
                                                       VAL_CONFIG,
                                                       VAL_CONFIG_FILE);

    OciAuthenticationDetailsProvider() {
    }

    @Override
    public AbstractAuthenticationDetailsProvider get() {
        OciConfig ociConfig = OciExtension.ociConfig();
        return select(ociConfig, true).authStrategy().select(ociConfig);
    }

    /**
     * Supply the selected strategy given the global OCI configuration. If one is named outright then use it, otherwise hunt for
     * the appropriate authentication strategy from the list of {@code auth-strategies} explicitly or implicitly defined.
     *
     * @param ociConfig the oci configuration
     * @param verifyIsAvailable flag to indicate whether the provider should be checked for availability
     * @return the configured, or else most applicable OCI auth strategy
     */
    static Supply select(OciConfig ociConfig,
                         boolean verifyIsAvailable) {
        Optional<String> authStrategy = ociConfig.authStrategy();
        if (authStrategy.isPresent() && !authStrategy.get().equalsIgnoreCase(AuthStrategy.AUTO.id)) {
            return new Supply(AuthStrategy.fromNameOrId(authStrategy.get()).orElseThrow(), ociConfig);
        }

        List<AuthStrategy> strategies = AuthStrategy.convert(ociConfig.potentialAuthStrategies());
        for (AuthStrategy s : strategies) {
            if (!verifyIsAvailable || s.isAvailable(ociConfig)) {
                LOGGER.log(System.Logger.Level.DEBUG, "Using authentication strategy " + s
                        + "; selected AbstractAuthenticationDetailsProvider " + s.type().getTypeName());
                return new Supply(s, ociConfig);
            } else {
                LOGGER.log(System.Logger.Level.TRACE, "Skipping authentication strategy " + s + " because it is not available");
            }
        }
        throw new NoSuchElementException("No instances of "
                                                 + AbstractAuthenticationDetailsProvider.class.getName()
                                                 + " available for use. Verify your configuration named: "
                                                 + OciConfig.CONFIG_KEY);
    }

    static boolean canReadPath(String pathName) {
        return (pathName != null && Path.of(pathName).toFile().canRead());
    }

    static String userHomePrivateKeyPath(OciConfig ociConfig) {
        return Paths.get(System.getProperty("user.home"), ".oci", ociConfig.authKeyFile()).toString();
    }


    enum AuthStrategy {
        /**
         * Auto selection of the auth strategy.
         */
        AUTO(VAL_AUTO,
             AbstractAuthenticationDetailsProvider.class,
             (ociConfig) -> true,
             (ociConfig) -> OciAuthenticationDetailsProvider.select(ociConfig, true).get()),

        /**
         * Corresponds to {@link SimpleAuthenticationDetailsProvider}.
         *
         * @see OciConfig#simpleConfigIsPresent()
         */
        CONFIG(VAL_CONFIG,
               SimpleAuthenticationDetailsProvider.class,
               OciConfig::simpleConfigIsPresent,
               (ociConfig) -> {
                   SimpleAuthenticationDetailsProvider.SimpleAuthenticationDetailsProviderBuilder b =
                           SimpleAuthenticationDetailsProvider.builder();
                   ociConfig.authTenantId().ifPresent(b::tenantId);
                   ociConfig.authUserId().ifPresent(b::userId);
                   ociConfig.authRegion().ifPresent(it -> b.region(Region.fromRegionCodeOrId(it)));
                   ociConfig.authFingerprint().ifPresent(b::fingerprint);
                   ociConfig.authPassphrase().ifPresent(chars -> b.passPhrase(String.valueOf(chars)));
                   ociConfig.authPrivateKey()
                           .ifPresentOrElse(pk -> b.privateKeySupplier(new StringPrivateKeySupplier(String.valueOf(pk))),
                                            () -> b.privateKeySupplier(new SimplePrivateKeySupplier(
                                                    userHomePrivateKeyPath(ociConfig))));
                   return b.build();
               }),

        /**
         * Corresponds to {@link ConfigFileAuthenticationDetailsProvider}, i.e., "$HOME/.oci/config/.
         *
         * @see OciConfig#fileConfigIsPresent()
         */
        CONFIG_FILE(VAL_CONFIG_FILE,
                    ConfigFileAuthenticationDetailsProvider.class,
                    (configBean) -> configBean.fileConfigIsPresent()
                            && (configBean.configPath().isEmpty() || canReadPath(configBean.configPath().orElse(null))),
                    (configBean) -> {
                        // https://github.com/oracle/oci-java-sdk/blob/master/bmc-common/src/main/java/com/oracle/bmc/auth/ConfigFileAuthenticationDetailsProvider.java
                        // https://github.com/oracle/oci-java-sdk/blob/master/bmc-examples/src/main/java/ObjectStorageSyncExample.java
                        try {
                            if (configBean.configPath().isPresent()) {
                                return new ConfigFileAuthenticationDetailsProvider(configBean.configPath().get(),
                                                                                   configBean.configProfile().orElseThrow());
                            } else {
                                return new ConfigFileAuthenticationDetailsProvider(ConfigFileReader.parseDefault());
                            }
                        } catch (IOException e) {
                            throw new UncheckedIOException(e.getMessage(), e);
                        }
                    }),

        /**
         * Corresponds to {@link InstancePrincipalsAuthenticationDetailsProvider}.
         *
         * @see OciConfig
         */
        INSTANCE_PRINCIPALS(VAL_INSTANCE_PRINCIPALS,
                            InstancePrincipalsAuthenticationDetailsProvider.class,
                            OciAvailabilityDefault::runningOnOci,
                            (configBean) -> InstancePrincipalsAuthenticationDetailsProvider.builder().build()),

        /**
         * Corresponds to {@link ResourcePrincipalAuthenticationDetailsProvider}.
         *
         * @see OciConfig
         */
        RESOURCE_PRINCIPAL(VAL_RESOURCE_PRINCIPAL,
                           ResourcePrincipalAuthenticationDetailsProvider.class,
                           (configBean) -> {
                               // https://github.com/oracle/oci-java-sdk/blob/v2.19.0/bmc-common/src/main/java/com/oracle/bmc/auth/ResourcePrincipalAuthenticationDetailsProvider.java#L246-L251
                               return (System.getenv(TAG_RESOURCE_PRINCIPAL_VERSION) != null);
                           },
                           (configBean) -> ResourcePrincipalAuthenticationDetailsProvider.builder().build());

        private final String id;
        private final Class<? extends AbstractAuthenticationDetailsProvider> type;
        private final Availability availability;
        private final Selector selector;

        AuthStrategy(String id,
                     Class<? extends AbstractAuthenticationDetailsProvider> type,
                     Availability availability,
                     Selector selector) {
            this.id = id;
            this.type = type;
            this.availability = availability;
            this.selector = selector;
        }

        String id() {
            return id;
        }

        Class<? extends AbstractAuthenticationDetailsProvider> type() {
            return type;
        }

        boolean isAvailable(OciConfig ociConfig) {
            return availability.isAvailable(ociConfig);
        }

        AbstractAuthenticationDetailsProvider select(OciConfig ociConfig) {
            return selector.select(ociConfig);
        }

        static Optional<AuthStrategy> fromNameOrId(String nameOrId) {
            try {
                return Optional.of(valueOf(nameOrId));
            } catch (Exception e) {
                return Arrays.stream(AuthStrategy.values())
                        .filter(it -> nameOrId.equalsIgnoreCase(it.id())
                                || nameOrId.equalsIgnoreCase(it.name()))
                        .findFirst();
            }
        }

        static List<AuthStrategy> convert(Collection<String> authStrategies) {
            return authStrategies.stream()
                    .map(AuthStrategy::fromNameOrId)
                    .map(Optional::orElseThrow)
                    .filter(s -> s != AuthStrategy.AUTO)
                    .toList();
        }
    }


    @FunctionalInterface
    interface Availability {
        boolean isAvailable(OciConfig ociConfig);
    }


    @FunctionalInterface
    interface Selector {
        AbstractAuthenticationDetailsProvider select(OciConfig ociConfig);
    }

    static class Supply implements Supplier<AbstractAuthenticationDetailsProvider> {
        private final AuthStrategy authStrategy;
        private final OciConfig ociConfig;

        private Supply(AuthStrategy authStrategy,
                       OciConfig ociConfig) {
            this.authStrategy = authStrategy;
            this.ociConfig = ociConfig;
        }

        @Override
        public AbstractAuthenticationDetailsProvider get() {
            return authStrategy.select(ociConfig);
        }

        public AuthStrategy authStrategy() {
            return authStrategy;
        }
    }

}
