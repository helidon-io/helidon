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

import io.helidon.common.Weight;
import io.helidon.common.types.Annotation;
import io.helidon.inject.api.ContextualServiceQuery;
import io.helidon.inject.api.InjectionPointInfo;
import io.helidon.inject.api.InjectionPointProvider;
import io.helidon.inject.api.ServiceInfoBasics;

import com.oracle.bmc.ConfigFileReader;
import com.oracle.bmc.Region;
import com.oracle.bmc.auth.AbstractAuthenticationDetailsProvider;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;
import com.oracle.bmc.auth.InstancePrincipalsAuthenticationDetailsProvider;
import com.oracle.bmc.auth.ResourcePrincipalAuthenticationDetailsProvider;
import com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider;
import com.oracle.bmc.auth.SimplePrivateKeySupplier;
import com.oracle.bmc.auth.StringPrivateKeySupplier;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import static io.helidon.common.types.Annotations.findFirst;

/**
 * This (overridable) provider will provide the default implementation for {@link AbstractAuthenticationDetailsProvider}.
 *
 * @see OciExtension
 * @see OciConfig
 */
@Singleton
@Weight(ServiceInfoBasics.DEFAULT_INJECT_WEIGHT)
class OciAuthenticationDetailsProvider implements InjectionPointProvider<AbstractAuthenticationDetailsProvider> {
    static final System.Logger LOGGER = System.getLogger(OciAuthenticationDetailsProvider.class.getName());

    static final String TAG_AUTO = "auto";
    static final String TAG_CONFIG = "config";
    static final String TAG_CONFIG_FILE = "config-file";
    static final String TAG_INSTANCE_PRINCIPALS = "instance-principals";
    static final String TAG_RESOURCE_PRINCIPALS = "resource-principals";

    static final List<String> ALL_STRATEGIES = List.of(TAG_CONFIG,
                                                       TAG_CONFIG_FILE,
                                                       TAG_INSTANCE_PRINCIPALS,
                                                       TAG_RESOURCE_PRINCIPALS);

    OciAuthenticationDetailsProvider() {
    }

    @Override
    public Optional<AbstractAuthenticationDetailsProvider> first(ContextualServiceQuery query) {
        OciConfig ociConfig = OciExtension.ociConfig();

        String requestedNamedProfile = toNamedProfile(query.injectionPointInfo().orElse(null));
        if (requestedNamedProfile != null && !requestedNamedProfile.isBlank()) {
            ociConfig = OciConfig.builder(ociConfig).configProfile(requestedNamedProfile).build();
        }

        return Optional.of(select(ociConfig));
    }

    private static AbstractAuthenticationDetailsProvider select(OciConfig ociConfig) {
        List<AuthStrategy> strategies = AuthStrategy.convert(ociConfig.potentialAuthStrategies());
        for (AuthStrategy s : strategies) {
            if (s.isAvailable(ociConfig)) {
                LOGGER.log(System.Logger.Level.DEBUG, "Using authentication strategy " + s
                        + "; selected AbstractAuthenticationDetailsProvider " + s.type().getTypeName());
                return s.select(ociConfig);
            } else {
                LOGGER.log(System.Logger.Level.TRACE, "Skipping authentication strategy " + s + " because it is not available");
            }
        }
        throw new NoSuchElementException("No instances of "
                                                 + AbstractAuthenticationDetailsProvider.class.getName()
                                                 + " available for use. Verify your configuration named: "
                                                 + OciConfig.CONFIG_KEY);
    }

    static String toNamedProfile(InjectionPointInfo ipi) {
        if (ipi == null) {
            return null;
        }

        Optional<? extends Annotation> named = findFirst(Named.class, ipi.qualifiers());
        if (named.isEmpty()) {
            return null;
        }

        String nameProfile = named.get().value().orElse(null);
        if (nameProfile == null || nameProfile.isBlank()) {
            return null;
        }

        return nameProfile.trim();
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
        AUTO(TAG_AUTO,
             AbstractAuthenticationDetailsProvider.class,
             (ociConfig) -> true,
             OciAuthenticationDetailsProvider::select),

        /**
         * Corresponds to {@link SimpleAuthenticationDetailsProvider}.
         *
         * @see OciConfig#simpleConfigIsPresent()
         */
        CONFIG(TAG_CONFIG,
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
         * Corresponds to {@link ConfigFileAuthenticationDetailsProvider}.
         *
         * @see OciConfig
         */
        CONFIG_FILE(TAG_CONFIG_FILE,
                    ConfigFileAuthenticationDetailsProvider.class,
                    (configBean) -> configBean.fileConfigIsPresent()
                            && canReadPath(configBean.configPath().orElse(null)),
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
         */
        INSTANCE_PRINCIPALS(TAG_INSTANCE_PRINCIPALS,
                            InstancePrincipalsAuthenticationDetailsProvider.class,
                            OciAvailabilityDefault::runningOnOci,
                            (configBean) -> InstancePrincipalsAuthenticationDetailsProvider.builder().build()),

        /**
         * Corresponds to {@link ResourcePrincipalAuthenticationDetailsProvider}.
         */
        RESOURCE_PRINCIPAL(TAG_RESOURCE_PRINCIPALS,
                           ResourcePrincipalAuthenticationDetailsProvider.class,
                           (configBean) -> {
                               // https://github.com/oracle/oci-java-sdk/blob/v2.19.0/bmc-common/src/main/java/com/oracle/bmc/auth/ResourcePrincipalAuthenticationDetailsProvider.java#L246-L251
                               return (System.getenv("OCI_RESOURCE_PRINCIPAL_VERSION") != null);
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

}
