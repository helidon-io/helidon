/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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
package io.helidon.integrations.datasource.ucp.cdi;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.enterprise.inject.CreationException;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.configurator.BeanConfigurator;
import javax.enterprise.util.TypeLiteral;
import javax.inject.Named;
import javax.net.ssl.SSLContext;
import javax.sql.DataSource;

import io.helidon.integrations.datasource.cdi.AbstractDataSourceExtension;

import oracle.ucp.jdbc.PoolDataSource;
import oracle.ucp.jdbc.PoolDataSourceFactory;

/**
 * An {@link Extension} that arranges for named {@link DataSource}
 * injection points to be satisfied by the <a
 * href="https://docs.oracle.com/en/database/oracle/oracle-database/19/jjucp/index.html">Oracle
 * Universal Connection Pool</a>.
 */
public class UCPBackedDataSourceExtension extends AbstractDataSourceExtension {

    private static final Pattern DATASOURCE_NAME_PATTERN =
        Pattern.compile("^(?:javax\\.sql\\.|oracle\\.ucp\\.jdbc\\.Pool)DataSource\\.([^.]+)\\.(.*)$");

    /**
     * Creates a new {@link UCPBackedDataSourceExtension}.
     */
    public UCPBackedDataSourceExtension() {
        super();
    }

    @Override
    protected final Matcher getDataSourcePropertyPatternMatcher(final String configPropertyName) {
        final Matcher returnValue;
        if (configPropertyName == null) {
            returnValue = null;
        } else {
            returnValue = DATASOURCE_NAME_PATTERN.matcher(configPropertyName);
        }
        return returnValue;
    }

    @Override
    protected final String getDataSourceName(final Matcher dataSourcePropertyPatternMatcher) {
        final String returnValue;
        if (dataSourcePropertyPatternMatcher == null) {
            returnValue = null;
        } else {
            returnValue = dataSourcePropertyPatternMatcher.group(1);
        }
        return returnValue;
    }

    @Override
    protected final String getDataSourcePropertyName(final Matcher dataSourcePropertyPatternMatcher) {
        final String returnValue;
        if (dataSourcePropertyPatternMatcher == null) {
            returnValue = null;
        } else {
            returnValue = dataSourcePropertyPatternMatcher.group(2);
        }
        return returnValue;
    }

    @Override
    protected final void addBean(final BeanConfigurator<DataSource> beanConfigurator,
                                 final Named dataSourceName,
                                 final Properties dataSourceProperties) {
        beanConfigurator
            .addQualifier(dataSourceName)
            .addTransitiveTypeClosure(PoolDataSource.class)
            .scope(ApplicationScoped.class)
            .produceWith(instance -> {
                    try {
                        return createDataSource(instance, dataSourceName, dataSourceProperties);
                    } catch (final IntrospectionException | ReflectiveOperationException | SQLException exception) {
                        throw new CreationException(exception.getMessage(), exception);
                    }
                })
            .disposeWith((dataSource, ignored) -> {
                    if (dataSource instanceof AutoCloseable) {
                        try {
                            ((AutoCloseable) dataSource).close();
                        } catch (final RuntimeException runtimeException) {
                            throw runtimeException;
                        } catch (final Exception exception) {
                            throw new CreationException(exception.getMessage(), exception);
                        }
                    }
                });
    }

