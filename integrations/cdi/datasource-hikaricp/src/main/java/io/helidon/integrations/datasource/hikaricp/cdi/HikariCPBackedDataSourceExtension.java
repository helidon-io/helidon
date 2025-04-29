/*
 * Copyright (c) 2018, 2025 Oracle and/or its affiliates.
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
package io.helidon.integrations.datasource.hikaricp.cdi;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.sql.Connection;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import io.helidon.integrations.datasource.cdi.AbstractDataSourceExtension;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.metrics.MetricsTrackerFactory;
import jakarta.annotation.sql.DataSourceDefinition;
import jakarta.annotation.sql.DataSourceDefinitions;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.CreationException;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.Annotated;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.enterprise.inject.spi.ProcessAnnotatedType;
import jakarta.enterprise.inject.spi.ProcessInjectionPoint;
import jakarta.enterprise.inject.spi.WithAnnotations;
import jakarta.enterprise.inject.spi.configurator.BeanConfigurator;
import jakarta.enterprise.util.TypeLiteral;
import jakarta.inject.Named;

/**
 * An {@link Extension} that arranges for named {@link DataSource}
 * injection points to be satisfied.
 */
public class HikariCPBackedDataSourceExtension extends AbstractDataSourceExtension {

    private static final Pattern DATASOURCE_NAME_PATTERN =
        Pattern.compile("^(?:javax\\.sql\\.|com\\.zaxxer\\.hikari\\.Hikari)DataSource\\.([^.]+)\\.(.*)$");

