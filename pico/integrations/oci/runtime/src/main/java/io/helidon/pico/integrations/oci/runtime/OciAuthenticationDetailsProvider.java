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

@Singleton
@Weight(ServiceInfoBasics.DEFAULT_PICO_WEIGHT)
class OciAuthenticationDetailsProvider implements InjectionPointProvider<AbstractAuthenticationDetailsProvider> {
    static final System.Logger LOGGER = System.getLogger(OciAuthenticationDetailsProvider.class.getName());

    OciAuthenticationDetailsProvider() {
    }

    @Override
    public Optional<AbstractAuthenticationDetailsProvider> first(ContextualServiceQuery query) {
        String requestedNamedProfile = toNamedProfile(query.injectionPointInfo().orElse(null));
        OciConfigBean ociConfig = OciExtension.ociConfig();
        return Optional.of(select(requestedNamedProfile, ociConfig));
    }

    private static AbstractAuthenticationDetailsProvider select(String requestedNamedProfile,
                                                                OciConfigBean ociConfigBean) {
        List<AuthStrategy> strategies = ociConfigBean.authStrategies().stream()
                .map(it -> AuthStrategy.fromNameOrId(it)
                        .orElseThrow(() -> new IllegalArgumentException("Unknown authentication strategy in: "
                                                                                + ociConfigBean.authStrategies())))
                .toList();
        for (AuthStrategy s : strategies) {
            if (s.isAvailable(requestedNamedProfile, ociConfigBean)) {
                LOGGER.log(System.Logger.Level.DEBUG, "Using authentication strategy " + s
                        + "; selected AbstractAuthenticationDetailsProvider " + s.type().getTypeName());
                return s.select(requestedNamedProfile, ociConfigBean);
            } else {
                LOGGER.log(System.Logger.Level.TRACE, "Skipping authentication strategy " + s + " because it is not available");
            }
        }
        throw new NoSuchElementException("No instances of "
                                                 + AbstractAuthenticationDetailsProvider.class.getName()
                                                 + " available for use");
    }

    static String toNamedProfile(InjectionPointInfo ipi) {
        if (ipi == null) {
            return null;
        }

        Optional<? extends AnnotationAndValue> named = findFirst(Named.class.getName(), ipi.annotations());
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
        return Paths.get(System.getProperty("user.home"), ".oci", ociConfigBean.authKeyFile()).toString();
    }


    enum AuthStrategy {
        AUTO("auto",
             AbstractAuthenticationDetailsProvider.class,
             (profileName, ociConfigBean) -> true,
             OciAuthenticationDetailsProvider::select),

        CONFIG("config",
               SimpleAuthenticationDetailsProvider.class,
               (profileName, ociConfigBean) -> ociConfigBean.simpleConfigIsPresent(),
               (profileName, ociConfigBean) -> {
                   SimpleAuthenticationDetailsProvider.SimpleAuthenticationDetailsProviderBuilder b =
                           SimpleAuthenticationDetailsProvider.builder();
                   ociConfigBean.authTenantId().ifPresent(b::tenantId);
                   ociConfigBean.authUserId().ifPresent(b::userId);
                   ociConfigBean.authRegion().ifPresent(it -> b.region(Region.fromRegionCodeOrId(it)));
                   ociConfigBean.authFingerprint().ifPresent(b::fingerprint);
                   ociConfigBean.authPassphrase().ifPresent(b::passPhrase);
                   ociConfigBean.authPrivateKey()
                           .ifPresentOrElse(pk -> b.privateKeySupplier(new StringPrivateKeySupplier(pk)),
                                            () -> b.privateKeySupplier(new SimplePrivateKeySupplier(
                                                    userHomePrivateKeyPath(ociConfigBean))));
                   return b.build();
               }),

        CONFIG_FILE("config-file",
                    ConfigFileAuthenticationDetailsProvider.class,
                    (profileName, configBean) -> canReadPath(configBean.configPath().orElse(null)),
                    (profileName, configBean) -> {
                        // https://github.com/oracle/oci-java-sdk/blob/master/bmc-common/src/main/java/com/oracle/bmc/auth/ConfigFileAuthenticationDetailsProvider.java
                        // https://github.com/oracle/oci-java-sdk/blob/master/bmc-examples/src/main/java/ObjectStorageSyncExample.java
                        try {
                            if (profileName != null && configBean.configPath().isPresent()) {
                                return new ConfigFileAuthenticationDetailsProvider(configBean.configPath().get(), profileName);
                            } else if (profileName != null) {
                                return new ConfigFileAuthenticationDetailsProvider(profileName);
                            } else {
                                return new ConfigFileAuthenticationDetailsProvider(ConfigFileReader.parseDefault());
                            }
                        } catch (IOException e) {
                            throw new UncheckedIOException(e.getMessage(), e);
                        }
                    }),

        INSTANCE_PRINCIPALS("instance-principals",
                            InstancePrincipalsAuthenticationDetailsProvider.class,
                            (profileName, configBean) -> OciAvailabilityDefault.runningOnOci(configBean),
                            (profileName, configBean) -> InstancePrincipalsAuthenticationDetailsProvider.builder().build()),

        RESOURCE_PRINCIPAL("resource-principal",
                           ResourcePrincipalAuthenticationDetailsProvider.class,
                           (profileName, configBean) -> {
                               // https://github.com/oracle/oci-java-sdk/blob/v2.19.0/bmc-common/src/main/java/com/oracle/bmc/auth/ResourcePrincipalAuthenticationDetailsProvider.java#L246-L251
                               return (System.getenv("OCI_RESOURCE_PRINCIPAL_VERSION") != null);
                           },
                           (profileName, configBean) -> ResourcePrincipalAuthenticationDetailsProvider.builder().build());

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

        boolean isAvailable(String profileName,
                            OciConfigBean ociConfigBean) {
            return availability.isAvailable(profileName, ociConfigBean);
        }

        AbstractAuthenticationDetailsProvider select(String profileName,
                                                     OciConfigBean ociConfigBean) {
            return selector.select(profileName, ociConfigBean);
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
    }

    @FunctionalInterface
    interface Availability {
        boolean isAvailable(String profileName,
                            OciConfigBean ociConfigBean);
    }

    @FunctionalInterface
    interface Selector {
        AbstractAuthenticationDetailsProvider select(String profileName,
                                                     OciConfigBean ociConfigBean);
    }

}
