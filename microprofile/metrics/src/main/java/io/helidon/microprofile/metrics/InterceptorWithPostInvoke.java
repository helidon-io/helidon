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

import javax.inject.Inject;
import javax.interceptor.InvocationContext;

import io.helidon.microprofile.metrics.MetricsCdiExtension.MetricWorkItem;
import io.helidon.servicecommon.restcdi.HelidonInterceptor;

import org.eclipse.microprofile.metrics.Metric;

/**
 * Basic metric interceptor which adds post-completion action on the associated metric.
 * <p>
 *     Concrete subclasses implement {@link #postComplete(Metric)} which takes metric-specific action after the intercepted
 *     constructor or method has completed. For async methods this occurs after completion (not immediately after the start) of
 *     the method invocation.
 * </p>
 * @param <T> type of metrics the interceptor handles
 */
abstract class InterceptorWithPostInvoke<T extends Metric> extends InterceptorBase<T>
        implements HelidonInterceptor.WithPostCompletion<MetricWorkItem> {

    @Inject
    private MetricsCdiExtension extension;

    InterceptorWithPostInvoke(Class<? extends Annotation> annotationType, Class<T> metricType) {
        super(annotationType, metricType);
    }

    @Override
    public void postCompletion(InvocationContext context, Throwable t, MetricWorkItem workItem) {
        invokeVerifiedAction(context, workItem, this::postComplete, ActionType.COMPLETE);
    }

    abstract void postComplete(T metric);
}
