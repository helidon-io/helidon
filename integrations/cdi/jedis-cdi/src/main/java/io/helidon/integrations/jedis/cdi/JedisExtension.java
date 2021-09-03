/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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
package io.helidon.integrations.jedis.cdi;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Dependent;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.CreationException;
import javax.enterprise.inject.Default;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.literal.NamedLiteral;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.enterprise.inject.spi.ProcessInjectionPoint;
import javax.inject.Named;
import javax.inject.Provider;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocketFactory;

import org.eclipse.microprofile.config.Config;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.Protocol;

/**
 * An {@link javax.enterprise.inject.spi.Extension} providing CDI
 * integration for the <a
 * href="https://github.com/xetorthio/jedis/blob/master/README.md">Jedis
 * Redis client</a>.
 *
 * @see <a
 * href="https://github.com/xetorthio/jedis/wiki/Getting-started#using-jedis-in-a-multithreaded-environment">Using
 * Jedis in a multithreaded environment</a>
 */
public class JedisExtension implements javax.enterprise.inject.spi.Extension {

    private static final Map<Class<?>, Map<String, Class<?>>> CONVERSION_TYPES = new LinkedHashMap<>();

    static {

        final Map<String, Class<?>> jedisPoolConversionTypes = new HashMap<>();
        jedisPoolConversionTypes.put("host", String.class);
        jedisPoolConversionTypes.put("port", Integer.class);
        jedisPoolConversionTypes.put("connectionTimeout", Integer.class);
        jedisPoolConversionTypes.put("socketTimeout", Integer.class);
        jedisPoolConversionTypes.put("password", String.class);
        jedisPoolConversionTypes.put("database", Integer.class);
        jedisPoolConversionTypes.put("clientName", String.class);
        jedisPoolConversionTypes.put("ssl", Boolean.class);
        CONVERSION_TYPES.put(JedisPool.class, jedisPoolConversionTypes);

    }

    private final Set<String> instanceNames;

    /**
     * Creates a new {@link JedisExtension}.
     */
    public JedisExtension() {
        super();
        this.instanceNames = new HashSet<>();
    }

    private <T extends Jedis> void processJedisInjectionPoint(@Observes final ProcessInjectionPoint<?, T> e) {
        if (e != null) {
            final InjectionPoint injectionPoint = e.getInjectionPoint();
            if (injectionPoint != null) {
                final Type type = injectionPoint.getType();
                assert type instanceof Class;
                assert Jedis.class.isAssignableFrom((Class<?>) type);
                final Set<Annotation> qualifiers = injectionPoint.getQualifiers();
                for (final Annotation qualifier : qualifiers) {
                    final String instanceName;
                    if (qualifier instanceof Default) {
                        instanceName = "default";
                    } else if (qualifier instanceof Named) {
                        instanceName = ((Named) qualifier).value();
                    } else {
                        instanceName = null;
                    }
                    if (instanceName != null && !instanceName.isEmpty()) {
                        this.instanceNames.add(instanceName);
                    }
                }
            }
        }
    }

    private <T extends Provider<Jedis>> void processJedisProviderInjectionPoint(@Observes final ProcessInjectionPoint<?, T> e) {
        if (e != null) {
            final InjectionPoint injectionPoint = e.getInjectionPoint();
            if (injectionPoint != null) {
                final Type type = injectionPoint.getType();
                assert type instanceof ParameterizedType;
                assert Provider.class.isAssignableFrom((Class<?>) ((ParameterizedType) type).getRawType());
                final Set<Annotation> qualifiers = injectionPoint.getQualifiers();
                for (final Annotation qualifier : qualifiers) {
                    final String instanceName;
                    if (qualifier instanceof Default) {
                        instanceName = "default";
                    } else if (qualifier instanceof Named) {
                        instanceName = ((Named) qualifier).value();
                    } else {
                        instanceName = null;
                    }
                    if (instanceName != null && !instanceName.isEmpty()) {
                        this.instanceNames.add(instanceName);
                    }
                }
            }
        }
    }


    private <T extends JedisPool> void processJedisPoolInjectionPoint(@Observes final ProcessInjectionPoint<?, T> e) {
        if (e != null) {
            final InjectionPoint injectionPoint = e.getInjectionPoint();
            if (injectionPoint != null) {
                final Type type = injectionPoint.getType();
                assert type instanceof Class;
                assert JedisPool.class.isAssignableFrom((Class<?>) type);
                final Set<Annotation> qualifiers = injectionPoint.getQualifiers();
                for (final Annotation qualifier : qualifiers) {
                    final String instanceName;
                    if (qualifier instanceof Default) {
                        instanceName = "default";
                    } else if (qualifier instanceof Named) {
                        instanceName = ((Named) qualifier).value();
                    } else {
                        instanceName = null;
                    }
                    if (instanceName != null && !instanceName.isEmpty()) {
                        this.instanceNames.add(instanceName);
                    }
                }
            }
        }
    }

