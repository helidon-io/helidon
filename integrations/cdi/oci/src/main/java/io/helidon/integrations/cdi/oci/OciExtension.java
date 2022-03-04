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
import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Type;
import java.net.ConnectException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.enterprise.event.Event;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.AmbiguousResolutionException;
import javax.enterprise.inject.CreationException;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.UnsatisfiedResolutionException;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.AfterDeploymentValidation;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.enterprise.inject.spi.ProcessInjectionPoint;
import javax.enterprise.util.TypeLiteral;
import javax.inject.Singleton;

import com.oracle.bmc.auth.AbstractAuthenticationDetailsProvider;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;
import com.oracle.bmc.auth.InstancePrincipalsAuthenticationDetailsProvider;
import com.oracle.bmc.auth.InstancePrincipalsAuthenticationDetailsProvider.InstancePrincipalsAuthenticationDetailsProviderBuilder;
import com.oracle.bmc.auth.ResourcePrincipalAuthenticationDetailsProvider;
import com.oracle.bmc.auth.ResourcePrincipalAuthenticationDetailsProvider.ResourcePrincipalAuthenticationDetailsProviderBuilder;
import com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider;
import com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider.SimpleAuthenticationDetailsProviderBuilder;
import com.oracle.bmc.common.ClientBuilderBase;
import org.eclipse.microprofile.config.Config;

import static java.lang.invoke.MethodHandles.publicLookup;
import static java.lang.invoke.MethodType.methodType;

/**
 * An {@link Extension} that allows injection of any client from the
 * <a
 * href="https://docs.oracle.com/en-us/iaas/tools/java/latest/index.html"
 * target="_top">Oracle Cloud Infrastructure Java SDK</a>.
 *
 * <p>For any service, {@code com.oracle.bmc.example.Example}, this
 * {@linkplain Extension extension} enables the {@linkplain
 * javax.inject.Inject injection} in a CDI 2.0 application of the
 * following types:</p>
 *
 * <ul>
 *
 * <li>{@code com.oracle.bmc.example.Example}</li>
 *
 * <li>{@code com.oracle.bmc.example.ExampleAsync}</li>
 *
 * <li>{@code com.oracle.bmc.example.ExampleAsyncClient}</li>
 *
 * <li>{@code com.oracle.bmc.example.ExampleAsyncClient.Builder}</li>
 *
 * <li>{@code com.oracle.bmc.example.ExampleClient}</li>
 *
 * <li>{@code com.oracle.bmc.example.ExampleClientBuilder}</li>
 *
 * </ul>
 *
 * <p>Additionally, this class enables the {@linkplain
 * javax.inject.Inject injection} of an appropriate {@link
 * AbstractAuthenticationDetailsProvider}, which is a foundational
 * construct needed to use any higher-level service from the Oracle
 * Cloud Infrastructure Java SDK.</p>
 *
 * <p>This {@link Extension extension} deliberately does <em>not</em>
 * provide such features for any class residing under the following
 * packages:</p>
 *
 * <ul>
 *
 * <li>{@code com.oracle.bmc.circuitbreaker}</li>
 *
 * </ul>
 *
 * <p>In all cases, user-supplied beans will be preferred over any
 * otherwise installed by this {@linkplain Extension extension}.</p>
 */
public final class OciExtension implements Extension {


    /*
     * Static fields.
     */


    private static final TypeLiteral<Event<Object>> EVENT_OBJECT_TYPE_LITERAL = new TypeLiteral<Event<Object>>() {};

    // "auth" is in here even though this class does work with
    // com.oracle.bmc.auth.AbstractAuthenticationDetailsProvider
    // instances.  That's because this list helps constrain the
    // higher-level subpackages (ailanguage, objectstorage,
    // loggingingestion, etc.) within the OCI SDK that adhere to the
    // general pattern of end-user use
    // (AbstractAuthenticationDetailsProvider instances are not a
    // member of this set of subpackages).
    private static final Set<String> PACKAGE_FRAGMENT_DENY_LIST = Set.of("auth", "circuitbreaker");

    private static final Pattern PACKAGE_PATTERN = Pattern.compile("^com\\.oracle\\.bmc\\.(.+)\\.([^.]+)$");

    private static final Lookup PUBLIC_LOOKUP = publicLookup();


    /*
     * Instance fields.
     */


    private final Collection<InjectionPoint> injectionPoints;

