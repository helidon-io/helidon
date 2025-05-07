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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.data.DataConfig;
import io.helidon.data.DataException;
import io.helidon.data.jakarta.persistence.spi.JakartaPersistenceExtension;
import io.helidon.data.jakarta.persistence.spi.JpaEntityProvider;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.transaction.spi.TxSupport;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.spi.PersistenceProvider;
import jakarta.persistence.spi.PersistenceUnitInfo;

/**
 * Helidon Data Jakarta Persistence compliant provider with no external dependency.
 */
@SuppressWarnings("deprecation")
class JpaExtensionImpl implements JakartaPersistenceExtension {
    /**
     * In Jakarta Persistence 3.1 transaction type is stored as {@link EntityManagerFactory} property.
     * <p>
     * This will be replaced by {@code EntityManagerFactory#getTransactionType()}
     */
    static final String TRANSACTION_TYPE = "io.helidon.data.transaction-type";
    private static final System.Logger LOGGER = System.getLogger(JpaExtensionImpl.class.getName());
    // Temporary code for Jakarta Persistence 3.1 compliant initialization
    private static final List<PersistenceProvider> PERSISTENCE_PROVIDERS = persistenceProviders();

    private final DataConfig dataConfig;
    private final DataJpaConfig dataJpaConfig;
    private final ServiceRegistry registry;
    private final List<JpaEntityProvider<?>> entityProviders;
    private final Set<Class<?>> entities;
    private final TxSupport txSupport;

    JpaExtensionImpl(DataConfig dataConfig,
                     ServiceRegistry registry,
                     List<JpaEntityProvider<?>> providers,
                     TxSupport txSupport) {
        this.registry = registry;
        this.entityProviders = providers;
        this.dataConfig = dataConfig;
        this.dataJpaConfig = (DataJpaConfig) dataConfig.provider();
        this.entities = providers.stream()
                .map(JpaEntityProvider::entityClass)
                .collect(Collectors.toUnmodifiableSet());
        this.txSupport = txSupport;
    }

    @Override
    public EntityManagerFactory createFactory() {
        PersistenceConfiguration config = PersistenceConfiguration.create(dataConfig.name(), txSupport);

        config.configureConnection(dataJpaConfig, registry);
        config.configureScripts(dataJpaConfig);
        config.configureProvider(dataJpaConfig);
        // Configure entities
        Map<String, Class<?>> entities = new HashMap<>();
        // EntityMetadataProvider is not implemented in Jakarta Persistence compliant provider.
        // But user may define his own, so they shall be scanned
        entityProviders.forEach(provider -> {
            if (LOGGER.isLoggable(Level.DEBUG)) {
                LOGGER.log(Level.DEBUG, String.format(" - Entity %s from provider", provider.entityClass().getName()));
            }
            entities.put(provider.entityClass().getName(), provider.entityClass());
        });
        entities.values().forEach(config::managedClass);
        return createFactory(config);
    }

    @Override
    public Set<Class<?>> entities() {
        return entities;
    }

    // Jakarta Persistence 3.2 compliant initialization, temporary implemented using internal classes

    /**
     * Creates an instance of {@link EntityManagerFactory} class from provided {@link PersistenceConfiguration}.
     *
     * @param config persistence unit configuration
     * @return new instance of {@link EntityManagerFactory}
     * @deprecated will be removed with Jakarta Persistence 3.2
     */
    @Deprecated
    protected EntityManagerFactory createFactory(PersistenceConfiguration config) {
        // Jakarta Persistence 3.1 workaround to pass PersistenceUnitTransactionType to executor
        Map<String, Object> properties = Map.of(TRANSACTION_TYPE, config.transactionType());
        EntityManagerFactory emf = null;
        Iterator<PersistenceProvider> iterator = PERSISTENCE_PROVIDERS.iterator();
        PersistenceUnitInfo persistenceUnitInfo = new PersistenceUnitInfoImpl(config);
        while (iterator.hasNext()) {
            PersistenceProvider provider = iterator.next();
            emf = provider.createContainerEntityManagerFactory(persistenceUnitInfo, properties);
            if (emf != null) {
                break;
            }
        }
        if (emf == null) {
            throw new DataException("No Persistence provider for EntityManager named " + config.name());
        }
        return emf;
    }

    // Temporary code for Jakarta Persistence 3.1 compliant initialization
    private static List<PersistenceProvider> persistenceProviders() {
        // Temporary code for Jakarta Persistence 3.1 compliant initialization
        return HelidonServiceLoader
                .create(ServiceLoader.load(PersistenceProvider.class))
                .asList();
    }

}
