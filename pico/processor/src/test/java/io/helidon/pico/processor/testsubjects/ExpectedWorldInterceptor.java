/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.pico.processor.testsubjects;

import java.util.Objects;
import java.util.Optional;

import io.helidon.common.Weight;
import io.helidon.pico.spi.ext.TypeInterceptor;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

@Singleton
@Weight(100.001)
public class ExpectedWorldInterceptor<T extends io.helidon.pico.testsubjects.hello.World> implements io.helidon.pico.testsubjects.hello.World,
                                                                                                     TypeInterceptor<T> {

    private final Provider<T> delegate;
    private final TypeInterceptor<T> interceptor;

    @Inject
    ExpectedWorldInterceptor(Provider<T> delegate, @Named("World") Optional<TypeInterceptor<T>> interceptor) {
        this.delegate = delegate;
        this.interceptor = interceptor.isPresent() ? interceptor.get().interceptorFor(delegate) : null;
    }

    @Override
    public TypeInterceptor<T> interceptorFor(Provider<T> delegate) {
        return interceptor;
    }

    @Override
    public Provider<T> providerFor(Provider<T> delegate, String methodName, Object... methodArgs) {
        return delegate;
    }

    // --- begin intercepted methods of world interface

    @Override
    public String getName() {
        if (Objects.isNull(interceptor)) {
            return delegate.get().getName();
        } else {
            Provider<T> delegate = interceptor.providerFor(this.delegate, "getName");
            interceptor.beforeCall(delegate, "getName");
            Throwable t = null;
            String result = null;
            try {
                result = delegate.get().getName();
            } catch (Throwable t1) {
                t = t1;
            } finally {
                if (Objects.isNull(t)) {
                    interceptor.afterCall(delegate, result, "getName");
                    return result;
                } else {
                    RuntimeException re = interceptor.afterFailedCall(t, delegate, "getName");
                    throw re;
                }
            }
        }
    }

    // --- end intercepted methods of world interface

}
