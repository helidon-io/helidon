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

import io.helidon.service.registry.Interception;
import io.helidon.service.registry.InterceptionContext;
import io.helidon.service.registry.Service;
import io.helidon.transaction.spi.TxSupport;

class TxInterceptor {

    abstract static class AbstractInterceptor implements Interception.Interceptor {

        private final TxSupport txSupport;

        AbstractInterceptor(TxSupport txSupport) {
            this.txSupport = txSupport;
        }

        <T> T proceed(Tx.Type type, Interception.Interceptor.Chain<T> chain, Object... args) {
            return txSupport.transaction(type, () -> chain.proceed(args));
        }

    }

    @Service.Singleton
    @Service.NamedByType(Tx.Mandatory.class)
    static final class Mandatory extends AbstractInterceptor {

        Mandatory(TxSupport txSupport) {
            super(txSupport);
        }

        /**
         * Transaction handler for {@link Tx.Mandatory}.
         * <p>
         * If called outside a transaction context, the {@link TxException} must be thrown.
         * If called inside a transaction context, method execution will then continue under that context.
         *
         * @param ctx  the invocation context
         * @param chain the chain to call proceed on
         * @param args annotated method arguments
         * @param <T>  the result type of the task
         * @return computed annotated method result
         */
        public <T> T proceed(InterceptionContext ctx, Interception.Interceptor.Chain<T> chain, Object... args) {
            return proceed(Tx.Type.MANDATORY, chain, args);
        }

    }

    @Service.Singleton
    @Service.NamedByType(Tx.New.class)
    static final class New extends AbstractInterceptor {

        New(TxSupport txSupport) {
            super(txSupport);
        }

        /**
         * Transaction handler for {@link Tx.New}.
         * <p>
         * If called outside a transaction context, the interceptor must begin a new transaction. The managed bean
         * method execution must then continue inside this transaction context, and the transaction must be completed
         * by the interceptor.
         * If called inside a transaction context, the current transaction context must be suspended. New transaction
         * will begin, the managed method execution must then continue inside this transaction context. The transaction
         * must be completed, and the previously suspended transaction must be resumed.
         *
         * @param ctx  the invocation context
         * @param chain the chain to call proceed on
         * @param args annotated method arguments
         * @param <T>  the result type of the task
         * @return computed annotated method result
         */
        public <T> T proceed(InterceptionContext ctx, Interception.Interceptor.Chain<T> chain, Object... args) {
            return proceed(Tx.Type.NEW, chain, args);
        }

    }

    @Service.Singleton
    @Service.NamedByType(Tx.Never.class)
    static final class Never extends AbstractInterceptor {

        Never(TxSupport txSupport) {
            super(txSupport);
        }

        /**
         * Transaction handler for {@link Tx.Never}.
         * <p>
         * If called outside a transaction context, method execution must then continue outside a transaction context.
         * If called inside a transaction context, the {@link TxException} must be thrown.
         *
         * @param ctx  the invocation context
         * @param chain the chain to call proceed on
         * @param args annotated method arguments
         * @param <T>  the result type of the task
         * @return computed annotated method result
         */
        public <T> T proceed(InterceptionContext ctx, Interception.Interceptor.Chain<T> chain, Object... args) {
            return proceed(Tx.Type.NEVER, chain, args);
        }

    }

    @Service.Singleton
    @Service.NamedByType(Tx.Required.class)
    static final class Required extends AbstractInterceptor {

        Required(TxSupport txSupport) {
            super(txSupport);
        }

        /**
         * Transaction handler for {@link Tx.Required}.
         * <p>
         * If called outside a transaction context, the interceptor must begin a new transaction. The managed bean
         * method execution must then continue inside this transaction context, and the transaction must be completed
         * by the interceptor.
         * If called inside a transaction context, the method execution must then continue inside this transaction context.
         *
         * @param ctx  the invocation context
         * @param chain the chain to call proceed on
         * @param args annotated method arguments
         * @param <T>  the result type of the task
         * @return computed annotated method result
         */
        public <T> T proceed(InterceptionContext ctx, Interception.Interceptor.Chain<T> chain, Object... args) {
            return proceed(Tx.Type.REQUIRED, chain, args);
        }

    }

    @Service.Singleton
    @Service.NamedByType(Tx.Supported.class)
    static final class Supported extends AbstractInterceptor {

        Supported(TxSupport txSupport) {
            super(txSupport);
        }

        /**
         * Transaction handler for {@link Tx.Supported}.
         * <p>
         * If called outside a transaction context, method execution must then continue outside a transaction context.
         * If called inside a transaction context, the method execution must then continue inside this transaction context.
         *
         * @param ctx  the invocation context
         * @param chain the chain to call proceed on
         * @param args annotated method arguments
         * @param <T>  the result type of the task
         * @return computed annotated method result
         */
        public <T> T proceed(InterceptionContext ctx, Interception.Interceptor.Chain<T> chain, Object... args) {
            return proceed(Tx.Type.SUPPORTED, chain, args);
        }

    }

    @Service.Singleton
    @Service.NamedByType(Tx.Unsupported.class)
    static final class Unsupported extends AbstractInterceptor {

        Unsupported(TxSupport txSupport) {
            super(txSupport);
        }

        /**
         * Transaction handler for {@link Tx.Unsupported}.
         * <p>
         * If called outside a transaction context, method execution must then continue outside a transaction context.
         * If called inside a transaction context, the current transaction context must be suspended. The method execution
         * must then continue outside a transaction context, and the previously suspended transaction must be resumed
         * by the interceptor that suspended it after the method execution has completed.
         *
         * @param ctx  the invocation context
         * @param chain the chain to call proceed on
         * @param args annotated method arguments
         * @param <T>  the result type of the task
         * @return computed annotated method result
         */
        public <T> T proceed(InterceptionContext ctx, Interception.Interceptor.Chain<T> chain, Object... args) {
            return proceed(Tx.Type.UNSUPPORTED, chain, args);
        }

    }

}
