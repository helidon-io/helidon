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
package io.helidon.integrations.oci.secrets.mp.configsource;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.URI;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.oracle.bmc.Region;
import com.oracle.bmc.auth.BasicAuthenticationDetailsProvider;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;
import com.oracle.bmc.auth.InstancePrincipalsAuthenticationDetailsProvider;
import com.oracle.bmc.auth.InstancePrincipalsAuthenticationDetailsProvider.InstancePrincipalsAuthenticationDetailsProviderBuilder;
import com.oracle.bmc.auth.ResourcePrincipalAuthenticationDetailsProvider;
import com.oracle.bmc.auth.ResourcePrincipalAuthenticationDetailsProvider.ResourcePrincipalAuthenticationDetailsProviderBuilder;
import com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider;
import com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider.SimpleAuthenticationDetailsProviderBuilder;
import com.oracle.bmc.auth.SimplePrivateKeySupplier;
import com.oracle.bmc.auth.StringPrivateKeySupplier;

import static java.lang.System.Logger;

/**
 * A utility class containing methods that produce {@link Supplier}s of {@link BasicAuthenticationDetailsProvider}
 * instances of various kinds.
 */
final class AdpSuppliers {

    private static final Pattern COMMA_SPLITTER = Pattern.compile("(?<!\\\\),"); // non-escaped comma

    private static final String DEFAULT_OCI_AUTH_PRIVATE_KEY_PATH =
        Paths.get(System.getProperty("user.home"), ".oci", "oci_api_key.pem").toString();

    private static final String DEFAULT_OCI_CONFIG_PROFILE = "DEFAULT";

    private static final int DEFAULT_OCI_IMDS_TIMEOUT_MILLIS = 100;

    private static final Logger LOGGER = System.getLogger(AdpSuppliers.class.getName());

    private AdpSuppliers() {
        super();
    }

    @SuppressWarnings("checkstyle:linelength")
    static Optional<Supplier<SimpleAuthenticationDetailsProvider>> simple(Function<? super String, ? extends Optional<String>> c) {
        return simple(c, SimpleAuthenticationDetailsProvider::builder);
    }

    @SuppressWarnings("checkstyle:linelength")
    static Optional<Supplier<SimpleAuthenticationDetailsProvider>> simple(Function<? super String, ? extends Optional<String>> c,
                                                                          Supplier<? extends SimpleAuthenticationDetailsProviderBuilder> bs) {
        return simple(c, bs, b -> b::build);
    }

    @SuppressWarnings("checkstyle:linelength")
    static Optional<Supplier<SimpleAuthenticationDetailsProvider>> simple(Function<? super String, ? extends Optional<String>> c,
                                                                          Supplier<? extends SimpleAuthenticationDetailsProviderBuilder> bs,
                                                                          Function<? super SimpleAuthenticationDetailsProviderBuilder, ? extends Supplier<SimpleAuthenticationDetailsProvider>> f) {
        return c.apply("oci.auth.fingerprint")
            .flatMap(fingerprint -> c.apply("oci.auth.region")
                     .flatMap(region -> c.apply("oci.auth.tenant-id")
                              .flatMap(tenantId -> c.apply("oci.auth.user-id")
                                       .map(userId -> {
                                               var b = bs.get();
                                               b.fingerprint(fingerprint);
                                               b.region(Region.valueOf(region));
                                               b.tenantId(tenantId);
                                               b.userId(userId);
                                               c.apply("oci.auth.passphrase").ifPresent(b::passPhrase);
                                               c.apply("oci.auth.private-key")
                                                   .ifPresentOrElse(pk -> b.privateKeySupplier(new StringPrivateKeySupplier(pk)),
                                                                    () -> b.privateKeySupplier(new SimplePrivateKeySupplier(c.apply("oci.auth.private-key-path")
                                                                                                                            .orElse(DEFAULT_OCI_AUTH_PRIVATE_KEY_PATH))));
                                               return f.apply(b);
                                           }))));
    }

    static Optional<Supplier<ConfigFileAuthenticationDetailsProvider>> configFile(Function<? super String, ? extends Optional<String>> c) {
        return
            configFile(c.apply("oci.config.path").orElse(null),
                       c.apply("oci.config.profile").orElse(DEFAULT_OCI_CONFIG_PROFILE));
    }

    static Optional<Supplier<ConfigFileAuthenticationDetailsProvider>> configFile() {
        return configFile((String) null);
    }

    static Optional<Supplier<ConfigFileAuthenticationDetailsProvider>> configFile(String ociConfigPath) {
        return configFile(ociConfigPath, DEFAULT_OCI_CONFIG_PROFILE);
    }

