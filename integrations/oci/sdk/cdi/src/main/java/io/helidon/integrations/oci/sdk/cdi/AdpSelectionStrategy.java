/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
package io.helidon.integrations.oci.sdk.cdi;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.annotation.Annotation;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.logging.Logger;

import com.oracle.bmc.Region;
import com.oracle.bmc.auth.AbstractAuthenticationDetailsProvider;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;
import com.oracle.bmc.auth.InstancePrincipalsAuthenticationDetailsProvider;
import com.oracle.bmc.auth.InstancePrincipalsAuthenticationDetailsProvider.InstancePrincipalsAuthenticationDetailsProviderBuilder;
import com.oracle.bmc.auth.ResourcePrincipalAuthenticationDetailsProvider;
import com.oracle.bmc.auth.ResourcePrincipalAuthenticationDetailsProvider.ResourcePrincipalAuthenticationDetailsProviderBuilder;
import com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider;
import com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider.SimpleAuthenticationDetailsProviderBuilder;
import com.oracle.bmc.auth.SimplePrivateKeySupplier;
import com.oracle.bmc.auth.StringPrivateKeySupplier;

import static com.oracle.bmc.auth.AbstractFederationClientAuthenticationDetailsProviderBuilder.METADATA_SERVICE_BASE_URL;

/**
 * A strategy for finding and building various well-known kinds of
 * {@link AbstractAuthenticationDetailsProvider} instances.
 */
enum AdpSelectionStrategy {


    //
    // ------------
    // PLEASE READ:
    // ------------
    //
    // The ordering of the constants in this enum is *extremely*
    // important! It governs the default "auto" case order
    // (EnumSets always traverse their members in declaration
    // order)! Do not reorder the constants unless you have a good
    // reason!
    //


    /*
     * Enum constants.
     */


