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
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
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
import javax.enterprise.inject.spi.Bean;
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


    private static final String CLIENT_PACKAGE_PREFIX = Service.class.getPackageName() + ".";

    private static final Pattern CLIENT_PACKAGE_PATTERN =
        Pattern.compile("^(com\\.oracle\\.bmc\\.([^.]+)\\.(.+))(Async|Client(\\$Builder)?)?$");

    private static final Set<String> CLIENT_PACKAGE_FRAGMENT_DENY_LIST = Set.of("auth", "circuitbreaker");

    private static final TypeLiteral<Event<Object>> EVENT_OBJECT_TYPE_LITERAL = new TypeLiteral<Event<Object>>() {};

    private static final Lookup PUBLIC_LOOKUP = publicLookup();


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


    private void processInjectionPoint(@Observes ProcessInjectionPoint<?, ?> event) throws ClassNotFoundException {
        InjectionPoint ip = event.getInjectionPoint();
        Type baseType = ip.getAnnotated().getBaseType();
        if (baseType instanceof Class) {
            // All OCI constructs we're interested in are non-generic
            // classes.
            Class<?> baseClass = (Class<?>) baseType;
            Set<Annotation> qualifiers = ip.getQualifiers();
            if (AbstractAuthenticationDetailsProvider.class.isAssignableFrom(baseClass)
                || AdpStrategy.builderClasses().contains(baseClass)) {
                this.serviceTaqs.computeIfAbsent(qualifiers, qs -> new ServiceTaqs());
            } else {
                String baseClassName = baseClass.getName();
                Matcher m = CLIENT_PACKAGE_PATTERN.matcher(baseClassName);
                if (m.matches() && !CLIENT_PACKAGE_FRAGMENT_DENY_LIST.contains(m.group(2))) {
                    ServiceTaqs serviceTaqs = this.serviceTaqs.get(qualifiers);
                    if (serviceTaqs == null || serviceTaqs.isEmpty()) {
                        Annotation[] qualifiersArray = qualifiers.toArray(new Annotation[0]);
                        String serviceInterface = m.group(1);
                        Class<?> taqClass =
                            baseClassName.equals(serviceInterface) ? baseClass : loadClass(serviceInterface);
                        TypeAndQualifiers serviceInterfaceTaq = new TypeAndQualifiers(taqClass, qualifiersArray);
                        String serviceClient = serviceInterface + "Client";
                        taqClass =
                            baseClassName.equals(serviceClient) ? baseClass : loadClass(serviceClient);
                        TypeAndQualifiers serviceClientTaq = new TypeAndQualifiers(taqClass, qualifiersArray);
                        String serviceClientBuilder = serviceClient + "$Builder";
                        taqClass =
                            baseClassName.equals(serviceClientBuilder) ? baseClass : loadClass(serviceClientBuilder);
                        TypeAndQualifiers serviceClientBuilderTaq = new TypeAndQualifiers(taqClass, qualifiersArray);
                        String serviceAsyncInterface = serviceInterface + "Async";
                        taqClass =
                            baseClassName.equals(serviceAsyncInterface) ? baseClass : loadClass(serviceAsyncInterface);
                        TypeAndQualifiers serviceAsyncInterfaceTaq = new TypeAndQualifiers(taqClass, qualifiersArray);
                        String serviceAsyncClient = serviceAsyncInterface + "Client";
                        taqClass =
                            baseClassName.equals(serviceAsyncClient) ? baseClass : loadClass(serviceAsyncClient);
                        TypeAndQualifiers serviceAsyncClientTaq = new TypeAndQualifiers(taqClass, qualifiersArray);
                        String serviceAsyncClientBuilder = serviceAsyncClient + "$Builder";
                        taqClass =
                            baseClassName.equals(serviceAsyncClientBuilder) ? baseClass : loadClass(serviceAsyncClientBuilder);
                        TypeAndQualifiers serviceAsyncClientBuilderTaq = new TypeAndQualifiers(taqClass, qualifiersArray);
                        this.serviceTaqs.put(qualifiers,
                                          new ServiceTaqs(serviceInterfaceTaq,
                                                          serviceClientTaq,
                                                          serviceClientBuilderTaq,
                                                          serviceAsyncInterfaceTaq,
                                                          serviceAsyncClientTaq,
                                                          serviceAsyncClientBuilderTaq));
                    }
                }
            }
        }
    }

    private void afterBeanDiscovery(@Observes AfterBeanDiscovery event, BeanManager bm) {
        for (Entry<Set<Annotation>, ServiceTaqs> entry : this.serviceTaqs.entrySet()) {
            Set<Annotation> qualifiers = entry.getKey();
            Annotation[] qualifiersArray = qualifiers.toArray(new Annotation[0]);
            installAdps(event, bm, qualifiersArray);
            ServiceTaqs serviceTaqs = entry.getValue();
            if (!serviceTaqs.isEmpty()) {
                TypeAndQualifiers serviceClientBuilderTaq = serviceTaqs.serviceClientBuilder;
                TypeAndQualifiers serviceClientTaq = serviceTaqs.serviceClient;
                installServiceClientBuilder(event,
                                            bm,
                                            serviceClientBuilderTaq,
                                            serviceClientTaq.toClass());
                TypeAndQualifiers serviceAsyncClientBuilderTaq = serviceTaqs.serviceAsyncClientBuilder;
                TypeAndQualifiers serviceAsyncClientTaq = serviceTaqs.serviceAsyncClient;
                installServiceClientBuilder(event,
                                            bm,
                                            serviceAsyncClientBuilderTaq,
                                            serviceAsyncClientTaq.toClass());
                installServiceClient(event,
                                     bm,
                                     serviceClientTaq,
                                     serviceTaqs.serviceInterface.toClass(),
                                     serviceClientBuilderTaq.toClass());
                installServiceClient(event,
                                     bm,
                                     serviceAsyncClientTaq,
                                     serviceTaqs.serviceAsyncInterface.toClass(),
                                     serviceAsyncClientBuilderTaq.toClass());
            }
        }
    }

    private void afterDeploymentValidation(@Observes AfterDeploymentValidation event) {
        this.serviceTaqs.clear();
    }


    /*
     * Other instance methods.
     */


    private void installAdps(AfterBeanDiscovery event,
                             BeanManager bm,
                             Annotation[] qualifiersArray) {
        Set<Annotation> qualifiers = Set.of(qualifiersArray);
        for (AdpStrategy s : EnumSet.allOf(AdpStrategy.class)) {
            Type builderType = s.builderType();
            if (builderType != null) {
                TypeAndQualifiers builderTaq = new TypeAndQualifiers(builderType, qualifiersArray);
                if (isUnresolved(builderTaq, bm)) {
                    event.addBean()
                        .types(builderType)
                        .qualifiers(qualifiers)
                        .scope(Singleton.class)
                        .produceWith(i -> s.produceBuilder(i, qualifiersArray));
                }
            }
            Type type = s.type();
            TypeAndQualifiers taq = new TypeAndQualifiers(type, qualifiersArray);
            if (isUnresolved(taq, bm)) {
                event.addBean()
                    .types(type)
                    .qualifiers(qualifiers)
                    .scope(Singleton.class)
                    .produceWith(i -> s.produce(i, qualifiersArray));
            }
        }
    }

    private boolean installServiceClientBuilder(AfterBeanDiscovery event,
                                                BeanManager bm,
                                                TypeAndQualifiers serviceClientBuilder,
                                                Class<?> serviceClientClass) {
        if (isUnresolved(serviceClientBuilder, bm)) {
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

    private boolean installServiceClient(AfterBeanDiscovery event,
                                         BeanManager bm,
                                         TypeAndQualifiers serviceClient,
                                         Class<?> serviceInterfaceClass,
                                         Class<?> serviceClientBuilderClass) {
        Annotation[] qualifiersArray = serviceClient.qualifiers();
        Class<?> serviceClientClass = serviceClient.toClass();
        Bean<?> b;
        try {
            b = bm.resolve(bm.getBeans(serviceClientClass, qualifiersArray));
        } catch (AmbiguousResolutionException ambiguousResolutionException) {
            return false;
        }
        if (b == null) {
            Set<Type> types = null;
            // Yay, no user-installed serviceClient bean
            // Is there a user-installed serviceInterface bean? If so, we'll have to limit our serviceClient bean's types.
            try {
                b = bm.resolve(bm.getBeans(serviceInterfaceClass, qualifiersArray));
            } catch (AmbiguousResolutionException ambiguousResolutionException) {
                // There were many user-installed serviceInterface
                // beans.  We're truly hosed, but that will happen
                // later.  We know that this just means we have to
                // limit our types.
                types = Set.of(serviceClientClass);
            }

            if (b == null) {
                assert types == null;
                // No user-installed serviceInterface bean and no user-installed serviceClient bean.  It's a dream come true.
                types = Set.of(serviceClientClass, serviceInterfaceClass);
            } else if (types == null) {
                types = Set.of(serviceClientClass);
            }
            event.addBean()
                .types(types)
                .qualifiers(Set.of(qualifiersArray))
                .scope(Singleton.class)
                .produceWith(i -> produceClient(i, serviceClientBuilderClass))
                .disposeWith(OciExtension::disposeClient);
            return true;
        }
        return false;
    }

    private static Bean<?> resolve(Type type, Annotation[] qualifiers, BeanManager bm) {
        try {
            return bm.resolve(bm.getBeans(type, qualifiers));
        } catch (AmbiguousResolutionException e) {
            return null;
        }
    }

    private static Bean<?> resolve(TypeAndQualifiers taq, BeanManager bm) {
        return resolve(taq.type(), taq.qualifiers(), bm);
    }

    private static boolean isUnresolved(TypeAndQualifiers taq, BeanManager bm) {
        return resolve(taq, bm) == null;
    }


    /*
     * Static methods.
     */


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

    private static Class<?> loadClass(String name) throws ClassNotFoundException {
        return Class.forName(name, false, Thread.currentThread().getContextClassLoader());
    }

    private static <T> void fire(Instance<? super Object> instance, Class<T> type, Annotation[] qualifiers, Object payload) {
        instance.select(EVENT_OBJECT_TYPE_LITERAL).get().select(type, qualifiers).fire(type.cast(payload));
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

        private final TypeAndQualifiers serviceInterface;

        private final TypeAndQualifiers serviceClient;

        private final TypeAndQualifiers serviceClientBuilder;

        private final TypeAndQualifiers serviceAsyncInterface;

        private final TypeAndQualifiers serviceAsyncClient;

        private final TypeAndQualifiers serviceAsyncClientBuilder;

        private final boolean empty;

        private ServiceTaqs() {
            this(null, null, null, null, null, null);
        }

        private ServiceTaqs(TypeAndQualifiers serviceInterface,
                            TypeAndQualifiers serviceClient,
                            TypeAndQualifiers serviceClientBuilder,
                            TypeAndQualifiers serviceAsyncInterface,
                            TypeAndQualifiers serviceAsyncClient,
                            TypeAndQualifiers serviceAsyncClientBuilder) {
            this.serviceInterface = serviceInterface;
            this.serviceClient = serviceClient;
            this.serviceClientBuilder = serviceClientBuilder;
            this.serviceAsyncInterface = serviceAsyncInterface;
            this.serviceAsyncClient = serviceAsyncClient;
            this.serviceAsyncClientBuilder = serviceAsyncClientBuilder;
            this.empty =
                serviceInterface == null
                && serviceClient == null
                && serviceClientBuilder == null
                && serviceAsyncInterface == null
                && serviceAsyncClient == null
                && serviceAsyncClientBuilder == null;
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

        MONITORING(crc -> {
                if ("POST".equalsIgnoreCase(crc.getMethod())) {
                    URI uri = crc.getUri();
                    String host = uri.getHost();
                    if (host != null && host.startsWith("telemetry.")) {
                        String path = uri.getPath();
                        if (path != null && path.endsWith("/metrics")) {
                            return true;
                        }
                    }
                }
                return false;
            },
            h -> "telemetry-ingestion." + h.substring("telemetry.".length())
            );

        private static final int PRE_AUTHENTICATION_PRIORITY = Priorities.AUTHENTICATION - 500; // 1000 - 500 = 500

        private static final Map<String, EndpointAdjuster> ENDPOINT_ADJUSTERS;

        static {
            Map<String, EndpointAdjuster> map = new HashMap<>();
            for (EndpointAdjuster ea : EnumSet.allOf(EndpointAdjuster.class)) {
                map.put(ea.clientBuilderClassName, ea);
                map.put(ea.asyncClientBuilderClassName, ea);
            }
            ENDPOINT_ADJUSTERS = Collections.unmodifiableMap(map);
        }

        private final String clientBuilderClassName;

        private final String asyncClientBuilderClassName;

        private final Predicate<? super ClientRequestContext> p;

        private final UnaryOperator<String> adjuster;

        EndpointAdjuster(Predicate<? super ClientRequestContext> p,
                         UnaryOperator<String> adjuster) {
            String lowerCaseName = this.name().toLowerCase();
            String titleCaseName = Character.toUpperCase(lowerCaseName.charAt(0)) + lowerCaseName.substring(1);
            String prefix = CLIENT_PACKAGE_PREFIX + lowerCaseName + "." + titleCaseName;
            this.clientBuilderClassName = prefix + "Client$Builder";
            this.asyncClientBuilderClassName = prefix + "AsyncClient$Builder";
            this.p = p;
            this.adjuster = adjuster;
        }

        @Override // ClientConfigurator
        public void customizeBuilder(ClientBuilder builder) {
            builder.register(this, PRE_AUTHENTICATION_PRIORITY);
        }

        @Override // ClientConfigurator
        public void customizeClient(Client client) {
        }

        @Override // Predicate
        public final boolean test(ClientRequestContext crc) {
            return this.p.test(crc);
        }

        @Override // ClientRequestFilter
        public final void filter(ClientRequestContext crc) throws IOException {
            if (this.test(crc)) {
                this.adjust(crc, crc.getUri().getHost());
            }
        }

        private void adjust(ClientRequestContext crc, String hostname) {
            crc.setUri(UriBuilder.fromUri(crc.getUri()).host(this.adjuster.apply(hostname)).build());
        }

        private static EndpointAdjuster of(String clientBuilderClassName) {
            return ENDPOINT_ADJUSTERS.get(clientBuilderClassName);
        }

    }

    // https://docs.oracle.com/en-us/iaas/tools/java/2.18.0/com/oracle/bmc/monitoring/MonitoringAsync.html#postMetricData-com.oracle.bmc.monitoring.requests.PostMetricDataRequest-com.oracle.bmc.responses.AsyncHandler-
    //
    // "The endpoints for this [particular POST] operation differ from
    // other Monitoring operations. Replace the string telemetry with
    // telemetry-ingestion in the endpoint, as in the following
    // example:
    // https://telemetry-ingestion.eu-frankfurt-1.oraclecloud.com"
    //
    // Doing this in an application that uses a MonitoringClient or a
    // MonitoringAsyncClient from several threads, not all of which
    // are POSTing, is, of course, unsafe.  This filter repairs this
    // flaw and is installed by OCI-SDK-supported client customization
    // facilities.
    //
    // The documented instructions above are imprecise.  This filter
    // implements what it seems was meant.
    //
    // The intent seems to be to replace "telemetry." with
    // "telemetry-ingestion.", and even that could run afoul of things
    // in the future (consider "super-telemetry.oraclecloud.com" where
    // these rules might not be intended to apply).
    //
    // Additionally, we want to guard against a future where the
    // hostname might *already* have "telemetry-ingestion" in it, so
    // clearly we cannot simply replace "telemetry", wherever it
    // occurs, with "telemetry-ingestion".
    //
    // So we reinterpret the above to mean: "In the hostname, replace
    // the first occurrence of the String matching the regex
    // ^telemetry\. with telemetry-ingestion." (but without using
    // regexes, because they're overkill in this situation).
    //
    // This filter is written defensively with respect to nulls to
    // ensure maximum non-interference: it gets out of the way at the
    // first sign of trouble.
    //
    // This method is safe for concurrent use by multiple threads.

}
