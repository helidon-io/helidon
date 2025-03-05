/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.data.jakarta.persistence;

import java.lang.System.Logger.Level;
import java.sql.Driver;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.sql.DataSource;

import io.helidon.service.registry.Lookup;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.ServiceRegistry;

import jakarta.persistence.SharedCacheMode;
import jakarta.persistence.ValidationMode;

/**
 * Temporary replacement of Jakarta Persistence 3.2 {@code PersistenceConfiguration} class
 * while Helidon depends on Jakarta Persistence 3.1.
 *
 * @deprecated Will be replaced by Jakarta Persistence 3.2 API
 */
@Deprecated
public final class PersistenceConfiguration {
    private static final System.Logger LOGGER = System.getLogger(PersistenceConfiguration.class.getName());

    private final String name;
    private final List<Class<?>> managedClasses = new ArrayList<>();
    private final List<String> mappingFileNames = new ArrayList<>();
    private final Map<String, Object> properties = new HashMap<>();
    private String provider;
    private DataSource jtaDataSource;
    private DataSource nonJtaDataSource;
    private SharedCacheMode sharedCacheMode = SharedCacheMode.UNSPECIFIED;
    private ValidationMode validationMode = ValidationMode.AUTO;
    private PersistenceUnitTransactionType transactionType = PersistenceUnitTransactionType.RESOURCE_LOCAL;

    /**
     * Create a new empty configuration.
     *
     * @param name the name of the persistence unit, which may be used by
     *             the persistence provider for logging and error reporting
     */
    private PersistenceConfiguration(String name) {
        Objects.requireNonNull(name, "Persistence unit name should not be null");
        this.name = name;
    }

    /**
     * Create a new empty (named) configuration.
     *
     * @param name name of the persistence unit
     * @return a new instance
     */
    public static PersistenceConfiguration create(String name) {
        return new PersistenceConfiguration(name);
    }

    /**
     * The name of the persistence unit, which may be used by the persistence
     * provider for logging and error reporting.
     *
     * @return the name of the persistence unit.
     */
    public String name() {
        return name;
    }

    /**
     * Specify the persistence provider.
     *
     * @param providerClassName the qualified name of the persistence provider class
     * @return this configuration
     */
    public PersistenceConfiguration provider(String providerClassName) {
        this.provider = providerClassName;
        return this;
    }

    /**
     * The fully-qualified name of a concrete class implementing
     * {@link jakarta.persistence.spi.PersistenceProvider}.
     *
     * @return the qualified name of the persistence provider class.
     */
    public String provider() {
        return provider;
    }

    /**
     * Specify the JNDI name of a JTA {@code javax.sql.DataSource}.
     *
     * @param dataSourceJndiName the JNDI name of a JTA datasource
     * @return this configuration
     */
    public PersistenceConfiguration jtaDataSource(DataSource dataSourceJndiName) {
        this.jtaDataSource = dataSourceJndiName;
        return this;
    }

    /**
     * The JNDI name of a JTA {@code javax.sql.DataSource}.
     *
     * @return the configured JTA datasource, if any, or null
     */
    public DataSource jtaDataSource() {
        return jtaDataSource;
    }

    /**
     * Specify the JNDI name of a non-JTA {@code javax.sql.DataSource}.
     *
     * @param dataSourceJndiName the JNDI name of a non-JTA datasource
     * @return this configuration
     */
    public PersistenceConfiguration nonJtaDataSource(DataSource dataSourceJndiName) {
        this.nonJtaDataSource = dataSourceJndiName;
        return this;
    }

    /**
     * The JNDI name of a non-JTA {@code javax.sql.DataSource}.
     *
     * @return the configured non-JTA datasource, if any, or null
     */
    public DataSource nonJtaDataSource() {
        return nonJtaDataSource;
    }

    /**
     * Add a managed class (an {@link jakarta.persistence.Entity}, {@link jakarta.persistence.Embeddable},
     * {@link jakarta.persistence.MappedSuperclass}, or {@link jakarta.persistence.Converter}) to the
     * configuration.
     *
     * @param managedClass the managed class
     * @return this configuration
     */
    public PersistenceConfiguration managedClass(Class<?> managedClass) {
        managedClasses.add(managedClass);
        return this;
    }

