/*
 * Copyright (c) 2019, 2022 Oracle and/or its affiliates.
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
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.SSLContext;
import javax.sql.DataSource;

import io.helidon.integrations.datasource.cdi.AbstractDataSourceExtension;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.CreationException;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.configurator.BeanConfigurator;
import jakarta.enterprise.util.TypeLiteral;
import jakarta.inject.Named;
import oracle.ucp.jdbc.PoolDataSource;
import oracle.ucp.jdbc.PoolDataSourceFactory;
import oracle.ucp.jdbc.PoolDataSourceImpl;
import oracle.ucp.jdbc.PoolXADataSource;
import oracle.ucp.jdbc.PoolXADataSourceImpl;

/**
 * An {@link Extension} that arranges for named {@link DataSource} injection points to be satisfied by the <a
 * href="https://docs.oracle.com/en/database/oracle/oracle-database/19/jjucp/index.html">Oracle Universal Connection
 * Pool</a>.
 *
 * <p>In accordance with the CDI specification, instances of this class are not necessarily safe for concurrent use by
 * multiple threads.</p>
 */
public class UCPBackedDataSourceExtension extends AbstractDataSourceExtension {

    private static final Pattern DATASOURCE_NAME_PATTERN =
        Pattern.compile("^(?:javax\\.sql\\.|oracle\\.ucp\\.jdbc\\.Pool)(XA)?DataSource\\.([^.]+)\\.(.*)$");
    // Capturing groups:                                               (1 )              (2    )   (3 )
    //                                                                 Are we XA?        DS name   DS Property

    private final Map<String, Boolean> xa;

    /**
     * Creates a new {@link UCPBackedDataSourceExtension}.
     */
    public UCPBackedDataSourceExtension() {
        super();
        this.xa = new HashMap<>();
    }

    @Override
    protected final Matcher getDataSourcePropertyPatternMatcher(String configPropertyName) {
        return configPropertyName == null ? null : DATASOURCE_NAME_PATTERN.matcher(configPropertyName);
    }

    @Override
    protected final String getDataSourceName(Matcher dataSourcePropertyPatternMatcher) {
        String returnValue;
        if (dataSourcePropertyPatternMatcher == null) {
            returnValue = null;
        } else {
            returnValue = dataSourcePropertyPatternMatcher.group(2);
            // While we have the Matcher available, store whether this is XA or not.
            this.xa.put(returnValue, dataSourcePropertyPatternMatcher.group(1) != null);
        }
        return returnValue;
    }

    @Override
    protected final String getDataSourcePropertyName(Matcher dataSourcePropertyPatternMatcher) {
        return dataSourcePropertyPatternMatcher == null ? null : dataSourcePropertyPatternMatcher.group(3);
    }

    @Override
    protected final void addBean(BeanConfigurator<DataSource> beanConfigurator,
                                 Named dataSourceName,
                                 Properties dataSourceProperties) {
        boolean xa = this.xa.get(dataSourceName.value());
        beanConfigurator
            .addQualifier(dataSourceName)
            .addTransitiveTypeClosure(xa ? PoolXADataSourceImpl.class : PoolDataSourceImpl.class)
            .scope(ApplicationScoped.class)
            .produceWith(instance -> {
                    try {
                        return createDataSource(instance, dataSourceName, xa, dataSourceProperties);
                    } catch (IntrospectionException | ReflectiveOperationException | SQLException exception) {
                        throw new CreationException(exception.getMessage(), exception);
                    }
                })
            .disposeWith((dataSource, ignored) -> {
                    if (dataSource instanceof AutoCloseable autoCloseable) {
                        try {
                            autoCloseable.close();
                        } catch (RuntimeException runtimeException) {
                            throw runtimeException;
                        } catch (Exception exception) {
                            throw new CreationException(exception.getMessage(), exception);
                        }
                    }
                });
    }

