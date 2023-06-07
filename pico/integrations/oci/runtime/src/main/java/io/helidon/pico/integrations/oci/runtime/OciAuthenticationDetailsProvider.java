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

package io.helidon.pico.integrations.oci.runtime;

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
import io.helidon.common.types.AnnotationAndValue;
import io.helidon.pico.api.ContextualServiceQuery;
import io.helidon.pico.api.InjectionPointInfo;
import io.helidon.pico.api.InjectionPointProvider;
import io.helidon.pico.api.ServiceInfoBasics;

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

import static io.helidon.common.types.AnnotationAndValueDefault.findFirst;

/**
 * This (overridable) provider will provide the default implementation for {@link AbstractAuthenticationDetailsProvider}.
 *
 * @see OciExtension
 * @see OciConfigBean
 */
@Singleton
@Weight(ServiceInfoBasics.DEFAULT_PICO_WEIGHT)
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
        OciConfigBean ociConfig = OciExtension.ociConfig();

        String requestedNamedProfile = toNamedProfile(query.injectionPointInfo().orElse(null));
        if (requestedNamedProfile != null && !requestedNamedProfile.isBlank()) {
            ociConfig = OciConfigBeanDefault.toBuilder(ociConfig).configProfile(requestedNamedProfile).build();
        }

        return Optional.of(select(ociConfig));
    }

    private static AbstractAuthenticationDetailsProvider select(OciConfigBean ociConfigBean) {
        List<AuthStrategy> strategies = AuthStrategy.convert(ociConfigBean.potentialAuthStrategies());
        for (AuthStrategy s : strategies) {
            if (s.isAvailable(ociConfigBean)) {
                LOGGER.log(System.Logger.Level.DEBUG, "Using authentication strategy " + s
                        + "; selected AbstractAuthenticationDetailsProvider " + s.type().getTypeName());
                return s.select(ociConfigBean);
            } else {
                LOGGER.log(System.Logger.Level.TRACE, "Skipping authentication strategy " + s + " because it is not available");
            }
        }
        throw new NoSuchElementException("No instances of "
                                                 + AbstractAuthenticationDetailsProvider.class.getName()
                                                 + " available for use. Verify your configuration named: " + OciConfigBean.NAME);
    }

    static String toNamedProfile(InjectionPointInfo ipi) {
        if (ipi == null) {
            return null;
        }

        Optional<? extends AnnotationAndValue> named = findFirst(Named.class.getName(), ipi.qualifiers());
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

    static String userHomePrivateKeyPath(OciConfigBean ociConfigBean) {
        return Paths.get(System.getProperty("user.home"), ".oci", ociConfigBean.authKeyFile().orElseThrow()).toString();
    }


    enum AuthStrategy {
        /**
         * Auto selection of the auth strategy.
         */
        AUTO(TAG_AUTO,
             AbstractAuthenticationDetailsProvider.class,
             (ociConfigBean) -> true,
             OciAuthenticationDetailsProvider::select),

        /**
         * Corresponds to {@link SimpleAuthenticationDetailsProvider}.
         *
         * @see OciConfigBean#simpleConfigIsPresent()
         */
        CONFIG(TAG_CONFIG,
               SimpleAuthenticationDetailsProvider.class,
               OciConfigBean::simpleConfigIsPresent,
               (ociConfigBean) -> {
                   SimpleAuthenticationDetailsProvider.SimpleAuthenticationDetailsProviderBuilder b =
                           SimpleAuthenticationDetailsProvider.builder();
                   ociConfigBean.authTenantId().ifPresent(b::tenantId);
                   ociConfigBean.authUserId().ifPresent(b::userId);
                   ociConfigBean.authRegion().ifPresent(it -> b.region(Region.fromRegionCodeOrId(it)));
                   ociConfigBean.authFingerprint().ifPresent(b::fingerprint);
                   ociConfigBean.authPassphrase().ifPresent(chars -> b.passPhrase(String.valueOf(chars)));
                   ociConfigBean.authPrivateKey()
                           .ifPresentOrElse(pk -> b.privateKeySupplier(new StringPrivateKeySupplier(String.valueOf(pk))),
                                            () -> b.privateKeySupplier(new SimplePrivateKeySupplier(
                                                    userHomePrivateKeyPath(ociConfigBean))));
                   return b.build();
               }),

        /**
         * Corresponds to {@link ConfigFileAuthenticationDetailsProvider}.
         *
         * @see OciConfigBean
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

        boolean isAvailable(OciConfigBean ociConfigBean) {
            return availability.isAvailable(ociConfigBean);
        }

        AbstractAuthenticationDetailsProvider select(OciConfigBean ociConfigBean) {
            return selector.select(ociConfigBean);
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
        boolean isAvailable(OciConfigBean ociConfigBean);
    }


    @FunctionalInterface
    interface Selector {
        AbstractAuthenticationDetailsProvider select(OciConfigBean ociConfigBean);
    }

}
