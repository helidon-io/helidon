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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.naming.Context;
import javax.sql.DataSource;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.config.Config;
import io.helidon.data.Data;
import io.helidon.data.DataException;
import io.helidon.data.jakarta.persistence.spi.JpaEntityProvider;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceInstance;
import io.helidon.transaction.spi.TxSupport;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.spi.PersistenceProvider;

@Service.Singleton
class PersistenceUnitFactory implements Service.ServicesFactory<EntityManagerFactory> {
    static final String JPA_PU_CONFIG_KEY = "data.persistence-units.jakarta";
    static final String PROVIDER_TYPE = "jakarta";
    /**
     * In Jakarta Persistence 3.1 transaction type is stored as {@link EntityManagerFactory} property.
     * <p>
     * This will be replaced by {@code EntityManagerFactory#getTransactionType()}
     */
    static final String TRANSACTION_TYPE = "io.helidon.data.transaction-type";
    private static final Qualifier PROVIDER_QUALIFIER = Qualifier.builder()
            .typeName(Data.ProviderType.TYPE)
            .value(PROVIDER_TYPE)
            .build();

    static {
        // Make sure we have JNDI lookup available.
        String property = System.getProperty(Context.INITIAL_CONTEXT_FACTORY);
        if (property == null) {
            System.setProperty(Context.INITIAL_CONTEXT_FACTORY, "io.helidon.service.jndi.NamingFactory");
        }
    }

    private final Supplier<List<ServiceInstance<DataSource>>> dataSourcesSupplier;
    private final Supplier<List<ServiceInstance<JpaEntityProvider<?>>>> entitiesSupplier;
    private final Supplier<Config> configSupplier;
    private final Supplier<TxSupport> txSupportSupplier;
    private final List<PersistenceProvider> persistenceProviders;
    private final List<EntityManagerFactory> createdFactories = new CopyOnWriteArrayList<>();

    @Service.Inject
    PersistenceUnitFactory(Supplier<List<ServiceInstance<DataSource>>> dataSourcesSupplier,
                           Supplier<List<ServiceInstance<JpaEntityProvider<?>>>> entitiesSupplier,
                           Supplier<Config> configSupplier,
                           Supplier<TxSupport> txSupportSupplier) {
        this.dataSourcesSupplier = dataSourcesSupplier;
        this.configSupplier = configSupplier;
        this.txSupportSupplier = txSupportSupplier;
        this.entitiesSupplier = entitiesSupplier;

        this.persistenceProviders = HelidonServiceLoader.create(PersistenceProvider.class)
                .asList();
    }

    @Override
    public List<Service.QualifiedInstance<EntityManagerFactory>> services() {
        Config config = configSupplier.get().get(JPA_PU_CONFIG_KEY);

        return config.asNodeList()
                .stream()
                .flatMap(List::stream)
                .flatMap(this::mapSingleConfig)
                .toList();
    }

    @Service.PreDestroy
    void preDestroy() {
        createdFactories.forEach(EntityManagerFactory::close);
    }

    private Stream<Service.QualifiedInstance<EntityManagerFactory>> mapSingleConfig(Config config) {
        String name = config.get("name").asString().orElse(Service.Named.DEFAULT_NAME);
        var namedQualifier = Qualifier.createNamed(name);
        // we always create an unqualified instance and a qualified one (both are named)
        var emf = create(name, config);
        createdFactories.add(emf);
        return Stream.of(Service.QualifiedInstance.create(emf,
                                                          namedQualifier),
                         Service.QualifiedInstance.create(emf,
                                                          namedQualifier,
                                                          PROVIDER_QUALIFIER));
    }

    @SuppressWarnings({"rawtypes"})
    private EntityManagerFactory create(String name, Config config) {
        JpaPersistenceUnitConfig jpaPersistenceUnitConfig = JpaPersistenceUnitConfig.create(config);

        TxSupport txSupport = txSupportSupplier.get();
        Set<Class> entityClasses = entitiesSupplier.get()
                .stream()
                .filter(this::isSupported)
                .map(ServiceInstance::get)
                .map(JpaEntityProvider::entityClass)
                .collect(Collectors.toSet());

        // and add all explicitly listed classes in config
        Set<Class> classes = jpaPersistenceUnitConfig.managedClasses();
        entityClasses.addAll(classes);

        PersistenceConfiguration puConfig = PersistenceConfiguration.create(name,
                                                                            txSupport,
                                                                            entityClasses,
                                                                            dataSourcesSupplier,
                                                                            jpaPersistenceUnitConfig);

        Map<String, Object> providerProps = Map.of(TRANSACTION_TYPE, puConfig.transactionType());

        for (PersistenceProvider persistenceProvider : persistenceProviders) {
            if (puConfig.isValid(persistenceProvider)) {
                var emf = persistenceProvider.createContainerEntityManagerFactory(puConfig, providerProps);
                if (emf != null) {
                    return emf;
                }
            }
        }

        throw new DataException("Could not find a JPA persistence provider for persistence unit \"" + name + "\"");
    }

    private boolean isSupported(ServiceInstance<?> serviceInstance) {
        // we support instances with `@Data.ProviderType` qualifier that matches us, or with no qualifier of such type
        for (Qualifier qualifier : serviceInstance.qualifiers()) {
            if (qualifier.typeName().equals(Data.ProviderType.TYPE)) {
                // only use if qualifier matches
                return qualifier.stringValue().orElse(PROVIDER_TYPE).equals(PROVIDER_TYPE);

            }
        }
        // no qualifier, matches
        return true;
    }
}
