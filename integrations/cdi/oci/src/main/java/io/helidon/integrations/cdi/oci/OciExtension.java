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
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;
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
import javax.enterprise.inject.spi.BeforeBeanDiscovery;
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

import com.oracle.bmc.Region;
import com.oracle.bmc.Service;
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
import com.oracle.bmc.common.ClientBuilderBase;
import com.oracle.bmc.http.ClientConfigurator;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

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
 * <li>{@code com.oracle.bmc.example.ExampleClient.Builder}</li>
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


    private static final Logger LOGGER = Logger.getLogger(OciExtension.class.getName());

    // Evaluates to "com.oracle.bmc." as of the current version of the
    // OCI Java SDK.
    private static final String OCI_PACKAGE_PREFIX = Service.class.getPackageName() + ".";

    // For an OCI service conceptually named "Example" in an
    // OCI_PACKAGE_PREFIX subpackage named "example":
    //
    // Match Strings expected to be class names that start with
    // OCI_PACKAGE_PREFIX...
    //
    // (1) ...followed by the service client package fragment ("example")...
    // (2) ...followed by a period (".")...
    // (3) ...followed by one or more of the following:
    //       "Example",
    //       "ExampleAsync",
    //       "ExampleAsyncClient",
    //       "ExampleAsyncClientBuilder", // com.oracle.bmc.streaming doesn't use a nested Builder class; all other services do
    //       "ExampleAsyncClient$Builder",
    //       "ExampleClient",
    //       "ExampleClientBuilder"...
    //       "ExampleClient$Builder"...
    // (4) ...followed by the end of String.
    //
    // Capturing group 0: the matched class name
    // Capturing group 1: Capturing group 2 and base noun ("example.Example")
    // Capturing group 2: "example"
    private static final Pattern SERVICE_CLIENT_CLASS_NAME_SUBSTRING_PATTERN =
        Pattern.compile("^(([^.]+)" // (1)
                        + "\\." // (2)
                        + ".+?)(?:Async)?(?:Client(?:\\$?Builder)?)?" // (3)
                        + "$"); // (4)

    private static final TypeLiteral<Event<Object>> EVENT_OBJECT_TYPE_LITERAL = new TypeLiteral<Event<Object>>() {};

    private static final Lookup PUBLIC_LOOKUP = MethodHandles.publicLookup();

    private static final Annotation[] EMPTY_ANNOTATION_ARRAY = new Annotation[0];


    /*
     * Instance fields.
     */


    private final Set<ServiceTaqs> serviceTaqs;

    private Set<String> additionalVetoes;

    private boolean lenient;


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
        this.lenient = true;
        this.additionalVetoes = Set.of();
        this.serviceTaqs = new HashSet<>();
    }


    /*
     * Container lifecycle observer methods.
     */


    private void beforeBeanDiscovery(@Observes BeforeBeanDiscovery event) {
        try {
            this.lenient =
                ConfigProvider.getConfig()
                .getOptionalValue(this.getClass().getName() + ".lenient", Boolean.class)
                .orElse(Boolean.TRUE)
                .booleanValue();
        } catch (IllegalArgumentException conversionException) {
            this.lenient = true;
        }
        this.additionalVetoes = ConfigProvider.getConfig()
            .getOptionalValue("oci.vetoes", String[].class)
            .<Set<String>>map(Set::of)
            .orElse(Set.of());
    }

    private void processInjectionPoint(@Observes ProcessInjectionPoint<?, ?> event) {
        InjectionPoint ip = event.getInjectionPoint();
        Type baseType = ip.getAnnotated().getBaseType();
        if (!(baseType instanceof Class)) {
            // Optimization: all OCI constructs we're interested in
            // are non-generic classes.
            return;
        }
        Class<?> baseClass = (Class<?>) baseType;
        String baseClassName = baseClass.getName();
        if (!baseClassName.startsWith(OCI_PACKAGE_PREFIX)) {
            // Optimization: the only classes we're interested in are
            // OCI classes.
            return;
        }
        Set<Annotation> qualifiers = ip.getQualifiers();
        if (AbstractAuthenticationDetailsProvider.class.isAssignableFrom(baseClass)
            || AdpSelectionStrategy.builderClasses().contains(baseClass)) {
            // Use an "empty" ServiceTaqs as an indicator of
            // demand for some kind of
            // AbstractAuthenticationDetailsProvider (or a
            // relevant builder).
            this.serviceTaqs.add(new ServiceTaqs(qualifiers.toArray(EMPTY_ANNOTATION_ARRAY)));
            return;
        }
        Matcher m = SERVICE_CLIENT_CLASS_NAME_SUBSTRING_PATTERN.matcher(baseClassName.substring(OCI_PACKAGE_PREFIX.length()));
        if (!m.matches() || this.isVetoed(baseClass)) {
            return;
        }
        this.processValidInjectionPoint(event::addDefinitionError, baseClass, qualifiers, OCI_PACKAGE_PREFIX + m.group(1));
    }

    private void processValidInjectionPoint(Consumer<? super ClassNotFoundException> errorHandler,
                                            Class<?> baseClass,
                                            Set<Annotation> qualifiers,
                                            String serviceInterface) {
        Annotation[] qualifiersArray = qualifiers.toArray(EMPTY_ANNOTATION_ARRAY);
        boolean lenient = this.lenient;
        ServiceTaqs serviceTaqsForAuth = null;
        // Create types-and-qualifiers for, e.g.:
        //   ....example.Example
        //   ....example.ExampleClient
        //   ....example.ExampleClient$Builder
        Class<?> serviceInterfaceClass = toClassUnresolved(errorHandler, baseClass, serviceInterface, lenient);
        if (serviceInterfaceClass != null && serviceInterfaceClass.isInterface()) {
            String serviceClient = serviceInterface + "Client";
            Class<?> serviceClientClass = toClassUnresolved(errorHandler, baseClass, serviceClient, lenient);
            if (serviceClientClass != null && serviceInterfaceClass.isAssignableFrom(serviceClientClass)) {
                Class<?> serviceClientBuilderClass = toClassUnresolved(errorHandler, baseClass, serviceClient + "$Builder", true);
                if (serviceClientBuilderClass == null) {
                    serviceClientBuilderClass = toClassUnresolved(errorHandler, serviceClient + "Builder", lenient);
                }
                if (serviceClientBuilderClass != null
                    && ClientBuilderBase.class.isAssignableFrom(serviceClientBuilderClass)) {
                    this.serviceTaqs.add(new ServiceTaqs(qualifiersArray,
                                                         serviceInterfaceClass,
                                                         serviceClientClass,
                                                         serviceClientBuilderClass));
                    // Use an "empty" ServiceTaqs as an indicator of
                    // demand for some kind of
                    // AbstractAuthenticationDetailsProvider (or a
                    // relevant builder).
                    serviceTaqsForAuth = new ServiceTaqs(qualifiersArray);
                    this.serviceTaqs.add(serviceTaqsForAuth);
                }
            }
        }
        // Create types-and-qualifiers for, e.g.:
        //   ....example.ExampleAsync
        //   ....example.ExampleAsyncClient
        //   ....example.ExampleAsyncClient$Builder
        String serviceAsyncInterface = serviceInterface + "Async";
        Class<?> serviceAsyncInterfaceClass = toClassUnresolved(errorHandler, baseClass, serviceAsyncInterface, lenient);
        if (serviceAsyncInterfaceClass != null && serviceAsyncInterfaceClass.isInterface()) {
            String serviceAsyncClient = serviceAsyncInterface + "Client";
            Class<?> serviceAsyncClientClass = toClassUnresolved(errorHandler, baseClass, serviceAsyncClient, lenient);
            if (serviceAsyncClientClass != null
                && serviceAsyncInterfaceClass.isAssignableFrom(serviceAsyncClientClass)) {
                Class<?> serviceAsyncClientBuilderClass =
                    toClassUnresolved(errorHandler, baseClass, serviceAsyncClient + "$Builder", true);
                if (serviceAsyncClientBuilderClass == null) {
                    serviceAsyncClientBuilderClass = toClassUnresolved(errorHandler, serviceAsyncClient + "Builder", lenient);
                }
                if (serviceAsyncClientBuilderClass != null
                    && ClientBuilderBase.class.isAssignableFrom(serviceAsyncClientBuilderClass)) {
                    this.serviceTaqs.add(new ServiceTaqs(qualifiersArray,
                                                         serviceAsyncInterfaceClass,
                                                         serviceAsyncClientClass,
                                                         serviceAsyncClientBuilderClass));
                    if (serviceTaqsForAuth == null) {
                        // Use an "empty" ServiceTaqs as
                        // an indicator of demand for some
                        // kind of
                        // AbstractAuthenticationDetailsProvider
                        // (or a relevant builder).
                        this.serviceTaqs.add(new ServiceTaqs(qualifiersArray));
                    }
                }
            }
        }
    }

    private void afterBeanDiscovery(@Observes AfterBeanDiscovery event, BeanManager bm) {
        boolean lenient = this.lenient;
        for (ServiceTaqs serviceTaqs : this.serviceTaqs) {
            if (serviceTaqs.isEmpty()) {
                installAdps(event, bm, serviceTaqs.qualifiers());
            } else {
                TypeAndQualifiers serviceClientBuilder = serviceTaqs.serviceClientBuilder();
                TypeAndQualifiers serviceClient = serviceTaqs.serviceClient();
                TypeAndQualifiers serviceInterface = serviceTaqs.serviceInterface();
                installServiceClientBuilder(event,
                                            bm,
                                            serviceClientBuilder,
                                            serviceClient,
                                            lenient);
                installServiceClient(event,
                                     bm,
                                     serviceClient,
                                     serviceInterface,
                                     serviceClientBuilder);
            }
        }
    }

    private void afterDeploymentValidation(@Observes AfterDeploymentValidation event) {
        this.serviceTaqs.clear();
    }


    /*
     * Additional instance methods.
     */


    /**
     * Returns {@code true} if the supplied {@link Class} is known to
     * not be directly related to an Oracle Cloud Infrastructure
     * service.
     *
     * <p>The check is fast and deliberately not exhaustive.</p>
     *
     * @param c the {@link Class} in question; must not be {@code
     * null}
     *
     * @return {@code true} if the supplied {@link Class} is known to
     * not be directly related to an Oracle Cloud Infrastructure
     * service
     *
     * @exception NullPointerException if {@code c} is {@code null}
     */
    private boolean isVetoed(Class<?> c) {
        // See
        // https://docs.oracle.com/en-us/iaas/tools/java/latest/overview-summary.html#:~:text=Oracle%20Cloud%20Infrastructure%20Common%20Runtime.
        // None of these packages contains OCI service clients or
        // service client interfaces or service client builders. There
        // are other packages (com.oracle.bmc.encryption, as an
        // arbitrary example) that should also conceptually be vetoed.
        // This method does not currently veto all of them, nor is it
        // clear that it ever could.  The strategy employed here,
        // however, vetoes quite a large number of them correctly and
        // very efficiently before more sophisticated tests are
        // employed.
        //
        // "Veto" in this context means only that this extension will
        // not further process the class in question.  The class
        // remains eligible for further processing; i.e. this is not a
        // CDI veto.
        return
            equals(Service.class.getProtectionDomain(), c.getProtectionDomain())
            || this.additionalVetoes.contains(c.getName());
    }

    /*
     * Static methods.
     */


    private static void installAdps(AfterBeanDiscovery event, BeanManager bm, Annotation[] qualifiersArray) {
        Set<Annotation> qualifiers = Set.of(qualifiersArray);
        for (AdpSelectionStrategy s : EnumSet.allOf(AdpSelectionStrategy.class)) {
            Type builderType = s.builderType();
            if (builderType != null) {
                TypeAndQualifiers builderTaq = new TypeAndQualifiers(builderType, qualifiersArray);
                if (isUnsatisfied(bm, builderTaq)) {
                    event.addBean()
                        .types(builderType)
                        .qualifiers(qualifiers)
                        .scope(Singleton.class)
                        .produceWith(i -> s.produceBuilder(i, qualifiersArray));
                }
            }
            Type type = s.type();
            TypeAndQualifiers taq = new TypeAndQualifiers(type, qualifiersArray);
            if (isUnsatisfied(bm, taq)) {
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
                                                       TypeAndQualifiers serviceClient,
                                                       boolean lenient) {
        if (serviceClient != null) {
            return installServiceClientBuilder(event,
                                               bm,
                                               serviceClientBuilder,
                                               serviceClient.toClass(),
                                               lenient);
        }
        return false;
    }

    private static boolean installServiceClientBuilder(AfterBeanDiscovery event,
                                                       BeanManager bm,
                                                       TypeAndQualifiers serviceClientBuilder,
                                                       Class<?> serviceClientClass,
                                                       boolean lenient) {
        if (serviceClientBuilder != null && isUnsatisfied(bm, serviceClientBuilder)) {
            Class<?> serviceClientBuilderClass = serviceClientBuilder.toClass();
            MethodHandle builderMethod;
            try {
                builderMethod = PUBLIC_LOOKUP.findStatic(serviceClientClass, "builder", methodType(serviceClientBuilderClass));
            } catch (ReflectiveOperationException reflectiveOperationException) {
                if (lenient) {
                    if (LOGGER.isLoggable(Level.WARNING)) {
                        LOGGER.logp(Level.WARNING,
                                    OciExtension.class.getName(),
                                    "installServiceClientBuilder",
                                    reflectiveOperationException.getMessage(),
                                    reflectiveOperationException);
                    }
                } else {
                    event.addDefinitionError(reflectiveOperationException);
                }
                return false;
            }
            Annotation[] qualifiersArray = serviceClientBuilder.qualifiers();
            event.addBean()
                .types(Set.of(serviceClientBuilderClass))
                .qualifiers(Set.of(qualifiersArray))
                .scope(Singleton.class)
                .produceWith(i -> produceClientBuilder(i, builderMethod, qualifiersArray));
            return true;
        }
        return false;
    }

    private static boolean installServiceClient(AfterBeanDiscovery event,
                                                BeanManager bm,
                                                TypeAndQualifiers serviceClient,
                                                TypeAndQualifiers serviceInterface,
                                                TypeAndQualifiers serviceClientBuilder) {
        if (serviceInterface != null && serviceClientBuilder != null) {
            return installServiceClient(event,
                                        bm,
                                        serviceClient,
                                        serviceInterface.type(),
                                        serviceClientBuilder.toClass());
        }
        return false;
    }

    private static boolean installServiceClient(AfterBeanDiscovery event,
                                                BeanManager bm,
                                                TypeAndQualifiers serviceClient,
                                                Type serviceInterfaceType,
                                                Class<?> serviceClientBuilderClass) {
        if (serviceClient != null) {
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
                        .produceWith(i -> produceClient(i, serviceClientBuilderClass, qualifiersArray))
                        .disposeWith(OciExtension::disposeClient);
                    return true;
                }
            } catch (AmbiguousResolutionException ambiguousResolutionException) {
            }
        }
        return false;
    }

    private static Object produceClientBuilder(Instance<? super Object> instance,
                                               MethodHandle builderMethod,
                                               Annotation[] qualifiers) {
        try {
            ClientBuilderBase<?, ?> builderInstance = (ClientBuilderBase<?, ?>) builderMethod.invoke();
            // Permit arbitrary customization.
            fire(instance, qualifiers, builderInstance);
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

    private static Object produceClient(Instance<? super Object> instance, Class<?> builderClass, Annotation[] qualifiersArray) {
        return
            ((ClientBuilderBase<?, ?>) instance.select(builderClass, qualifiersArray).get())
            .build(instance.select(AbstractAuthenticationDetailsProvider.class, qualifiersArray).get());
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

    @SuppressWarnings("unchecked")
    private static <T> void fire(Instance<? super Object> instance, Annotation[] qualifiers, T payload) {
        instance.select(EVENT_OBJECT_TYPE_LITERAL).get().select((Class<T>) payload.getClass(), qualifiers).fire(payload);
    }

    private static boolean isUnsatisfied(BeanManager bm, TypeAndQualifiers taq) {
        return isUnsatisfied(bm, taq.type(), taq.qualifiers());
    }

    private static boolean isUnsatisfied(BeanManager bm, Type type) {
        try {
            return bm.resolve(bm.getBeans(type)) == null;
        } catch (AmbiguousResolutionException ambiguousResolutionException) {
            return false;
        }
    }

    private static boolean isUnsatisfied(BeanManager bm, Type type, Annotation[] qualifiers) {
        try {
            return bm.resolve(bm.getBeans(type, qualifiers)) == null;
        } catch (AmbiguousResolutionException ambiguousResolutionException) {
            return false;
        }
    }

    private static boolean equals(ProtectionDomain pd0, ProtectionDomain pd1) {
        if (pd0 == null) {
            return pd1 == null;
        } else if (pd1 == null) {
            return false;
        }
        return equals(pd0.getCodeSource(), pd1.getCodeSource());
    }

    private static boolean equals(CodeSource cs0, CodeSource cs1) {
        if (cs0 == null) {
            return cs1 == null;
        } else if (cs1 == null) {
            return false;
        }
        return equals(cs0.getLocation(), cs1.getLocation());
    }

    private static boolean equals(URL url0, URL url1) {
        if (url0 == null) {
            return url1 == null;
        } else if (url1 == null) {
            return false;
        }
        try {
            return Objects.equals(url0.toURI(), url1.toURI());
        } catch (URISyntaxException uriSyntaxException) {
            // Use URL#equals(Object) only as a last resort, since it
            // involves DNS lookups (!).
            return url0.equals(url1);
        }
    }

    private static Class<?> toClassUnresolved(Consumer<? super ClassNotFoundException> errorHandler,
                                              String name,
                                              boolean lenient) {
        return toClassUnresolved(errorHandler, null, name, lenient);
    }

    private static Class<?> toClassUnresolved(Consumer<? super ClassNotFoundException> errorHandler,
                                              Class<?> referenceClass,
                                              String name,
                                              boolean lenient) {
        if (referenceClass != null && referenceClass.getName().equals(name)) {
            return referenceClass;
        }
        try {
            return loadClassUnresolved(name);
        } catch (ClassNotFoundException classNotFoundException) {
            if (lenient) {
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.logp(Level.FINE,
                                OciExtension.class.getName(),
                                "toClass",
                                classNotFoundException.getMessage(),
                                classNotFoundException);
                }
            } else {
                errorHandler.accept(classNotFoundException);
            }
            return null;
        }
    }

    private static Class<?> loadClassUnresolved(String name) throws ClassNotFoundException {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        return Class.forName(name, false, cl == null ? OciExtension.class.getClassLoader() : cl);
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
                this.qualifiers = qualifiers.clone();
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
            return this.qualifiers.clone();
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


        private final Annotation[] qualifiers;

        private final TypeAndQualifiers serviceInterface;

        private final TypeAndQualifiers serviceClient;

        private final TypeAndQualifiers serviceClientBuilder;

        private final boolean empty;

        private final int hashCode;


        /*
         * Constructors.
         */


        private ServiceTaqs() {
            this(EMPTY_ANNOTATION_ARRAY,
                 null,
                 null,
                 null);
        }

        private ServiceTaqs(Annotation[] qualifiers) {
            this(qualifiers,
                 null,
                 null,
                 null);
        }

        private ServiceTaqs(Annotation[] qualifiers,
                            Type serviceInterface,
                            Type serviceClient,
                            Type serviceClientBuilder) {
            qualifiers = qualifiers == null ? EMPTY_ANNOTATION_ARRAY : qualifiers;
            this.qualifiers = qualifiers;
            boolean empty = true;
            if (serviceInterface == null) {
                this.serviceInterface = null;
            } else {
                this.serviceInterface = new TypeAndQualifiers(serviceInterface, qualifiers);
                if (empty) {
                    empty = false;
                }
            }
            if (serviceClient == null) {
                this.serviceClient = null;
            } else {
                this.serviceClient = new TypeAndQualifiers(serviceClient, qualifiers);
                if (empty) {
                    empty = false;
                }
            }
            if (serviceClientBuilder == null) {
                this.serviceClientBuilder = null;
            } else {
                this.serviceClientBuilder = new TypeAndQualifiers(serviceClientBuilder, qualifiers);
                if (empty) {
                    empty = false;
                }
            }
            this.empty = empty;
            this.hashCode = this.computeHashCode();
        }


        /*
         * Instance methods.
         */


        private Annotation[] qualifiers() {
            return this.qualifiers;
        }

        private TypeAndQualifiers serviceInterface() {
            return this.serviceInterface;
        }

        private TypeAndQualifiers serviceClient() {
            return this.serviceClient;
        }

        private TypeAndQualifiers serviceClientBuilder() {
            return this.serviceClientBuilder;
        }

        private boolean isEmpty() {
            return this.empty;
        }

        @Override // Object
        public final int hashCode() {
            return this.hashCode;
        }

        private int computeHashCode() {
            int hashCode = 17;
            Annotation[] qualifiersArray = this.qualifiers();
            int c = qualifiersArray == null ? 0 : Arrays.hashCode(qualifiersArray);
            hashCode = 37 * hashCode + c;
            TypeAndQualifiers x = this.serviceInterface();
            c = x == null ? 0 : x.hashCode();
            hashCode = 37 * hashCode + c;
            x = this.serviceClient();
            c = x == null ? 0 : x.hashCode();
            hashCode = 37 * hashCode + c;
            x = this.serviceClientBuilder();
            c = x == null ? 0 : x.hashCode();
            hashCode = 37 * hashCode + c;
            return hashCode;
        }

        @Override // Object
        public final boolean equals(Object other) {
            if (other == this) {
                return true;
            } else if (other != null && other.getClass() == this.getClass()) {
                ServiceTaqs her = (ServiceTaqs) other;
                return
                    Arrays.equals(this.qualifiers(), her.qualifiers())
                    && Objects.equals(this.serviceInterface(), her.serviceInterface())
                    && Objects.equals(this.serviceClient(), her.serviceClient())
                    && Objects.equals(this.serviceClientBuilder(), her.serviceClientBuilder());
            } else {
                return false;
            }
        }

    }

    private enum AdpSelectionStrategy {


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
            boolean isAvailable(Instance<? super Object> instance, Config config, Annotation[] qualifiersArray) {
                // See
                // https://docs.oracle.com/en-us/iaas/tools/java/latest/com/oracle/bmc/auth/SimpleAuthenticationDetailsProvider.SimpleAuthenticationDetailsProviderBuilder.html#method.summary
                //
                // ".auth." keys are for backwards compatibility with a
                // prior OCI-related extension.
                return
                    super.isAvailable(instance, config, qualifiersArray)
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
            AbstractAuthenticationDetailsProvider produce(Instance<? super Object> instance,
                                                          Config config,
                                                          Annotation[] qualifiersArray) {
                return instance.select(SimpleAuthenticationDetailsProviderBuilder.class, qualifiersArray).get().build();
            }

            @Override
            @SuppressWarnings("checkstyle:LineLength")
            SimpleAuthenticationDetailsProviderBuilder produceBuilder(Instance<? super Object> instance,
                                                                      Config c,
                                                                      Annotation[] qualifiersArray) {
                SimpleAuthenticationDetailsProviderBuilder b = SimpleAuthenticationDetailsProvider.builder();
                // See
                // https://docs.oracle.com/en-us/iaas/tools/java/latest/com/oracle/bmc/auth/SimpleAuthenticationDetailsProvider.SimpleAuthenticationDetailsProviderBuilder.html#method.summary
                //
                // Some fallback logic is for backwards compatibility
                // with a prior OCI-related extension.
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
                fire(instance, qualifiersArray, b);
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
            boolean isAvailable(Instance<? super Object> instance, Config config, Annotation[] qualifiersArray) {
                if (super.isAvailable(instance, config, qualifiersArray)) {
                    try {
                        this.select(instance, config, qualifiersArray);
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
            Object produceBuilder(Instance<? super Object> instance, Annotation[] qualifiersArray) {
                throw new UnsupportedOperationException();
            }

            @Override
            Object produceBuilder(Instance<? super Object> instance, Config config, Annotation[] qualifiersArray) {
                throw new UnsupportedOperationException();
            }

            @Override
            AbstractAuthenticationDetailsProvider produce(Instance<? super Object> instance,
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
                    // The underlying ConfigFileReader that does the real work
                    // does not throw a FileNotFoundException in this case (as
                    // it probably should).  We have no choice but to parse
                    // the error message.  See
                    // https://github.com/oracle/oci-java-sdk/blob/vlatest/bmc-common/src/main/java/com/oracle/bmc/ConfigFileReader.java#L94-L98.
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
            boolean isAvailable(Instance<? super Object> instance, Config config, Annotation[] qualifiersArray) {
                if (super.isAvailable(instance, config, qualifiersArray)) {
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
                                                                                  Annotation[] qualifiersArray) {
                return this.produceBuilder(instance, null, qualifiersArray); // no need to find Config; it's not used
            }

            @Override
            InstancePrincipalsAuthenticationDetailsProviderBuilder produceBuilder(Instance<? super Object> instance,
                                                                                  Config config,
                                                                                  Annotation[] qualifiersArray) {
                InstancePrincipalsAuthenticationDetailsProviderBuilder builder =
                    InstancePrincipalsAuthenticationDetailsProvider.builder();
                fire(instance, qualifiersArray, builder);
                return builder;
            }

            @Override
            AbstractAuthenticationDetailsProvider produce(Instance<? super Object> instance,
                                                          Config config,
                                                          Annotation[] qualifiersArray) {
                return
                    instance.select(InstancePrincipalsAuthenticationDetailsProviderBuilder.class, qualifiersArray).get().build();
            }
        },

        RESOURCE_PRINCIPAL(ResourcePrincipalAuthenticationDetailsProvider.class,
                           ResourcePrincipalAuthenticationDetailsProviderBuilder.class) {

            @Override
            boolean isAvailable(Instance<? super Object> instance, Config config, Annotation[] qualifiersArray) {
                return
                    super.isAvailable(instance, config, qualifiersArray)
                    // https://github.com/oracle/oci-java-sdk/blob/v2.18.0/bmc-common/src/main/java/com/oracle/bmc/auth/ResourcePrincipalAuthenticationDetailsProvider.java#L246-L251
                    && System.getenv("OCI_RESOURCE_PRINCIPAL_VERSION") != null;
            }

            @Override
            ResourcePrincipalAuthenticationDetailsProviderBuilder produceBuilder(Instance<? super Object> instance,
                                                                                 Annotation[] qualifiersArray) {
                return this.produceBuilder(instance, null, qualifiersArray); // no need to find Config; it's not used
            }

            @Override
            ResourcePrincipalAuthenticationDetailsProviderBuilder produceBuilder(Instance<? super Object> instance,
                                                                                 Config config,
                                                                                 Annotation[] qualifiersArray) {
                ResourcePrincipalAuthenticationDetailsProviderBuilder builder =
                    ResourcePrincipalAuthenticationDetailsProvider.builder();
                fire(instance, qualifiersArray, builder);
                return builder;
            }

            @Override
            AbstractAuthenticationDetailsProvider produce(Instance<? super Object> instance,
                                                          Config config,
                                                          Annotation[] qualifiersArray) {
                return
                    instance.select(ResourcePrincipalAuthenticationDetailsProviderBuilder.class, qualifiersArray).get().build();
            }
        },

        AUTO(AbstractAuthenticationDetailsProvider.class, true) {

            @Override
            Object produceBuilder(Instance<? super Object> instance, Config config, Annotation[] qualifiersArray) {
                throw new UnsupportedOperationException();
            }

            @Override
            @SuppressWarnings("fallthrough") // note
            AbstractAuthenticationDetailsProvider produce(Instance<? super Object> instance,
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
                        // No availability check on purpose.  We have
                        // only one strategy.  It is not itself AUTO
                        // so there's no fallback, so we don't try to
                        // help out in any way.
                        return strategy.select(instance, config, qualifiersArray);
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
                        if (s != this && (!i.hasNext() || s.isAvailable(instance, config, qualifiersArray))) {
                            return s.select(instance, config, qualifiersArray);
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


        Type type() {
            return this.type;
        }

        Type builderType() {
            return this.builderType;
        }

        final boolean isAbstract() {
            return this.isAbstract;
        }

        boolean isAvailable(Instance<? super Object> instance, Config config, Annotation[] qualifiersArray) {
            return !this.isAbstract();
        }

        AbstractAuthenticationDetailsProvider select(Instance<? super Object> instance,
                                                     Config config,
                                                     Annotation[] qualifiersArray) {
            return instance.select(this.type, qualifiersArray).get();
        }

        Object produceBuilder(Instance<? super Object> instance, Annotation[] qualifiersArray) {
            return this.produceBuilder(instance, instance.select(Config.class).get(), qualifiersArray);
        }

        abstract Object produceBuilder(Instance<? super Object> instance, Config config, Annotation[] qualifiersArray);

        AbstractAuthenticationDetailsProvider produce(Instance<? super Object> instance, Annotation[] qualifiersArray) {
            return this.produce(instance, instance.select(Config.class).get(), qualifiersArray);
        }

        abstract AbstractAuthenticationDetailsProvider produce(Instance<? super Object> instance,
                                                               Config config,
                                                               Annotation[] qualifiersArray);


        /*
         * Static methods.
         */


        private static Set<Class<?>> builderClasses() {
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
        // https://docs.oracle.com/en-us/iaas/tools/java/latest/com/oracle/bmc/monitoring/MonitoringAsync.html#postMetricData-com.oracle.bmc.monitoring.requests.PostMetricDataRequest-com.oracle.bmc.responses.AsyncHandler-:
        //
        //   The endpoints for this [particular POST] operation differ
        //   from other Monitoring operations. Replace the string
        //   telemetry with telemetry-ingestion in the endpoint, as in
        //   the following example:
        //
        //   https://telemetry-ingestion.eu-frankfurt-1.oraclecloud.com
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
