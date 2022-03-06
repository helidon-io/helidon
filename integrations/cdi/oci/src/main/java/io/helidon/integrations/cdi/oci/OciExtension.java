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
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
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
import javax.ws.rs.Priorities;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.core.UriBuilder;

import com.oracle.bmc.Service;
import com.oracle.bmc.auth.AbstractAuthenticationDetailsProvider;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;
import com.oracle.bmc.auth.InstancePrincipalsAuthenticationDetailsProvider;
import com.oracle.bmc.auth.InstancePrincipalsAuthenticationDetailsProvider.InstancePrincipalsAuthenticationDetailsProviderBuilder;
import com.oracle.bmc.auth.ResourcePrincipalAuthenticationDetailsProvider;
import com.oracle.bmc.auth.ResourcePrincipalAuthenticationDetailsProvider.ResourcePrincipalAuthenticationDetailsProviderBuilder;
import com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider;
import com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider.SimpleAuthenticationDetailsProviderBuilder;
import com.oracle.bmc.common.ClientBuilderBase;
import com.oracle.bmc.http.ClientConfigurator;
import org.eclipse.microprofile.config.Config;

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


    // Evaluates to "com.oracle.bmc." as of the current version of the
    // OCI Java SDK.
    private static final String OCI_PACKAGE_PREFIX = Service.class.getPackageName() + ".";

    // For an OCI service conceptually named "Example" in an
    // OCI_PACKAGE_PREFIX subpackage named "example":
    //
    // Match Strings expected to be class names:
    //
    // (0) ...starting with OCI_PACKAGE_PREFIX...
    // (1) ...followed by the service client package fragment ("example")...
    // (2) ...followed by a period (".")...
    // (3) ...followed by one or more of the following:
    //       "Example",
    //       "ExampleAsync",
    //       "ExampleAsyncClient",
    //       "ExampleAsyncClient$Builder",
    //       "ExampleClient",
    //       "ExampleClient$Builder"...
    // (4) ...followed by the end of String.
    //
    // Capturing group 0: the matched class name
    // Capturing group 1: the service client interface (OCI_PACKAGE_PREFIX + "example.Example")
    // Capturing group 2: "example"
    private static final Pattern SERVICE_CLIENT_CLASS_NAME_PATTERN =
        Pattern.compile("^(" + OCI_PACKAGE_PREFIX // (0)
                        + "([^.]+)" // (1)
                        + "\\." // (2)
                        + "(.+))(?:Async|Client(?:\\$Builder)?)?" // (3)
                        + "$"); // (4)

    // OCI Java SDK subPackage fragments identifying subpackages whose
    // classes and interfaces do not contain classes and interfaces
    // that follow the service client pattern described above,
    // i.e. that are more foundational.
    private static final SortedSet<String> SERVICE_CLIENT_PACKAGE_FRAGMENT_DENY_LIST =
        Collections.unmodifiableSortedSet(new TreeSet<>(List.of("auth",
                                                                "circuitbreaker",
                                                                "common",
                                                                "encryption",
                                                                "helper",
                                                                "http",
                                                                "internal",
                                                                "io",
                                                                "model",
                                                                "requests",
                                                                "responses",
                                                                "retrier",
                                                                "util",
                                                                "waiter")));

    private static final TypeLiteral<Event<Object>> EVENT_OBJECT_TYPE_LITERAL = new TypeLiteral<Event<Object>>() {};

    private static final Lookup PUBLIC_LOOKUP = MethodHandles.publicLookup();

    private static final Annotation[] EMPTY_ANNOTATION_ARRAY = new Annotation[0];


    /*
     * Instance fields.
     */


    private final Map<Set<Annotation>, ServiceTaqs> serviceTaqs;


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
        this.serviceTaqs = new HashMap<>();
    }


    /*
     * Container lifecycle observer methods.
     */


    private void processInjectionPoint(@Observes ProcessInjectionPoint<?, ?> event) {
        InjectionPoint ip = event.getInjectionPoint();
        Type baseType = ip.getAnnotated().getBaseType();
        if (baseType instanceof Class) {
            // Optimization: all OCI constructs we're interested in
            // are non-generic classes.
            Class<?> baseClass = (Class<?>) baseType;
            Set<Annotation> qualifiers = ip.getQualifiers();
            if (AbstractAuthenticationDetailsProvider.class.isAssignableFrom(baseClass)
                || AdpStrategy.builderClasses().contains(baseClass)) {
                this.serviceTaqs.computeIfAbsent(qualifiers, qs -> new ServiceTaqs());
            } else {
                String baseClassName = baseClass.getName();
                Matcher m = SERVICE_CLIENT_CLASS_NAME_PATTERN.matcher(baseClassName);
                if (m.matches() && !SERVICE_CLIENT_PACKAGE_FRAGMENT_DENY_LIST.contains(m.group(2))) {
                    ServiceTaqs serviceTaqs = this.serviceTaqs.get(qualifiers);
                    if (serviceTaqs == null || serviceTaqs.isEmpty()) {
                        // Create types-and-qualifiers for, e.g.:
                        //   ....example.Example
                        //   ....example.ExampleAsync
                        //   ....example.ExampleAsyncClient
                        //   ....example.ExampleAsyncClient$Builder
                        //   ....example.ExampleClient
                        //   ....example.ExampleClient$Builder
                        String serviceInterface = m.group(1);
                        Class<?> serviceInterfaceClass = toClass(event, baseClass, serviceInterface);
                        String serviceAsyncInterface = serviceInterface + "Async";
                        Class<?> serviceAsyncInterfaceClass = toClass(event, baseClass, serviceAsyncInterface);
                        String serviceAsyncClient = serviceAsyncInterface + "Client";
                        Class<?> serviceAsyncClientClass = toClass(event, baseClass, serviceAsyncClient);
                        String serviceAsyncClientBuilder = serviceAsyncClient + "$Builder";
                        Class<?> serviceAsyncClientBuilderClass = toClass(event, baseClass, serviceAsyncClientBuilder);
                        String serviceClient = serviceInterface + "Client";
                        Class<?> serviceClientClass = toClass(event, baseClass, serviceClient);
                        String serviceClientBuilder = serviceClient + "$Builder";
                        Class<?> serviceClientBuilderClass = toClass(event, baseClass, serviceClientBuilder);
                        this.serviceTaqs.put(qualifiers,
                                             new ServiceTaqs(qualifiers.toArray(EMPTY_ANNOTATION_ARRAY),
                                                             serviceInterfaceClass,
                                                             serviceClientClass,
                                                             serviceClientBuilderClass,
                                                             serviceAsyncInterfaceClass,
                                                             serviceAsyncClientClass,
                                                             serviceAsyncClientBuilderClass));
                    }
                }
            }
        }
    }

    private void afterBeanDiscovery(@Observes AfterBeanDiscovery event, BeanManager bm) {
        for (Entry<Set<Annotation>, ServiceTaqs> entry : this.serviceTaqs.entrySet()) {
            installAdps(event, bm, entry.getKey());
            ServiceTaqs serviceTaqs = entry.getValue();
            if (!serviceTaqs.isEmpty()) {
                TypeAndQualifiers serviceClientBuilderTaq = serviceTaqs.serviceClientBuilder();
                TypeAndQualifiers serviceClientTaq = serviceTaqs.serviceClient();
                installServiceClientBuilder(event,
                                            bm,
                                            serviceClientBuilderTaq,
                                            serviceClientTaq.toClass());
                installServiceClient(event,
                                     bm,
                                     serviceClientTaq,
                                     serviceTaqs.serviceInterface().type(),
                                     serviceClientBuilderTaq.toClass());
                TypeAndQualifiers serviceAsyncClientBuilderTaq = serviceTaqs.serviceAsyncClientBuilder();
                TypeAndQualifiers serviceAsyncClientTaq = serviceTaqs.serviceAsyncClient();
                installServiceClientBuilder(event,
                                            bm,
                                            serviceAsyncClientBuilderTaq,
                                            serviceAsyncClientTaq.toClass());
                installServiceClient(event,
                                     bm,
                                     serviceAsyncClientTaq,
                                     serviceTaqs.serviceAsyncInterface().type(),
                                     serviceAsyncClientBuilderTaq.toClass());
            }
        }
    }

    private void afterDeploymentValidation(@Observes AfterDeploymentValidation event) {
        this.serviceTaqs.clear();
    }


    /*
     * Static methods.
     */


    private static void installAdps(AfterBeanDiscovery event, BeanManager bm, Set<Annotation> qualifiers) {
        Annotation[] qualifiersArray = qualifiers.toArray(EMPTY_ANNOTATION_ARRAY);
        for (AdpStrategy s : EnumSet.allOf(AdpStrategy.class)) {
            Type builderType = s.builderType();
            if (builderType != null) {
                TypeAndQualifiers builderTaq = new TypeAndQualifiers(builderType, qualifiersArray);
                if (isUnresolved(bm, builderTaq)) {
                    event.addBean()
                        .types(builderType)
                        .qualifiers(qualifiers)
                        .scope(Singleton.class)
                        .produceWith(i -> s.produceBuilder(i, qualifiersArray));
                }
            }
            Type type = s.type();
            TypeAndQualifiers taq = new TypeAndQualifiers(type, qualifiersArray);
            if (isUnresolved(bm, taq)) {
                event.addBean()
                    .types(type)
                    .qualifiers(qualifiers)
                    .scope(Singleton.class)
                    .produceWith(i -> s.produce(i, qualifiersArray));
            }
        }
    }

    private static boolean installServiceClientBuilder(AfterBeanDiscovery event,
                                                       BeanManager bm,
                                                       TypeAndQualifiers serviceClientBuilder,
                                                       Class<?> serviceClientClass) {
        if (isUnresolved(bm, serviceClientBuilder)) {
            Class<?> serviceClientBuilderClass = serviceClientBuilder.toClass();
            MethodHandle builderMethod;
            try {
                builderMethod = PUBLIC_LOOKUP.findStatic(serviceClientClass, "builder", methodType(serviceClientBuilderClass));
            } catch (ReflectiveOperationException reflectiveOperationException) {
                event.addDefinitionError(reflectiveOperationException);
                return false;
            }
            Annotation[] qualifiersArray = serviceClientBuilder.qualifiers();
            event.addBean()
                .types(Set.of(serviceClientBuilderClass))
                .qualifiers(Set.of(qualifiersArray))
                .scope(Singleton.class)
                .produceWith(i -> produceClientBuilder(i, builderMethod, serviceClientBuilderClass, qualifiersArray));
            return true;
        }
        return false;
    }

    private static boolean installServiceClient(AfterBeanDiscovery event,
                                                BeanManager bm,
                                                TypeAndQualifiers serviceClient,
                                                Type serviceInterfaceType,
                                                Class<?> serviceClientBuilderClass) {
        Annotation[] qualifiersArray = serviceClient.qualifiers();
        Type serviceClientType = serviceClient.type();
        try {
            if (bm.resolve(bm.getBeans(serviceClientType, qualifiersArray)) == null) {
                Set<Type> types = null;
                if (bm.resolve(bm.getBeans(serviceInterfaceType, qualifiersArray)) == null) {
                    types = Set.of(serviceClientType, serviceInterfaceType);
                } else {
                    types = Set.of(serviceClientType);
                }
                event.addBean()
                    .types(types)
                    .qualifiers(Set.of(qualifiersArray))
                    .scope(Singleton.class)
                    .produceWith(i -> produceClient(i, serviceClientBuilderClass))
                    .disposeWith(OciExtension::disposeClient);
                return true;
            }
        } catch (AmbiguousResolutionException ambiguousResolutionException) {
        }
        return false;
    }

    private static Object produceClientBuilder(Instance<? super Object> instance,
                                               MethodHandle builderMethod,
                                               Class<?> builderClass,
                                               Annotation[] qualifiers) {
        try {
            ClientBuilderBase<?, ?> builderInstance = (ClientBuilderBase<?, ?>) builderMethod.invoke();
            // Permit arbitrary customization.
            fire(instance, builderClass, qualifiers, builderInstance);
            customizeEndpointResolution(builderInstance);
            return builderInstance;
        } catch (RuntimeException runtimeException) {
            throw runtimeException;
        } catch (Exception exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
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

    private static <T> void fire(Instance<? super Object> instance, Class<T> type, Annotation[] qualifiers, Object payload) {
        instance.select(EVENT_OBJECT_TYPE_LITERAL).get().select(type, qualifiers).fire(type.cast(payload));
    }

    private static boolean isUnresolved(BeanManager bm, TypeAndQualifiers taq) {
        return isUnresolved(bm, taq.type(), taq.qualifiers());
    }

    private static boolean isUnresolved(BeanManager bm, Type type) {
        try {
            return bm.resolve(bm.getBeans(type)) == null;
        } catch (AmbiguousResolutionException ambiguousResolutionException) {
            return false;
        }
    }

    private static boolean isUnresolved(BeanManager bm, Type type, Annotation[] qualifiers) {
        try {
            return bm.resolve(bm.getBeans(type, qualifiers)) == null;
        } catch (AmbiguousResolutionException ambiguousResolutionException) {
            return false;
        }
    }

    private static Class<?> toClass(ProcessInjectionPoint<?, ?> event,
                                    Class<?> referenceClass,
                                    String name) {
        if (referenceClass.getName().equals(name)) {
            return referenceClass;
        }
        try {
            return loadClass(name);
        } catch (ClassNotFoundException classNotFoundException) {
            event.addDefinitionError(classNotFoundException);
            return null;
        }
    }

    private static Class<?> loadClass(String name) throws ClassNotFoundException {
        return Class.forName(name, false, Thread.currentThread().getContextClassLoader());
    }

    private static void customizeEndpointResolution(ClientBuilderBase<?, ?> clientBuilder) {
        EndpointAdjuster ea = EndpointAdjuster.of(clientBuilder.getClass().getName());
        if (ea != null) {
            clientBuilder.additionalClientConfigurator(ea);
        }
    }


    /*
     * Inner and nested classes.
     */


    private static class TypeAndQualifiers {


        /*
         * Instance fields.
         */


        private final Type type;

        private final Annotation[] qualifiers;

        private final int hashCode;


        /*
         * Constructors.
         */


        private TypeAndQualifiers(final Type type, final Annotation[] qualifiers) {
            super();
            this.type = Objects.requireNonNull(type, "type");
            if (qualifiers == null) {
                this.qualifiers = EMPTY_ANNOTATION_ARRAY;
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
            this.type = Objects.requireNonNull(type, "type");
            if (qualifiers == null || qualifiers.isEmpty()) {
                this.qualifiers = EMPTY_ANNOTATION_ARRAY;
            } else {
                this.qualifiers = qualifiers.toArray(EMPTY_ANNOTATION_ARRAY);
            }
            this.hashCode = computeHashCode(this.type, this.qualifiers);
        }


        /*
         * Instance methods.
         */


        private Type type() {
            return this.type;
        }

        private Annotation[] qualifiers() {
            return this.qualifiers; // note: uncloned because this is a private class and we know what we're doing
        }

        private Class<?> toClass() {
            if (this.type instanceof Class) {
                return (Class<?>) this.type;
            } else if (this.type instanceof ParameterizedType) {
                return (Class<?>) ((ParameterizedType) this.type).getRawType();
            } else {
                return null;
            }
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

        @Override
        public final String toString() {
            return Arrays.asList(this.qualifiers()).toString() + " " + this.type().toString();
        }


        /*
         * Static methods.
         */


        private static int computeHashCode(Object type, Object[] qualifiers) {
            int hashCode = 17;
            int c = type == null ? 0 : type.hashCode();
            hashCode = 37 * hashCode + c;
            c = qualifiers == null ? 0 : Arrays.hashCode(qualifiers);
            hashCode = 37 * hashCode + c;
            return hashCode;
        }

    }

    private static class ServiceTaqs {


        /*
         * Instance fields.
         */


        private final TypeAndQualifiers serviceInterface;

        private final TypeAndQualifiers serviceClient;

        private final TypeAndQualifiers serviceClientBuilder;

        private final TypeAndQualifiers serviceAsyncInterface;

        private final TypeAndQualifiers serviceAsyncClient;

        private final TypeAndQualifiers serviceAsyncClientBuilder;

        private final boolean empty;


        /*
         * Constructors.
         */


        private ServiceTaqs() {
            super();
            this.serviceInterface = null;
            this.serviceClient = null;
            this.serviceClientBuilder = null;
            this.serviceAsyncInterface = null;
            this.serviceAsyncClient = null;
            this.serviceAsyncClientBuilder = null;
            this.empty = true;
        }

        private ServiceTaqs(Annotation[] qualifiers,
                            Type serviceInterface,
                            Type serviceClient,
                            Type serviceClientBuilder,
                            Type serviceAsyncInterface,
                            Type serviceAsyncClient,
                            Type serviceAsyncClientBuilder) {
            qualifiers = qualifiers == null ? EMPTY_ANNOTATION_ARRAY : qualifiers;
            boolean empty = false;
            if (serviceInterface == null) {
                this.serviceInterface = null;
                if (!empty) {
                    empty = true;
                }
            } else {
                this.serviceInterface = new TypeAndQualifiers(serviceInterface, qualifiers);
            }
            if (serviceClient == null) {
                this.serviceClient = null;
                if (!empty) {
                    empty = true;
                }
            } else {
                this.serviceClient = new TypeAndQualifiers(serviceClient, qualifiers);
            }
            if (serviceClientBuilder == null) {
                this.serviceClientBuilder = null;
                if (!empty) {
                    empty = true;
                }
            } else {
                this.serviceClientBuilder = new TypeAndQualifiers(serviceClientBuilder, qualifiers);
            }
            if (serviceAsyncInterface == null) {
                this.serviceAsyncInterface = null;
                if (!empty) {
                    empty = true;
                }
            } else {
                this.serviceAsyncInterface = new TypeAndQualifiers(serviceAsyncInterface, qualifiers);
            }
            if (serviceAsyncClient == null) {
                this.serviceAsyncClient = null;
                if (!empty) {
                    empty = true;
                }
            } else {
                this.serviceAsyncClient = new TypeAndQualifiers(serviceAsyncClient, qualifiers);
            }
            if (serviceAsyncClientBuilder == null) {
                this.serviceAsyncClientBuilder = null;
                if (!empty) {
                    empty = true;
                }
            } else {
                this.serviceAsyncClientBuilder = new TypeAndQualifiers(serviceAsyncClientBuilder, qualifiers);
            }
            this.empty = empty;
        }


        /*
         * Instance methods.
         */


        private TypeAndQualifiers serviceInterface() {
            return this.serviceInterface;
        }

        private TypeAndQualifiers serviceClient() {
            return this.serviceClient;
        }

        private TypeAndQualifiers serviceClientBuilder() {
            return this.serviceClientBuilder;
        }

        private TypeAndQualifiers serviceAsyncInterface() {
            return this.serviceAsyncInterface;
        }

        private TypeAndQualifiers serviceAsyncClient() {
            return this.serviceAsyncClient;
        }

        private TypeAndQualifiers serviceAsyncClientBuilder() {
            return this.serviceAsyncClientBuilder;
        }

        private boolean isEmpty() {
            return this.empty;
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


        /*
         * Enum constants.
         */


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

        AUTO(AbstractAuthenticationDetailsProvider.class, true) {

            @Override
            Object produceBuilder(Instance<? super Object> instance, Config config, Annotation[] qualifiers) {
                throw new UnsupportedOperationException();
            }

            @Override
            @SuppressWarnings("fallthrough")
            AbstractAuthenticationDetailsProvider produce(Instance<? super Object> instance,
                                                          Config config,
                                                          Annotation[] qualifiers) {
                Collection<AdpStrategy> strategies =
                    concreteStrategies(config.getOptionalValue("oci.config.strategy", String[].class).orElse(null));
                switch (strategies.size()) {
                case 0:
                    throw new AssertionError();
                case 1:
                    AdpStrategy strategy = strategies.iterator().next();
                    if (strategy != this) {
                        // No availability check on purpose; there's
                        // only one strategy that is not itself AUTO
                        // and no fallback or magic.
                        return strategy.select(instance, config, qualifiers);
                    }
                    // (Edge case.)
                    strategies = concreteStrategies();
                    // fall-through
                default:
                    for (AdpStrategy s : strategies) {
                        if (s != this && s.isAvailable(instance, config, qualifiers)) {
                            return s.select(instance, config, qualifiers);
                        }
                    }
                    break;
                }
                throw new UnsatisfiedResolutionException();
            }
        };


        /*
         * Static fields.
         */


        private static final Collection<AdpStrategy> CONCRETE_STRATEGIES;

        private static final Set<Class<?>> BUILDER_CLASSES;

        static {
            EnumSet<AdpStrategy> set = EnumSet.allOf(AdpStrategy.class);
            set.removeIf(AdpStrategy::isAbstract);
            CONCRETE_STRATEGIES = Collections.unmodifiableCollection(set);
            Set<Class<?>> builderClasses = new HashSet<>(7);
            for (AdpStrategy s : set) {
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

        Type builderType() {
            return this.builderType;
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


        private static Set<Class<?>> builderClasses() {
            return BUILDER_CLASSES;
        }

        private static AdpStrategy of(String name) {
            return valueOf(name.replace('-', '_').toUpperCase());
        }

        private static AdpStrategy ofConfigString(String strategyString) {
            if (strategyString == null) {
                return AUTO;
            } else {
                strategyString = strategyString.strip();
                if (!strategyString.isBlank()) {
                    return AUTO;
                } else {
                    return of(strategyString);
                }
            }
        }

        private static Collection<AdpStrategy> concreteStrategies() {
            return CONCRETE_STRATEGIES;
        }

        private static Collection<AdpStrategy> concreteStrategies(String[] strategyStringsArray) {
            Collection<AdpStrategy> strategies;
            AdpStrategy strategy;
            int length = strategyStringsArray == null ? 0 : strategyStringsArray.length;
            switch (length) {
            case 0:
                return concreteStrategies();
            case 1:
                strategy = ofConfigString(strategyStringsArray[0]);
                if (strategy.isAbstract()) {
                    return concreteStrategies();
                }
                strategies = EnumSet.of(strategy);
                break;
            default:
                Set<String> strategyStrings = new LinkedHashSet<>(Arrays.asList(strategyStringsArray));
                switch (strategyStrings.size()) {
                case 0:
                    throw new AssertionError();
                case 1:
                    strategy = ofConfigString(strategyStrings.iterator().next());
                    if (strategy.isAbstract()) {
                        return concreteStrategies();
                    }
                    strategies = EnumSet.of(strategy);
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

    }

    private enum EndpointAdjuster implements ClientConfigurator, ClientRequestFilter, Predicate<ClientRequestContext> {


        /*
         * Enum constants.
         */


        // See
        // https://docs.oracle.com/en-us/iaas/tools/java/2.18.0/com/oracle/bmc/monitoring/MonitoringAsync.html#postMetricData-com.oracle.bmc.monitoring.requests.PostMetricDataRequest-com.oracle.bmc.responses.AsyncHandler-:
        //
        //   The endpoints for this [particular POST] operation differ
        //   from other Monitoring operations. Replace the string
        //   telemetry with telemetry-ingestion in the endpoint, as in
        //   the following example:
        //
        //   https://telemetry-ingestion.eu-frankfurt-1.oraclecloud.com"
        //
        // Doing this in an application that uses a MonitoringClient
        // or a MonitoringAsyncClient from several threads, not all of
        // which are POSTing, is, of course, unsafe, since a thread
        // performing a GET operation using the client might use the
        // POSTing endpoint.  This filter repairs this flaw and is
        // installed by OCI-SDK-supported client customization
        // facilities.
        //
        // The documented instructions above are imprecise.  This filter
        // implements what it seems was actually meant:
        //
        //   The OCI hostname to which metrics should be POSTed must
        //   be a specific hostname that is derived from, but not
        //   equal to, the automatically computed hostname used for
        //   all other HTTP operations initiated by the Monitoring
        //   service client. This specific custom hostname must be
        //   derived as follows:
        //
        //     If the automatically computed hostname begins with
        //     "telemetry.", replace only that occurrence of
        //     "telemetry."  with "telemetry-ingestion.".  The
        //     resulting hostname is the derived hostname to use for
        //     POSTing metrics and for no other purpose.
        MONITORING(crc -> {
                if ("POST".equalsIgnoreCase(crc.getMethod())) {
                    URI uri = crc.getUri();
                    if (uri != null) {
                        String host = uri.getHost();
                        if (host != null && host.startsWith("telemetry.")) {
                            String path = uri.getPath();
                            return path != null && path.endsWith("/metrics");
                        }
                    }
                }
                return false;
            },
            h -> "telemetry-ingestion." + h.substring("telemetry.".length())
            );


        /*
         * Static fields.
         */


        private static final Map<String, EndpointAdjuster> ENDPOINT_ADJUSTERS;

        static {
            Map<String, EndpointAdjuster> map = new HashMap<>();
            for (EndpointAdjuster ea : EnumSet.allOf(EndpointAdjuster.class)) {
                map.put(ea.clientBuilderClassName, ea);
                map.put(ea.asyncClientBuilderClassName, ea);
            }
            ENDPOINT_ADJUSTERS = Collections.unmodifiableMap(map);
        }


        /*
         * Instance fields.
         */


        private final String clientBuilderClassName;

        private final String asyncClientBuilderClassName;

        private final Predicate<? super ClientRequestContext> p;

        private final UnaryOperator<String> adjuster;


        /*
         * Constructors.
         */


        EndpointAdjuster(Predicate<? super ClientRequestContext> p,
                         UnaryOperator<String> adjuster) {
            String lowerCaseName = this.name().toLowerCase();
            String titleCaseName = Character.toUpperCase(lowerCaseName.charAt(0)) + lowerCaseName.substring(1);
            String prefix = OCI_PACKAGE_PREFIX + lowerCaseName + "." + titleCaseName;
            this.clientBuilderClassName = prefix + "Client$Builder";
            this.asyncClientBuilderClassName = prefix + "AsyncClient$Builder";
            this.p = p;
            this.adjuster = adjuster;
        }


        /*
         * Instance methods.
         */


        @Override // ClientConfigurator
        public void customizeBuilder(ClientBuilder builder) {
            builder.register(this, Map.of(ClientRequestFilter.class, Integer.valueOf(Priorities.AUTHENTICATION - 500)));
        }

        @Override // ClientConfigurator
        public void customizeClient(Client client) {
        }

        @Override // Predicate<ClientRequestContext>
        public final boolean test(ClientRequestContext crc) {
            return this.p.test(crc);
        }

        @Override // ClientRequestFilter
        public final void filter(ClientRequestContext crc) throws IOException {
            if (this.test(crc)) {
                URI uri = crc.getUri();
                if (uri != null) {
                    String hostname = uri.getHost();
                    if (hostname != null) {
                        this.adjust(crc, hostname);
                    }
                }
            }
        }

        private void adjust(ClientRequestContext crc, String hostname) {
            crc.setUri(UriBuilder.fromUri(crc.getUri()).host(this.adjuster.apply(hostname)).build());
        }


        /*
         * Static methods.
         */


        private static EndpointAdjuster of(String clientBuilderClassName) {
            return ENDPOINT_ADJUSTERS.get(clientBuilderClassName);
        }

    }

}
