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
import java.util.logging.Logger;

import javax.interceptor.InvocationContext;

import org.eclipse.microprofile.faulttolerance.ExecutionContext;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.FallbackHandler;
import org.eclipse.microprofile.faulttolerance.exceptions.FaultToleranceDefinitionException;

/**
 * Class CommandFallback.
 */
class CommandFallback {
    private static final Logger LOGGER = Logger.getLogger(CommandFallback.class.getName());

    private final InvocationContext context;

    private Class<? extends FallbackHandler<?>> handler;

    private Method fallbackMethod;

    /**
     * Constructor.
     *
     * @param context Invocation context.
     * @param introspector Method introspector.
     */
    CommandFallback(InvocationContext context, MethodIntrospector introspector) {
        this.context = context;

        // First check for error conditions
        final Fallback fallback = introspector.getFallback();
        if (fallback.value() != Fallback.DEFAULT.class && !fallback.fallbackMethod().isEmpty()) {
            throw new FaultToleranceDefinitionException("Fallback annotation cannot declare a "
                                                        + "handler and a fallback fallbackMethod");
        }

        // Establish fallback strategy
        if (fallback.value() != Fallback.DEFAULT.class) {
            handler = fallback.value();
        } else if (!fallback.fallbackMethod().isEmpty()) {
            Object instance = context.getTarget();
            try {
                fallbackMethod = instance.getClass().getMethod(introspector.getFallback().fallbackMethod());
            } catch (NoSuchMethodException e) {
                throw new FaultToleranceDefinitionException(e);
            }
        } else {
            handler = Fallback.DEFAULT.class;
        }
    }

    /**
     * Executes fallback policy.
     *
     * @return Object returned by command.
     * @throws Exception If something fails.
     */
    public Object execute() throws Exception {
        assert handler != null || fallbackMethod != null;

        updateMetrics();

        if (handler != null) {
            return handler.getDeclaredConstructor().newInstance().handle(
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
            return fallbackMethod.invoke(context.getTarget());
        }
    }

    /**
     * Updates fallback metrics.
     */
    private void updateMetrics() {
        FaultToleranceMetrics.getCounter(context.getMethod(), FaultToleranceMetrics.FALLBACK_CALLS_TOTAL).inc();
    }
}
