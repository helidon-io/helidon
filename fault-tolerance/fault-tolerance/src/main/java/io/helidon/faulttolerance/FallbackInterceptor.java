/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.faulttolerance;

import java.util.List;

import io.helidon.common.GenericType;
import io.helidon.common.Weight;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.inject.api.ClassNamed;
import io.helidon.inject.api.InjectionException;
import io.helidon.inject.api.InjectionServices;
import io.helidon.inject.api.InvocationContext;

import jakarta.inject.Singleton;

@SuppressWarnings({"unchecked", "deprecation", "rawtypes"})
@ClassNamed(FaultTolerance.Fallback.class)
@Weight(FaultTolerance.WEIGHT_FALLBACK)
@Singleton
class FallbackInterceptor extends InterceptorBase<Fallback> {
    FallbackInterceptor() {
        super(InjectionServices.realizedServices(), Fallback.class, FaultTolerance.Fallback.class);
    }

    @Override
    public <V> V proceed(InvocationContext ctx, Chain<V> chain, Object... args) {

        try {
            return chain.proceed(args);
        } catch (Throwable t) {
            // these are our cache keys
            // class name of the service
            TypeName typeName = ctx.serviceTypeName();
            // method this was declared on (as Fallback can only be defined on a method)
            String methodName = ctx.elementInfo().elementName();
            List<TypedElementInfo> params = ctx.elementArgInfo();

            CacheRecord cacheRecord = new CacheRecord(typeName, methodName, params);
            FallbackMethod<V, Object> fallbackMethod = generatedMethod(FallbackMethod.class, cacheRecord)
                    .orElseGet(() -> new FailingFallbackMethod(cacheRecord));

            try {
                return fallbackMethod.fallback(ctx.serviceProvider().get(), t, args);
            } catch (RuntimeException e) {
                e.addSuppressed(t);
                throw e;
            } catch (Throwable x) {
                x.addSuppressed(t);
                throw new SupplierException("Failed to invoke fallback method: " + cacheRecord, x);
            }
        }
    }

    private static class FailingFallbackMethod implements FallbackMethod {
        private final CacheRecord cacheRecord;

        private FailingFallbackMethod(CacheRecord cacheRecord) {
            this.cacheRecord = cacheRecord;
        }

        @Override
        public Object fallback(Object service, Throwable throwable, Object... arguments) {
            throw new InjectionException("Could not find a service that implements fallback method for: "
                                            + cacheRecord);
        }

        @Override
        public List<GenericType<?>> parameterTypes() {
            return List.of();
        }
    }
}
