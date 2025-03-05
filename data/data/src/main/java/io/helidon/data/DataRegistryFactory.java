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

package io.helidon.data;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.helidon.common.Functions;
import io.helidon.data.spi.DataSupport;
import io.helidon.data.spi.RepositoryProvider;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceInstance;
import io.helidon.transaction.Tx;

@Service.Singleton
@Service.Named(Service.Named.WILDCARD_NAME)
class DataRegistryFactory implements Service.ServicesFactory<DataRegistry> {
    private final List<ServiceInstance<DataSupport>> supports;
    private final List<RepositoryProvider<?>> repositories;

    DataRegistryFactory(List<ServiceInstance<DataSupport>> supports, List<RepositoryProvider<?>> repositories) {
        this.supports = supports;
        this.repositories = repositories;
    }

    @Override
    public List<Service.QualifiedInstance<DataRegistry>> services() {
        // each support is one registry
        return supports.stream()
                .map(it -> toRegistry(it, repositories))
                .collect(Collectors.toUnmodifiableList());
    }

    private Service.QualifiedInstance<DataRegistry> toRegistry(ServiceInstance<DataSupport> it,
                                                               List<RepositoryProvider<?>> repositories) {
        DataSupport dataSupport = it.get();
        String type = dataSupport.type();
        String name = it.qualifiers()
                .stream()
                .filter(q -> q.typeName().equals(Service.Named.TYPE))
                .findFirst()
                .flatMap(Qualifier::value)
                .orElse(null);
        var matchingRepositories = repositories.stream()
                .filter(repo -> repo.dataSupportType().equals(type))
                .filter(repo -> sameName(repo, name))
                .collect(Collectors.toUnmodifiableList());
        Set<Qualifier> qualifiers = new HashSet<>();
        if (name != null) {
            qualifiers.add(Qualifier.createNamed(name));
        }
        return Service.QualifiedInstance.create(new RegistryImpl(dataSupport, matchingRepositories), qualifiers);
    }

    private boolean sameName(RepositoryProvider<?> repo, String name) {
        String repoName = repo.dataSourceName();

        if (name == null && repoName.equals(Service.Named.DEFAULT_NAME)) {
            return true;
        }
        if (name == null) {
            return false;
        }
        return repoName.equals(name);
    }

    private static final class RegistryImpl implements DataRegistry {
        private final Map<Class<?>, Supplier<Object>> repositories;
        private final DataSupport support;

        private RegistryImpl(DataSupport dataSupport, List<RepositoryProvider<?>> matchingRepositories) {
            this.support = dataSupport;

            Map<Class<?>, Supplier<Object>> repositories = new HashMap<>();
            for (RepositoryProvider<?> repo : matchingRepositories) {
                repositories.putIfAbsent(repo.type(), repo::get);
            }
            this.repositories = Map.copyOf(repositories);
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T extends Data.GenericRepository<?, ?>> T repository(Class<? super T> repository) {
            var repo = repositories.get(repository);
            if (repo == null) {
                throw new DataException("Repository of type " + repository.getName() + " is not available in this data support: "
                                                + support);
            }
            return (T) repo.get();
        }

        @Override
        public DataConfig dataConfig() {
            return support.dataConfig();
        }

        @Override
        public void close() throws Exception {
            support.close();
        }

        @Override
        public <T> T transaction(Tx.Type type, Callable<T> task) {
            return support.transaction(type, task);
        }

        @Override
        public <E extends Throwable> void transaction(Tx.Type type, Functions.CheckedRunnable<E> task) {
            support.transaction(type, task);
        }

        @Override
        public <T> T transaction(Tx.Type type, Function<Tx.Transaction, T> task) {
            return support.transaction(type, task);
        }

        @Override
        public void transaction(Tx.Type type, Consumer<Tx.Transaction> task) {
            support.transaction(type, task);
        }

        @Override
        public Tx.Transaction transaction(Tx.Type type) {
            return support.transaction(type);
        }
    }
}