    /**
     * The configured managed classes, that is, a list of classes
     * annotated {@link jakarta.persistence.Entity}, {@link jakarta.persistence.Embeddable},
     * {@link jakarta.persistence.MappedSuperclass}, or {@link jakarta.persistence.Converter}.
     *
     * @return all configured managed classes
     */
    public List<Class<?>> managedClasses() {
        return managedClasses;
    }

    /**
     * Add the path of an XML mapping file loaded as a resource to
     * the configuration.
     *
     * @param name the resource path of the mapping file
     * @return this configuration
     */
    public PersistenceConfiguration mappingFile(String name) {
        mappingFileNames.add(name);
        return this;
    }

    /**
     * The configured resource paths of XML mapping files.
     *
     * @return all configured mapping file resource paths
     */
    public List<String> mappingFiles() {
        return mappingFileNames;
    }

    /**
     * Specify the transaction type for the persistence unit.
     *
     * @param transactionType the transaction type
     * @return this configuration
     */
    public PersistenceConfiguration transactionType(PersistenceUnitTransactionType transactionType) {
        this.transactionType = transactionType;
        return this;
    }

    /**
     * The {@linkplain PersistenceUnitTransactionType transaction type}.
     * <ul>
     * <li>If {@link PersistenceUnitTransactionType#JTA}, a JTA data
     *    source must be provided via {@link #jtaDataSource()},
     *    or by the container.
     * <li>If {@link PersistenceUnitTransactionType#RESOURCE_LOCAL},
     *    database connection properties may be specified via
     *    {@link #properties()}, or a non-JTA datasource may be
     *    provided via {@link #nonJtaDataSource()}.
     * </ul>
     *
     * @return the transaction type
     */
    public PersistenceUnitTransactionType transactionType() {
        return transactionType;
    }

    /**
     * Specify the shared cache mode for the persistence unit.
     *
     * @param sharedCacheMode the shared cache mode
     * @return this configuration
     */
    public PersistenceConfiguration sharedCacheMode(SharedCacheMode sharedCacheMode) {
        this.sharedCacheMode = sharedCacheMode;
        return this;
    }

    /**
     * The shared cache mode. The default behavior is unspecified
     * and {@linkplain SharedCacheMode#UNSPECIFIED provider-specific}.
     *
     * @return the shared cache mode
     */
    public SharedCacheMode sharedCacheMode() {
        return sharedCacheMode;
    }

    /**
     * Specify the validation mode for the persistence unit.
     *
     * @param validationMode the shared cache mode
     * @return this configuration
     */
    public PersistenceConfiguration validationMode(ValidationMode validationMode) {
        this.validationMode = validationMode;
        return this;
    }

    /**
     * The validation mode, {@link ValidationMode#AUTO} by default.
     *
     * @return the validation mode
     */
    public ValidationMode validationMode() {
        return validationMode;
    }

    /**
     * Set a property of this persistence unit.
     *
     * @param name  the property name
     * @param value the property value
     * @return this configuration
     */
    public PersistenceConfiguration property(String name, Object value) {
        properties.put(name, value);
        return this;
    }

    /**
     * Set multiple properties of this persistence unit.
     *
     * @param properties the properties
     * @return this configuration
     */
    public PersistenceConfiguration properties(Map<String, ?> properties) {
        this.properties.putAll(properties);
        return this;
    }

    /**
     * Standard and vendor-specific property settings.
     *
     * @return the configured properties
     */
    public Map<String, Object> properties() {
        return properties;
    }