    private final Set<TypeAndQualifiers> beanTypesAndQualifiers;


    /*
     * Constructors.
     */


    /**
     * Creates a new {@link OciExtension}.
     *
     * @deprecated For {@link java.util.ServiceLoader} use only.
     */
    @Deprecated // for java.util.ServiceLoader use only
    public OciExtension() {
        super();
        this.injectionPoints = new ArrayList<>();
        this.beanTypesAndQualifiers = new HashSet<>();
    }


    /*
     * Container lifecycle observer methods.
     */


    /**
     * A <a
     * href="https://jakarta.ee/specifications/cdi/2.0/cdi-spec-2.0.html#init_events"
     * target="_top">container lifecycle observer method</a> called by
     * the CDI container during the <a
     * href="https://jakarta.ee/specifications/cdi/2.0/cdi-spec-2.0.html#initialization"
     * target="_top">application initialization lifecycle</a> that
     * examines the {@linkplain
     * ProcessInjectionPoint#getInjectionPoint() event's associated
     * <code>InjectionPoint</code>} to see if it is an OCI Java SDK
     * class that fits the general OCI usage pattern as described in
     * this extension's class documentation, and, if so, ensures that
     * relevant CDI beans will be created if necessary to satisfy it.
     *
     * @param event the {@link ProcessInjectionPoint} event; will not
     * be {@code null}
     */
    private void processInjectionPoint(@Observes ProcessInjectionPoint<?, ?> event) {
        InjectionPoint ip = event.getInjectionPoint();
        Type baseType = ip.getAnnotated().getBaseType();
        if (baseType instanceof Class) { // none of the OCI end-user classes is parameterized
            Matcher m = PACKAGE_PATTERN.matcher(baseType.getTypeName());
            if (m.matches() && !PACKAGE_FRAGMENT_DENY_LIST.contains(m.group(1))) {
                this.injectionPoints.add(ip);
            }
        }
    }