    static Optional<Supplier<ConfigFileAuthenticationDetailsProvider>> configFile(String ociConfigPath,
                                                                                  String ociConfigProfile) {
        if (ociConfigProfile == null) {
            ociConfigProfile = DEFAULT_OCI_CONFIG_PROFILE;
        }
        ConfigFileAuthenticationDetailsProvider adp;
        try {
            if (ociConfigPath == null) {
                adp = new ConfigFileAuthenticationDetailsProvider(ociConfigProfile);
            } else {
                adp = new ConfigFileAuthenticationDetailsProvider(ociConfigPath, ociConfigProfile);
            }
        } catch (FileNotFoundException | NoSuchFileException e) {
            return Optional.empty();
        } catch (IOException e) {
            // The underlying ConfigFileReader that does the real work does not throw a FileNotFoundException (as it
            // probably should) when it cannot find the configuration file. To distinguish this "ordinary" IOException
            // from other IOExceptions, we therefore have no choice but to parse the error message. See
            // https://github.com/oracle/oci-java-sdk/blob/v3.23.0/bmc-common/src/main/java/com/oracle/bmc/ConfigFileReader.java#L91-L95.
            String message = e.getMessage();
            if (message != null
                && message.startsWith("Can't load the default config from ")
                && message.endsWith(" because it does not exist or it is not a file.")) {
                return Optional.empty();
            }
            // It's not a "file not found" case; it's some other exception.
            throw new UncheckedIOException(message, e);
        }
        return Optional.of(() -> adp);
    }

    @SuppressWarnings("checkstyle:linelength")
    static Optional<Supplier<InstancePrincipalsAuthenticationDetailsProvider>> instancePrincipals(Function<? super String, ? extends Optional<String>> c) {
        return instancePrincipals(c, InstancePrincipalsAuthenticationDetailsProvider::builder);
    }

    @SuppressWarnings("checkstyle:linelength")
    static Optional<Supplier<InstancePrincipalsAuthenticationDetailsProvider>> instancePrincipals(Function<? super String, ? extends Optional<String>> c,
                                                                                                  Supplier<? extends InstancePrincipalsAuthenticationDetailsProviderBuilder> bs) {
        return instancePrincipals(c, bs, b -> b::build);
    }

    @SuppressWarnings("checkstyle:linelength")
    static Optional<Supplier<InstancePrincipalsAuthenticationDetailsProvider>> instancePrincipals(Function<? super String, ? extends Optional<String>> c,
                                                                                                  Supplier<? extends InstancePrincipalsAuthenticationDetailsProviderBuilder> bs,
                                                                                                  Function<? super InstancePrincipalsAuthenticationDetailsProviderBuilder, ? extends Supplier<InstancePrincipalsAuthenticationDetailsProvider>> f) {
        int timeoutPositiveMillis = DEFAULT_OCI_IMDS_TIMEOUT_MILLIS;
        try {
            timeoutPositiveMillis =
                Math.max(0, c.apply("oci.imds.timeout.milliseconds").map(Integer::valueOf).orElse(DEFAULT_OCI_IMDS_TIMEOUT_MILLIS));
        } catch (IllegalArgumentException conversionException) {
        }
        return instancePrincipals(timeoutPositiveMillis, bs, f);
    }

    static Optional<Supplier<InstancePrincipalsAuthenticationDetailsProvider>> instancePrincipals() {
        return instancePrincipals(DEFAULT_OCI_IMDS_TIMEOUT_MILLIS);
    }

    static Optional<Supplier<InstancePrincipalsAuthenticationDetailsProvider>> instancePrincipals(int timeoutPositiveMillis) {
        return instancePrincipals(timeoutPositiveMillis, InstancePrincipalsAuthenticationDetailsProvider::builder);
    }

    @SuppressWarnings("checkstyle:linelength")
    static Optional<Supplier<InstancePrincipalsAuthenticationDetailsProvider>> instancePrincipals(int timeoutPositiveMillis,
                                                                                                  Supplier<? extends InstancePrincipalsAuthenticationDetailsProviderBuilder> bs) {
        return instancePrincipals(timeoutPositiveMillis, bs, b -> b::build);
    }

    @SuppressWarnings("checkstyle:linelength")
    static Optional<Supplier<InstancePrincipalsAuthenticationDetailsProvider>> instancePrincipals(int timeoutPositiveMillis,
                                                                                                  Supplier<? extends InstancePrincipalsAuthenticationDetailsProviderBuilder> bs,
                                                                                                  Function<? super InstancePrincipalsAuthenticationDetailsProviderBuilder, ? extends Supplier<InstancePrincipalsAuthenticationDetailsProvider>> f) {
        var b = bs.get();
        try {
            if (InetAddress.getByName(URI.create(b.getMetadataBaseUrl()).getHost()).isReachable(timeoutPositiveMillis)) {
                return Optional.of(f.apply(b));
            }
        } catch (ConnectException e) {
        } catch (IOException e) {
            throw new UncheckedIOException(e.getMessage(), e);
        }
        return Optional.empty();
    }

