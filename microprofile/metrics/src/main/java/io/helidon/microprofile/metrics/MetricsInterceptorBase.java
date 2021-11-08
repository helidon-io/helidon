/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
 *
 */
package io.helidon.microprofile.metrics;

import java.lang.annotation.Annotation;
import java.lang.reflect.Executable;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.inject.Inject;
import javax.interceptor.InvocationContext;

import io.helidon.metrics.api.HelidonMetric;
import io.helidon.microprofile.metrics.MetricsCdiExtension.MetricWorkItem;
import io.helidon.servicecommon.restcdi.HelidonInterceptor;

import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricRegistry;

/**
 * Basic interceptor implementation which supports pre-invoke updates to metrics.
 * <p>
 *     Concrete subclasses implement {@link #preInvoke(Metric)} which takes metric-specific action on a metric before the
 *     intercepted constructor or method runs.
 * </p>
 * @param <M> type of metrics the interceptor handles
 */
abstract class MetricsInterceptorBase<M extends Metric> extends HelidonInterceptor.Base<MetricWorkItem> {

    static final Logger LOGGER = Logger.getLogger(MetricsInterceptorBase.class.getName());

    private final Class<? extends Annotation> annotationType;
    private final Class<M> metricType;

    @Inject
    private MetricsCdiExtension extension;

    @Inject
    private MetricRegistry registry;

    enum ActionType {
        PREINVOKE("preinvoke"), COMPLETE("complete");

        private final String label;

        ActionType(String label) {
            this.label = label;
        }

        public String toString() {
            return label;
        }
    }

    MetricsInterceptorBase(Class<? extends Annotation> annotationType, Class<M> metricType) {
        this.annotationType = annotationType;
        this.metricType = metricType;
    }

    @Override
    public Iterable<MetricWorkItem> workItems(Executable executable) {
        return extension.workItems(executable, annotationType);
    }

    @Override
    public void preInvocation(InvocationContext context, MetricWorkItem workItem) {
        verifyMetric(context, workItem, ActionType.PREINVOKE);
        preInvoke(metricType.cast(workItem.metric()));
    }

    void verifyMetric(InvocationContext context, MetricWorkItem workItem, ActionType actionType) {
        Metric metric = workItem.metric();
        if (HelidonMetric.isMarkedAsDeleted(metric)) {
            throw new IllegalStateException("Attempt to use previously-removed metric" + workItem.metricID());
        }
        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.log(Level.FINEST, String.format(
                    "%s (%s) is accepting %s %s for processing on %s triggered by @%s",
                    getClass().getSimpleName(), actionType, workItem.metric()
                            .getClass()
                            .getSimpleName(), workItem.metricID(),
                    context.getMethod() != null ? context.getMethod() : context.getConstructor(), annotationType.getSimpleName()));
        }
    }

    abstract void preInvoke(M metric);

    /**
     * Basic metric interceptor which adds post-completion action on the associated metric.
     * <p>
     *     Concrete subclasses implement {@link #postComplete(Metric)} which takes metric-specific action after the intercepted
     *     constructor or method has completed. For async methods this occurs after completion (not immediately after the start) of
     *     the method invocation.
     * </p>
     * @param <T> type of metrics the interceptor handles
     */
    abstract static class WithPostCompletion<T extends Metric> extends MetricsInterceptorBase<T>
            implements HelidonInterceptor.WithPostCompletion<MetricWorkItem> {

        private final Class<T> metricType;

        WithPostCompletion(Class<? extends Annotation> annotationType, Class<T> metricType) {
            super(annotationType, metricType);
            this.metricType = metricType;
        }

        @Override
        public void postCompletion(InvocationContext context, Throwable throwable, MetricWorkItem workItem) {
            verifyMetric(context, workItem, ActionType.COMPLETE);
            postComplete(metricType.cast(workItem.metric()));
        }

        abstract void postComplete(T metric);
    }
}