    /**
     * A <a
     * href="https://jakarta.ee/specifications/cdi/2.0/cdi-spec-2.0.html#init_events"
     * target="_top">container lifecycle observer method</a> called by
     * the CDI container during the <a
     * href="https://jakarta.ee/specifications/cdi/2.0/cdi-spec-2.0.html#initialization"
     * target="_top">application initialization lifecycle</a> that
     * adds relevant CDI beans to enable injection of OCI Java SDK objects.
     *
     * @param event the {@link AfterBeanDiscovery} event; will not
     * be {@code null}
     *
     * @param bm the {@link BeanManager} being used by the container;
     * will not be {@code null}
     */
    private void afterBeanDiscovery(@Observes AfterBeanDiscovery event, BeanManager bm) {
        for (InjectionPoint ip : this.injectionPoints) {
            Set<Annotation> qualifiers = ip.getQualifiers();
            Annotation[] qualifiersArray = qualifiers.toArray(new Annotation[0]);
            TypeAndQualifiers adpTaq = new TypeAndQualifiers(AbstractAuthenticationDetailsProvider.class, qualifiersArray);
            if (!this.beanTypesAndQualifiers.contains(adpTaq)) {
                installAdps(event, bm, qualifiers);
                this.beanTypesAndQualifiers.add(adpTaq);
            }
            Class<?> inputClass = (Class<?>) ip.getAnnotated().getBaseType();
            TypeAndQualifiers inputTaq = new TypeAndQualifiers(inputClass, qualifiersArray);
            if (this.supply(inputTaq, bm, event::addDefinitionError)) {
                String input = inputClass.getName();
                // input could be any of:
                //
                //   com.oracle.bmc.example.Example
                //   com.oracle.bmc.example.ExampleAsync
                //   com.oracle.bmc.example.ExampleAsyncClient
                //   com.oracle.bmc.example.ExampleAsyncClient$Builder
                //   com.oracle.bmc.example.ExampleClient
                //   com.oracle.bmc.example.ExampleClient$Builder
                //
                // First, is there a builder (recalling that maybe
                // input is a builder itself)? Maybe the user supplied
                // one, in which case we should use hers. Otherwise
                // install our own.
                TypeAndQualifiers builderTaq;
                Class<?> builderClass;
                String builder = builder(input);
                if (builder.equals(input)) {
                    builderClass = inputClass;
                    builderTaq = inputTaq;
                } else {
                    builderClass = loadClass(builder, event::addDefinitionError);
                    if (builderClass == null) {
                        continue;
                    }
                    builderTaq = new TypeAndQualifiers(builderClass, qualifiersArray);
                }
                // (No matter what else may happen, we'll need to
                // load, e.g.,
                // com.oracle.bmc.example.ExampleAsyncClient and/or
                // com.oracle.bmc.example.ExampleClient.)
                //
                // (Among other reasons, this is because if we have to
                // synthesize a builder (by invoking the static
                // builder() method on a client class, e.g,
                // com.oracle.bmc.example.ExampleAsyncClient or
                // com.oracle.bmc.example.ExampleClient), we need to
                // have loaded the class already.)
                TypeAndQualifiers clientTaq;
                Class<?> clientClass;
                String client = client(input);
                if (client.equals(input)) {
                    clientClass = inputClass;
                    clientTaq = inputTaq;
                } else {
                    clientClass = loadClass(client, event::addDefinitionError);
                    if (clientClass == null) {
                        continue;
                    }
                    clientTaq = new TypeAndQualifiers(clientClass, qualifiersArray);
                }
                if (this.supply(builderTaq, bm, event::addDefinitionError)) {
                    // OK, we need to create:
                    //
                    //   com.oracle.bmc.example.ExampleClient$Builder
                    //
                    // or:
                    //
                    //   com.oracle.bmc.example.ExampleAsyncClient$Builder
                    //
                    // client will be one of:
                    //
                    // * com.oracle.bmc.example.ExampleClient
                    // * com.oracle.bmc.example.ExampleAsyncClient
                    //
                    // We will call its static builder() method.
                    MethodHandle builderMethod;
                    try {
                        builderMethod = PUBLIC_LOOKUP.findStatic(clientClass, "builder", methodType(builderClass));
                    } catch (ReflectiveOperationException reflectiveOperationException) {
                        event.addDefinitionError(reflectiveOperationException);
                        continue;
                    }
                    event.addBean()
                        .types(Set.of(builderClass))
                        .qualifiers(qualifiers)
                        .scope(Singleton.class)
                        .produceWith(i -> produceClientBuilder(i, builderMethod, builderClass, qualifiersArray));
                    this.beanTypesAndQualifiers.add(builderTaq);
                }
                if (builderTaq != inputTaq) {
                    // input was not a builder itself.  Also input was
                    // not supplied by the user.  We have already
                    // ensured that input's builder will be made. So
                    // now we can create input.
                    Set<Type> types;
                    if (clientTaq == inputTaq) {
                        // OK, the injection point is for
                        // ExampleClient (or ExampleAsyncClient).
                        // Let's make sure there's no corresponding
                        // user-supplied bean for Example (or
                        // ExampleAsync).
                        //
                        // Reassign inputClass to be, e.g., Example
                        // (or ExampleAsync).
                        inputClass = loadClass(thing(client), event::addDefinitionError);
                        if (inputClass == null) {
                            continue;
                        }
                        inputTaq = new TypeAndQualifiers(inputClass, qualifiersArray);
                        if (this.supply(inputTaq, bm, event::addDefinitionError)) {
                            // OK, we can install one synthetic bean
                            // (rather than two) to satisfy both
                            // Example/ExampleClient and
                            // ExampleAsync/ExampleAyncClient.
                            types = Set.of(clientClass, inputClass);
                        } else {
                            types = Set.of(clientClass);
                        }
                    } else if (isBuilder(input)) {
                        throw new AssertionError("input: " + input);
                    } else if (this.supply(clientTaq, bm, event::addDefinitionError)) {
                        types = Set.of(clientClass, inputClass);
                    } else {
                        types = Set.of(inputClass);
                    }
                    event.addBean()
                        .types(types)
                        .qualifiers(qualifiers)
                        .scope(Singleton.class)
                        .produceWith(i -> produceClient(i, builderClass))
                        .disposeWith(OciExtension::disposeClient);
                    for (Type type : types) {
                        if (type.equals(inputClass)) {
                            this.beanTypesAndQualifiers.add(inputTaq);
                        } else if (type.equals(clientClass)) {
                            this.beanTypesAndQualifiers.add(clientTaq);
                        } else {
                            throw new AssertionError("type: " + type.getTypeName());
                        }
                    }
                }
            }
        }
    }

