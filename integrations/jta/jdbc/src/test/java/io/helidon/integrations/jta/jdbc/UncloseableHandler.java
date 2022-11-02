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
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class UncloseableHandler<D> extends ConditionalInvocationHandler<D> {

    private final java.util.function.Predicate<? super D> isClosedPredicate;

    private final Consumer<? super D> closedNotifier;

    private volatile boolean closed;

    UncloseableHandler(D delegate,
                       java.util.function.Predicate<? super D> isClosedPredicate,
                       Consumer<? super D> closedNotifier,
                       BiConsumer<? super D, ? super Throwable> errorNotifier) {
        this(() -> delegate, isClosedPredicate, closedNotifier, errorNotifier);
    }
    
    UncloseableHandler(Supplier<? extends D> delegateSupplier,
                       java.util.function.Predicate<? super D> isClosedPredicate,
                       Consumer<? super D> closedNotifier,
                       BiConsumer<? super D, ? super Throwable> errorNotifier) {
        super(delegateSupplier, UncloseableHandler::closeOrIsClosed, errorNotifier);
        this.closedNotifier = closedNotifier == null ? UncloseableHandler::sink : closedNotifier;
        this.isClosedPredicate = Objects.requireNonNull(isClosedPredicate, "isClosedPredicate");
    }


    /*
     * Instance methods.
     */


    @Override
    protected Object invoke(Object proxy, D delegate, Method method, Object[] arguments) throws Throwable {
        switch (method.getName()) {
        case "isClosed":
            return Boolean.valueOf(this.isClosed(delegate));
        case "close":
            if (!this.isClosed(delegate)) {
                this.closed = true;
                this.closedNotifier.accept(delegate);
            }
            return null;
        default:
            return super.invoke(proxy, delegate, method, arguments);
        }
    }

    private boolean isClosed(D delegate) {
        return this.closed || this.isClosedPredicate.test(delegate);
    }


    /*
     * Static methods.
     */


    private static boolean closeOrIsClosed(Object proxy, Object delegate, Method method, Object arguments) {
        if (method.getParameterCount() == 0) {
            switch (method.getName()) {
            case "isClosed":
                return method.getReturnType() == boolean.class;
            case "close":
                return method.getReturnType() == void.class;
            }
        }
        return false;
    }

    private static boolean returnFalse(Object ignored) {
        return false;
    }

    private static void sink(Object ignored) {}

}