    @SuppressWarnings("checkstyle:linelength")
    private <T extends Provider<JedisPool>> void processJedisPoolProviderInjectionPoint(@Observes final ProcessInjectionPoint<?, T> e) {
        if (e != null) {
            final InjectionPoint injectionPoint = e.getInjectionPoint();
            if (injectionPoint != null) {
                final Type type = injectionPoint.getType();
                assert type instanceof Class;
                assert Provider.class.isAssignableFrom((Class<?>) type);
                final Set<Annotation> qualifiers = injectionPoint.getQualifiers();
                for (final Annotation qualifier : qualifiers) {
                    final String instanceName;
                    if (qualifier instanceof Default) {
                        instanceName = "default";
                    } else if (qualifier instanceof Named) {
                        instanceName = ((Named) qualifier).value();
                    } else {
                        instanceName = null;
                    }
                    if (instanceName != null && !instanceName.isEmpty()) {
                        this.instanceNames.add(instanceName);
                    }
                }
            }
        }
    }

    private <T extends JedisPoolConfig> void processJedisPoolConfigInjectionPoint(@Observes final ProcessInjectionPoint<?, T> e) {
        if (e != null) {
            final InjectionPoint injectionPoint = e.getInjectionPoint();
            if (injectionPoint != null) {
                final Type type = injectionPoint.getType();
                assert type instanceof Class;
                assert JedisPoolConfig.class.isAssignableFrom((Class<?>) type);
                final Set<Annotation> qualifiers = injectionPoint.getQualifiers();
                for (final Annotation qualifier : qualifiers) {
                    final String instanceName;
                    if (qualifier instanceof Default) {
                        instanceName = "default";
                    } else if (qualifier instanceof Named) {
                        instanceName = ((Named) qualifier).value();
                    } else {
                        instanceName = null;
                    }
                    if (instanceName != null && !instanceName.isEmpty()) {
                        this.instanceNames.add(instanceName);
                    }
                }
            }
        }
    }

    @SuppressWarnings("checkstyle:linelength")
    private <T extends Provider<JedisPoolConfig>> void processJedisPoolConfigProviderInjectionPoint(@Observes final ProcessInjectionPoint<?, T> e) {
        if (e != null) {
            final InjectionPoint injectionPoint = e.getInjectionPoint();
            if (injectionPoint != null) {
                final Type type = injectionPoint.getType();
                assert type instanceof Class;
                assert Provider.class.isAssignableFrom((Class<?>) type);
                final Set<Annotation> qualifiers = injectionPoint.getQualifiers();
                for (final Annotation qualifier : qualifiers) {
                    final String instanceName;
                    if (qualifier instanceof Default) {
                        instanceName = "default";
                    } else if (qualifier instanceof Named) {
                        instanceName = ((Named) qualifier).value();
                    } else {
                        instanceName = null;
                    }
                    if (instanceName != null && !instanceName.isEmpty()) {
                        this.instanceNames.add(instanceName);
                    }
                }
            }
        }
    }

    private void addBeans(@Observes final AfterBeanDiscovery event, final BeanManager beanManager) throws IntrospectionException {
        if (event != null && beanManager != null) {
            for (final String instanceName : this.instanceNames) {
                if (instanceName != null) {

                    final Set<Annotation> qualifiers;
                    if (instanceName.equals("default")) {
                        qualifiers = Collections.singleton(Default.Literal.INSTANCE);
                    } else {
                        qualifiers = Collections.singleton(NamedLiteral.of(instanceName));
                    }

                    event.<JedisPoolConfig>addBean()
                        .addTransitiveTypeClosure(JedisPoolConfig.class)
                        .scope(ApplicationScoped.class)
                        .addQualifiers(qualifiers)
                        .produceWith((instance) -> {
                                final JedisPoolConfig returnValue = new JedisPoolConfig();
                                try {
                                    this.configure(instance.select(Config.class).get(),
                                                   returnValue,
                                                   JedisPoolConfig.class,
                                                   instanceName);
                                } catch (final IntrospectionException | ReflectiveOperationException e) {
                                    throw new CreationException(e.getMessage(), e);
                                }
                                return returnValue;
                            });

                    final Annotation[] qualifiersArray = qualifiers.toArray(new Annotation[qualifiers.size()]);

                    event.<JedisPool>addBean()
                        .addTransitiveTypeClosure(JedisPool.class)
                        .scope(ApplicationScoped.class)
                        .addQualifiers(qualifiers)
                        .produceWith(instance -> {
                                return produceJedisPool(instance, instanceName, qualifiersArray);
                            })
                        .disposeWith((p, instance) -> p.destroy());

                    event.<Jedis>addBean()
                        .addTransitiveTypeClosure(Jedis.class)
                        .scope(Dependent.class)
                        .addQualifiers(qualifiers)
                        .produceWith(instance ->
                                     instance.select(JedisPool.class, qualifiersArray).get().getResource())
                        .disposeWith((j, instance) -> j.close());
                }
            }
        }
    }

