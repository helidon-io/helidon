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

import java.util.Set;
import java.util.function.Function;

import io.helidon.data.DataConfig;
import io.helidon.data.jakarta.persistence.spi.JakartaPersistenceExtension;
import io.helidon.data.spi.DataSupport;
import io.helidon.data.spi.RepositoryFactory;

import jakarta.persistence.EntityManagerFactory;

@SuppressWarnings("deprecation")
class DataJpaSupport implements DataSupport {
    private static final System.Logger LOGGER = System.getLogger(DataJpaSupport.class.getName());

    private final DataConfig config;
    private final EntityManagerFactory factory;
    private final Set<Class<?>> entities;

    DataJpaSupport(DataConfig config, EntityManagerFactory factory, Set<Class<?>> entities) {
        this.config = config;
        this.factory = factory;
        this.entities = entities;
    }

    static DataJpaSupport create(DataConfig config, JakartaPersistenceExtension extension) {
        EntityManagerFactory factory = extension.createFactory();
        Set<Class<?>> entities = extension.entities();
        return new DataJpaSupport(config, factory, entities);
    }

    @Override
    public RepositoryFactory repositoryFactory() {
        return new RepositoryFactoryImpl(factory);
    }

    @Override
    public String type() {
        return DataJpaSupportProviderService.PROVIDER_TYPE;
    }

    @Override
    public void close() {
        factory.close();
    }

    @Override
    public DataConfig dataConfig() {
        return config;
    }

    @Override
    public String toString() {
        return type() + " data support, config: " + config;
    }

    private static class RepositoryFactoryImpl implements RepositoryFactory {
        private final EntityManagerFactory factory;

        private RepositoryFactoryImpl(EntityManagerFactory factory) {
            this.factory = factory;
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        @Override
        public <E, T> T create(Function<E, T> creator) {
            Function creatorErased = creator;
            return (T) creatorErased.apply(new JpaRepositoryExecutorImpl(factory));
        }
    }

}
