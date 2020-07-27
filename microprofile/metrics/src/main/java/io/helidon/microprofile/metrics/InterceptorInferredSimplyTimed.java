/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.microprofile.metrics;

import java.lang.reflect.Method;
import java.util.logging.Logger;

import javax.annotation.Priority;
import javax.inject.Inject;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;

import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.SimpleTimer;
import org.eclipse.microprofile.metrics.annotation.SimplyTimed;

/**
 * Interceptor for synthetic {@link SimplyTimed} annotations.
 * <p>
 *     This interceptor handles each JAX-RS endpoint (as denoted by the JAX-RS annotations such as {@code @GET}, etc.
 *     and updates the metric for the corresponding inferred {@code SimplyTimed} annotation.
 * </p>
 */
@SyntheticSimplyTimed
@Interceptor
@Priority(Interceptor.Priority.PLATFORM_BEFORE + 10)
final class InterceptorInferredSimplyTimed {

    private static final Logger LOGGER = Logger.getLogger(InterceptorInferredSimplyTimed.class.getName());

    private final MetricRegistry metricRegistry;

    @Inject
    InterceptorInferredSimplyTimed(MetricRegistry registry) {
        metricRegistry = registry;
    }

    /**
     * Intercepts a call to bean method annotated by a JAX-RS annotation.
     *
     * @param context invocation context
     * @return the intercepted method's return value
     * @throws Throwable in case any error occurs
     */
    @AroundInvoke
    public Object interceptRestEndpoint(InvocationContext context) throws Throwable {
        try {
            LOGGER.fine("Interceptor of synthetic SimplyTimed called for '" + context.getTarget().getClass()
                    + "::" + context.getMethod().getName() + "'");

            Method timedMethod = context.getMethod();

            SimpleTimer simpleTimer = findSimpleTimer(timedMethod);
            return simpleTimer.time(context::proceed);
        } catch (Throwable t) {
            LOGGER.fine("Throwable caught by interceptor '" + t.getMessage() + "'");
            throw t;
        }
    }

    private SimpleTimer findSimpleTimer(Method timedMethod) {
        return MetricsCdiExtension.syntheticSimpleTimer(metricRegistry, timedMethod);
    }

    private static <T> T getSimplyTimedDefaultValue(String methodName, Class<? extends T> type) {
        try {
            return type.cast(SimplyTimed.class.getMethod(methodName).getDefaultValue());
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }
}
