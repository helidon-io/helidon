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

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Predicate;

import io.helidon.common.Weight;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.inject.api.ClassNamed;
import io.helidon.inject.api.InjectionServices;
import io.helidon.inject.api.Interceptor;

import jakarta.inject.Singleton;

@ClassNamed(FaultTolerance.Async.class)
@Weight(FaultTolerance.WEIGHT_ASYNC)
@Singleton
class AsyncInterceptor extends InterceptorBase<Async> implements Interceptor {
    AsyncInterceptor() {
        super(InjectionServices.realizedServices(), Async.class, FaultTolerance.Async.class);
    }

    @Override
    <V> V invokeHandler(Async ftHandler, Chain<V> chain, Object[] args) {
        try {
            return doInvoke(ftHandler, chain, args);
        } catch (RuntimeException e) {
            // we want to re-throw runtime exceptions
            throw e;
        } catch (Error e) {
            throw e;
        } catch (Throwable e) {
            throw new SupplierException("Failed to invoke asynchronous supplier", e.getCause());
        }
    }

    private <V> V doInvoke(Async ftHandler, Chain<V> chain, Object[] args) throws Throwable {
        try {
            return ftHandler.invoke(() -> chain.proceed(args))
                    .get();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            cause.addSuppressed(e);
            throw cause;
        } catch (InterruptedException e) {
            throw new SupplierException("Failed to invoke asynchronous supplier, interrupted", e);
        }
    }

    @Override
    Async obtainHandler(TypedElementInfo elementInfo, CacheRecord cacheRecord) {
        return namedHandler(elementInfo, this::fromAnnotation);
    }

    private Async fromAnnotation(Annotation annotation) {
        String name = annotation.getValue("name").orElse("async-") + System.identityHashCode(annotation);
        ExecutorService executorService = annotation.getValue("executorName")
                .filter(Predicate.not(String::isBlank))
                .flatMap(it -> lookupNamed(ExecutorService.class, it))
                .orElseGet(() -> FaultTolerance.executor().get());

        return Async.create(AsyncConfig.builder()
                                                                   .name(name)
                                                                   .executor(executorService)
                                                                   .buildPrototype());
    }
}
