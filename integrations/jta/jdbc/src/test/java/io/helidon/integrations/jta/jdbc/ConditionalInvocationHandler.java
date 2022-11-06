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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

class ConditionalInvocationHandler<D> implements InvocationHandler {


    /*
     * Instance fields.
     */


    private final Supplier<? extends D> delegateSupplier;

    private final Predicate<? super D> predicate;

    private final BiConsumer<? super D, ? super Throwable> errorNotifier;


    /*
     * Constructors.
     */


    ConditionalInvocationHandler(D delegate,
                                 Predicate<? super D> predicate,
                                 BiConsumer<? super D, ? super Throwable> errorNotifier) {
        this(() -> delegate, predicate, errorNotifier);
    }
  
    ConditionalInvocationHandler(Supplier<? extends D> delegateSupplier,
                                 Predicate<? super D> predicate,
                                 BiConsumer<? super D, ? super Throwable> errorNotifier) {
        super();
        this.delegateSupplier = Objects.requireNonNull(delegateSupplier, "delegateSupplier");
        this.predicate = predicate == null ? Predicate.TRUE : predicate;
        if (errorNotifier == null) {
            this.errorNotifier = ConditionalInvocationHandler::sink;
        } else {
            this.errorNotifier = errorNotifier;
        }
    }


    /*
     * Instance methods.
     */


    final D delegate() {
        return this.delegateSupplier.get();
    }
    
    final boolean handles(Object proxy, Method method, Object[] arguments) {
        return this.predicate.test(proxy, this.delegateSupplier, method, arguments);
    }

    @Override
    public final Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable {
        if (this.handles(proxy, method, arguments)) {
            D delegate = this.delegate();
            try {
                return this.invoke(proxy, delegate, method, arguments);
            } catch (Throwable t) {
                if (t instanceof InvocationTargetException) {
                    t = t.getCause();
                }
                this.errorNotifier.accept(delegate, t);
                throw t;
            }
        }
        throw new UnsupportedOperationException(method.toString());
    }

    protected Object invoke(Object proxy, D delegate, Method method, Object[] arguments) throws Throwable {
        return method.invoke(delegate, arguments);
    }


    /*
     * Static methods.
     */


    private static boolean returnFalse(Object proxy, Object delegate, Object method, Object arguments) {
        return false;
    }


    private static boolean returnTrue(Object proxy, Object delegate, Object method, Object arguments) {
        return true;
    }

    private static void sink(Object delegate, Object throwable) {}


    /*
     * Inner and nested classes.
     */


    @FunctionalInterface
    static interface Predicate<D> {

        public static final Predicate<Object> FALSE = ConditionalInvocationHandler::returnFalse;

        public static final Predicate<Object> TRUE = ConditionalInvocationHandler::returnTrue;

        public boolean test(Object proxy, Supplier<? extends D> delegateSupplier, Method method, Object[] arguments);

    }

    static class ObjectMethods<D> extends ConditionalInvocationHandler<D> {


        /*
         * Constructors.
         */


        public ObjectMethods(D delegate) {
          this(() -> delegate, ObjectMethods::sink);
        }
      
        public ObjectMethods(Supplier<? extends D> delegate) {
          this(delegate, ObjectMethods::sink);
        }
      
        public ObjectMethods(Supplier<? extends D> delegate, BiConsumer<? super D, ? super Throwable> errorNotifier) {
            super(delegate, ObjectMethods::test, errorNotifier);
        }


        /*
         * Instance methods.
         */


        @Override
        protected final Object invoke(Object proxy, D delegate, Method method, Object[] arguments) throws Throwable {
            String methodName = method.getName();
            if (methodName.equals("equals")) {
                return Boolean.valueOf(proxy == arguments[0]);
            } else if (methodName.equals("hashCode")) {
                return Integer.valueOf(System.identityHashCode(proxy));
            } else if (methodName.equals("toString")) {
                return this.toString(proxy, delegate);
            } else {
                return super.invoke(proxy, delegate, method, arguments);
            }
        }

        protected String toString(Object proxy, D delegate) {
            return String.valueOf(delegate);
        }


        /*
         * Static methods.
         */


        private static boolean test(Object proxy, Supplier<?> delegateSupplier, Method method, Object[] arguments) {
            return method.getDeclaringClass() == Object.class;
        }

        private static void sink(Object o1, Object o2) {}

    }

}