    private static JedisPool produceJedisPool(final Instance<Object> instance,
                                              final String instanceName,
                                              final Annotation[] qualifiersArray) {
        final Config config = instance.select(Config.class).get();
        assert config != null;

        final String host =
            getPropertyValue(config, JedisPool.class, instanceName, "host",
                             String.class, Protocol.DEFAULT_HOST);
        final Integer port =
            getPropertyValue(config, JedisPool.class, instanceName, "port",
                             Integer.class, Protocol.DEFAULT_PORT);
        final Integer connectionTimeout =
            getPropertyValue(config, JedisPool.class, instanceName, "connectionTimeout",
                             Integer.class, Integer.valueOf(Protocol.DEFAULT_TIMEOUT));
        final Integer socketTimeout =
            getPropertyValue(config, JedisPool.class, instanceName, "socketTimeout",
                             Integer.class, Integer.valueOf(Protocol.DEFAULT_TIMEOUT));
        final Integer infiniteSocketTimeout =
            getPropertyValue(config, JedisPool.class, instanceName, "infiniteSocketTimeout",
                             Integer.class, Integer.valueOf(Protocol.DEFAULT_TIMEOUT));
        final String user =
            getPropertyValue(config, JedisPool.class, instanceName, "user",
                             String.class, null);
        final String password =
            getPropertyValue(config, JedisPool.class, instanceName, "password",
                             String.class, null);
        final Integer database =
            getPropertyValue(config, JedisPool.class, instanceName, "database",
                             Integer.class, Protocol.DEFAULT_DATABASE);
        final String clientName =
            getPropertyValue(config, JedisPool.class, instanceName, "clientName",
                             String.class, null);
        final Boolean ssl =
            getPropertyValue(config, JedisPool.class, instanceName, "ssl",
                             Boolean.class, Boolean.FALSE);
        SSLSocketFactory socketFactory = null;
        SSLParameters sslParameters = null;
        HostnameVerifier hostnameVerifier = null;
        if (Boolean.TRUE.equals(ssl)) {
            Instance<SSLSocketFactory> socketFactoriesInstance =
                instance.select(SSLSocketFactory.class, qualifiersArray);
            if (socketFactoriesInstance.isUnsatisfied()) {
                socketFactoriesInstance =
                    instance.select(SSLSocketFactory.class, Default.Literal.INSTANCE);
                if (socketFactoriesInstance.isUnsatisfied()) {
                    socketFactory = null;
                } else {
                    socketFactory = socketFactoriesInstance.get();
                }
            } else {
                socketFactory = socketFactoriesInstance.get();
            }

            Instance<SSLParameters> sslParametersInstance =
                instance.select(SSLParameters.class, qualifiersArray);
            if (sslParametersInstance.isUnsatisfied()) {
                sslParametersInstance =
                    instance.select(SSLParameters.class, Default.Literal.INSTANCE);
                if (sslParametersInstance.isUnsatisfied()) {
                    sslParameters = null;
                } else {
                    sslParameters = sslParametersInstance.get();
                }
            } else {
                sslParameters = sslParametersInstance.get();
            }

            Instance<HostnameVerifier> hostnameVerifiersInstance =
                instance.select(HostnameVerifier.class, qualifiersArray);
            if (hostnameVerifiersInstance.isUnsatisfied()) {
                hostnameVerifiersInstance =
                    instance.select(HostnameVerifier.class, Default.Literal.INSTANCE);
                if (hostnameVerifiersInstance.isUnsatisfied()) {
                    hostnameVerifier = null;
                } else {
                    hostnameVerifier = hostnameVerifiersInstance.get();
                }
            } else {
                hostnameVerifier = hostnameVerifiersInstance.get();
            }

        }
        return new JedisPool(instance.select(JedisPoolConfig.class, qualifiersArray).get(),
                             host,
                             port.intValue(),
                             connectionTimeout.intValue(),
                             socketTimeout.intValue(),
                             infiniteSocketTimeout.intValue(),
                             user,
                             password,
                             database.intValue(),
                             clientName,
                             ssl.booleanValue(),
                             socketFactory,
                             sslParameters,
                             hostnameVerifier);
    }

