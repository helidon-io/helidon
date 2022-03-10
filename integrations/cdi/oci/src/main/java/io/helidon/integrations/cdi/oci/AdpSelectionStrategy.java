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
package io.helidon.integrations.cdi.oci;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.net.ConnectException;
import java.net.InetAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.logging.Level;
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


    CONFIG(SimpleAuthenticationDetailsProvider.class,
           SimpleAuthenticationDetailsProviderBuilder.class) {

        @Override
        boolean isAvailable(Selector selector, Config config, Annotation[] qualifiersArray) {
            // See
            // https://docs.oracle.com/en-us/iaas/tools/java/latest/com/oracle/bmc/auth/SimpleAuthenticationDetailsProvider.SimpleAuthenticationDetailsProviderBuilder.html#method.summary
            //
            // ".auth." keys are for backwards compatibility with a
            // prior OCI-related extension.
            return
                super.isAvailable(selector, config, qualifiersArray)
                && (config.getOptionalValue("oci.config.fingerprint", String.class).isPresent()
                    || config.getOptionalValue("oci.auth.fingerprint", String.class).isPresent())
                && config.getOptionalValue("oci.config.region", Region.class).isPresent()
                && (config.getOptionalValue("oci.config.tenantId", String.class).isPresent()
                    || config.getOptionalValue("oci.config.tenancy", String.class).isPresent()
                    || config.getOptionalValue("oci.auth.tenancy", String.class).isPresent())
                && (config.getOptionalValue("oci.config.userId", String.class).isPresent()
                    || config.getOptionalValue("oci.config.user", String.class).isPresent()
                    || config.getOptionalValue("oci.auth.user", String.class).isPresent());
        }

        @Override
        AbstractAuthenticationDetailsProvider produce(Selector selector,
                                                      Config config,
                                                      Annotation[] qualifiersArray) {
            return selector.select(SimpleAuthenticationDetailsProviderBuilder.class, qualifiersArray).get().build();
        }

        @Override
        @SuppressWarnings("checkstyle:LineLength")
        SimpleAuthenticationDetailsProviderBuilder produceBuilder(Selector selector,
                                                                  Config c,
                                                                  Annotation[] qualifiersArray) {
            SimpleAuthenticationDetailsProviderBuilder b = SimpleAuthenticationDetailsProvider.builder();
            // See
            // https://docs.oracle.com/en-us/iaas/tools/java/latest/com/oracle/bmc/auth/SimpleAuthenticationDetailsProvider.SimpleAuthenticationDetailsProviderBuilder.html#method.summary
            //
            // Some fallback logic is for backwards compatibility with
            // a prior OCI-related extension.
            c.getOptionalValue("oci.config.fingerprint", String.class)
                .or(() -> c.getOptionalValue("oci.auth.fingerprint", String.class))
                .ifPresent(b::fingerprint);
            c.getOptionalValue("oci.config.passPhrase", String.class)
                .or(() -> c.getOptionalValue("oci.config.passPhraseCharacters", String.class))
                .or(() -> c.getOptionalValue("oci.auth.passPhraseCharacters", String.class))
                .ifPresent(b::passPhrase);
            c.getOptionalValue("oci.config.privateKey", String.class)
                .or(() -> c.getOptionalValue("oci.auth.privateKey", String.class))
                .ifPresentOrElse(pk -> b.privateKeySupplier(new StringPrivateKeySupplier(pk)),
                                 () -> b.privateKeySupplier(new SimplePrivateKeySupplier(c.getOptionalValue("oci.config.privateKeyPath", String.class)
                                                                                         .orElse(c.getOptionalValue("oci.auth.keyFile", String.class)
                                                                                                 .orElse(Paths.get(System.getProperty("user.home"), ".oci", "oci_api_key.pem")
                                                                                                         .toString())))));
            c.getOptionalValue("oci.config.region", Region.class)
                .ifPresent(b::region);
            c.getOptionalValue("oci.config.tenantId", String.class)
                .or(() -> c.getOptionalValue("oci.config.tenancy", String.class))
                .or(() -> c.getOptionalValue("oci.auth.tenancy", String.class))
                .ifPresent(b::tenantId);
            c.getOptionalValue("oci.config.userId", String.class)
                .or(() -> c.getOptionalValue("oci.config.user", String.class))
                .or(() -> c.getOptionalValue("oci.auth.user", String.class))
                .ifPresent(b::userId);
            // Remember, caller will have to arrange for further
            // customization.
            return b;
        }

        private void privateKey(SimpleAuthenticationDetailsProviderBuilder builder, String privateKey) {
            builder.privateKeySupplier(new StringPrivateKeySupplier(privateKey));
        }

        private void privateKey(SimpleAuthenticationDetailsProviderBuilder builder, Path privateKey) {
            builder.privateKeySupplier(new SimplePrivateKeySupplier(privateKey.toString()));
        }
    },

    CONFIG_FILE(ConfigFileAuthenticationDetailsProvider.class) {

        @Override
        boolean isAvailable(Selector selector, Config config, Annotation[] qualifiersArray) {
            if (super.isAvailable(selector, config, qualifiersArray)) {
                try {
                    this.select(selector, config, qualifiersArray);
                } catch (UncheckedIOException uncheckedIoException) {
                    if (uncheckedIoException.getCause() instanceof FileNotFoundException) {
                        return false;
                    }
                    throw uncheckedIoException;
                }
                return true;
            }
            return false;
        }

        @Override
        Object produceBuilder(Selector selector, Annotation[] qualifiersArray) {
            throw new UnsupportedOperationException();
        }

        @Override
        Object produceBuilder(Selector selector, Config config, Annotation[] qualifiersArray) {
            throw new UnsupportedOperationException();
        }

        @Override
        AbstractAuthenticationDetailsProvider produce(Selector selector,
                                                      Config config,
                                                      Annotation[] qualifiersArray) {
            return
                this.produce(config.getOptionalValue("oci.config.path", String.class).orElse(null),
                             config.getOptionalValue("oci.auth.profile", String.class).orElse("DEFAULT"));
        }

        private AbstractAuthenticationDetailsProvider produce(String ociConfigPath, String ociAuthProfile) {
            try {
                if (ociConfigPath == null) {
                    return new ConfigFileAuthenticationDetailsProvider(ociAuthProfile);
                } else {
                    return new ConfigFileAuthenticationDetailsProvider(ociConfigPath, ociAuthProfile);
                }
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

    INSTANCE_PRINCIPALS(InstancePrincipalsAuthenticationDetailsProvider.class,
                        InstancePrincipalsAuthenticationDetailsProviderBuilder.class) {

        @Override
        boolean isAvailable(Selector selector, Config config, Annotation[] qualifiersArray) {
            if (super.isAvailable(selector, config, qualifiersArray)) {
                String ociImdsHostname = config.getOptionalValue("oci.imds.hostname", String.class).orElse("169.254.169.254");
                int ociImdsTimeoutMillis =
                    config.getOptionalValue("oci.imds.timeout.milliseconds", Integer.class).orElse(Integer.valueOf(100));
                try {
                    return InetAddress.getByName(ociImdsHostname).isReachable(ociImdsTimeoutMillis);
                } catch (ConnectException connectException) {
                    return false;
                } catch (IOException ioException) {
                    throw new UncheckedIOException(ioException.getMessage(), ioException);
                }
            }
            return false;
        }

        @Override
        InstancePrincipalsAuthenticationDetailsProviderBuilder produceBuilder(Selector selector,
                                                                              Annotation[] qualifiersArray) {
            return this.produceBuilder(selector, null, qualifiersArray); // no need to find Config; it's not used
        }

        @Override
        InstancePrincipalsAuthenticationDetailsProviderBuilder produceBuilder(Selector selector,
                                                                              Config config,
                                                                              Annotation[] qualifiersArray) {
            InstancePrincipalsAuthenticationDetailsProviderBuilder builder =
                InstancePrincipalsAuthenticationDetailsProvider.builder();
            // Remember, caller will have to arrange for further
            // customization.
            return builder;
        }

        @Override
        AbstractAuthenticationDetailsProvider produce(Selector selector,
                                                      Config config,
                                                      Annotation[] qualifiersArray) {
            return
                selector.select(InstancePrincipalsAuthenticationDetailsProviderBuilder.class, qualifiersArray).get().build();
        }
    },

    RESOURCE_PRINCIPAL(ResourcePrincipalAuthenticationDetailsProvider.class,
                       ResourcePrincipalAuthenticationDetailsProviderBuilder.class) {

        @Override
        boolean isAvailable(Selector selector, Config config, Annotation[] qualifiersArray) {
            return
                super.isAvailable(selector, config, qualifiersArray)
                // https://github.com/oracle/oci-java-sdk/blob/v2.19.0/bmc-common/src/main/java/com/oracle/bmc/auth/ResourcePrincipalAuthenticationDetailsProvider.java#L246-L251
                && System.getenv("OCI_RESOURCE_PRINCIPAL_VERSION") != null;
        }

        @Override
        ResourcePrincipalAuthenticationDetailsProviderBuilder produceBuilder(Selector selector,
                                                                             Annotation[] qualifiersArray) {
            return this.produceBuilder(selector, null, qualifiersArray); // no need to find Config; it's not used
        }

        @Override
        ResourcePrincipalAuthenticationDetailsProviderBuilder produceBuilder(Selector selector,
                                                                             Config config,
                                                                             Annotation[] qualifiersArray) {
            ResourcePrincipalAuthenticationDetailsProviderBuilder builder =
                ResourcePrincipalAuthenticationDetailsProvider.builder();
            // Remember, caller will have to arrange for further
            // customization.
            return builder;
        }

        @Override
        AbstractAuthenticationDetailsProvider produce(Selector selector,
                                                      Config config,
                                                      Annotation[] qualifiersArray) {
            return
                selector.select(ResourcePrincipalAuthenticationDetailsProviderBuilder.class, qualifiersArray).get().build();
        }
    },

    AUTO(AbstractAuthenticationDetailsProvider.class, true) {

        private final Logger logger = Logger.getLogger(this.getClass().getName(),
                                                       OciExtension.class.getName() + "Messages");

        @Override
        Object produceBuilder(Selector selector, Config config, Annotation[] qualifiersArray) {
            throw new UnsupportedOperationException();
        }

        @Override
        @SuppressWarnings("fallthrough") // note
        AbstractAuthenticationDetailsProvider produce(Selector selector,
                                                      Config config,
                                                      Annotation[] qualifiersArray) {
            Collection<? extends AdpSelectionStrategy> strategies =
                concreteStrategies(config.getOptionalValue("oci.config.strategies", String[].class)
                                   .or(() -> config.getOptionalValue("oci.config.strategy", String[].class))
                                   .orElse(null));
            switch (strategies.size()) {
            case 1:
                AdpSelectionStrategy strategy = strategies.iterator().next();
                if (strategy != this) {
                    // No availability check on purpose.  We have only
                    // one strategy.  It is not itself AUTO so there's
                    // no fallback, so we don't try to help out in any
                    // way.
                    if (logger.isLoggable(Level.CONFIG)) {
                        logger.logp(Level.CONFIG,
                                    this.getClass().getName(),
                                    "produce",
                                    "usingStrategy",
                                    new Object[] {strategy, strategy.configName(), strategy.type().getTypeName()});
                    }
                    return strategy.select(selector, config, qualifiersArray);
                } else {
                    // (Edge case.) Somehow the sole strategy was us
                    // (AUTO), so that means cycle through the
                    // concrete ones.
                    strategies = concreteStrategies();
                }
                // fall-through on purpose
            default:
                Iterator<? extends AdpSelectionStrategy> i = strategies.iterator();
                while (i.hasNext()) {
                    AdpSelectionStrategy s = i.next();
                    if (s != this && (!i.hasNext() || s.isAvailable(selector, config, qualifiersArray))) {
                        if (logger.isLoggable(Level.CONFIG)) {
                            logger.logp(Level.CONFIG,
                                        this.getClass().getName(),
                                        "produce",
                                        "usingStrategy",
                                        new Object[] {s, s.configName(), s.type().getTypeName()});
                        }
                        return s.select(selector, config, qualifiersArray);
                    } else if (logger.isLoggable(Level.FINE)) {
                        logger.logp(Level.FINE,
                                    this.getClass().getName(),
                                    "produce",
                                    "strategyUnavailable",
                                    new Object[] {s, s.configName(), s.type().getTypeName()});
                    }
                }
                break;
            }
            throw new NoSuchElementException();
        }
    };


    /*
     * Static fields.
     */


    private static final Logger LOGGER = Logger.getLogger(AdpSelectionStrategy.class.getName(),
                                                          AdpSelectionStrategy.class.getName() + "Messages");

    private static final Collection<AdpSelectionStrategy> CONCRETE_STRATEGIES;

    private static final Set<Class<?>> BUILDER_CLASSES;

    static {
        EnumSet<AdpSelectionStrategy> set = EnumSet.allOf(AdpSelectionStrategy.class);
        set.removeIf(AdpSelectionStrategy::isAbstract);
        CONCRETE_STRATEGIES = Collections.unmodifiableCollection(set);
        Set<Class<?>> builderClasses = new HashSet<>(7);
        for (AdpSelectionStrategy s : set) {
            Type builderType = s.builderType();
            if (builderType instanceof Class) {
                builderClasses.add((Class<?>) builderType);
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

    AdpSelectionStrategy(Class<? extends AbstractAuthenticationDetailsProvider> type,
                         Class<?> builderType,
                         boolean isAbstract) {
        this.type = Objects.requireNonNull(type, "type");
        this.builderType = builderType;
        this.isAbstract = isAbstract;
    }


    /*
     * Instance methods.
     */


    String configName() {
        return this.name().replace('_', '-').toLowerCase();
    }

    Type type() {
        return this.type;
    }

    Type builderType() {
        return this.builderType;
    }

    final boolean isAbstract() {
        return this.isAbstract;
    }

    boolean isAvailable(Selector selector, Config config, Annotation[] qualifiersArray) {
        return !this.isAbstract();
    }

    AbstractAuthenticationDetailsProvider select(Selector selector,
                                                 Config config,
                                                 Annotation[] qualifiersArray) {
        return selector.select(this.type, qualifiersArray).get();
    }

    Object produceBuilder(Selector selector, Annotation[] qualifiersArray) {
        return this.produceBuilder(selector, selector.select(Config.class).get(), qualifiersArray);
    }

    abstract Object produceBuilder(Selector selector, Config config, Annotation[] qualifiersArray);

    AbstractAuthenticationDetailsProvider produce(Selector selector, Annotation[] qualifiersArray) {
        return this.produce(selector, selector.select(Config.class).get(), qualifiersArray);
    }

    abstract AbstractAuthenticationDetailsProvider produce(Selector selector,
                                                           Config config,
                                                           Annotation[] qualifiersArray);


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
            if (strategyString.isBlank()) {
                return AUTO;
            } else {
                return of(strategyString);
            }
        }
    }

    private static Collection<AdpSelectionStrategy> concreteStrategies() {
        return CONCRETE_STRATEGIES;
    }

    private static Collection<AdpSelectionStrategy> concreteStrategies(String[] strategyStringsArray) {
        Collection<AdpSelectionStrategy> strategies;
        AdpSelectionStrategy strategy;
        int length = strategyStringsArray == null ? 0 : strategyStringsArray.length;
        switch (length) {
        case 0:
            strategies = List.of();
            break;
        case 1:
            strategy = ofConfigString(strategyStringsArray[0]);
            if (strategy.isAbstract()) {
                strategies = List.of();
            } else {
                strategies = EnumSet.of(strategy);
            }
            break;
        default:
            Set<String> strategyStrings = new LinkedHashSet<>(Arrays.asList(strategyStringsArray));
            switch (strategyStrings.size()) {
            case 0:
                throw new AssertionError();
            case 1:
                strategy = ofConfigString(strategyStrings.iterator().next());
                if (strategy.isAbstract()) {
                    strategies = List.of();
                } else {
                    strategies = EnumSet.of(strategy);
                }
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

    interface Selector {

        default <T> Supplier<T> select(Class<T> c) {
            return select(c, null);
        }

        <T> Supplier<T> select(Class<T> c, Annotation[] as);

    }

    interface Config {

        <T> Optional<T> getOptionalValue(String propertyName, Class<T> propertyType);

    }

}