    /**
     * An {@link AdpSelectionStrategy} that works with {@link
     * SimpleAuthenticationDetailsProvider} instances configured from
     * configuration.
     */
    CONFIG(SimpleAuthenticationDetailsProvider.class,
           SimpleAuthenticationDetailsProviderBuilder.class) {

        private static final String OCI_AUTH_FINGERPRINT = "oci.auth.fingerprint";

        private static final String OCI_AUTH_PASSPHRASE = "oci.auth.passphrase";

        private static final String OCI_AUTH_PRIVATE_KEY = "oci.auth.private-key";

        private static final String OCI_AUTH_REGION = "oci.auth.region";

        private static final String OCI_AUTH_TENANT_ID = "oci.auth.tenant-id";

        private static final String OCI_AUTH_USER_ID = "oci.auth.user-id";

        @Override // AdpSelectionStrategy
        boolean isAvailable(Selector selector, Config c, Annotation[] qualifiersArray) {
            // See
            // https://docs.oracle.com/en-us/iaas/tools/java/latest/com/oracle/bmc/auth/SimpleAuthenticationDetailsProvider.SimpleAuthenticationDetailsProviderBuilder.html#method.summary
            //
            // Some fallback logic is for backwards compatibility with
            // a prior OCI-related extension.
            return
                super.isAvailable(selector, c, qualifiersArray)
                && c.get(OCI_AUTH_FINGERPRINT, String.class).isPresent()
                && c.get(OCI_AUTH_REGION, String.class).isPresent()
                && (c.get(OCI_AUTH_TENANT_ID, String.class).isPresent() || c.get("oci.auth.tenancy", String.class).isPresent())
                && (c.get(OCI_AUTH_USER_ID, String.class).isPresent() || c.get("oci.auth.user", String.class).isPresent());
        }

        @Override // AdpSelectionStrategy
        @SuppressWarnings("checkstyle:LineLength")
        SimpleAuthenticationDetailsProviderBuilder produceBuilder(Selector selector, Config c, Annotation[] qualifiersArray) {
            SimpleAuthenticationDetailsProviderBuilder b = SimpleAuthenticationDetailsProvider.builder();
            // See
            // https://docs.oracle.com/en-us/iaas/tools/java/latest/com/oracle/bmc/auth/SimpleAuthenticationDetailsProvider.SimpleAuthenticationDetailsProviderBuilder.html#method.summary
            //
            // See
            // https://docs.oracle.com/en-us/iaas/Content/API/Concepts/apisigningkey.htm#apisigningkey_topic_How_to_Generate_an_API_Signing_Key_Mac_Linux__ol_t5v_lwz_zmb
            // for default private key file naming rationale used in a
            // fallback case below.
            //
            // Some fallback logic is for backwards compatibility with
            // a prior OCI-related extension.
            c.get(OCI_AUTH_FINGERPRINT, String.class)
                .ifPresent(b::fingerprint);
            c.get(OCI_AUTH_PASSPHRASE, String.class).or(() -> c.get(OCI_AUTH_PASSPHRASE + "Characters", String.class))
                .ifPresent(b::passPhrase);
            c.get(OCI_AUTH_PRIVATE_KEY, String.class).or(() -> c.get("oci.auth.privateKey", String.class))
                .ifPresentOrElse(pk -> b.privateKeySupplier((Supplier<InputStream>) new StringPrivateKeySupplier(pk)),
                                 () -> b.privateKeySupplier((Supplier<InputStream>) new SimplePrivateKeySupplier(c.get(OCI_AUTH_PRIVATE_KEY + "-path", String.class)
                                                                                                                 .orElse(c.get("oci.auth.keyFile", String.class)
                                                                                                                         .orElse(Paths.get(System.getProperty("user.home"), ".oci", "oci_api_key.pem").toString())))));
            c.get(OCI_AUTH_REGION, Region.class)
                .ifPresent(b::region);
            c.get(OCI_AUTH_TENANT_ID, String.class).or(() -> c.get("oci.auth.tenancy", String.class))
                .ifPresent(b::tenantId);
            c.get(OCI_AUTH_USER_ID, String.class).or(() -> c.get("oci.auth.user", String.class))
                .ifPresent(b::userId);
            return b;
        }

        @Override // AdpSelectionStrategy
        SimpleAuthenticationDetailsProvider produce(Selector selector, Config c, Annotation[] qualifiersArray) {
            return selector.select(SimpleAuthenticationDetailsProviderBuilder.class, qualifiersArray).get().build();
        }

    },

