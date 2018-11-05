/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Method;

import javax.enterprise.inject.spi.CDI;
import javax.interceptor.InvocationContext;

import org.eclipse.microprofile.faulttolerance.ExecutionContext;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.FallbackHandler;

/**
 * Class CommandFallback.
 */
class CommandFallback {

    private final InvocationContext context;

    private Class<? extends FallbackHandler<?>> handlerClass;

    private Method fallbackMethod;

    /**
     * Constructor.
     *
     * @param context Invocation context.
     * @param introspector Method introspector.
     */
    CommandFallback(InvocationContext context, MethodIntrospector introspector) {
        this.context = context;

        // Establish fallback strategy
        final Fallback fallback = introspector.getFallback();
        if (fallback.value() != Fallback.DEFAULT.class) {
            handlerClass = fallback.value();
        } else if (!fallback.fallbackMethod().isEmpty()) {
            Object instance = context.getTarget();
            try {
                fallbackMethod = instance.getClass().getMethod(introspector.getFallback().fallbackMethod(),
                        context.getMethod().getParameterTypes());
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

        updateMetrics();

        if (handlerClass != null) {
            // Instantiate handler using CDI
            FallbackHandler<?> handler = CDI.current().select(handlerClass).get();
            return handler.handle(
                new ExecutionContext() {
                    @Override
                    public Method getMethod() {
                        return context.getMethod();
                    }

                    @Override
                    public Object[] getParameters() {
                        return context.getParameters();
                    }
                }
            );
        } else {
            return fallbackMethod.invoke(context.getTarget(), context.getParameters());
        }
    }

    /**
     * Updates fallback metrics.
     */
    private void updateMetrics() {
        FaultToleranceMetrics.getCounter(context.getMethod(), FaultToleranceMetrics.FALLBACK_CALLS_TOTAL).inc();
    }
}
