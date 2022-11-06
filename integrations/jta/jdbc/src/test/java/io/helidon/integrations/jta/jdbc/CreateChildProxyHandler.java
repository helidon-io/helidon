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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.lang.reflect.Proxy.newProxyInstance;

class CreateChildProxyHandler<D, C> extends ConditionalInvocationHandler<D> {

    private final BiFunction<? super D, ? super C, InvocationHandler> creator;

    private final Function<? super Method, ? extends Class<? extends C>> childTypeFunction;
    
    CreateChildProxyHandler(Supplier<? extends D> delegateSupplier,
                            Predicate<? super D> predicate,
                            Function<? super Method, ? extends Class<? extends C>> childTypeFunction,
                            ChildInvocationHandlerCreator<? super D, C> childInvocationHandlerCreator,
                            BiConsumer<? super C, ? super Throwable> errorNotifier) {
        super(delegateSupplier, predicate, null);
        this.childTypeFunction = Objects.requireNonNull(childTypeFunction, "childTypeFunction");
        Objects.requireNonNull(childInvocationHandlerCreator, "childInvocationHandlerCreator");
        this.creator = (p, c) -> childInvocationHandlerCreator.create(p, c, errorNotifier);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Object invoke(Object proxy, D delegate, Method method, Object[] arguments) throws Throwable {
        return
            newProxyInstance(Thread.currentThread().getContextClassLoader(),
                             new Class<?>[] { this.childTypeFunction.apply(method) },
                             this.creator.apply(delegate, (C) method.invoke(delegate, arguments)));
    }

    static interface ChildInvocationHandlerCreator<P, C> {

        InvocationHandler create(P parent, C child, BiConsumer<? super C, ? super Throwable> errorNotifier);
        
    }
  
}