    /**
     * An {@link AdpSelectionStrategy} that works with {@link
     * ConfigFileAuthenticationDetailsProvider} instances.
     */
    CONFIG_FILE(ConfigFileAuthenticationDetailsProvider.class) {

        @Override // AdpSelectionStrategy
        boolean isAvailable(Selector selector, Config c, Annotation[] qualifiersArray) {
            if (super.isAvailable(selector, c, qualifiersArray)) {
                try {
                    this.get(selector, c, qualifiersArray);
                } catch (UncheckedIOException uncheckedIoException) {
                    Object cause = uncheckedIoException.getCause();
                    if (cause instanceof FileNotFoundException || cause instanceof NoSuchFileException) {
                        return false;
                    }
                    throw uncheckedIoException;
                }
                return true;
            }
            return false;
        }

        @Override // AdpSelectionStrategy
        Void produceBuilder(Selector selector, Config c, Annotation[] qualifiersArray) {
            throw new UnsupportedOperationException();
        }

        @Override // AdpSelectionStrategy
        ConfigFileAuthenticationDetailsProvider produce(Selector selector, Config c, Annotation[] qualifiersArray) {
            return
                this.produce(c.get("oci.config.path", String.class).orElse(null), // null on purpose; use OCI defaulting logic
                             c.get("oci.config.profile", String.class)
                             .orElse(c.get("oci.auth.profile", String.class)
                                     .orElse("DEFAULT")));
        }

        private ConfigFileAuthenticationDetailsProvider produce(String ociConfigPath, String ociAuthProfile) {
            try {
                if (ociConfigPath == null) {
                    // Don't get clever and try to use ~/.oci/config
                    // as a default value; there is OCI-managed logic
                    // to figure out the proper default; we want to
                    // make sure it's used
                    return new ConfigFileAuthenticationDetailsProvider(ociAuthProfile);
                } else {
                    return new ConfigFileAuthenticationDetailsProvider(ociConfigPath, ociAuthProfile);
                }
            } catch (FileNotFoundException fileNotFoundException) {
                throw new UncheckedIOException(fileNotFoundException.getMessage(), fileNotFoundException);
            } catch (NoSuchFileException noSuchFileException) {
                throw new UncheckedIOException(noSuchFileException.getMessage(), noSuchFileException);
            } catch (IOException ioException) {
                // The underlying ConfigFileReader that does the real
                // work does not throw a FileNotFoundException in this
                // case (as it probably should).  We have no choice
                // but to parse the error message.  See
                // https://github.com/oracle/oci-java-sdk/blob/2.19.0/bmc-common/src/main/java/com/oracle/bmc/ConfigFileReader.java#L94-L98.
                String message = ioException.getMessage();
                if (message != null
                    && message.startsWith("Can't load the default config from ")
                    && message.endsWith(" because it does not exist or it is not a file.")) {
                    throw new UncheckedIOException(message,
                                                   (IOException) new FileNotFoundException(message).initCause(ioException));
                }
                throw new UncheckedIOException(message, ioException);
            }
        }
    },

    /**
     * An {@link AdpSelectionStrategy} that works with {@link
     * InstancePrincipalsAuthenticationDetailsProvider} instances.
     */
    INSTANCE_PRINCIPALS(InstancePrincipalsAuthenticationDetailsProvider.class,
                        InstancePrincipalsAuthenticationDetailsProviderBuilder.class) {

        private final String defaultImdsHostname = URI.create(METADATA_SERVICE_BASE_URL).getHost();

        @Override // AdpSelectionStrategy
        boolean isAvailable(Selector selector, Config c, Annotation[] qualifiersArray) {
            if (super.isAvailable(selector, c, qualifiersArray)) {
                InetAddress imds = null;
                try {
                    imds = InetAddress.getByName(c.get("oci.imds.hostname", String.class).orElse(this.defaultImdsHostname));
                } catch (UnknownHostException unknownHostException) {
                    throw new UncheckedIOException(unknownHostException.getMessage(), unknownHostException);
                }
                int ociImdsTimeoutMillis = 0;
                try {
                    ociImdsTimeoutMillis =
                        Math.max(0, c.get("oci.imds.timeout.milliseconds", Integer.class).orElse(Integer.valueOf(100)));
                } catch (IllegalArgumentException conversionException) {
                    ociImdsTimeoutMillis = 100;
                }
                try {
                    return imds.isReachable(ociImdsTimeoutMillis);
                } catch (ConnectException connectException) {
                    return false;
                } catch (IOException ioException) {
                    throw new UncheckedIOException(ioException.getMessage(), ioException);
                }
            }
            return false;
        }

        @Override // AdpSelectionStrategy
        InstancePrincipalsAuthenticationDetailsProviderBuilder produceBuilder(Selector s, Config c, Annotation[] qs) {
            return InstancePrincipalsAuthenticationDetailsProvider.builder();
        }

        @Override // AdpSelectionStrategy
        InstancePrincipalsAuthenticationDetailsProvider produce(Selector selector, Config c, Annotation[] qualifiersArray) {
            return selector.select(InstancePrincipalsAuthenticationDetailsProviderBuilder.class, qualifiersArray).get().build();
        }
    },

