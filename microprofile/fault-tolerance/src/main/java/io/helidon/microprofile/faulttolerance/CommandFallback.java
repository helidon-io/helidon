/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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

import javax.enterprise.inject.spi.CDI;
import javax.interceptor.InvocationContext;

import net.jodah.failsafe.event.ExecutionAttemptedEvent;
import org.eclipse.microprofile.faulttolerance.ExecutionContext;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.FallbackHandler;

import static io.helidon.microprofile.faulttolerance.ExceptionUtil.toException;

/**
 * Class CommandFallback.
 */
class CommandFallback {

    private final InvocationContext context;

    private final Throwable throwable;

    private Class<? extends FallbackHandler<?>> handlerClass;

    private Method fallbackMethod;

    /**
     * Constructor.
     *
     * @param context Invocation context.
     * @param introspector Method introspector.
     * @param event ExecutionAttemptedEvent representing the failure causing the fallback invocation
     */
    CommandFallback(InvocationContext context, MethodIntrospector introspector, ExecutionAttemptedEvent<?> event) {
        this.context = context;
        this.throwable = event.getLastFailure();

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
        assert handlerClass != null || fallbackMethod != null;

        Object result;
        try {
            if (handlerClass != null) {
                // Instantiate handler using CDI
                FallbackHandler<?> handler = CDI.current().select(handlerClass).get();
                result = handler.handle(
                        new ExecutionContext() {
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
                        }
                );
            } else {
                result = fallbackMethod.invoke(context.getTarget(), context.getParameters());
            }
        } catch (Throwable t) {
            updateMetrics(t);

            // If InvocationTargetException, then unwrap underlying cause
            if (t instanceof InvocationTargetException) {
                t = t.getCause();
            }
            throw toException(t);
        }

        updateMetrics(null);
        return result;
    }

    /**
     * Updates fallback metrics and adjust failed invocations based on outcome of fallback.
     */
    private void updateMetrics(Throwable throwable) {
        final Method method = context.getMethod();
        FaultToleranceMetrics.getCounter(method, FaultToleranceMetrics.FALLBACK_CALLS_TOTAL).inc();

        // If fallback was successful, it is not a failed invocation
        if (throwable == null) {
            // Since metrics 2.0, countes should only be incrementing, so we cheat here
            FaultToleranceMetrics.getCounter(method, FaultToleranceMetrics.INVOCATIONS_FAILED_TOTAL).inc(-1L);
        }
    }
}
