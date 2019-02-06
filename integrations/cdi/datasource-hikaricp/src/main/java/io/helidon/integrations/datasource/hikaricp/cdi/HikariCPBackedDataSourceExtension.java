/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.sql.DataSourceDefinition;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.literal.NamedLiteral;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.Annotated;
import javax.enterprise.inject.spi.BeforeBeanDiscovery;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.enterprise.inject.spi.ProcessAnnotatedType;
import javax.enterprise.inject.spi.ProcessInjectionPoint;
import javax.enterprise.inject.spi.WithAnnotations;
import javax.inject.Named;
import javax.sql.DataSource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.spi.ConfigSource;

/**
 * An {@link Extension} that arranges for named {@link DataSource}
 * injection points to be satisfied.
 */
public class HikariCPBackedDataSourceExtension implements Extension {

    private static final Pattern DATASOURCE_NAME_PATTERN =
        Pattern.compile("^(?:javax\\.sql\\.|com\\.zaxxer\\.hikari\\.Hikari)DataSource\\.([^.]+)\\.(.*)$");

    private final Map<String, Properties> masterProperties;

    private final Config config;

    /**
     * Creates a new {@link HikariCPBackedDataSourceExtension}.
     */
    public HikariCPBackedDataSourceExtension() {
        super();
        this.masterProperties = new HashMap<>();
        this.config = ConfigProvider.getConfig();
    }

    private void beforeBeanDiscovery(@Observes final BeforeBeanDiscovery event) {
        final Set<? extends String> allPropertyNames = this.getPropertyNames();
        if (allPropertyNames != null && !allPropertyNames.isEmpty()) {
            for (final String propertyName : allPropertyNames) {
                final Optional<String> propertyValue = this.config.getOptionalValue(propertyName, String.class);
                if (propertyValue != null && propertyValue.isPresent()) {
                    final Matcher matcher = DATASOURCE_NAME_PATTERN.matcher(propertyName);
                    assert matcher != null;
                    if (matcher.matches()) {
                        final String dataSourceName = matcher.group(1);
                        Properties properties = this.masterProperties.get(dataSourceName);
                        if (properties == null) {
                            properties = new Properties();
                            this.masterProperties.put(dataSourceName, properties);
                        }
                        final String dataSourcePropertyName = matcher.group(2);
                        properties.setProperty(dataSourcePropertyName, propertyValue.get());
                    }
                }
            }
        }
    }

