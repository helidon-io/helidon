/*
 * Copyright (c) 2020, 2022 Oracle and/or its affiliates.
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

import java.lang.reflect.Executable;
import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.metrics.serviceapi.PostRequestMetricsSupport;
import io.helidon.servicecommon.restcdi.HelidonInterceptor;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import jakarta.ws.rs.core.Context;

/**
 * Interceptor for synthetic {@link SyntheticRestRequest} annotations.
 * <p>
 *     This interceptor handles each JAX-RS endpoint (as denoted by the JAX-RS annotations such as {@code @GET}, etc.)
 *     and updates the {@code SimpleTimer} metric for successful handling or the {@code Counter} metric for unsuccessful handling
 *     as specified by the corresponding {@code SyntheticRestRequest} annotation.
 * </p>
 * <p>
 *     Note that the metrics updates for {@code REST.request} occur after the server has sent the response, due to the sequencing
 *     of {@code ExceptionMapping} invocations vs. when the {@code MetricsSupport} handler regains control and due to possible
 *     asynchronous requests. To distinguish between mapped and unmapped exceptions, the
 *     {@link io.helidon.microprofile.server.JaxRsCdiExtension} {@code CatchAllExceptionMapper}, which runs only if the
 *     application does not map the exception, stores the unmapped exception in the request context. The code here infers whether
 *     any exception is mapped by the presence or absence of that  request context element to decide which {@code REST.request}
 *     metric to update.
 * </p>
 */
@SyntheticRestRequest
@Interceptor
@Priority(Interceptor.Priority.PLATFORM_BEFORE + 10)
final class InterceptorSyntheticRestRequest extends HelidonInterceptor.Base<SyntheticRestRequestWorkItem>
        implements HelidonInterceptor<SyntheticRestRequestWorkItem> {


    private static final Logger LOGGER = Logger.getLogger(InterceptorSyntheticRestRequest.class.getName());

    private long startNanos;

    @Inject
    private MetricsCdiExtension extension;

    @Context
    private ServerRequest request;

    @Override
    public Iterable<SyntheticRestRequestWorkItem> workItems(Executable executable) {
        return extension.workItems(executable, SyntheticRestRequest.class, SyntheticRestRequestWorkItem.class);
    }

    @Override
    public void preInvocation(InvocationContext context, SyntheticRestRequestWorkItem workItem) {
        MetricsInterceptorBase.verifyMetric(workItem.successfulSimpleTimerMetricID(),
                                            workItem.successfulSimpleTimer());
        MetricsInterceptorBase.verifyMetric(workItem.unmappedExceptionCounterMetricID(),
                                            workItem.unmappedExceptionCounter());
        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.log(Level.FINEST, String.format(
                    "%s (%s) is starting processing of a REST request on %s triggered by @%s",
                    getClass().getSimpleName(),
                    MetricsInterceptorBase.ActionType.PREINVOKE,
                    context.getMethod() != null ? context.getMethod() : context.getConstructor(),
                    SyntheticRestRequest.class.getSimpleName()));
        }

        PostCompletionMetricsUpdate update = new PostCompletionMetricsUpdate(workItem);
        PostRequestMetricsSupport.recordPostProcessingWork(request, update::updateRestRequestMetrics);

        startNanos = System.nanoTime();
    }

    private class PostCompletionMetricsUpdate {
        private final SyntheticRestRequestWorkItem workItem;

        private PostCompletionMetricsUpdate(SyntheticRestRequestWorkItem workItem) {
            this.workItem = workItem;
        }

        void updateRestRequestMetrics(ServerResponse serverResponse, Throwable throwable) {
            long endNanos = System.nanoTime();
            if (throwable == null) {
                // Because our SimpleTimer implementation does not update the metric if the elapsed time is 0, make sure to record
                // a duration of at least 1 nanosecond.
                long elapsedNanos = endNanos > startNanos ? endNanos - startNanos : 1;
                workItem.successfulSimpleTimer().update(Duration.ofNanos(elapsedNanos));
            } else {
                workItem.unmappedExceptionCounter().inc();
            }
        }
    }
}