    private static <T> T getPropertyValue(final Config config,
                                          final Class<?> type,
                                          final String instanceName,
                                          final String propertyName,
                                          final Class<T> returnType,
                                          final T defaultValue) {
        Objects.requireNonNull(returnType);
        T returnValue = null;
        if (config != null
            && type != null
            && propertyName != null) {

            final String configPropertyName;
            if (instanceName == null) {
                configPropertyName = type.getName() + "." + propertyName;
            } else {
                configPropertyName = type.getName() + "." + instanceName + "." + propertyName;
            }
            final Optional<T> value = config.getOptionalValue(configPropertyName, returnType);
            if (value != null && value.isPresent()) {
                returnValue = value.get();
            }
        }
        if (returnValue == null) {
            returnValue = defaultValue;
        }
        return returnValue;
    }

    /**
     * Returns a non-{@code null} {@link Class} representing the type
     * to which MicroProfile Config-based conversion of the property
     * identified by the supplied {@code name} should occur.
     *
     * @param type the {@link Class} housing the property identified
     * by the supplied {@code name}; must not be {@code null}
     *
     * @param name the name of a property logically belonging to the
     * supplied {@code type}; must not be {@code null}
     *
     * @return a non-{@code null} {@link Class} representing the type
     * to which MicroProfile Config-based conversion of the property
     * identified by the supplied {@code name} should occur; {@link
     * String String.class} if no conversion type could be found
     *
     * @exception IntrospectionException if introspection fails;
     * overrides are not required to use introspection
     *
     * @exception NullPointerException if {@code type} or {@code name}
     * is {@code null}
     */
    protected Class<?> getConversionType(final Class<?> type, final String name) throws IntrospectionException {
        Objects.requireNonNull(type);
        Objects.requireNonNull(name);
        Class<?> returnValue = null;
        final Map<?, Class<?>> conversionTypes = CONVERSION_TYPES.get(type);
        if (conversionTypes != null) {
            returnValue = conversionTypes.get(name);
        }
        if (returnValue == null) {
            final BeanInfo beanInfo = Introspector.getBeanInfo(type);
            if (beanInfo != null) {
                final PropertyDescriptor[] pds = beanInfo.getPropertyDescriptors();
                if (pds != null && pds.length > 0) {
                    for (final PropertyDescriptor pd : pds) {
                        if (pd != null && pd.getWriteMethod() != null && name.equals(pd.getName())) {
                            returnValue = pd.getPropertyType();
                            break;
                        }
                    }
                }
            }
        }
        if (returnValue == null) {
            returnValue = String.class;
        }
        return returnValue;
    }

    /**
     * Configures the supplied {@link Object} by using the supplied
     * {@link Config} in some way.
     *
     * <p>This implementation uses {@code java.beans} facilities to
     * perform the configuration.</p>
     *
     * @param <T> the type of object to configure
     *
     * @param config the {@link Config} containing configuration; may
     * be {@code null} in which case no action will be taken
     *
     * @param object the {@link Object} to configure; may be {@code
     * null} in which case no action will be taken
     *
     * @param type one of the ancestral types of the supplied {@code
     * object}; must not be {@code null}
     *
     * @param instanceName the name of the instance to configure; may
     * be {@code null}
     *
     * @exception NullPointerException if {@code type} is {@code null}
     *
     * @exception IntrospectionException if introspection fails;
     * overrides of this method are not required to use introspection
     *
     * @exception ReflectiveOperationException if there was a problem
     * reflecting on the supplied {@link Object}'s {@link
     * Object#getClass() Class}; overrides of this method are not
     * required to use reflection
     */
    protected <T> void configure(final Config config, final T object, final Class<? super T> type, final String instanceName)
        throws IntrospectionException, ReflectiveOperationException {
        Objects.requireNonNull(type);
        if (config != null && object != null) {
            final Class<?> c = object.getClass();
            final BeanInfo beanInfo = Introspector.getBeanInfo(c);
            assert beanInfo != null;
            final PropertyDescriptor[] pds = beanInfo.getPropertyDescriptors();
            if (pds != null && pds.length > 0) {
                for (final PropertyDescriptor pd : pds) {
                    if (pd != null) {
                        final Method writeMethod = pd.getWriteMethod();
                        if (writeMethod != null) {
                            final String propertyName = pd.getName();
                            assert propertyName != null;
                            final String configPropertyName;
                            if (instanceName == null) {
                                configPropertyName = type.getName() + "." + propertyName;
                            } else {
                                configPropertyName = type.getName() + "." + instanceName + "." + propertyName;
                            }
                            final Class<?> conversionType = getConversionType(c, propertyName);
                            final Optional<?> value = config.getOptionalValue(configPropertyName, conversionType);
                            if (value != null && value.isPresent()) {
                                writeMethod.invoke(object, value.get());
                            }
                        }
                    }
                }
            }
        }
    }

}