    private static PoolDataSource createDataSource(final Instance<Object> instance,
                                                   final Named dataSourceName,
                                                   final Properties properties)
        throws IntrospectionException, ReflectiveOperationException, SQLException {
        // See
        // https://docs.oracle.com/en/database/oracle/oracle-database/19/jjucp/get-started.html#GUID-2CC8D6EC-483F-4942-88BA-C0A1A1B68226
        // for the general pattern.
        final PoolDataSource returnValue = PoolDataSourceFactory.getPoolDataSource();
        final Set<String> propertyNames = properties.stringPropertyNames();
        if (!propertyNames.isEmpty()) {
            final Properties connectionFactoryProperties = new Properties();
            final BeanInfo beanInfo = Introspector.getBeanInfo(returnValue.getClass());
            final PropertyDescriptor[] pds = beanInfo.getPropertyDescriptors();
            for (final String propertyName : propertyNames) {
                if (propertyName != null) {
                    boolean handled = false;
                    for (final PropertyDescriptor pd : pds) {
                        if (propertyName.equals(pd.getName())) {
                            // We have matched a Java Beans property
                            // on the PoolDataSource implementation
                            // class.  Set it if we can.  Note that
                            // these properties are NOT those of the
                            // PoolDataSource's *underlying* "real"
                            // connection factory (usually a
                            // DataSource that provides the actual
                            // connections ultimately pooled by the
                            // Universal Connection Pool).  Those are
                            // handled in a manner unfortunately
                            // restricted by the limited configuration
                            // mechanism belonging to the
                            // PoolDataSource implementation itself
                            // via the connectionFactoryProperties
                            // object.  See below.
                            final Method writeMethod = pd.getWriteMethod();
                            if (writeMethod != null) {
                                final Class<?> type = pd.getPropertyType();
                                if (type.equals(String.class)) {
                                    writeMethod.invoke(returnValue, properties.getProperty(propertyName));
                                    handled = true;
                                } else if (type.equals(Integer.TYPE)) {
                                    writeMethod.invoke(returnValue, Integer.parseInt(properties.getProperty(propertyName)));
                                    handled = true;
                                } else if (type.equals(Long.TYPE)) {
                                    writeMethod.invoke(returnValue, Long.parseLong(properties.getProperty(propertyName)));
                                    handled = true;
                                } else if (type.equals(Boolean.TYPE)) {
                                    writeMethod.invoke(returnValue, Boolean.parseBoolean(properties.getProperty(propertyName)));
                                    handled = true;
                                }
                            }
                        }
                    }
                    if (!handled && !propertyName.equals("serviceName")) {
                        // We have found a property that is not a Java
                        // Beans property of the PoolDataSource, but
                        // is supposed to be a property of the
                        // connection factory that it wraps.
                        //
                        // (Sadly, "serviceName" is a special property
                        // that has significance to certain connection
                        // factories (such as Oracle database-oriented
                        // DataSources), and to the
                        // oracle.ucp.jdbc.UCPConnectionBuilder class,
                        // which underlies getConnection(user,
                        // password) calls, but which sadly cannot be
                        // set on a PoolDataSource except by means of
                        // some irrelevant XML configuration.  We work
                        // around this design and special case it
                        // below, not here.)
                        //
                        // Sadly, the Universal Connection Pool lacks
                        // a mechanism to tunnel arbitrary Java
                        // Beans-conformant property values destined
                        // for the underlying connection factory
                        // (which is usually a DataSource or
                        // ConnectionPoolDataSource implementation,
                        // but may be other things) through to that
                        // underlying connection factory with
                        // arbitrary type information set properly.
                        // Because the PoolDataSource is in charge of
                        // instantiating the connection factory (the
                        // underlying DataSource), you can't pass a
                        // fully configured DataSource into it, nor
                        // can you access an unconfigured instance of
                        // it that you can work with. The only
                        // configuration the Universal Connection Pool
                        // supports is via a Properties object, whose
                        // values are retrieved by the PoolDataSource
                        // implementation, as Strings.  This limits
                        // the kinds of underlying connection
                        // factories (DataSource implementations,
                        // usually) that can be fully configured with
                        // the Universal Connection Pool to Strings
                        // and those Strings which can be converted by
                        // the PoolDataSourceImpl#toBasicType(String,
                        // String) method.
                        connectionFactoryProperties.setProperty(propertyName, properties.getProperty(propertyName));
                    }
                }
            }
            if (!connectionFactoryProperties.stringPropertyNames().isEmpty()) {
                // We found some String-typed properties that are
                // destined for the underlying connection factory to
                // hopefully fully configure it.  Apply them here.
                returnValue.setConnectionFactoryProperties(connectionFactoryProperties);
                // Set the PoolDataSource's serviceName property so
                // that it appears to the PoolDataSource to have been
                // set via the undocumented XML configuration that the
                // PoolDataSource can apparently be configured with in
                // certain (irrelevant for Helidon) application server
                // cases.
                final String serviceName = connectionFactoryProperties.getProperty("serviceName");
                if (serviceName != null) {
                    try {
                        Method m = returnValue.getClass().getDeclaredMethod("setServiceName", String.class);
                        if (m.trySetAccessible()) {
                            m.invoke(returnValue, serviceName);
                        }
                    } catch (final NoSuchMethodException ignoreOnPurpose) {

                    }
                }
            }
            // Permit further customization before the bean is actually created
            instance.select(new TypeLiteral<Event<PoolDataSource>>() {}, dataSourceName).get().fire(returnValue);
        }
        final Instance<SSLContext> sslContextInstance = instance.select(SSLContext.class);
        if (!sslContextInstance.isUnsatisfied()) {
            returnValue.setSSLContext(sslContextInstance.get());
        }
        return returnValue;
    }

}