    /**
     * A <a
     * href="https://jakarta.ee/specifications/cdi/2.0/cdi-spec-2.0.html#init_events"
     * target="_top">container lifecycle observer method</a> called by
     * the CDI container during the <a
     * href="https://jakarta.ee/specifications/cdi/2.0/cdi-spec-2.0.html#initialization"
     * target="_top">application initialization lifecycle</a> that
     * cleans up internal caches.
     *
     * @param event the {@link AfterDeploymentValidation} event; will
     * not be {@code null}
     */
    private void afterDeploymentValidation(@Observes AfterDeploymentValidation event) {
        // Cleanup
        this.beanTypesAndQualifiers.clear();
        this.injectionPoints.clear();
    }


    /*
     * Other instance methods.
     */


    /**
     * Called by the {@link #afterBeanDiscovery(AfterBeanDiscovery,
     * BeanManager)} <a
     * href="https://jakarta.ee/specifications/cdi/2.0/cdi-spec-2.0.html#init_events"
     * target="_top">container lifecycle observer method</a> to
     * install various beans supporting the injection of {@link
     * AbstractAuthenticationDetailsProvider} instances.
     *
     * @param event the {@link AfterBeanDiscovery} event supplied to
     * the {@link #afterBeanDiscovery(AfterBeanDiscovery,
     * BeanManager)} method; will not be {@code null}
     *
     * @param bm the {@link BeanManager} supplied to
     * the {@link #afterBeanDiscovery(AfterBeanDiscovery,
     * BeanManager)} method; will not be {@code null}
     *
     * @param qualifiers the {@link Set} of qualifier annotations for
     * which support will be installed; will not be {@code null}; must
     * not be modified
     */
    private void installAdps(AfterBeanDiscovery event, BeanManager bm, Set<Annotation> qualifiers) {
        Annotation[] qualifiersArray = qualifiers.toArray(new Annotation[0]);
        if (this.supply(new TypeAndQualifiers(AbstractAuthenticationDetailsProvider.class, qualifiersArray),
                        bm,
                        event::addDefinitionError)) {

            for (AdpStrategy s : AdpStrategy.concreteStrategies()) {
                if (this.supply(s, qualifiersArray, bm, event::addDefinitionError)) {
                    if (s.usesBuilder()) {
                        event.addBean()
                            .types(s.builderType())
                            .qualifiers(qualifiers)
                            .scope(Singleton.class)
                            .produceWith(i -> s.produceBuilder(i, qualifiersArray));
                    }
                    event.addBean()
                        .types(s.type())
                        .qualifiers(qualifiers)
                        .scope(Singleton.class)
                        .produceWith(i -> s.produce(i, qualifiersArray));
                }
            }

            // Finally, AbstractAuthenticationDetailsProvider which
            // will make use of the stuff above.
            event.addBean()
                .types(AbstractAuthenticationDetailsProvider.class)
                .qualifiers(qualifiers)
                .scope(Singleton.class)
                .produceWith(i -> AdpStrategy.AUTO.produce(i, qualifiersArray));
        }
    }

    private boolean supply(AdpStrategy s,
                           Annotation[] qualifiersArray,
                           BeanManager bm,
                           Consumer<? super Throwable> errorHandler) {
        return !s.isAbstract() && this.supply(s.taq(qualifiersArray), bm, errorHandler);
    }

    /**
     * Returns {@code true} if a {@linkplain
     * AfterBeanDiscovery#addBean() synthetic bean should be added}
     * for the supplied {@link Class}.
     *
     * @param c the {@link Class} in question; may be {@code null} in
     * which case {@code false} will be returned
     *
     * @param bm a {@link BeanManager}; must not be {@code null}
     *
     * @param errorHandler a {@link Consumer} that should be prepared
     * to accept any errors; must not be {@code null}
     *
     * @return {@code true} if a {@linkplain
     * AfterBeanDiscovery#addBean() synthetic bean should be added}
     * for the supplied {@link Class}; {@code false} otherwise
     *
     * @exception NullPointerException if {@code bm} or {@code
     * errorHandler} is {@code null}
     */
    private boolean supply(TypeAndQualifiers taq, BeanManager bm, Consumer<? super Throwable> errorHandler) {
        if (taq != null && !this.beanTypesAndQualifiers.contains(taq)) {
            try {
                return bm.resolve(bm.getBeans(taq.type, taq.qualifiers)) == null;
            } catch (AmbiguousResolutionException e) {
                errorHandler.accept(e);
                return false;
            }
        }
        return false;
    }


    /*
     * Static methods.
     */


