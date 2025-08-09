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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

import io.helidon.common.Functions;
import io.helidon.service.registry.Service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceUnitUtil;

/**
 * Jakarta Persistence specific repository tasks executor.
 */
@Service.Contract
public interface JpaRepositoryExecutor extends AutoCloseable {
    /**
     * {@link BigDecimal} factory method for {@link Number}.
     *
     * @param number source number
     * @return the {@link BigDecimal} instance
     */
    static BigDecimal createBigDecimal(Number number) {
        return switch (number) {
            // Integer values simple conversion
            case BigDecimal bd -> bd;
            case BigInteger bi -> new BigDecimal(bi);
            case Long l -> BigDecimal.valueOf(l);
            case Integer i -> BigDecimal.valueOf(i);
            case Short s -> BigDecimal.valueOf(s);
            case Byte b -> BigDecimal.valueOf(b);
            // Floating point conversion may lose precision
            case Float f -> BigDecimal.valueOf(f);
            case Double d -> BigDecimal.valueOf(d);
            // Fallback to safe but slow method
            default -> new BigDecimal(number.toString());
        };
    }

    /**
     * Transform query result as {@link java.util.Optional}.
     * This is Jakarta Persistence 3.1 workaround.
     *
     * @param queryResult the query result as {@link java.util.Optional}
     * @param <T>         type of the result
     * @return query result as {@link java.util.Optional}
     * @deprecated will be removed with Jakarta Persistence 3.2
     */
    @Deprecated
    static <T> Optional<T> optionalFromQuery(List<T> queryResult) {
        return queryResult == null || queryResult.isEmpty()
                ? Optional.empty() : Optional.of(queryResult.getFirst());
    }

    // NEXT VERSION: AutoCloseable was added to support native EclipseLink.
    @Override
    default void close() {
    }

    /**
     * Persistence session factory.
     *
     * @return the session factory instance
     */
    EntityManagerFactory factory();

    /**
     * Retrieve {@link PersistenceUnitUtil} from persistence session factory.
     *
     * @return the {@link PersistenceUnitUtil} instance
     */
    @SuppressWarnings("resource")
    default PersistenceUnitUtil persistenceUnitUtil() {
        return factory().getPersistenceUnitUtil();
    }

    /**
     * Run persistence session task.
     *
     * @param task task to run
     * @param <R>  task result type
     * @param <E>  type of (checked) exception that can be thrown
     * @return task result
     * @throws RuntimeException as is if unable to compute a result, usually {@code io.helidon.data.DataException}
     *                          with checked {@link Exception} as a cause if unable to compute a result
     */
    <R, E extends Throwable> R call(Functions.CheckedFunction<EntityManager, R, E> task);

    /**
     * Run persistence session task.
     *
     * @param task task to run
     * @param <E>  type of (checked) exception that can be thrown
     * @throws RuntimeException as is if unable to compute a result, usually {@code io.helidon.data.DataException}
     *                          with checked {@link Exception} as a cause if unable to compute a result
     */
    <E extends Throwable> void run(Functions.CheckedConsumer<EntityManager, E> task);

    /**
     * Persistence tasks executor.
     * Instances are stored in static {@link java.util.Map} so executor context is passed as an argument.
     */
    interface DataRunner {
        /**
         * Call persistence session task and return result.
         *
         * @param executor executor context
         * @param task task to call
         * @param <R>  task result type
         * @param <E>  type of (checked) exception that can be thrown
         * @return task result
         */
        <R, E extends Throwable> R call(JpaRepositoryExecutor executor,
                                        Functions.CheckedFunction<EntityManager, R, E> task);

        /**
         * Run persistence session task with no result.
         * @param executor executor context
         * @param task task to run
         * @param <E>  type of (checked) exception that can be thrown
         */
        <E extends Throwable> void run(JpaRepositoryExecutor executor,
                                       Functions.CheckedConsumer<EntityManager, E> task);
    }
}