    /**
     * Jakarta Persistence DDL scripts configuration.
     * Copies database initialization and cleanup scripts configuration into persistence unit configuration.
     *
     * @param dataJpaConfig source Helidon data Configuration
     */
    public void configureScripts(DataJpaConfig dataJpaConfig) {
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            LOGGER.log(System.Logger.Level.DEBUG, "DDL script initialization");
        }
        byte databaseAction = 0x00;
        if (dataJpaConfig.initScript().isPresent()) {
            property("jakarta.persistence.schema-generation.create-script-source",
                     dataJpaConfig.initScript().get());
            property("jakarta.persistence.schema-generation.create-source",
                     "script");

            databaseAction |= 0x01;
        }
        if (dataJpaConfig.dropScript().isPresent()) {
            property("jakarta.persistence.schema-generation.drop-script-source",
                     dataJpaConfig.dropScript().get());
            property("jakarta.persistence.schema-generation.drop-source",
                     "script");
            databaseAction |= 0x02;
        }
        switch (databaseAction) {
        case 0x00:
            break;
        case 0x01:
            property("jakarta.persistence.schema-generation.database.action",
                     "create");
            break;
        case 0x02:
            property("jakarta.persistence.schema-generation.database.action",
                     "drop");
            break;
        case 0x03:
            property("jakarta.persistence.schema-generation.database.action",
                     "drop-and-create");
            break;
        default:
            throw new IllegalStateException("Value " + databaseAction + " of databaseAction is out of bounds [0x01, 0x03]");
        }
    }

    /**
     * Persistence provider configuration.
     * Copies common persistence provider configuration.
     *
     * @param dataJpaConfig source Helidon data Configuration
     */
    public void configureProvider(DataJpaConfig dataJpaConfig) {
        dataJpaConfig.providerClassName()
                .ifPresent(this::provider);
        dataJpaConfig.properties()
                .forEach(this::property);
        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            LOGGER.log(System.Logger.Level.DEBUG, "Persistence provider initialization");
            if (dataJpaConfig.providerClassName().isPresent()) {
                LOGGER.log(System.Logger.Level.DEBUG, String.format(" - provider: %s",
                                                                    dataJpaConfig.providerClassName().get()));
            }
            for (Map.Entry<String, Object> mapEntry : properties().entrySet()) {
                LOGGER.log(System.Logger.Level.DEBUG,
                           String.format(" - property: %s = %s", mapEntry.getKey(), mapEntry.getValue()));
            }
        }
    }

    /**
     * Jakarta Persistence database connection configuration.
     * Copies database connection configuration into persistence unit configuration.
     *
     * @param dataJpaConfig source Helidon data Configuration
     * @param driverClass JDBC driver class
     * @param registry service registry
     */
    public void configureConnection(DataJpaConfig dataJpaConfig,
                                    Class<? extends Driver> driverClass,
                                    ServiceRegistry registry) {
        if (LOGGER.isLoggable(Level.DEBUG)) {
            LOGGER.log(Level.DEBUG, "Database connection initialization");
        }
        // Only exactly one of the following options is possible, DataSource or URL
        // - DataSource
        if (dataJpaConfig.dataSource().isPresent()) {
            configureDataSource(dataJpaConfig.dataSource().get(),
                                dataJpaConfig.jtaDataSource().orElse(false),
                                registry);
        }
        // - URL as connection-string
        if (dataJpaConfig.connectionString().isPresent()) {
            // Set JDBC driver found by DriverManager when no value was configured
            // This is required only with URL
            dataJpaConfig.jdbcDriverClassName()
                    .ifPresentOrElse(value -> property("jakarta.persistence.jdbc.driver", value),
                                     () -> property("jakarta.persistence.jdbc.driver", driverClass.getName()));
            // Connection may be configured using connectionString, username and password or using DataSource
            dataJpaConfig.username()
                    .ifPresent(value -> property("jakarta.persistence.jdbc.user", value));
            dataJpaConfig.password()
                    .ifPresent(value -> property(
                            "jakarta.persistence.jdbc.password",
                            // Temporary removed, because EclipseLink 4.0 fails with password as char[]
                            // EclipseLink accepts password as char[] value, but common Jakarta Persistence API does not
                            //dataJpaConfig.provider().isPresent()
                            //        && dataJpaConfig.provider().get().startsWith("org.eclipse.persistence")
                            //        ? value : new String(value)));
                            // EclipseLink 4.0 workaround
                            new String(value)));
            dataJpaConfig.connectionString()
                    .ifPresent(value -> property("jakarta.persistence.jdbc.url", value));
        }
    }

    // Configure DataSource using name from Config
    private void configureDataSource(String dataSourceName, boolean isJta, ServiceRegistry registry) {
        DataSource ds = registry.get(Lookup.builder()
                             .addContract(DataSource.class)
                             .addQualifier(Qualifier.createNamed(dataSourceName))
                             .build());
        if (isJta) {
            jtaDataSource(ds);
        } else {
            nonJtaDataSource(ds);
        }
    }

}
