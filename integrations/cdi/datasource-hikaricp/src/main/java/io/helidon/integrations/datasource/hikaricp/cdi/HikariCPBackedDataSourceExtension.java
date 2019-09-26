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
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.sql.DataSourceDefinition;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.CreationException;
import javax.enterprise.inject.spi.AfterDeploymentValidation;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.enterprise.inject.spi.ProcessInjectionPoint;
import javax.enterprise.inject.spi.configurator.BeanConfigurator;
import javax.inject.Named;
import javax.sql.DataSource;

import io.helidon.integrations.datasource.cdi.AbstractDataSourceExtension;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.eclipse.microprofile.config.Config;

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
                                 final Function<? super Boolean, ? extends Properties> dataSourcePropertiesFunction) {
        beanConfigurator.addQualifier(dataSourceName)
            .addTransitiveTypeClosure(HikariDataSource.class)
            .beanClass(HikariDataSource.class)
            .scope(ApplicationScoped.class)
            .createWith(ignored -> new HikariDataSource(new HikariConfig(dataSourcePropertiesFunction.apply(true))))
            .destroyWith((dataSource, ignored) -> {
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

    private <T extends DataSource> void processInjectionPoint(@Observes final ProcessInjectionPoint<?, T> event) {
        if (event != null) {
            final InjectionPoint injectionPoint = event.getInjectionPoint();
            if (injectionPoint != null) {
                final Type type = injectionPoint.getType();
                if (type instanceof Class && DataSource.class.isAssignableFrom((Class<?>) type)) {
                    final Set<? extends Annotation> qualifiers = injectionPoint.getQualifiers();
                    if (qualifiers != null && !qualifiers.isEmpty()) {
                        final Config config = this.getConfig();
                        assert config != null;
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
                                    config.getOptionalValue("javax.sql.DataSource."
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
    }

    @Override
    protected void validateDataSourceProperties(final AfterDeploymentValidation event,
                                                final String dataSourceName,
                                                final Properties dataSourceProperties) {
        super.validateDataSourceProperties(event, dataSourceName, dataSourceProperties);
        // Our superclass takes care of null parameter values, but via
        // event.addDeploymentProblem(), not via throwing exceptions.
        if (dataSourceProperties != null) {
            try {
                final HikariConfig hikariConfig = new HikariConfig(dataSourceProperties);
                hikariConfig.validate();
            } catch (final RuntimeException validationProblem) {
                event.addDeploymentProblem(validationProblem);
            }
        }
    }

    @Override
    protected Properties toProperties(final DataSourceDefinition dsd) {
        Objects.requireNonNull(dsd);
        final Properties returnValue = new Properties();

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
                returnValue.setProperty("dataSource." + name.trim(), value.trim());
            }
        }

        // See https://github.com/brettwooldridge/HikariCP#configuration-knobs-baby.

        returnValue.setProperty("dataSourceClassName", dsd.className());

        // initialPoolSize -> (ignored)

        // maxStatements -> (ignored)

        // transactional -> (ignored, I guess, until I figure out what to do about XA)

        // minPoolSize -> minimumIdle
        final int minPoolSize = dsd.minPoolSize();
        if (minPoolSize >= 0) {
            returnValue.setProperty("dataSource.minimumIdle", String.valueOf(minPoolSize));
        }

        // maxPoolSize -> maximumPoolSize
        final int maxPoolSize = dsd.maxPoolSize();
        if (maxPoolSize >= 0) {
            returnValue.setProperty("dataSource.maximumPoolSize", String.valueOf(maxPoolSize));
        }

        // loginTimeout -> connectionTimeout
        final int loginTimeout = dsd.loginTimeout();
        if (loginTimeout > 0) {
            returnValue.setProperty("dataSource.connectionTimeout", String.valueOf(loginTimeout));
        }

        // maxIdleTime -> idleTimeout
        final int maxIdleTime = dsd.maxIdleTime();
        if (maxIdleTime >= 0) {
            returnValue.setProperty("dataSource.idleTimeout", String.valueOf(maxIdleTime));
        }

        // password -> password
        //
        // Note: *not* dataSource.password
        final String password = dsd.password();
        assert password != null;
        if (!password.isEmpty()) {
            returnValue.setProperty("dataSource.password", password);
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
            returnValue.setProperty("dataSource.transactionIsolation", propertyValue);
        }

        // user -> dataSource.user (standard DataSource property)
        final String user = dsd.user();
        assert user != null;
        if (!user.isEmpty()) {
            returnValue.setProperty("dataSource.user", user);
        }

        // databaseName -> dataSource.databaseName (standard DataSource property)
        final String databaseName = dsd.databaseName();
        assert databaseName != null;
        if (!databaseName.isEmpty()) {
            returnValue.setProperty("dataSource.databaseName", databaseName);
        }

        // description -> dataSource.description (standard DataSource property)
        final String description = dsd.description();
        assert description != null;
        if (!description.isEmpty()) {
            returnValue.setProperty("dataSource.description", description);
        }

        // portNumber -> dataSource.portNumber (standard DataSource property)
        final int portNumber = dsd.portNumber();
        if (portNumber >= 0) {
            returnValue.setProperty("dataSource.portNumber", String.valueOf(portNumber));
        }

        // serverName -> dataSource.serverName (standard DataSource property)
        final String serverName = dsd.serverName();
        assert serverName != null;
        if (!serverName.isEmpty()) {
            returnValue.setProperty("dataSource.serverName", serverName);
        }

        // url -> dataSource.url (standard DataSource property)
        final String url = dsd.url();
        assert url != null;
        if (!url.isEmpty()) {
            returnValue.setProperty("dataSource.url", url);
        }

        return returnValue;
    }

}
