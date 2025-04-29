/*
 * Copyright (c) 2018, 2025 Oracle and/or its affiliates.
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

package io.helidon.microprofile.faulttolerance;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.enterprise.inject.spi.Unmanaged;
import jakarta.interceptor.InvocationContext;
import org.eclipse.microprofile.faulttolerance.ExecutionContext;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.FallbackHandler;

/**
 * Implements invocation callback logic.
 */
class FallbackHelper {

    private final InvocationContext context;

    private final Throwable throwable;

    private final ExecutionContext executionContext = new ExecutionContext() {
        @Override
        public Method getMethod() {
            return context.getMethod();
        }

        @Override
        public Object[] getParameters() {
            return context.getParameters();
        }

        @Override
        public Throwable getFailure() {
            return throwable;
        }
    };

    private Class<? extends FallbackHandler<?>> handlerClass;

    private Method fallbackMethod;

    /**
     * Constructor.
     *
     * @param context Invocation context.
     * @param introspector Method introspector.
     * @param throwable Throwable that caused execution of fallback
     */
    FallbackHelper(InvocationContext context, MethodIntrospector introspector, Throwable throwable) {
        this.context = context;
        this.throwable = throwable;

        // Establish fallback strategy
        final Fallback fallback = introspector.getFallback();
        if (fallback.value() != Fallback.DEFAULT.class) {
            handlerClass = fallback.value();
        } else if (!fallback.fallbackMethod().isEmpty()) {
            Object instance = context.getTarget();
            try {
                fallbackMethod = JavaMethodFinder.findMethod(instance.getClass(),
                        introspector.getFallback().fallbackMethod(),
                        context.getMethod().getGenericParameterTypes());
            } catch (NoSuchMethodException e) {
                throw new InternalError(e);     // should have been validated
            }
        } else {
            handlerClass = Fallback.DEFAULT.class;
        }
    }

    /**
     * Executes fallback policy.
     *
     * @return Object returned by command.
     * @throws Exception If something fails.
     */
    public Object execute() throws Exception {

        Object result;
        try {
            if (handlerClass != null) {
                // Instantiate handler using CDI
                Instance<? extends FallbackHandler> instance = CDI.current().select(handlerClass);
                if (instance.isResolvable()) {
                    FallbackHandler<?> handler = instance.get();
                    result = handler.handle(executionContext);
                } else {
                    // It is not required that FallbackHandler is a bean. TCKs will fail otherwise
                    Unmanaged<FallbackHandler<?>> unmanaged = new Unmanaged<>(CDI.current().getBeanManager(),
                            (Class<FallbackHandler<?>>) handlerClass);
                    Unmanaged.UnmanagedInstance<FallbackHandler<?>> unmanagedInstance = unmanaged.newInstance();
                    FallbackHandler<?> handler = unmanagedInstance.produce().inject().postConstruct().get();
                    try {
                        result = handler.handle(executionContext);
                    } finally {
                        // The instance exists to service a single invocation only
                        unmanagedInstance.preDestroy().dispose();
                    }
                }
            } else {
                result = fallbackMethod.invoke(context.getTarget(), context.getParameters());
            }
        } catch (Throwable t) {
            // If InvocationTargetException, then unwrap underlying cause
            if (t instanceof InvocationTargetException) {
                t = t.getCause();
            }
            throw t instanceof Exception ? (Exception) t : new RuntimeException(t);
        }

        return result;
    }
}
