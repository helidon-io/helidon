/*
 * Copyright (c) 2020, 2025 Oracle and/or its affiliates.
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

import java.util.function.Function;
import java.util.function.Supplier;

class FallbackImpl<T> implements Fallback<T> {
    private final Function<Throwable, ? extends T> fallback;
    private final ErrorChecker errorChecker;
    @SuppressWarnings("rawtypes")
    private final Function<Supplier, Object> function;

    FallbackImpl(FallbackConfig<T> config) {
        this.fallback = config.fallback();
        this.errorChecker = ErrorChecker.create(config.skipOn(), config.applyOn());

        if (config.enabled()) {
            function = this::realInvoke;
        } else {
            function = Supplier::get;
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public T invoke(Supplier<? extends T> supplier) {
        // if disabled, just call the supplier, if enabled, call real invoke
        return (T) function.apply(supplier);
    }

    private T realInvoke(Supplier<? extends T> supplier) {
        try {
            return supplier.get();
        } catch (Throwable t) {
            Throwable throwable = SupplierHelper.unwrapThrowable(t);
            if (errorChecker.shouldSkip(throwable)) {
                throw SupplierHelper.toRuntimeException(throwable);
            }
            try {
                return fallback.apply(throwable);
            } catch (Throwable t2) {
                Throwable throwable2 = SupplierHelper.unwrapThrowable(t2);
                if (throwable2 != throwable) {      // cannot self suppress
                    throwable2.addSuppressed(throwable);
                }
                throw SupplierHelper.toRuntimeException(throwable2);
            }
        }
    }
}
