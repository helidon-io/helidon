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

import io.helidon.service.registry.Interception;

/**
 * Transaction annotations and types.
 */
public final class Tx {

    private Tx() {
        throw new UnsupportedOperationException("No instances of Tx are allowed");
    }

    /**
     * Transaction type.
     * Indicates whether method is to be executed within a transaction context where the values provide the following
     * corresponding behavior.
     */
    public enum Type {
        /**
         * If called outside a transaction context, the {@link TxException} must be thrown.
         * If called inside a transaction context, method execution will then continue under that context.
         */
        MANDATORY,
        /**
         * If called outside a transaction context, the interceptor must begin a new transaction. The managed bean
         * method execution must then continue inside this transaction context, and the transaction must be completed
         * by the interceptor.
         * If called inside a transaction context, the current transaction context must be suspended. New transaction
         * will begin, the managed method execution must then continue inside this transaction context. The transaction
         * must be completed, and the previously suspended transaction must be resumed.
         */
        NEW,
        /**
         * If called outside a transaction context, method execution must then continue outside a transaction context.
         * If called inside a transaction context, the {@link TxException} must be thrown.
         */
        NEVER,
        /**
         * If called outside a transaction context, the interceptor must begin a new transaction. The managed bean
         * method execution must then continue inside this transaction context, and the transaction must be completed
         * by the interceptor.
         * If called inside a transaction context, the method execution must then continue inside this transaction context.
         */
        REQUIRED,
        /**
         * If called outside a transaction context, method execution must then continue outside a transaction context.
         * If called inside a transaction context, the method execution must then continue inside this transaction context.
         */
        SUPPORTED,
        /**
         * If called outside a transaction context, method execution must then continue outside a transaction context.
         * If called inside a transaction context, the current transaction context must be suspended. The method execution
         * must then continue outside a transaction context, and the previously suspended transaction must be resumed
         * by the interceptor that suspended it after the method execution has completed.
         */
        UNSUPPORTED
    }

    /**
     * {@link Type} annotation.
     * Defines transaction type of the annotated method.
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
     * Defines {@link Type#MANDATORY} transaction type of the annotated method.
     */
    @Interception.Intercepted
    @TransactionType(Type.MANDATORY)
    @Retention(RetentionPolicy.SOURCE)
    @Target({ElementType.TYPE, ElementType.METHOD})
    public @interface Mandatory { }

    /**
     * Defines {@link Type#NEW} transaction type of the annotated method.
     */
    @Interception.Intercepted
    @TransactionType(Type.NEW)
    @Retention(RetentionPolicy.SOURCE)
    @Target({ElementType.TYPE, ElementType.METHOD})
    public @interface New { }

    /**
     * Defines {@link Type#NEVER} transaction type of the annotated method.
     */
    @Interception.Intercepted
    @TransactionType(Type.NEVER)
    @Retention(RetentionPolicy.SOURCE)
    @Target({ElementType.TYPE, ElementType.METHOD})
    public @interface Never { }

    /**
     * Defines {@link Type#REQUIRED} transaction type of the annotated method.
     */
    @Interception.Intercepted
    @TransactionType(Type.REQUIRED)
    @Retention(RetentionPolicy.SOURCE)
    @Target({ElementType.TYPE, ElementType.METHOD})
    public @interface Required { }

    /**
     * Defines {@link Type#SUPPORTED} transaction type of the annotated method.
     */
    @Interception.Intercepted
    @TransactionType(Type.SUPPORTED)
    @Retention(RetentionPolicy.SOURCE)
    @Target({ElementType.TYPE, ElementType.METHOD})
    public @interface Supported { }

    /**
     * Defines {@link Type#UNSUPPORTED} transaction type of the annotated method.
     */
    @Interception.Intercepted
    @TransactionType(Type.UNSUPPORTED)
    @Retention(RetentionPolicy.SOURCE)
    @Target({ElementType.TYPE, ElementType.METHOD})
    public @interface Unsupported { }

    /**
     * The {@link Transaction} interface defines methods that allow user code to manage transaction boundaries.
     */
    public interface Transaction {

        /**
         * Complete current transaction.
         */
        void commit();

        /**
         * Roll back current transaction.
         */
        void rollback();

    }

}
