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
package io.helidon.transaction.spi;

import java.util.concurrent.Callable;

import io.helidon.transaction.Tx;

/**
 * Implemented by each transaction handling support.
 * <p>
 * It is Helidon Service interface with {@code io.helidon.common.Weight} specified.
 * Default {@code JTA} implementation module with {@code io.helidon.common.Weighted.DEFAULT_WEIGHT}
 * weight and {@code "jta"} {@link #type()} is available as part of Helidon Transaction modules set.
 * Helidon Data Jakarta Persistence module provides its own {@code RESOURCE-LOCAL} implementation
 * with {@code "resource-local"} {@link #type()} as a fallback option when JTA is not present.
 */
public interface TxSupport {

    /**
     * Type of the transaction API support, e.g. {@code "jta"}, {@code "resource-local"}.
     * Never returns {@code null}.
     *
     * @return the transaction API support
     */
    String type();

    /**
     * Execute provided task as database transaction.
     * Transaction is handled automatically. Task computes and returns result.
     *
     * @param type transaction type, shall not be {@code null}
     * @param task task to run in transaction, shall not be {@code null}
     * @param <T>  the result type of the task
     * @return computed task result
     * @throws io.helidon.transaction.TxException when result computation failed
     */
    <T> T transaction(Tx.Type type, Callable<T> task);

}