    private static Object produceClientBuilder(Instance<? super Object> instance,
                                               MethodHandle builderMethod,
                                               Class<?> builderClass,
                                               Annotation[] qualifiers) {
        try {
            Object builderInstance = builderMethod.invoke();
            // Permit arbitrary customization.
            fire(instance, builderClass, qualifiers, builderInstance);
            return builderInstance;
        } catch (RuntimeException runtimeException) {
            throw runtimeException;
        } catch (Exception exception) {
            throw new CreationException(exception.getMessage(), exception);
        } catch (Throwable error) {
            throw (Error) error;
        }
    }

    private static Object produceClient(Instance<? super Object> instance, final Class<?> builderClass) {
        return
            ((ClientBuilderBase<?, ?>) instance.select(builderClass).get())
            .build(instance.select(AbstractAuthenticationDetailsProvider.class).get());
    }

    private static void disposeClient(Object client, Object ignored) {
        if (client instanceof AutoCloseable) {
            close((AutoCloseable) client);
        }
    }

    private static void close(AutoCloseable autoCloseable) {
        if (autoCloseable != null) {
            try {
                autoCloseable.close();
            } catch (RuntimeException runtimeException) {
                throw runtimeException;
            } catch (Exception exception) {
                if (exception instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                throw new IllegalStateException(exception.getMessage(), exception);
            }
        }
    }

    // May return null.
    private static Class<?> loadClass(String name, Consumer<? super Throwable> errorHandler) {
        try {
            return Class.forName(name, false, Thread.currentThread().getContextClassLoader());
        } catch (ClassNotFoundException classNotFoundException) {
            errorHandler.accept(classNotFoundException);
            return null;
        }
    }

    /**
     * Uses the supplied {@link Instance} to {@linkplain
     * Instance#select(TypeLiteral, java.lang.Annotation...) obtain}
     * an appropriate {@link Event} (broadcaster) that will fire the
     * supplied {@code payload} after {@linkplain Class#cast(Object)
     * casting} it to the supplied {@code type}.
     *
     * @param the {@link Instance}; must not be {@code null}
     *
     * @param type the type the supplied {@code payload} must bear;
     * must not be {@code null}
     *
     * @param qualifiers the qualifiers to further refine the
     * selection; must not be {@code null}
     *
     * @param payload the thing to be fired; must not be {@code null}
     *
     * @exception NullPointerException if any parameter is {@code
     * null}
     */
    private static <T> void fire(Instance<? super Object> instance, Class<T> type, Annotation[] qualifiers, Object payload) {
        instance.select(EVENT_OBJECT_TYPE_LITERAL).get().select(type, qualifiers).fire(type.cast(payload));
    }

    private static String client(String input) {
        if (input.endsWith("$Builder")) {
            return input.substring(0, input.length() - "$Builder".length());
        }
        return input.endsWith("Client") ? input : input + "Client";
    }

    private static String thing(String input) {
        if (input.endsWith("$Builder")) {
            input = input.substring(0, input.length() - "$Builder".length());
        }
        return input.endsWith("Client") ? input.substring(0, input.length() - "Client".length()) : input;
    }

    private static String builder(String input) {
        if (input.endsWith("$Builder")) {
            return input;
        } else if (input.endsWith("Client")) {
            return input + "$Builder";
        } else {
            return input + "Client$Builder";
        }
    }

    private static boolean isBuilder(String input) {
        return input.endsWith("$Builder");
    }


    /*
     * Inner and nested classes.
     */


    private static class TypeAndQualifiers {

        private final Type type;

        private final Annotation[] qualifiers;

        private final int hashCode;

        private TypeAndQualifiers(final Type type, final Annotation[] qualifiers) {
            super();
            this.type = type;
            if (qualifiers == null) {
                this.qualifiers = new Annotation[0];
            } else {
                // NOTE: we do not clone() qualifiers because this is
                // a private class and we know what we're doing and we
                // avoid a lot of copying this way. Don't modify the
                // incoming qualifiers array.
                this.qualifiers = qualifiers;
            }
            this.hashCode = computeHashCode(this.type, this.qualifiers);
        }

        private TypeAndQualifiers(final Type type, final Collection<? extends Annotation> qualifiers) {
            super();
            this.type = type;
            if (qualifiers == null || qualifiers.isEmpty()) {
                this.qualifiers = new Annotation[0];
            } else {
                this.qualifiers = qualifiers.toArray(new Annotation[0]);
            }
            this.hashCode = computeHashCode(this.type, this.qualifiers);
        }

        @Override
        public final int hashCode() {
            return this.hashCode;
        }

        @Override
        public final boolean equals(Object other) {
            if (other == this) {
                return true;
            } else if (other != null && other.getClass() == this.getClass()) {
                TypeAndQualifiers her = (TypeAndQualifiers) other;
                return Objects.equals(this.type, her.type) && Arrays.equals(this.qualifiers, her.qualifiers);
            } else {
                return false;
            }
        }

        private static int computeHashCode(Object type, Object[] qualifiers) {
            int hashCode = 17;
            int c = type == null ? 0 : type.hashCode();
            hashCode = 37 * hashCode + c;
            c = qualifiers == null ? 0 : Arrays.hashCode(qualifiers);
            hashCode = 37 * hashCode + c;
            return hashCode;
        }

    }

    private enum AdpStrategy {

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

        CONFIG(SimpleAuthenticationDetailsProvider.class,
               SimpleAuthenticationDetailsProviderBuilder.class) {
            @Override
            boolean isAvailable(Instance<? super Object> instance, Config config, Annotation[] qualifiers) {
                return false; // Not yet fully implemented; hence the false return
            }

            @Override
            AbstractAuthenticationDetailsProvider produce(Instance<? super Object> instance,
                                                          Config config,
                                                          Annotation[] qualifiers) {
                return instance.select(SimpleAuthenticationDetailsProviderBuilder.class, qualifiers).get().build();
            }

            @Override
            SimpleAuthenticationDetailsProviderBuilder produceBuilder(Instance<? super Object> instance,
                                                                      Config config,
                                                                      Annotation[] qualifiers) {
                SimpleAuthenticationDetailsProviderBuilder builder = SimpleAuthenticationDetailsProvider.builder();
                OciExtension.fire(instance, SimpleAuthenticationDetailsProviderBuilder.class, qualifiers, builder);
                return builder;
            }
        },

        OCI_CONFIG_FILE(ConfigFileAuthenticationDetailsProvider.class) {
            @Override
            boolean isAvailable(Instance<? super Object> instance, Config config, Annotation[] qualifiers) {
                if (super.isAvailable(instance, config, qualifiers)) {
                    try {
                        this.select(instance, config, qualifiers);
                    } catch (CreationException creationException) {
                        if (creationException.getCause() instanceof FileNotFoundException) {
                            return false;
                        }
                        throw creationException;
                    }
                    return true;
                }
                return false;
            }

            @Override
            Object produceBuilder(Instance<? super Object> instance, Annotation[] qualifiers) {
                throw new UnsupportedOperationException();
            }

            @Override
            Object produceBuilder(Instance<? super Object> instance, Config config, Annotation[] qualifiers) {
                throw new UnsupportedOperationException();
            }

            @Override
            AbstractAuthenticationDetailsProvider produce(Instance<? super Object> instance,
                                                          Config config,
                                                          Annotation[] qualifiers) {
                return
                    this.produce(config.getOptionalValue("oci.config.path", String.class),
                                 config.getOptionalValue("oci.auth.profile", String.class).orElse("DEFAULT"));
            }

            private AbstractAuthenticationDetailsProvider produce(Optional<String> ociConfigPath, String ociAuthProfile) {
                try {
                    if (ociConfigPath.isEmpty()) {
                        return new ConfigFileAuthenticationDetailsProvider(ociAuthProfile);
                    } else {
                        return new ConfigFileAuthenticationDetailsProvider(ociConfigPath.orElseThrow(), ociAuthProfile);
                    }
                } catch (IOException ioException) {
                    // The underlying ConfigFileReader that does the real work
                    // does not throw a FileNotFoundException in this case (as
                    // it probably should).  We have no choice but to parse
                    // the error message.  See
                    // https://github.com/oracle/oci-java-sdk/blob/v2.18.0/bmc-common/src/main/java/com/oracle/bmc/ConfigFileReader.java#L94-L98.
                    String message = ioException.getMessage();
                    if (message != null
                        && message.startsWith("Can't load the default config from ")
                        && message.endsWith(" because it does not exist or it is not a file.")) {
                        throw new CreationException(message, new FileNotFoundException(message).initCause(ioException));
                    }
                    throw new CreationException(message, ioException);
                }
            }
        },

        INSTANCE_PRINCIPALS(InstancePrincipalsAuthenticationDetailsProvider.class,
                            InstancePrincipalsAuthenticationDetailsProviderBuilder.class) {
            @Override
            boolean isAvailable(Instance<? super Object> instance, Config config, Annotation[] qualifiers) {
                if (super.isAvailable(instance, config, qualifiers)) {
                    String ociImdsHostname = config.getOptionalValue("oci.imds.hostname", String.class).orElse("169.254.169.254");
                    int ociImdsTimeoutMillis =
                        config.getOptionalValue("oci.imds.timeout.milliseconds", Integer.class).orElse(Integer.valueOf(500));
                    try {
                        return InetAddress.getByName(ociImdsHostname).isReachable(ociImdsTimeoutMillis);
                    } catch (ConnectException connectException) {
                        return false;
                    } catch (IOException ioException) {
                        throw new CreationException(ioException.getMessage(), ioException);
                    }
                }
                return false;
            }

            @Override
            InstancePrincipalsAuthenticationDetailsProviderBuilder produceBuilder(Instance<? super Object> instance,
                                                                                  Annotation[] qualifiers) {
                return this.produceBuilder(instance, null, qualifiers); // no need to find Config; it's not used
            }

            @Override
            InstancePrincipalsAuthenticationDetailsProviderBuilder produceBuilder(Instance<? super Object> instance,
                                                                                  Config config,
                                                                                  Annotation[] qualifiers) {
                InstancePrincipalsAuthenticationDetailsProviderBuilder builder =
                    InstancePrincipalsAuthenticationDetailsProvider.builder();
                OciExtension.fire(instance, InstancePrincipalsAuthenticationDetailsProviderBuilder.class, qualifiers, builder);
                return builder;
            }

            @Override
            AbstractAuthenticationDetailsProvider produce(Instance<? super Object> instance,
                                                          Config config,
                                                          Annotation[] qualifiers) {
                return instance.select(InstancePrincipalsAuthenticationDetailsProviderBuilder.class, qualifiers).get().build();
            }
        },

        RESOURCE_PRINCIPAL(ResourcePrincipalAuthenticationDetailsProvider.class,
                           ResourcePrincipalAuthenticationDetailsProviderBuilder.class) {
            @Override
            boolean isAvailable(Instance<? super Object> instance, Config config, Annotation[] qualifiers) {
                return
                    super.isAvailable(instance, config, qualifiers)
                    // https://github.com/oracle/oci-java-sdk/blob/v2.15.0/bmc-common/src/main/java/com/oracle/bmc/auth/ResourcePrincipalAuthenticationDetailsProvider.java#L246-L251
                    && System.getenv("OCI_RESOURCE_PRINCIPAL_VERSION") != null;
            }

            @Override
            ResourcePrincipalAuthenticationDetailsProviderBuilder produceBuilder(Instance<? super Object> instance,
                                                                                 Annotation[] qualifiers) {
                return this.produceBuilder(instance, null, qualifiers); // no need to find Config; it's not used
            }

            @Override
            ResourcePrincipalAuthenticationDetailsProviderBuilder produceBuilder(Instance<? super Object> instance,
                                                                                 Config config,
                                                                                 Annotation[] qualifiers) {
                ResourcePrincipalAuthenticationDetailsProviderBuilder builder =
                    ResourcePrincipalAuthenticationDetailsProvider.builder();
                OciExtension.fire(instance, ResourcePrincipalAuthenticationDetailsProviderBuilder.class, qualifiers, builder);
                return builder;
            }

            @Override
            AbstractAuthenticationDetailsProvider produce(Instance<? super Object> instance,
                                                          Config config,
                                                          Annotation[] qualifiers) {
                return instance.select(ResourcePrincipalAuthenticationDetailsProviderBuilder.class, qualifiers).get().build();
            }
        },

        AUTO(AbstractAuthenticationDetailsProvider.class, true /* isAbstract */) {

            @Override
            Object produceBuilder(Instance<? super Object> instance, Config config, Annotation[] qualifiers) {
                throw new UnsupportedOperationException();
            }

            @Override
            AbstractAuthenticationDetailsProvider produce(Instance<? super Object> instance,
                                                          Config config,
                                                          Annotation[] qualifiers) {
                Collection<AdpStrategy> strategies =
                    concreteStrategies(config.getOptionalValue("oci.config.strategy", String[].class).orElse(new String[0]));
                for (final AdpStrategy s : strategies) {
                    if (!s.isAbstract() && s.isAvailable(instance, config, qualifiers)) {
                        return s.select(instance, config, qualifiers);
                    }
                }
                throw new UnsatisfiedResolutionException();
            }
        };


        /*
         * Instance fields.
         */


        private final Class<? extends AbstractAuthenticationDetailsProvider> type;

        private final Class<?> builderType;

        private final boolean isAbstract;


        /*
         * Constructors.
         */


        AdpStrategy(Class<? extends AbstractAuthenticationDetailsProvider> type) {
            this(type, null, false);
        }

        AdpStrategy(Class<? extends AbstractAuthenticationDetailsProvider> type,
                    boolean isAbstract) {
            this(type, null, isAbstract);
        }

        AdpStrategy(Class<? extends AbstractAuthenticationDetailsProvider> type,
                    Class<?> builderType) {
            this(type, builderType, false);
        }

        AdpStrategy(Class<? extends AbstractAuthenticationDetailsProvider> type,
                    Class<?> builderType,
                    boolean isAbstract) {
            this.type = Objects.requireNonNull(type, "type");
            this.builderType = builderType;
            this.isAbstract = isAbstract;
        }


        /*
         * Instance methods.
         */


        Type type() {
            return this.type;
        }

        TypeAndQualifiers taq(Annotation[] qualifiers) {
            return new TypeAndQualifiers(this.type(), qualifiers);
        }

        Type builderType() {
            return this.builderType;
        }

        boolean usesBuilder() {
            return this.builderType() != null;
        }

        final boolean isAbstract() {
            return this.isAbstract;
        }

        boolean isAvailable(Instance<? super Object> instance, Config config, Annotation[] qualifiers) {
            return !this.isAbstract();
        }

        AbstractAuthenticationDetailsProvider select(Instance<? super Object> instance,
                                                     Config config,
                                                     Annotation[] qualifiers) {
            return instance.select(this.type, qualifiers).get();
        }

        Object produceBuilder(Instance<? super Object> instance, Annotation[] qualifiers) {
            return this.produceBuilder(instance, instance.select(Config.class).get(), qualifiers);
        }

        abstract Object produceBuilder(Instance<? super Object> instance, Config config, Annotation[] qualifiers);

        AbstractAuthenticationDetailsProvider produce(Instance<? super Object> instance, Annotation[] qualifiers) {
            return this.produce(instance, instance.select(Config.class).get(), qualifiers);
        }

        abstract AbstractAuthenticationDetailsProvider produce(Instance<? super Object> instance,
                                                               Config config,
                                                               Annotation[] qualifiers);


        /*
         * Static methods.
         */


        private static AdpStrategy of(String name) {
            return valueOf(name.replace('-', '_').toUpperCase());
        }

        private static Collection<AdpStrategy> concreteStrategies() {
            EnumSet<AdpStrategy> set = EnumSet.allOf(AdpStrategy.class);
            set.removeIf(AdpStrategy::isAbstract);
            return set;
        }

        private static Collection<AdpStrategy> concreteStrategies(String[] strategyStringsArray) {
            Collection<AdpStrategy> strategies;
            switch (strategyStringsArray.length) {
            case 0:
                strategies = concreteStrategies();
                break;
            case 1:
                strategies = toStrategies(strategyStringsArray[0]);
                break;
            default:
                Set<String> strategyStrings = new LinkedHashSet<>(Arrays.asList(strategyStringsArray));
                switch (strategyStrings.size()) {
                case 0:
                    throw new AssertionError();
                case 1:
                    strategies = toStrategies(strategyStrings.iterator().next());
                    break;
                default:
                    strategies = new ArrayList<>(strategyStrings.size());
                    for (String s : strategyStrings) {
                        strategies.addAll(toStrategies(s));
                    }
                    break;
                }
                break;
            }
            strategies.removeIf(AdpStrategy::isAbstract);
            if (strategies.isEmpty()) {
                strategies = concreteStrategies();
            }
            return strategies;
        }

        private static Collection<AdpStrategy> toStrategies(String strategyString) {
            if (strategyString == null) {
                return EnumSet.of(AdpStrategy.AUTO);
            } else {
                strategyString = strategyString.strip();
                if (!strategyString.isBlank()) {
                    return EnumSet.of(AdpStrategy.AUTO);
                } else {
                    return EnumSet.of(AdpStrategy.of(strategyString));
                }
            }
        }
    }

}
