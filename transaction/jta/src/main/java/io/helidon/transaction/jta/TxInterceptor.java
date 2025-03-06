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
package io.helidon.transaction.jta;

import java.util.Optional;

import io.helidon.service.registry.Interception;
import io.helidon.service.registry.InterceptionContext;
import io.helidon.service.registry.Service;
import io.helidon.transaction.Tx;

class TxInterceptor {

    private static final System.Logger LOGGER = System.getLogger(TxInterceptor.class.getName());

    static abstract class AbstractInterceptor implements Interception.Interceptor {

        private final JtaProviderSupplier supplier;

        AbstractInterceptor(JtaProviderSupplier supplier) {
            this.supplier = supplier;
        }

        <V> V proceed(Tx.Type type, InterceptionContext interceptionContext, Chain<V> chain, Object... args) throws Exception {
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                LOGGER.log(System.Logger.Level.DEBUG,
                           String.format("Starting %s transaction in annotated method in thread [%x]",
                                         type.name(),
                                         Thread.currentThread().hashCode()));
            }
            if (supplier.exists()) {

            }
            V result = chain.proceed(args);
            if (supplier.exists()) {

            }
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                LOGGER.log(System.Logger.Level.DEBUG,
                           String.format("Finishing %s transaction in annotated method in thread [%x]",
                                         type.name(),
                                         Thread.currentThread().hashCode()));
            }
            return result;
        }

        Optional<JtaProvider> provider() {
            return supplier.get();
        }

    }

    @Service.Singleton
    @Service.NamedByType(Tx.Mandatory.class)
    static final class Mandatory extends AbstractInterceptor {

        @Service.Inject
        Mandatory(JtaProviderSupplier supplier) {
            super(supplier);
        }

        @Override
        public <V> V proceed(InterceptionContext interceptionContext, Chain<V> chain, Object... args) throws Exception {
            return proceed(Tx.Type.MANDATORY, interceptionContext, chain, args);
        }

    }

    @Service.Singleton
    @Service.NamedByType(Tx.New.class)
    static final class New extends AbstractInterceptor {

        @Service.Inject
        New(JtaProviderSupplier supplier) {
            super(supplier);
        }

        @Override
        public <V> V proceed(InterceptionContext interceptionContext, Chain<V> chain, Object... args) throws Exception {
            return proceed(Tx.Type.NEW, interceptionContext, chain, args);
        }

    }

    @Service.Singleton
    @Service.NamedByType(Tx.Never.class)
    static final class Never extends AbstractInterceptor {

        @Service.Inject
        Never(JtaProviderSupplier supplier) {
            super(supplier);
        }

        @Override
        public <V> V proceed(InterceptionContext interceptionContext, Chain<V> chain, Object... args) throws Exception {
            return proceed(Tx.Type.NEVER, interceptionContext, chain, args);
        }

    }

    @Service.Singleton
    @Service.NamedByType(Tx.Required.class)
    static final class Required extends AbstractInterceptor {

        @Service.Inject
        Required(JtaProviderSupplier supplier) {
            super(supplier);
        }

        @Override
        public <V> V proceed(InterceptionContext interceptionContext, Chain<V> chain, Object... args) throws Exception {
            return proceed(Tx.Type.REQUIRED, interceptionContext, chain, args);
        }

    }

    @Service.Singleton
    @Service.NamedByType(Tx.Supported.class)
    static final class Supported extends AbstractInterceptor {

        @Service.Inject
        Supported(JtaProviderSupplier supplier) {
            super(supplier);
        }

        @Override
        public <V> V proceed(InterceptionContext interceptionContext, Chain<V> chain, Object... args) throws Exception {
            return proceed(Tx.Type.SUPPORTED, interceptionContext, chain, args);
        }

    }

    @Service.Singleton
    @Service.NamedByType(Tx.Unsupported.class)
    static final class Unsupported extends AbstractInterceptor {

        @Service.Inject
        Unsupported(JtaProviderSupplier supplier) {
            super(supplier);
        }

        @Override
        public <V> V proceed(InterceptionContext interceptionContext, Chain<V> chain, Object... args) throws Exception {
            return proceed(Tx.Type.UNSUPPORTED, interceptionContext, chain, args);
        }

    }

}