    /**
     * An {@link AdpSelectionStrategy} that works with {@link
     * ResourcePrincipalAuthenticationDetailsProvider} instances.
     */
    RESOURCE_PRINCIPAL(ResourcePrincipalAuthenticationDetailsProvider.class,
                       ResourcePrincipalAuthenticationDetailsProviderBuilder.class) {

        @Override // AdpSelectionStrategy
        boolean isAvailable(Selector selector, Config c, Annotation[] qualifiersArray) {
            return
                super.isAvailable(selector, c, qualifiersArray)
                // https://github.com/oracle/oci-java-sdk/blob/v2.19.0/bmc-common/src/main/java/com/oracle/bmc/auth/ResourcePrincipalAuthenticationDetailsProvider.java#L246-L251
                && System.getenv("OCI_RESOURCE_PRINCIPAL_VERSION") != null;
        }

        @Override // AdpSelectionStrategy
        ResourcePrincipalAuthenticationDetailsProviderBuilder produceBuilder(Selector s, Config c, Annotation[] qualifiersArray) {
            return ResourcePrincipalAuthenticationDetailsProvider.builder();
        }

        @Override // AdpSelectionStrategy
        ResourcePrincipalAuthenticationDetailsProvider produce(Selector selector, Config c, Annotation[] qualifiersArray) {
            return selector.select(ResourcePrincipalAuthenticationDetailsProviderBuilder.class, qualifiersArray).get().build();
        }
    },

    /**
     * An {@linkplain #isAbstract() abstract} {@link
     * AdpSelectionStrategy} that selects the most appropriate
     * concrete {@link AdpSelectionStrategy} by consulting
     * configuration.
     */
    AUTO(AbstractAuthenticationDetailsProvider.class, true) {

        private final transient Logger logger = Logger.getLogger(this.getClass().getName());

        @Override // AdpSelectionStrategy
        Void produceBuilder(Selector selector, Config c, Annotation[] qualifiersArray) {
            throw new UnsupportedOperationException();
        }

        @Override // AdpSelectionStrategy
        AbstractAuthenticationDetailsProvider produce(Selector selector, Config c, Annotation[] qualifiersArray) {
            Collection<? extends AdpSelectionStrategy> strategies =
                concreteStrategies(c.get("oci.auth-strategies", String[].class)
                                   .or(() -> c.get("oci.auth-strategy", String[].class))
                                   .orElse(null));
            for (AdpSelectionStrategy s : strategies) {
                if (s == this) {
                    // concreteStrategies(String[]) bug
                    throw new IllegalStateException("concreteStrategies(String[]) returned " + this.name());
                } else if (s.isAvailable(selector, c, qualifiersArray)) {
                    logger.config(() -> "Using authentication strategy " + s.configName()
                                  + "; selected AbstractAuthenticationDetailsProvider " + s.type().getTypeName());
                    return s.get(selector, c, qualifiersArray);
                } else {
                    logger.fine(() -> "Skipping authentication strategy " + s.configName() + " because it is not available");
                }
            }
            throw new NoSuchElementException("No instances of "
                                             + AbstractAuthenticationDetailsProvider.class.getName()
                                             + " available for use");
        }

    };


    /*
     * Static fields.
     */


    private static final Collection<AdpSelectionStrategy> CONCRETE_STRATEGIES;

    private static final Set<Class<?>> BUILDER_CLASSES;

    static {
        EnumSet<AdpSelectionStrategy> set = EnumSet.allOf(AdpSelectionStrategy.class);
        set.removeIf(AdpSelectionStrategy::isAbstract);
        CONCRETE_STRATEGIES = Collections.unmodifiableCollection(set);
        Set<Class<?>> builderClasses = new HashSet<>(7);
        for (AdpSelectionStrategy s : set) {
            Class<?> builderType = s.builderType();
            if (builderType != null) {
                builderClasses.add(builderType);
            }
        }
        BUILDER_CLASSES = Collections.unmodifiableSet(builderClasses);
    }


    /*
     * Instance fields.
     */


    private final Class<? extends AbstractAuthenticationDetailsProvider> type;

