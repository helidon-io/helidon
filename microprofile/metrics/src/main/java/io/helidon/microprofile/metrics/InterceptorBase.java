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
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.inject.Inject;
import javax.interceptor.AroundConstruct;
import javax.interceptor.AroundInvoke;
import javax.interceptor.InvocationContext;

import io.helidon.microprofile.metrics.MetricsCdiExtension.MetricWorkItem;

import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;

/**
 * Basic interceptor implementation which supports pre-invoke updates to metrics.
 * <p>
 *     Concrete subclasses implement {@link #preInvoke(Metric)} which takes metric-specific action on a metric before the
 *     intercepted constructor or method runs.
 * </p>
 * @param <M> type of metrics the interceptor handles
 */
abstract class InterceptorBase<M extends Metric> {

    private static final Logger LOGGER = Logger.getLogger(InterceptorBase.class.getName());

    private final Class<? extends Annotation> annotationType;
    private final Class<M> metricType;

    @Inject
    private MetricsCdiExtension extension;

    @Inject
    private MetricRegistry registry;

    private Map<MetricID, Metric> metricsForVerification;

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

    InterceptorBase(Class<? extends Annotation> annotationType, Class<M> metricType) {
        this.annotationType = annotationType;
        this.metricType = metricType;
    }

    Class<? extends Annotation> annotationType() {
        return annotationType;
    }

    Map<MetricID, Metric> metricsForVerification() {
        if (metricsForVerification == null) {
            metricsForVerification = registry.getMetrics();
        }
        return metricsForVerification;
    }

    @AroundConstruct
    Object aroundConstruct(InvocationContext context) throws Exception {
        InterceptInfo<MetricWorkItem> interceptInfo = extension.interceptInfo(context.getConstructor());
        return interceptInfo.runner().run(context, interceptInfo.workItems(annotationType), this::preInvoke);
    }

    @AroundInvoke
    Object aroundInvoke(InvocationContext context) throws Exception {
        InterceptInfo<MetricWorkItem> interceptInfo = extension.interceptInfo(context.getMethod());
        return interceptInfo.runner().run(context, interceptInfo.workItems(annotationType), this::preInvoke);
    }

    void preInvoke(InvocationContext context, MetricWorkItem workItem) {
        invokeVerifiedAction(context, workItem, this::preInvoke, ActionType.PREINVOKE);
    }

    void invokeVerifiedAction(InvocationContext context, MetricWorkItem workItem, Consumer<M> action, ActionType actionType) {
        if (!metricsForVerification().containsKey(workItem.metricID())) {
            throw new IllegalStateException("Attempt to use previously-removed metric" + workItem.metricID());
        }
        Metric metric = workItem.metric();
        LOGGER.log(Level.FINEST, () -> String.format(
                "%s (%s) is accepting %s %s for processing on %s triggered by @%s",
                getClass().getSimpleName(), actionType, workItem.metric().getClass().getSimpleName(), workItem.metricID(),
                context.getMethod() != null ? context.getMethod() : context.getConstructor(), annotationType.getSimpleName()));
        action.accept(metricType.cast(metric));
    }

    abstract void preInvoke(M metric);
}
