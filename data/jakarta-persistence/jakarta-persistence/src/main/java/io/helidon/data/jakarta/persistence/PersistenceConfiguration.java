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
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.function.Supplier;

import javax.sql.DataSource;

import io.helidon.data.sql.common.ConnectionConfig;
import io.helidon.data.sql.common.SqlDriver;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.ServiceInstance;
import io.helidon.transaction.spi.TxSupport;

import jakarta.persistence.SharedCacheMode;
import jakarta.persistence.ValidationMode;
import jakarta.persistence.spi.ClassTransformer;
import jakarta.persistence.spi.PersistenceProvider;
import jakarta.persistence.spi.PersistenceUnitInfo;

/**
 * Temporary replacement of Jakarta Persistence 3.2 {@code PersistenceConfiguration} class
 * while Helidon depends on Jakarta Persistence 3.1.
 */
@SuppressWarnings({"deprecation", "rawtypes"})
final class PersistenceConfiguration implements PersistenceUnitInfo {
    private static final System.Logger LOGGER = System.getLogger(PersistenceConfiguration.class.getName());

    private final String name;
    private final PersistenceUnitTransactionType transactionType;
    private final Set<Class> managedClasses;
    private final Map<String, Object> properties;
    private final String provider;
    private final DataSource jtaDataSource;
    private final DataSource nonJtaDataSource;
    private final ClassLoader classLoader;

