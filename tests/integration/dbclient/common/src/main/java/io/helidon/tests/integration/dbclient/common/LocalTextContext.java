/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.dbclient.common;

import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.spi.ConfigNode;
import io.helidon.config.spi.ConfigSource;
import io.helidon.config.spi.LazyConfigSource;
import io.helidon.dbclient.DbClient;

/**
 * Tuple for local tests.
 *
 * @param <T> delegate type
 */
public record LocalTextContext<T>(DbClient db, Config config, Supplier<T> delegateSupplier) {

    /**
     * Create a new context.
     *
     * @param factory      delegate factory
     * @param overrides    config overrides
     * @param createSchema {@code true} to create the schema
     * @param <T>          delegate type
     * @return context
     */
    public static <T> LocalTextContext<T> create(BiFunction<DbClient, Config, T> factory,
                                                 Map<String, Supplier<?>> overrides,
                                                 boolean createSchema) {
        Config config = Config.create(
                new LazyMapConfigSourceImpl(overrides),
                ConfigSources.classpath("db.yaml"),
                ConfigSources.classpath("db-common.yaml"));
        Config.global(config);
        DbClient db = DbClient.create(config.get("db"));
        T delegate = factory.apply(db, config);
        if (createSchema) {
            DBHelper.createSchema(db);
        }
        DBHelper.insertDataSet(db);
        return new LocalTextContext<>(db, config, () -> delegate);
    }

    /**
     * Get the delegate.
     *
     * @return delegate
     */
    public T delegate() {
        return delegateSupplier.get();
    }

    private record LazyMapConfigSourceImpl(Map<String, Supplier<?>> map) implements ConfigSource, LazyConfigSource {

        @Override
        public Optional<ConfigNode> node(String key) {
            return Optional.ofNullable(map.get(key))
                    .flatMap(v -> Optional.ofNullable(v.get()))
                    .map(Object::toString)
                    .map(ConfigNode.ValueNode::create);
        }
    }
}