    private final Class<?> builderType;

    private final boolean isAbstract;


    /*
     * Constructors.
     */


    AdpSelectionStrategy(Class<? extends AbstractAuthenticationDetailsProvider> type) {
        this(type, null, false);
    }

    AdpSelectionStrategy(Class<? extends AbstractAuthenticationDetailsProvider> type, boolean isAbstract) {
        this(type, null, isAbstract);
    }

    AdpSelectionStrategy(Class<? extends AbstractAuthenticationDetailsProvider> type, Class<?> builderType) {
        this(type, builderType, false);
    }

    AdpSelectionStrategy(Class<? extends AbstractAuthenticationDetailsProvider> type, Class<?> builderType, boolean isAbstract) {
        this.type = Objects.requireNonNull(type, "type");
        this.builderType = builderType;
        this.isAbstract = isAbstract;
    }


    /*
     * Instance methods.
     */


    final String configName() {
        return this.name().replace('_', '-').toLowerCase();
    }

    final Class<? extends AbstractAuthenticationDetailsProvider> type() {
        return this.type;
    }

    final Class<?> builderType() {
        return this.builderType;
    }

    final AbstractAuthenticationDetailsProvider get(Selector selector, Config c, Annotation[] qualifiersArray) {
        return selector.select(this.type(), qualifiersArray).get();
    }

    final boolean isAbstract() {
        return this.isAbstract;
    }

    boolean isAvailable(Selector selector, Config c, Annotation[] qualifiersArray) {
        return !this.isAbstract();
    }

    abstract Object produceBuilder(Selector selector, Config c, Annotation[] qualifiersArray);

    abstract AbstractAuthenticationDetailsProvider produce(Selector selector, Config c, Annotation[] qualifiersArray);


    /*
     * Static methods.
     */


    static Set<Class<?>> builderClasses() {
        return BUILDER_CLASSES;
    }

    private static AdpSelectionStrategy of(String name) {
        return valueOf(name.replace('-', '_').toUpperCase());
    }

    private static AdpSelectionStrategy ofConfigString(String strategyString) {
        if (strategyString == null) {
            return AUTO;
        } else {
            strategyString = strategyString.strip();
            return strategyString.isBlank() ? AUTO : of(strategyString);
        }
    }

    private static Collection<AdpSelectionStrategy> concreteStrategies() {
        return CONCRETE_STRATEGIES;
    }

    private static Collection<AdpSelectionStrategy> concreteStrategies(String[] strategyStringsArray) {
        Collection<AdpSelectionStrategy> strategies;
        AdpSelectionStrategy strategy;
        switch (strategyStringsArray == null ? 0 : strategyStringsArray.length) {
        case 0:
            strategies = List.of();
            break;
        case 1:
            strategy = ofConfigString(strategyStringsArray[0]);
            strategies = strategy.isAbstract() ? List.of() : EnumSet.of(strategy);
            break;
        default:
            Set<String> strategyStrings = new LinkedHashSet<>(Arrays.asList(strategyStringsArray));
            switch (strategyStrings.size()) {
            case 0:
                throw new AssertionError();
            case 1:
                strategy = ofConfigString(strategyStrings.iterator().next());
                strategies = strategy.isAbstract() ? List.of() : EnumSet.of(strategy);
                break;
            default:
                strategies = new ArrayList<>(strategyStrings.size());
                for (String s : strategyStrings) {
                    strategy = ofConfigString(s);
                    if (!strategy.isAbstract()) {
                        strategies.add(strategy);
                    }
                }
            }
            break;
        }
        if (strategies.isEmpty()) {
            return concreteStrategies();
        }
        return Collections.unmodifiableCollection(strategies);
    }


    /*
     * Inner and nested classes.
     */


    interface Selector {

        <T> Supplier<T> select(Class<T> type, Annotation... qualifiers);

    }

    interface Config {

        <T> Optional<T> get(String name, Class<T> type);

    }

}
