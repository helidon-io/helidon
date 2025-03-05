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

package io.helidon.data.spi;

import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;

import io.helidon.common.Functions;
import io.helidon.data.DataConfig;
import io.helidon.transaction.Tx;

/**
 * Implemented by each support (such as Jakarta persistence, Eclipselink native, SQL native etc.).
 * Instances are created through {@link io.helidon.data.spi.DataSupportProvider#create(DataConfig)}.
 */
public interface DataSupport extends AutoCloseable {
    /**
     * Factory to instantiate repositories, specific to the support.
     *
     * @return repository factory
     */
    RepositoryFactory repositoryFactory();

    /**
     * Type of support (such as {@code jakarta}, {@code eclipselink}, {@code sql}).
     *
     * @return type uniquely identifying this support
     */
    String type();

    /**
     * Execute provided task as database transaction.
     * Transaction is handled automatically. Task computes and returns result.
     *
     * @param type transaction type
     * @param task task to run in transaction
     * @param <T>  the result type of the task
     * @return computed task result
     * @throws io.helidon.data.DataException when result computation failed
     */
    <T> T transaction(Tx.Type type, Callable<T> task);

    /**
     * Execute provided task as database transaction.
     * Transaction is handled automatically. Task does not return any result.
     *
     * @param type transaction type
     * @param task task to run in transaction
     * @param <E> type of thrown (checked) exception
     * @throws io.helidon.data.DataException when task computation failed
     */
    <E extends Throwable> void transaction(Tx.Type type, Functions.CheckedRunnable<E> task);

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
     * @param type transaction type
     * @param task task to run in transaction
     */
    void transaction(Tx.Type type, Consumer<Tx.Transaction> task);

    /**
     * Start transaction.
     * Transaction is finished manually.
     *
     * @param type transaction type
     * @return transaction handler
     */
    Tx.Transaction transaction(Tx.Type type);

    /**
     * Data config that was used to create this instance.
     *
     * @return data config
     */
    DataConfig dataConfig();
}