    static Optional<Supplier<ResourcePrincipalAuthenticationDetailsProvider>> resourcePrincipal() {
        return resourcePrincipal(ResourcePrincipalAuthenticationDetailsProvider::builder);
    }

    @SuppressWarnings("checkstyle:linelength")
    static Optional<Supplier<ResourcePrincipalAuthenticationDetailsProvider>> resourcePrincipal(Supplier<? extends ResourcePrincipalAuthenticationDetailsProviderBuilder> bs) {
        return resourcePrincipal(bs, b -> b::build);
    }

    @SuppressWarnings("checkstyle:linelength")
    static Optional<Supplier<ResourcePrincipalAuthenticationDetailsProvider>> resourcePrincipal(Supplier<? extends ResourcePrincipalAuthenticationDetailsProviderBuilder> bs,
                                                                                                Function<? super ResourcePrincipalAuthenticationDetailsProviderBuilder, ? extends Supplier<ResourcePrincipalAuthenticationDetailsProvider>> f) {
        return Optional.ofNullable(System.getenv("OCI_RESOURCE_PRINCIPAL_VERSION") == null ? null : f.apply(bs.get()));
    }

    @SuppressWarnings("checkstyle:linelength")
    static Supplier<? extends BasicAuthenticationDetailsProvider> adpSupplier(Function<? super String, ? extends Optional<String>> c) {
        return
            adpSupplier(c.apply("oci.auth-strategies")
                        .or(() -> c.apply("oci.auth-strategy"))
                        .or(() -> Optional.of("auto"))
                        .stream()
                        .flatMap(s -> Stream.of(COMMA_SPLITTER.split(s, 0)))
                        .flatMap(s -> switch (s.trim().toLowerCase()) {
                            case "auto" -> Stream.of(simple(c),
                                                     configFile(c),
                                                     instancePrincipals(c),
                                                     resourcePrincipal());
                            case "config", "simple" -> Stream.of(simple(c));
                            case "config-file" -> Stream.of(configFile(c));
                            case "instance-principals" -> Stream.of(instancePrincipals(c));
                            case "resource-principal" -> Stream.of(resourcePrincipal());
                            default -> throw new java.util.NoSuchElementException();
                            }));
    }

    @SuppressWarnings("checkstyle:linelength")
    static Supplier<? extends BasicAuthenticationDetailsProvider> adpSupplier(Optional<? extends Supplier<? extends BasicAuthenticationDetailsProvider>> o0,
                                                                              Optional<? extends Supplier<? extends BasicAuthenticationDetailsProvider>> o1) {
        return adpSupplier(Stream.of(o0, o1));
    }

    @SuppressWarnings("checkstyle:linelength")
    static Supplier<? extends BasicAuthenticationDetailsProvider> adpSupplier(Optional<? extends Supplier<? extends BasicAuthenticationDetailsProvider>> o0,
                                                                              Optional<? extends Supplier<? extends BasicAuthenticationDetailsProvider>> o1,
                                                                              Optional<? extends Supplier<? extends BasicAuthenticationDetailsProvider>> o2) {
        return adpSupplier(Stream.of(o0, o1, o2));
    }

    @SuppressWarnings("checkstyle:linelength")
    static Supplier<? extends BasicAuthenticationDetailsProvider> adpSupplier(Optional<? extends Supplier<? extends BasicAuthenticationDetailsProvider>> o0,
                                                                              Optional<? extends Supplier<? extends BasicAuthenticationDetailsProvider>> o1,
                                                                              Optional<? extends Supplier<? extends BasicAuthenticationDetailsProvider>> o2,
                                                                              Optional<? extends Supplier<? extends BasicAuthenticationDetailsProvider>> o3) {
        return adpSupplier(Stream.of(o0, o1, o2, o3));
    }

    @SuppressWarnings("checkstyle:linelength")
    static Supplier<? extends BasicAuthenticationDetailsProvider> adpSupplier(Collection<? extends Optional<? extends Supplier<? extends BasicAuthenticationDetailsProvider>>> c) {
        return adpSupplier(c.stream());
    }

    @SuppressWarnings("checkstyle:linelength")
    static Supplier<? extends BasicAuthenticationDetailsProvider> adpSupplier(Stream<? extends Optional<? extends Supplier<? extends BasicAuthenticationDetailsProvider>>> s) {
        return s
            .flatMap(Optional::stream)
            .findFirst()
            .orElseThrow();
    }

}