    private void processAnnotatedType(@Observes
                                      @WithAnnotations(DataSourceDefinition.class)
                                      final ProcessAnnotatedType<?> event) {
        if (event != null) {
            final Annotated annotated = event.getAnnotatedType();
            if (annotated != null) {
                final Set<? extends DataSourceDefinition> dataSourceDefinitions =
                    annotated.getAnnotations(DataSourceDefinition.class);
                if (dataSourceDefinitions != null && !dataSourceDefinitions.isEmpty()) {
                    for (final DataSourceDefinition dsd : dataSourceDefinitions) {
                        assert dsd != null;
                        final Entry<? extends String, ? extends Properties> entry = toProperties(dsd);
                        if (entry != null) {
                            final String dataSourceName = entry.getKey();
                            if (dataSourceName != null && !this.masterProperties.containsKey(dataSourceName)) {
                                this.masterProperties.put(dataSourceName, entry.getValue());
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
                    for (final Annotation qualifier : qualifiers) {
                        if (qualifier instanceof Named) {
                            final String dataSourceName = ((Named) qualifier).value();
                            if (dataSourceName != null && !dataSourceName.isEmpty()) {
                                // The injection point might reference
                                // a data source name that wasn't
                                // present in a MicroProfile
                                // ConfigSource initially.  Some
                                // ConfigSources that Helidon provides
                                // can automatically synthesize
                                // configuration property values upon
                                // first lookup.  So we give those
                                // ConfigSources a chance to bootstrap
                                // here by issuing a request for a
                                // commonly present data source
                                // property value.
                                this.config.getOptionalValue("javax.sql.DataSource."
                                                             + dataSourceName
                                                             + ".dataSourceClassName",
                                                             String.class);
                            }
                        }
                    }
                }
            }
        }
    }

    private void afterBeanDiscovery(@Observes final AfterBeanDiscovery event) {
        if (event != null) {
            final Set<? extends Entry<? extends String, ? extends Properties>> masterPropertiesEntries =
                this.masterProperties.entrySet();
            if (masterPropertiesEntries != null && !masterPropertiesEntries.isEmpty()) {
                for (final Entry<? extends String, ? extends Properties> entry : masterPropertiesEntries) {
                    if (entry != null) {
                        event.<HikariDataSource>addBean()
                            .addQualifier(NamedLiteral.of(entry.getKey())) // ...and Default and Any?
                            .addTransitiveTypeClosure(HikariDataSource.class)
                            .beanClass(HikariDataSource.class)
                            .scope(ApplicationScoped.class)
                            .createWith(ignored -> new HikariDataSource(new HikariConfig(entry.getValue())))
                            .destroyWith((dataSource, ignored) -> dataSource.close());
                    }
                }
            }
        }
        this.masterProperties.clear();
    }

    private Set<String> getPropertyNames() {
        // The MicroProfile Config specification does not say whether
        // property names must be cached or must not be cached
        // (https://github.com/eclipse/microprofile-config/issues/370).
        // It is implied in the MicroProfile Google group
        // (https://groups.google.com/d/msg/microprofile/tvjgSR9qL2Q/M2TNUQrOAQAJ),
        // but not in the specification, that ConfigSources can be
        // mutable and dynamic.  Consequently one would expect their
        // property names to come and go.  Because of this we have to
        // make sure to get all property names from all ConfigSources
        // "by hand".
        //
        // (The MicroProfile Config specification also does not say
        // whether a ConfigSource is thread-safe
        // (https://github.com/eclipse/microprofile-config/issues/369),
        // so iteration over its coming-and-going dynamic property
        // names may be problematic, but there's nothing we can do.)
        //
        // As of this writing, the Helidon MicroProfile Config
        // implementation caches all property names up front, which
        // may not be correct, but is also not forbidden.
        final Set<String> returnValue;
        final Set<String> propertyNames = getPropertyNames(this.config.getConfigSources());
        if (propertyNames == null || propertyNames.isEmpty()) {
            returnValue = Collections.emptySet();
        } else {
            returnValue = Collections.unmodifiableSet(propertyNames);
        }
        return returnValue;
    }

    private static Set<String> getPropertyNames(final Iterable<? extends ConfigSource> configSources) {
        final Set<String> returnValue = new HashSet<>();
        if (configSources != null) {
            for (final ConfigSource configSource : configSources) {
                if (configSource != null) {
                    final Set<String> configSourcePropertyNames = configSource.getPropertyNames();
                    if (configSourcePropertyNames != null && !configSourcePropertyNames.isEmpty()) {
                        returnValue.addAll(configSourcePropertyNames);
                    }
                }
            }
        }
        return Collections.unmodifiableSet(returnValue);
    }

    private static Entry<String, Properties> toProperties(final DataSourceDefinition dsd) {
        Objects.requireNonNull(dsd);
        final String dataSourceName = dsd.name();
        final Properties properties = new Properties();
        final Entry<String, Properties> returnValue = new SimpleImmutableEntry<>(dataSourceName, properties);

        final String[] propertyStrings = dsd.properties();
        assert propertyStrings != null;
        for (final String propertyString : propertyStrings) {
            assert propertyString != null;
            final int equalsIndex = propertyString.indexOf('=');
            if (equalsIndex > 0 && equalsIndex < propertyString.length()) {
                final String name = propertyString.substring(0, equalsIndex);
                assert name != null;
                final String value = propertyString.substring(equalsIndex + 1);
                assert value != null;
                properties.setProperty("dataSource." + name, value);
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
        assert password != null;
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
        assert user != null;
        if (!user.isEmpty()) {
            properties.setProperty("dataSource.username", user);
        }

        // databaseName -> dataSource.databaseName (standard DataSource property)
        final String databaseName = dsd.databaseName();
        assert databaseName != null;
        if (!databaseName.isEmpty()) {
            properties.setProperty("datasource.databaseName", databaseName);
        }

        // description -> dataSource.description (standard DataSource property)
        final String description = dsd.description();
        assert description != null;
        if (!description.isEmpty()) {
            properties.setProperty("datasource.description", description);
        }

        // portNumber -> dataSource.portNumber (standard DataSource property)
        final int portNumber = dsd.portNumber();
        if (portNumber >= 0) {
            properties.setProperty("datasource.portNumber", String.valueOf(portNumber));
        }

        // serverName -> dataSource.serverName (standard DataSource property)
        final String serverName = dsd.serverName();
        assert serverName != null;
        if (!serverName.isEmpty()) {
            properties.setProperty("datasource.serverName", serverName);
        }

        // url -> dataSource.url (standard DataSource property)
        final String url = dsd.url();
        assert url != null;
        if (!url.isEmpty()) {
            properties.setProperty("datasource.url", url);
        }

        return returnValue;
    }

}
