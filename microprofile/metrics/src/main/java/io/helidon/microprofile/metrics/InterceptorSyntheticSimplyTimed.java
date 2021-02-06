/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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
import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Priority;
import javax.inject.Inject;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.CompletionCallback;

import org.eclipse.microprofile.metrics.SimpleTimer;

/**
 * Interceptor for synthetic {@link SyntheticSimplyTimed} annotations.
 * <p>
 *     This interceptor handles each JAX-RS endpoint (as denoted by the JAX-RS annotations such as {@code @GET}, etc.)
 *     and updates the metric for the corresponding {@code SyntheticSimplyTimed} annotation.
 * </p>
 */
@SyntheticSimplyTimed
@Interceptor
@Priority(Interceptor.Priority.PLATFORM_BEFORE + 10)
final class InterceptorSyntheticSimplyTimed {

    private static final Logger LOGGER = Logger.getLogger(InterceptorSyntheticSimplyTimed.class.getName());

    private final boolean isEnabled;
    private final RestEndpointMetricsInfo restEndpointMetricsInfo;

    @Inject
    InterceptorSyntheticSimplyTimed(RestEndpointMetricsInfo restEndpointMetricsInfo) {
        this.restEndpointMetricsInfo = restEndpointMetricsInfo;
        isEnabled = restEndpointMetricsInfo.isEnabled();
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
        if (!isEnabled) {
            return context.proceed();
        }
        long startNanos = System.nanoTime();
        try {
            LOGGER.fine("Interceptor of SyntheticSimplyTimed called for '" + context.getTarget().getClass()
                    + "::" + context.getMethod().getName() + "'");

            Method timedMethod = context.getMethod();
            SimpleTimer simpleTimer = MetricsCdiExtension.syntheticSimpleTimer(timedMethod);
            AsyncResponse asyncResponse = restEndpointMetricsInfo.asyncResponse(context);
            if (asyncResponse != null) {
                asyncResponse.register(new FinishCallback(startNanos, simpleTimer));
                return context.proceed();
            }
            return simpleTimer.time(context::proceed);
        } catch (Throwable t) {
            LOGGER.log(Level.FINE, "Throwable caught by interceptor", t);
            throw t;
        }
    }

    /**
     * Async callback which updates a {@code SimpleTimer} associated with the REST endpoint.
     */
    static class FinishCallback implements CompletionCallback {

        private final long startTimeNanos;
        private final SimpleTimer simpleTimer;

        private FinishCallback(long startTimeNanos, SimpleTimer simpleTimer) {
            this.simpleTimer = simpleTimer;
            this.startTimeNanos = startTimeNanos;
        }

        @Override
        public void onComplete(Throwable throwable) {
            long nowNanos = System.nanoTime();
            simpleTimer.update(Duration.ofNanos(nowNanos - startTimeNanos));
            if (throwable != null) {
                LOGGER.log(Level.FINE, "Throwable detected by interceptor callback", throwable);
            }
        }
    }
}