    private PersistenceConfiguration(String name,
                                     PersistenceUnitTransactionType txType,
                                     Set<Class> entityClasses,
                                     String providerClassName,
                                     Map<String, Object> properties,
                                     DataSource jtaDataSource,
                                     DataSource nonJtaDataSource) {
        Objects.requireNonNull(name, "Persistence unit name should not be null");
        this.name = name;
        this.transactionType = txType;
        this.managedClasses = Set.copyOf(entityClasses);
        this.properties = Map.copyOf(properties);
        this.provider = providerClassName;
        this.jtaDataSource = jtaDataSource;
        this.nonJtaDataSource = nonJtaDataSource;

        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = PersistenceConfiguration.class.getClassLoader();
        }
        this.classLoader = classLoader;
    }

    @SuppressWarnings("rawtypes")
    static PersistenceConfiguration create(String name,
                                           TxSupport txSupport,
                                           Set<Class> entityClasses,
                                           Supplier<List<ServiceInstance<DataSource>>> dataSourcesSupplier,
                                           JpaPersistenceUnitConfig jpaConfig) {

        PersistenceUnitTransactionType txType = txType(txSupport.type());
        Map<String, Object> properties = new HashMap<>();
        DataSource jtaDataSource = txType == PersistenceUnitTransactionType.JTA && jpaConfig.dataSource().isPresent()
                ? dataSource(name, jpaConfig.dataSource().get(), dataSourcesSupplier)
                : null;
        DataSource nonJtaDataSource = txType != PersistenceUnitTransactionType.JTA && jpaConfig.dataSource().isPresent()
                ? dataSource(name, jpaConfig.dataSource().get(), dataSourcesSupplier)
                : null;
        if (jpaConfig.connection().isPresent()) {
            configureConnection(jpaConfig.connection().get(), properties);
        }

        configureScripts(jpaConfig, properties);

        String providerClassName = jpaConfig.providerClassName().orElse(null);
        properties.putAll(jpaConfig.properties());

        if (LOGGER.isLoggable(Level.DEBUG)) {
            LOGGER.log(Level.DEBUG, "Persistence provider initialization");
            if (jpaConfig.providerClassName().isPresent()) {
                LOGGER.log(Level.DEBUG, String.format(" - provider: %s",
                                                      jpaConfig.providerClassName().get()));
            }
            for (Map.Entry<String, Object> mapEntry : properties.entrySet()) {
                LOGGER.log(Level.DEBUG,
                           String.format(" - property: %s = %s", mapEntry.getKey(), mapEntry.getValue()));
            }
        }

        return new PersistenceConfiguration(name,
                                            txType,
                                            entityClasses,
                                            providerClassName,
                                            properties,
                                            jtaDataSource,
                                            nonJtaDataSource);
    }

    @Override
    public String getPersistenceUnitName() {
        return name;
    }

    @Override
    public String getPersistenceProviderClassName() {
        return provider;
    }

    @Override
    public jakarta.persistence.spi.PersistenceUnitTransactionType getTransactionType() {
        return transactionType.jpaType();
    }

    @Override
    public DataSource getJtaDataSource() {
        return jtaDataSource;
    }

    @Override
    public DataSource getNonJtaDataSource() {
        return nonJtaDataSource;
    }

    @Override
    public List<String> getMappingFileNames() {
        return List.of();
    }

    @Override
    public List<URL> getJarFileUrls() {
        return List.of();
    }

    @Override
    public URL getPersistenceUnitRootUrl() {
        // just point to current user directory, we do not have support for PU URL, as we may run in native image
        // this should be used to discover persistence.xml, which this class replaces
        try {
            return Paths.get(".").toUri().toURL();
        } catch (MalformedURLException e) {
            throw new IllegalStateException("Cannot define root PU URL", e);
        }
    }

    @Override
    public List<String> getManagedClassNames() {
        return managedClasses.stream()
                .map(Class::getName)
                .toList();
    }

    @Override
    public boolean excludeUnlistedClasses() {
        return true;
    }

    @Override
    public SharedCacheMode getSharedCacheMode() {
        return SharedCacheMode.UNSPECIFIED;
    }

    @Override
    public ValidationMode getValidationMode() {
        return ValidationMode.AUTO;
    }

    @Override
    public Properties getProperties() {
        Properties props = new Properties();
        props.putAll(this.properties);
        return props;
    }

    @Override
    public String getPersistenceXMLSchemaVersion() {
        return null;
    }

    @Override
    public ClassLoader getClassLoader() {
        return classLoader;
    }

    @Override
    public void addTransformer(ClassTransformer transformer) {

    }

    @Override
    public ClassLoader getNewTempClassLoader() {
        return new URLClassLoader(name + "-tmp", new URL[0], classLoader);
    }

    /**
     * The {@linkplain PersistenceUnitTransactionType transaction type}.
     * <ul>
     * <li>If {@link PersistenceUnitTransactionType#JTA}, a JTA data
     *    source must be provided via {@link #getJtaDataSource()},
     *    or by the container.
     * <li>If {@link PersistenceUnitTransactionType#RESOURCE_LOCAL},
     *    database connection properties may be specified via
     *    {@link #getProperties()}, or a non-JTA datasource may be
     *    provided via {@link #getNonJtaDataSource()}.
     * </ul>
     *
     * @return the transaction type
     */
    PersistenceUnitTransactionType transactionType() {
        return transactionType;
    }

    boolean isValid(PersistenceProvider persistenceProvider) {
        if (this.provider == null) {
            // not explicit
            return true;
        }
        return provider.equals(persistenceProvider.getClass().getName());
    }

    /**
     * Jakarta Persistence DDL scripts configuration.
     * Copies database initialization and cleanup scripts configuration into persistence unit configuration.
     *
     * @param dataJpaConfig source Helidon data Configuration
     */
    private static void configureScripts(JpaPersistenceUnitConfig dataJpaConfig, Map<String, Object> properties) {
        if (LOGGER.isLoggable(Level.DEBUG)) {
            LOGGER.log(Level.DEBUG, "DDL script initialization");
        }
        byte databaseAction = 0x00;
        if (dataJpaConfig.initScript().isPresent()) {
            properties.put("jakarta.persistence.schema-generation.create-script-source",
                           dataJpaConfig.initScript().get().toString());
            properties.put("jakarta.persistence.schema-generation.create-source",
                           "script");

            databaseAction |= 0x01;
        }
        if (dataJpaConfig.dropScript().isPresent()) {
            properties.put("jakarta.persistence.schema-generation.drop-script-source",
                           dataJpaConfig.dropScript().get().toString());
            properties.put("jakarta.persistence.schema-generation.drop-source",
                           "script");
            databaseAction |= 0x02;
        }
        switch (databaseAction) {
        case 0x00:
            break;
        case 0x01:
            properties.put("jakarta.persistence.schema-generation.database.action",
                           "create");
            break;
        case 0x02:
            properties.put("jakarta.persistence.schema-generation.database.action",
                           "drop");
            break;
        case 0x03:
            properties.put("jakarta.persistence.schema-generation.database.action",
                           "drop-and-create");
            break;
        default:
            throw new IllegalStateException("Value " + databaseAction + " of databaseAction is out of bounds [0x01, 0x03]");
        }
    }

    private static PersistenceUnitTransactionType txType(String type) {
        return PersistenceUnitTransactionType.parse(type);
    }

    // Configure DataSource using name from Config
    private static DataSource dataSource(String puName,
                                         String dataSourceName,
                                         Supplier<List<ServiceInstance<DataSource>>> dataSourcesSupplier) {
        Qualifier dataSourceQualifier = Qualifier.createNamed(dataSourceName);

        return dataSourcesSupplier.get()
                .stream()
                .filter(it -> it.qualifiers().contains(dataSourceQualifier))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Configured data source for persistence unit \"" + puName
                                                                     + "\" named \"" + dataSourceName
                                                                     + "\" is not available in ServiceRegistry."))
                .get();
    }

    private static void configureConnection(ConnectionConfig connectionConfig,
                                            Map<String, Object> properties) {

        // connection directly to database
        SqlDriver driverSource = SqlDriver.create(connectionConfig);
        // Set JDBC driver found by DriverManager when no value was configured
        // This is required only with URL
        properties.put("jakarta.persistence.jdbc.driver", driverSource.driverClass().getName());

        // Connection may be configured using connectionUri, username and password or using DataSource
        connectionConfig.username()
                .ifPresent(value -> properties.put("jakarta.persistence.jdbc.user", value));
        connectionConfig.password()
                .ifPresent(value -> properties.put(
                        "jakarta.persistence.jdbc.password",
                        // Temporary removed, because EclipseLink 4.0 fails with password as char[]
                        // EclipseLink accepts password as char[] value, but common Jakarta Persistence API does not
                        //dataJpaConfig.provider().isPresent()
                        //        && dataJpaConfig.provider().get().startsWith("org.eclipse.persistence")
                        //        ? value : new String(value)));
                        // EclipseLink 4.0 workaround
                        new String(value)));
        properties.put("jakarta.persistence.jdbc.url", connectionConfig.url());
    }
}
