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
package io.helidon.integrations.jta.jdbc;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

final class ReturnProxiedCreatorHandler<PC, D> extends ConditionalInvocationHandler<D> {


    /*
     * Instance fields.
     */


    private final PC proxiedCreator;


    /*
     * Constructors.
     */


    ReturnProxiedCreatorHandler(PC proxiedCreator,
                                D delegate,
                                Set<? extends String> methodNames,
                                BiConsumer<? super D, ? super Throwable> errorNotifier) {
        this(proxiedCreator, () -> delegate, methodNames, errorNotifier);
    }

    ReturnProxiedCreatorHandler(PC proxiedCreator,
                                Supplier<? extends D> delegateSupplier,
                                Set<? extends String> methodNames,
                                BiConsumer<? super D, ? super Throwable> errorNotifier) {
        super(delegateSupplier,
              proxiedCreator == null ? Predicate.FALSE : predicate(methodNames, proxiedCreator),
              errorNotifier);
        this.proxiedCreator = proxiedCreator; // nullable on purpose
    }


    /*
     * Instance methods.
     */


    @Override
    protected Object invoke(Object proxy, D delegate, Method method, Object[] args) {
        return this.proxiedCreator;
    }


    /*
     * Static methods.
     */


    private static <PC, D> Predicate<? super D> predicate(Set<? extends String> methodNames, PC proxiedCreator) {
        Objects.requireNonNull(proxiedCreator, "proxiedCreator");
        if (methodNames.isEmpty()) {
            return Predicate.FALSE;
        }
        final Set<String> names = Set.copyOf(methodNames);        
        return (p, d, m, a) -> m.getParameterCount() == 0
            && m.getReturnType().isInstance(proxiedCreator)
            && names.contains(m.getName());
    }

}
