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
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

class CompositeInvocationHandler<D> extends ConditionalInvocationHandler<D> {


    /*
     * Instance fields.
     */


    private final List<? extends ConditionalInvocationHandler<D>> list;


    /*
     * Constructors.
     */


    CompositeInvocationHandler(Supplier<? extends D> delegateSupplier,
                               List<? extends ConditionalInvocationHandler<D>> list) {
        this(delegateSupplier, Predicate.TRUE, list, CompositeInvocationHandler::sink);
    }

    CompositeInvocationHandler(Supplier<? extends D> delegateSupplier,
                               List<? extends ConditionalInvocationHandler<D>> list,
                               BiConsumer<? super D, ? super Throwable> errorNotifier) {
        this(delegateSupplier, Predicate.TRUE, list, errorNotifier);
    }
    
    CompositeInvocationHandler(List<? extends ConditionalInvocationHandler<D>> list) {
        this(() -> null, Predicate.FALSE, list, CompositeInvocationHandler::sink);
    }

    CompositeInvocationHandler(Supplier<? extends D> delegateSupplier,
                               Predicate<? super D> predicate,
                               List<? extends ConditionalInvocationHandler<D>> list,
                               BiConsumer<? super D, ? super Throwable> errorNotifier) {
        super(delegateSupplier, predicate, errorNotifier);
        this.list = list == null ? List.of() : List.copyOf(list);
    }


    /*
     * Instance methods.
     */


    @Override
    protected Object invoke(Object proxy, D delegate, Method method, Object[] arguments) throws Throwable {
        ConditionalInvocationHandler<D> ih = this.select(proxy, method, arguments);
        if (ih == null) {
            throw new UnsupportedOperationException(method.toString());
        } else if (ih == this) {
            return method.invoke(delegate, arguments);
        } else {
            return ih.invoke(proxy, ih.delegate(), method, arguments);
        }
    }

    final ConditionalInvocationHandler<D> select(Object proxy, Method method, Object[] arguments) {
        for (ConditionalInvocationHandler<D> ih : this.list) {
            if (ih.handles(proxy, method, arguments)) {
                return ih;
            }
        }
        if (this.handles(proxy, method, arguments)) {
            return this;
        }
        return null;
    }

    private static void sink(Object delegate, Object throwable) {}

}
