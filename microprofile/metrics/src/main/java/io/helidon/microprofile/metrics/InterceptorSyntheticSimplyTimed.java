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

import java.util.Map;

import javax.annotation.Priority;
import javax.inject.Inject;
import javax.interceptor.Interceptor;

import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.annotation.RegistryType;

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
final class InterceptorSyntheticSimplyTimed extends InterceptorSimplyTimedBase {

    private final Map<MetricID, Metric> metricsForVerification;

    @Inject
    InterceptorSyntheticSimplyTimed(@RegistryType(type = MetricRegistry.Type.BASE) MetricRegistry baseRegistry) {
        super(SyntheticSimplyTimed.class);
        metricsForVerification = baseRegistry.getMetrics();
    }

    @Override
    Map<MetricID, Metric> metricsForVerification() {
        return metricsForVerification;
    }
}
