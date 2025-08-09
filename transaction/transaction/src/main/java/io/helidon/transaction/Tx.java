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
package io.helidon.transaction;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.Callable;

import io.helidon.common.Functions;
import io.helidon.service.registry.Interception;
import io.helidon.service.registry.Services;
import io.helidon.transaction.spi.TxSupport;

/**
 * Annotations and types related to transactional method execution.
 */
public final class Tx {

    private Tx() {
        throw new UnsupportedOperationException("No instances of Tx are allowed");
    }

    /**
     * Transaction type.
     * <p>
     * An {@code enum} describing the possible ways in which transactional support must be applied to transactional method executions.
     */
    public enum Type {
        /**
         * Indicates that a transaction must already be in effect when a method executes.
         * If called outside a transaction context, the {@link TxException} must be thrown.
         * If called inside a transaction context, method execution will then continue under that context.
         */
        MANDATORY,
        /**
         * Indicates that a new transaction will be started when a method executes.
         * If called outside a transaction context, the interceptor must begin a new transaction. The managed bean
         * method execution must then continue inside this transaction context, and the transaction must be completed
         * by the interceptor.
         * If called inside a transaction context, the current transaction context must be suspended. New transaction
         * will begin, the managed method execution must then continue inside this transaction context. The transaction
         * must be completed, and the previously suspended transaction must be resumed.
         */
        NEW,
        /**
         * Indicates that no transaction must be in effect when a method executes.
         * If called outside a transaction context, method execution must then continue outside a transaction context.
         * If called inside a transaction context, the {@link TxException} must be thrown.
         */
        NEVER,
        /**
         * Indicates that transaction will be in effect when a method executes.
         * If called outside a transaction context, the interceptor must begin a new transaction. The managed bean
         * method execution must then continue inside this transaction context, and the transaction must be completed
         * by the interceptor.
         * If called inside a transaction context, the method execution must then continue inside this transaction context.
         */
        REQUIRED,
        /**
         * Indicates that transaction may optionally be in effect when a method executes.
         * If called outside a transaction context, method execution must then continue outside a transaction context.
         * If called inside a transaction context, the method execution must then continue inside this transaction context.
         */
        SUPPORTED,
        /**
         * Indicates that no transaction will be in effect when a method executes.
         * If called outside a transaction context, method execution must then continue outside a transaction context.
         * If called inside a transaction context, the current transaction context must be suspended. The method execution
         * must then continue outside a transaction context, and the previously suspended transaction must be resumed
         * by the interceptor that suspended it after the method execution has completed.
         */
        UNSUPPORTED
    }

    /**
     * Transaction {@link Type} annotation.
     * <p>
     * Defines transaction type of the annotated method. This annotation is applied on set of annotations used to mark
     * methods to be executed in managed transaction of selected {@link Type}.
     * <p>
     * Those method annotations are:<ul>
     *     <li>{@link Mandatory}</li>
     *     <li>{@link New}</li>
     *     <li>{@link Never}</li>
     *     <li>{@link Required}</li>
     *     <li>{@link Supported}</li>
     *     <li>{@link Unsupported}</li>
     * </ul>
     */
    @Target(ElementType.ANNOTATION_TYPE)
    public @interface TransactionType {
        /**
         * Transaction type of the annotated method.
         *
         * @return the transaction type
         */
        Type value();
    }

    /**
     * Annotated method will be executed with managed transaction of {@link Type#MANDATORY} type.
     */
    @Interception.Intercepted
    @TransactionType(Type.MANDATORY)
    @Retention(RetentionPolicy.SOURCE)
    @Target({ElementType.TYPE, ElementType.METHOD})
    public @interface Mandatory { }

    /**
     * Annotated method will be executed with managed transaction of {@link Type#NEW} type.
     */
    @Interception.Intercepted
    @TransactionType(Type.NEW)
    @Retention(RetentionPolicy.SOURCE)
    @Target({ElementType.TYPE, ElementType.METHOD})
    public @interface New { }

    /**
     * Annotated method will be executed with managed transaction of {@link Type#NEVER} type.
     */
    @Interception.Intercepted
    @TransactionType(Type.NEVER)
    @Retention(RetentionPolicy.SOURCE)
    @Target({ElementType.TYPE, ElementType.METHOD})
    public @interface Never { }

    /**
     * Annotated method will be executed with managed transaction of {@link Type#REQUIRED} type.
     */
    @Interception.Intercepted
    @TransactionType(Type.REQUIRED)
    @Retention(RetentionPolicy.SOURCE)
    @Target({ElementType.TYPE, ElementType.METHOD})
    public @interface Required { }

    /**
     * Annotated method will be executed with managed transaction of {@link Type#SUPPORTED} type.
     */
    @Interception.Intercepted
    @TransactionType(Type.SUPPORTED)
    @Retention(RetentionPolicy.SOURCE)
    @Target({ElementType.TYPE, ElementType.METHOD})
    public @interface Supported { }

    /**
     * Annotated method will be executed with managed transaction of {@link Type#UNSUPPORTED} type.
     */
    @Interception.Intercepted
    @TransactionType(Type.UNSUPPORTED)
    @Retention(RetentionPolicy.SOURCE)
    @Target({ElementType.TYPE, ElementType.METHOD})
    public @interface Unsupported { }

    /**
     * Execute provided {@code task} with managed transaction of {@link Type#REQUIRED} type.
     * Task computes and returns result according to {@link Callable} contract.
     *
     * @param task task to run in transaction, shall not be {@code null}
     * @param <T>  the result type of the task
     * @return computed task result
     * @throws TxException when result computation failed
     */
    public static <T> T transaction(Callable<T> task) {
        return transaction(Type.REQUIRED, task);
    }

    /**
     * Execute provided {@code task} with managed transaction of provided {@code type}.
     * Task computes and returns result according to {@link Callable} contract.
     *
     * @param type transaction type, shall not be {@code null}
     * @param task task to run in transaction, shall not be {@code null}
     * @param <T>  the result type of the task
     * @return computed task result
     * @throws TxException when result computation failed
     */
    public static <T> T transaction(Type type, Callable<T> task) {
        return Services.get(TxSupport.class)
                .transaction(type, task);
    }

    /**
     * Execute provided {@code task} with managed transaction of {@link Type#REQUIRED} type.
     * Task does not return any result according to {@link Functions.CheckedRunnable} contract.
     *
     * @param task task to run in transaction, shall not be {@code null}
     * @throws TxException when task computation failed
     */
    public static void transaction(Functions.CheckedRunnable<Exception> task) {
        transaction(Type.REQUIRED, task);
    }

    /**
     * Execute provided {@code task} with managed transaction of provided {@code type}.
     * Task does not return any result according to {@link Functions.CheckedRunnable} contract.
     *
     * @param type transaction type, shall not be {@code null}
     * @param task task to run in transaction, shall not be {@code null}
     * @throws TxException when task computation failed
     */
    public static void transaction(Type type, Functions.CheckedRunnable<Exception> task) {
        Services.get(TxSupport.class)
                .transaction(type,
                             () -> {
                                 task.run();
                                 return null;
                             });
    }

}