    private static PoolDataSource createDataSource(Instance<Object> instance,
                                                   Named dataSourceName,
                                                   boolean xa,
                                                   Properties properties)
        throws IntrospectionException, ReflectiveOperationException, SQLException {
        // See
        // https://docs.oracle.com/en/database/oracle/oracle-database/19/jjucp/get-started.html#GUID-2CC8D6EC-483F-4942-88BA-C0A1A1B68226
        // for the general pattern.
        PoolDataSource returnValue =
            xa ? PoolDataSourceFactory.getPoolXADataSource() : PoolDataSourceFactory.getPoolDataSource();
        Set<String> propertyNames = properties.stringPropertyNames();
        if (!propertyNames.isEmpty()) {
            Properties connectionFactoryProperties = new Properties();
            BeanInfo beanInfo = Introspector.getBeanInfo(returnValue.getClass());
            PropertyDescriptor[] pds = beanInfo.getPropertyDescriptors();
            for (String propertyName : propertyNames) {
                if (propertyName != null) {
                    boolean handled = false;
                    for (PropertyDescriptor pd : pds) {
                        if (propertyName.equals(pd.getName())) {
                            // We have matched a Java Beans property on the PoolDataSource implementation class.  Set it
                            // if we can.  Note that these properties are NOT those of the PoolDataSource's *underlying*
                            // "real" connection factory (usually a DataSource that provides the actual connections
                            // ultimately pooled by the Universal Connection Pool).  Those are handled in a manner
                            // unfortunately restricted by the limited configuration mechanism belonging to the
                            // PoolDataSource implementation itself via the connectionFactoryProperties object.  See
                            // below.
                            Method writeMethod = pd.getWriteMethod();
                            if (writeMethod != null) {
                                Class<?> type = pd.getPropertyType();
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
                    if (!handled) {
                        // We have found a property that is not a Java Beans property of the PoolDataSource, but is
                        // supposed to be a property of the connection factory that it wraps.
                        //
                        // (Sadly, "serviceName" and "pdbRoles" are special properties that have significance to certain
                        // connection factories (such as Oracle database-oriented DataSources), and to the
                        // oracle.ucp.jdbc.UCPConnectionBuilder class, which underlies getConnection(user, password)
                        // calls, but which sadly cannot be set on a PoolDataSource except by means of some irrelevant
                        // XML configuration.  We work around this design and special case it below, not here.)
                        //
                        // Sadly, the Universal Connection Pool lacks a mechanism to tunnel arbitrary Java
                        // Beans-conformant property values destined for the underlying connection factory (which is
                        // usually a DataSource or ConnectionPoolDataSource implementation, but may be other things)
                        // through to that underlying connection factory with arbitrary type information set
                        // properly. Because the PoolDataSource is in charge of instantiating the connection factory
                        // (the underlying DataSource), you can't pass a fully configured DataSource into it, nor can
                        // you access an unconfigured instance of it that you can work with. The only configuration the
                        // Universal Connection Pool supports is via a Properties object, whose values are retrieved by
                        // the PoolDataSource implementation, as Strings.  This limits the kinds of underlying
                        // connection factories (DataSource implementations, usually) that can be fully configured with
                        // the Universal Connection Pool to Strings and those Strings which can be converted by the
                        // PoolDataSourceImpl#toBasicType(String, String) method.
                        connectionFactoryProperties.setProperty(propertyName, properties.getProperty(propertyName));
                    }
                }
            }
            Object serviceName = connectionFactoryProperties.remove("serviceName");
            Object pdbRoles = connectionFactoryProperties.remove("pdbRoles");
            // Used for OCI ATP Integration
            // Removing this so that it is not set on connectionFactoryProperties,
            // Else we get exception with getConnection using this DS, if its set.
            connectionFactoryProperties.remove("tnsNetServiceName");
            if (!connectionFactoryProperties.stringPropertyNames().isEmpty()) {
                // We found some String-typed properties that are destined for the underlying connection factory to
                // hopefully fully configure it.  Apply them here.
                returnValue.setConnectionFactoryProperties(connectionFactoryProperties);
            }
            // Set the PoolDataSource's serviceName property so that it appears to the PoolDataSource to have been set
            // via the undocumented XML configuration that the PoolDataSource can apparently be configured with in
            // certain (irrelevant for Helidon) application server cases.
            if (serviceName instanceof String) {
                try {
                    Method m = returnValue.getClass().getDeclaredMethod("setServiceName", String.class);
                    if (m.trySetAccessible()) {
                        m.invoke(returnValue, serviceName);
                    }
                } catch (NoSuchMethodException ignoreOnPurpose) {

                }
            }
            // Set the PoolDataSource's pdbRoles property so that it appears to the PoolDataSource to have been set via
            // the undocumented XML configuration that the PoolDataSource can apparently be configured with in certain
            // (irrelevant for Helidon) application server cases.
            if (pdbRoles instanceof Properties) {
                try {
                    Method m = returnValue.getClass().getDeclaredMethod("setPdbRoles", Properties.class);
                    if (m.trySetAccessible()) {
                        m.invoke(returnValue, pdbRoles);
                    }
                } catch (NoSuchMethodException ignoreOnPurpose) {

                }
            }
        }
        Instance<SSLContext> sslContextInstance = instance.select(SSLContext.class, dataSourceName);
        if (!sslContextInstance.isUnsatisfied()) {
            returnValue.setSSLContext(sslContextInstance.get());
        }
        // Permit further customization before the bean is actually created
        if (xa) {
            instance.select(new TypeLiteral<Event<PoolXADataSource>>() {}, dataSourceName)
                .get()
                .fire((PoolXADataSource) returnValue);
        } else {
            instance.select(new TypeLiteral<Event<PoolDataSource>>() {}, dataSourceName)
                .get()
                .fire(returnValue);
        }
        return returnValue;
    }

}