    /**
     * Creates a new {@link HikariCPBackedDataSourceExtension}.
     */
    public HikariCPBackedDataSourceExtension() {
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
        beanConfigurator.addQualifier(dataSourceName)
            .addTransitiveTypeClosure(HikariDataSource.class)
            .beanClass(HikariDataSource.class)
            .scope(ApplicationScoped.class)
            .produceWith(instance -> {
                    final HikariConfig config = produceHikariConfig(instance, dataSourceName, dataSourceProperties);
                    // Permit further customization before the bean is actually created
                    instance.select(new TypeLiteral<Event<HikariConfig>>() {}, dataSourceName).get().fire(config);
                    return new HikariDataSource(config);
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

    private static HikariConfig produceHikariConfig(final Instance<Object> instance,
                                                    final Named dataSourceName,
                                                    final Properties dataSourceProperties) {
        final HikariConfig hikariConfig = new HikariConfig(dataSourceProperties);
        Instance<MetricsTrackerFactory> i;
        if (dataSourceName == null) {
            i = instance.select(MetricsTrackerFactory.class);
        } else {
            if (hikariConfig.getPoolName() == null) {
                hikariConfig.setPoolName(dataSourceName.value());
            }
            i = instance.select(MetricsTrackerFactory.class, dataSourceName);
            if (i.isUnsatisfied()) {
                i = instance.select(MetricsTrackerFactory.class);
            }
        }
        if (!i.isUnsatisfied()) {
            hikariConfig.setMetricsTrackerFactory(i.get());
        }
        return hikariConfig;
    }

    private void processAnnotatedType(@Observes
                                      @WithAnnotations({ DataSourceDefinition.class, DataSourceDefinitions.class })
                                      final ProcessAnnotatedType<?> event) {
        if (event != null) {
            final Annotated annotated = event.getAnnotatedType();
            if (annotated != null) {
                final Set<? extends DataSourceDefinition> dataSourceDefinitions =
                    annotated.getAnnotations(DataSourceDefinition.class);
                if (dataSourceDefinitions != null && !dataSourceDefinitions.isEmpty()) {
                    for (final DataSourceDefinition dsd : dataSourceDefinitions) {
                        final Set<String> knownDataSourceNames = this.names();
                        final String dataSourceName = dsd.name();
                        if (!knownDataSourceNames.contains(dataSourceName)) {
                            final Entry<? extends String, ? extends Properties> entry = toProperties(dsd);
                            if (entry != null) {
                                this.put(dataSourceName, entry.getValue());
                            }
                        }
                    }
                }
            }
        }
    }

    private <T extends DataSource> void processInjectionPoint(@Observes final ProcessInjectionPoint<?, T> event) {
        if (event != null) {
            final InjectionPoint injectionPoint = event.getInjectionPoint();
            if (injectionPoint != null) {
                final Type type = injectionPoint.getType();
                if (type instanceof Class && DataSource.class.isAssignableFrom((Class<?>) type)) {
                    final Set<? extends Annotation> qualifiers = injectionPoint.getQualifiers();
                    if (qualifiers != null && !qualifiers.isEmpty()) {
                        for (final Annotation qualifier : qualifiers) {
                            if (qualifier instanceof Named) {
                                final String dataSourceName = ((Named) qualifier).value();
                                if (dataSourceName != null && !dataSourceName.isEmpty()) {
                                    // The injection point might
                                    // reference a data source name
                                    // that wasn't present in a
                                    // MicroProfile ConfigSource
                                    // initially.  Some ConfigSources
                                    // that Helidon provides can
                                    // automatically synthesize
                                    // configuration property values
                                    // upon first lookup.  So we give
                                    // those ConfigSources a chance to
                                    // bootstrap here by issuing a
                                    // request for a commonly present
                                    // data source property value.
                                    configPropertyValue("javax.sql.DataSource."
                                                        + dataSourceName
                                                        + ".dataSourceClassName");
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static Entry<String, Properties> toProperties(final DataSourceDefinition dsd) {
        Objects.requireNonNull(dsd);
        final String dataSourceName = dsd.name();
        final Properties properties = new Properties();
        final Entry<String, Properties> returnValue = new SimpleImmutableEntry<>(dataSourceName, properties);

        final String[] propertyStrings = dsd.properties();
        for (final String propertyString : propertyStrings) {
            final int equalsIndex = propertyString.indexOf('=');
            if (equalsIndex > 0 && equalsIndex < propertyString.length()) {
                final String name = propertyString.substring(0, equalsIndex);
                final String value = propertyString.substring(equalsIndex + 1);
                properties.setProperty("dataSource." + name.trim(), value.trim());
            }
        }

        // See https://github.com/brettwooldridge/HikariCP#configuration-knobs-baby.

        properties.setProperty("dataSourceClassName", dsd.className());

        // initialPoolSize -> (ignored)

        // maxStatements -> (ignored)

        // transactional -> (ignored, I guess, until I figure out what to do about XA)

        // minPoolSize -> minimumIdle
        final int minPoolSize = dsd.minPoolSize();
        if (minPoolSize >= 0) {
            properties.setProperty("dataSource.minimumIdle", String.valueOf(minPoolSize));
        }

        // maxPoolSize -> maximumPoolSize
        final int maxPoolSize = dsd.maxPoolSize();
        if (maxPoolSize >= 0) {
            properties.setProperty("dataSource.maximumPoolSize", String.valueOf(maxPoolSize));
        }

        // loginTimeout -> connectionTimeout
        final int loginTimeout = dsd.loginTimeout();
        if (loginTimeout > 0) {
            properties.setProperty("dataSource.connectionTimeout", String.valueOf(loginTimeout));
        }

        // maxIdleTime -> idleTimeout
        final int maxIdleTime = dsd.maxIdleTime();
        if (maxIdleTime >= 0) {
            properties.setProperty("dataSource.idleTimeout", String.valueOf(maxIdleTime));
        }

        // password -> password
        //
        // Note: *not* dataSource.password
        final String password = dsd.password();
        if (!password.isEmpty()) {
            properties.setProperty("dataSource.password", password);
        }

        // isolationLevel -> transactionIsolation
        final int isolationLevel = dsd.isolationLevel();
        if (isolationLevel >= 0) {
            final String propertyValue;
            switch (isolationLevel) {
            case Connection.TRANSACTION_NONE:
                propertyValue = "TRANSACTION_NONE";
                break;
            case Connection.TRANSACTION_READ_UNCOMMITTED:
                propertyValue = "TRANSACTION_READ_UNCOMMITTED";
                break;
            case Connection.TRANSACTION_READ_COMMITTED:
                propertyValue = "TRANSACTION_READ_COMMITTED";
                break;
            case Connection.TRANSACTION_REPEATABLE_READ:
                propertyValue = "TRANSACTION_REPEATABLE_READ";
                break;
            case Connection.TRANSACTION_SERIALIZABLE:
                propertyValue = "TRANSACTION_SERIALIZABLE";
                break;
            default:
                propertyValue = null;
                throw new IllegalStateException("Unexpected isolation level: " + isolationLevel);
            }
            properties.setProperty("dataSource.transactionIsolation", propertyValue);
        }

        // user -> dataSource.username
        //
        // This one's a bit odd.  Note that this does NOT map to
        // dataSource.user!
        final String user = dsd.user();
        if (!user.isEmpty()) {
            properties.setProperty("dataSource.username", user);
        }

        // databaseName -> dataSource.databaseName (standard DataSource property)
        final String databaseName = dsd.databaseName();
        if (!databaseName.isEmpty()) {
            properties.setProperty("dataSource.databaseName", databaseName);
        }

        // description -> dataSource.description (standard DataSource property)
        final String description = dsd.description();
        if (!description.isEmpty()) {
            properties.setProperty("dataSource.description", description);
        }

        // portNumber -> dataSource.portNumber (standard DataSource property)
        final int portNumber = dsd.portNumber();
        if (portNumber >= 0) {
            properties.setProperty("dataSource.portNumber", String.valueOf(portNumber));
        }

        // serverName -> dataSource.serverName (standard DataSource property)
        final String serverName = dsd.serverName();
        if (!serverName.isEmpty()) {
            properties.setProperty("dataSource.serverName", serverName);
        }

        // url -> dataSource.url (standard DataSource property)
        final String url = dsd.url();
        if (!url.isEmpty()) {
            properties.setProperty("dataSource.url", url);
        }

        return returnValue;
    }

}
