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
import java.util.Objects;
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
                    } catch (final IntrospectionException | ReflectiveOperationException exception) {
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
        throws IntrospectionException, ReflectiveOperationException {
        // See
        // https://docs.oracle.com/en/database/oracle/oracle-database/19/jjucp/get-started.html#GUID-2CC8D6EC-483F-4942-88BA-C0A1A1B68226
        // for the general pattern.
        final PoolDataSource returnValue = PoolDataSourceFactory.getPoolDataSource();
        final Set<String> propertyNames = properties.stringPropertyNames();
        if (!propertyNames.isEmpty()) {
            final BeanInfo beanInfo = Introspector.getBeanInfo(returnValue.getClass());
            final PropertyDescriptor[] pds = beanInfo.getPropertyDescriptors();
            for (final String propertyName : propertyNames) {
                if (propertyName != null) {
                    for (final PropertyDescriptor pd : pds) {
                        if (propertyName.equals(pd.getName())) {
                            final Method writeMethod = pd.getWriteMethod();
                            if (writeMethod != null) {
                                final Class<?> type = pd.getPropertyType();
                                if (type.equals(String.class)) {
                                    writeMethod.invoke(returnValue, properties.getProperty(propertyName));
                                } else if (type.equals(Integer.TYPE)) {
                                    writeMethod.invoke(returnValue, Integer.parseInt(properties.getProperty(propertyName)));
                                } else if (type.equals(Long.TYPE)) {
                                    writeMethod.invoke(returnValue, Long.parseLong(properties.getProperty(propertyName)));
                                } else if (type.equals(Boolean.TYPE)) {
                                    writeMethod.invoke(returnValue, Boolean.parseBoolean(properties.getProperty(propertyName)));
                                }
                            }
                        }
                    }
                }
            }
            // Permit further customization before the bean is actually created
            instance.select(new TypeLiteral<Event<PoolDataSource>>() {}, dataSourceName).get().fire(returnValue);
        }
        return returnValue;
    }

}
