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
package io.helidon.data.api;

import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;

import io.helidon.function.ThrowingRunnable;
import io.helidon.transaction.Tx;

public class TestHelidonData implements DataRegistry {

    private final DataConfig repositoryConfig;

    public TestHelidonData(DataConfig config) {
        repositoryConfig = config;
    }

    @Override
    public <T extends Data.GenericRepository<?, ?>> T repository(Class<? super T> repository) {
        return null;
    }

    @Override
    public <T> T transaction(Tx.Type type, Callable<T> task) {
        return null;
    }

    @Override
    public void transaction(Tx.Type type, ThrowingRunnable task) {
    }

    @Override
    public <T> T transaction(Tx.Type type, Function<Tx.Transaction, T> task) {
        return null;
    }

    @Override
    public void transaction(Tx.Type type, Consumer<Tx.Transaction> task) {
    }

    @Override
    public Tx.Transaction transaction(Tx.Type type) {
        return null;
    }

    @Override
    public DataConfig dataConfig() {
        return repositoryConfig;
    }

    @Override
    public void close() {
    }
}
