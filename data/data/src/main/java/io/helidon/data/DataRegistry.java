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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;

import io.helidon.common.Functions.CheckedRunnable;
import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.config.Config;
import io.helidon.common.config.ConfigException;
import io.helidon.data.spi.DataProvider;
import io.helidon.transaction.Tx;

/**
 * Registry of repositories that also provides methods for handling transactions.
 * <p>
 * Helidon Service Registry is used to set up the data and repositories. Easiest way to use this module is to use inject
 * service registry and lookup appropriate repository (or simply inject it, if you are implementing a service).
 * <p>
 * We create a named instance for each named configuration under
 * {@code data} root key. Even when one of the factory methods on this class is used, the same approach is applied. Only a
 * new service registry is created for each call.
 * <p>
 * Specific repositories are available in the default data instance, unless they are annotated with
 * {@link io.helidon.data.Data.DataSource} with the correct name.
 * A repository CANNOT be available in more than one data registry.
 */
public interface DataRegistry extends AutoCloseable {
    /**
     * Create new instance of Helidon Data.
     * This will look for data configuration named {@code @default} under the current node.
     *
     * @param config Helidon Data Repository specific configuration node.
     * @return new instance of Helidon Data.
     */
    static DataRegistry create(Config config) {
        return create(config, "@default");
    }

    /**
     * Create new instance of Helidon Data.
     * This will look for data configuration with the provided name.
     *
     * @param config config node of Helidon data (usually {@code data} root node)
     * @param name   name of the child configuration (either an object name, or {@code name} node of a list element)
     * @return new instance of Helidon Data.
     */
    static DataRegistry create(Config config, String name) {
        Objects.requireNonNull(config);
        Objects.requireNonNull(name);

        DataConfig dataConfig;

        if (config.isList() || config.get("0").exists()) {
            dataConfig = fromList(config, name);
        } else {
            dataConfig = fromObject(config, name);
        }

        return create(dataConfig);
    }

    /**
     * Create instances for the provided configurations. Note that each configuration must have a unique name.
     * One configuration may not have a name defined (i.e. it would use the default name).
     *
     * @param dataConfigs configurations
     * @return all data registries for the configurations
     */
    static List<DataRegistry> create(List<DataConfig> dataConfigs) {
        List<DataProvider> providers = HelidonServiceLoader.create(ServiceLoader.load(DataProvider.class)).asList();
        if (providers.isEmpty()) {
            throw new DataException("No Helidon Data Repository implementation found, maybe add helidon-data module on "
                                            + "runtime classpath");
        }
        return providers.getFirst().create(dataConfigs);
    }

    /**
     * Create new instance of Helidon Data.
     *
     * @param dataConfig Helidon Data configuration
     * @return new instance of Helidon Data.
     */
    static DataRegistry create(DataConfig dataConfig) {
        return create(List.of(dataConfig)).getFirst();
    }

    /**
     * Data config that was used to create this instance.
     *
     * @return data config
     */
    DataConfig dataConfig();

    /**
     * Creates an instance of data repository for provided data repository interface.
     *
     * @param repository data repository interface or abstract class to create, shall not be {@code null}
     * @param <T>        target data repository type
     * @return new data repository instance
     * @throws io.helidon.data.DataException in case the repository is not a valid type for this registry
     */
    <T extends Data.GenericRepository<?, ?>> T repository(Class<? super T> repository);

    /**
     * Execute provided task as database transaction.
     * Transaction is handled automatically. Task computes and returns result.
     *
     * @param task task to run in transaction
     * @param <T>  the result type of the task
     * @return computed task result
     * @throws DataException when result computation failed
     */
    default <T> T transaction(Callable<T> task) {
        return transaction(Tx.Type.REQUIRED, task);
    }

    /**
     * Execute provided task as database transaction.
     * Transaction is handled automatically. Task computes and returns result.
     *
     * @param type transaction type
     * @param task task to run in transaction
     * @param <T>  the result type of the task
     * @return computed task result
     * @throws DataException when result computation failed
     */
    <T> T transaction(Tx.Type type, Callable<T> task);

    /**
     * Execute provided task as database transaction.
     * Transaction is handled automatically. Task does not return any result.
     *
     * @param task task to run in transaction
     * @param <E>  type of thrown (checked) exception
     * @throws DataException when task computation failed
     */
    default <E extends Throwable> void transaction(CheckedRunnable<E> task) {
        transaction(Tx.Type.REQUIRED, task);
    }

    /**
     * Execute provided task as database transaction.
     * Transaction is handled automatically. Task does not return any result.
     *
     * @param type transaction type
     * @param task task to run in transaction
     * @param <E>  type of thrown (checked) exception
     * @throws DataException when task computation failed
     */
    <E extends Throwable> void transaction(Tx.Type type, CheckedRunnable<E> task);

    /**
     * Execute provided task as database transaction.
     * Transaction is finished manually. Task computes and returns result.
     *
     * @param task task to run in transaction
     * @param <T>  the result type of the task
     * @return computed task result
     */
    default <T> T transaction(Function<Tx.Transaction, T> task) {
        return transaction(Tx.Type.REQUIRED, task);
    }

    /**
     * Execute provided task as database transaction.
     * Transaction is finished manually. Task computes and returns result.
     *
     * @param type transaction type
     * @param task task to run in transaction
     * @param <T>  the result type of the task
     * @return computed task result
     */
    <T> T transaction(Tx.Type type, Function<Tx.Transaction, T> task);

    /**
     * Execute provided task as database transaction.
     * Transaction is handled automatically. Task does not return any result.
     *
     * @param task task to run in transaction
     */
    default void transaction(Consumer<Tx.Transaction> task) {
        transaction(Tx.Type.REQUIRED, task);
    }

    /**
     * Execute provided task as database transaction.
     * Transaction is handled automatically. Task does not return any result.
     *
     * @param type transaction type
     * @param task task to run in transaction
     */
    void transaction(Tx.Type type, Consumer<Tx.Transaction> task);

    /**
     * Start transaction.
     * Transaction is finished manually.
     *
     * @return transaction handler
     */
    default Tx.Transaction transaction() {
        return transaction(Tx.Type.REQUIRED);
    }

    /**
     * Start transaction.
     * Transaction is finished manually.
     *
     * @param type transaction type
     * @return transaction handler
     */
    Tx.Transaction transaction(Tx.Type type);

    private static DataConfig fromObject(Config config, String name) {
        Config namedConfig = config.get(name);
        if (namedConfig.exists()) {
            return DataConfig.create(namedConfig);
        }
        if ("@default".equals(name)) {
            return DataConfig.create(config);
        }
        throw new ConfigException("Helidon data configuration node for named instance is missing. Name: \"" + name + "\"");
    }

    private static DataConfig fromList(Config config, String name) {
        List<Config> configs = config.asNodeList()
                .orElseGet(List::of);

        List<String> foundNames = new ArrayList<>();

        for (Config namedConfig : configs) {
            String nodeName = namedConfig.get("name").asString().orElse("@default");
            if (name.equals(nodeName)) {
                return DataConfig.create(namedConfig);
            }
            foundNames.add(nodeName);
        }
        throw new ConfigException("Helidon data configuration node for named instance is missing. Name: \"" + name + "\", found"
                                          + " names: " + foundNames);
    }
}
