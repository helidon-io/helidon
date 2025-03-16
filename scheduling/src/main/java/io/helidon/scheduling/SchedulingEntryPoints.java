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

package io.helidon.scheduling;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.service.registry.EntryPointInterceptor;
import io.helidon.service.registry.Interception;
import io.helidon.service.registry.InterceptionContext;
import io.helidon.service.registry.Service;

@Service.Singleton
class SchedulingEntryPoints {
    private final List<EntryPointInterceptor> entryPointInterceptors;

    @Service.Inject
    SchedulingEntryPoints(List<EntryPointInterceptor> entryPointInterceptors) {
        this.entryPointInterceptors = entryPointInterceptors;
    }

    <V> V proceed(InterceptionContext ctx, Interception.Interceptor.Chain<V> chain, Object[] args) throws Exception {
        return new ChainImpl<V>(ctx, entryPointInterceptors, new TargetInterceptor<>(chain))
                .proceed(args);
    }

    private static class ChainImpl<V> implements Interception.Interceptor.Chain<V> {
        private final InterceptionContext ctx;
        private final List<EntryPointInterceptor> interceptors;
        private final EntryPointInterceptor last;

        private int interceptorPos;

        private ChainImpl(InterceptionContext ctx, List<EntryPointInterceptor> interceptors, EntryPointInterceptor last) {
            this.ctx = ctx;
            this.interceptors = interceptors;
            this.last = last;
        }

        @Override
        public V proceed(Object[] args) throws Exception {
            if (interceptorPos < interceptors.size()) {
                var interceptor = interceptors.get(interceptorPos);
                interceptorPos++;
                try {
                    return interceptor.proceed(ctx, this, args);
                } catch (Exception e) {
                    interceptorPos--;
                    throw e;
                }
            }
            return last.proceed(ctx, this, args);
        }

        @Override
        public String toString() {
            return String.valueOf(ctx.elementInfo());
        }

        V initialProceed() {
            AtomicReference<V> result = new AtomicReference<>();

            return result.get();
        }
    }

    private static class TargetInterceptor<V> implements EntryPointInterceptor {
        private final Interception.Interceptor.Chain<V> chain;

        private TargetInterceptor(Interception.Interceptor.Chain<V> chain) {
            this.chain = chain;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T proceed(InterceptionContext invocationContext, Interception.Interceptor.Chain<T> chain, Object... args)
                throws Exception {
            return (T) this.chain.proceed(args);
        }
    }
}
