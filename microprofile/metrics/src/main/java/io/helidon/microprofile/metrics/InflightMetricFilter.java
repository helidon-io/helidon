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

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;

import org.eclipse.microprofile.metrics.ConcurrentGauge;

/**
 * Measures the number of in-flight requests for MP.
 * <p>
 *     Using a filter (rather than the SE-style routing handler) lets us update the metric after completion (not immediately
 *     after invocation) of asynchronous JAX-RS endpoints.
 * </p>
 */
class InflightMetricFilter implements ContainerRequestFilter, ContainerResponseFilter {

    @Override
    public void filter(ContainerRequestContext requestContext) {
        // The filter is in-place very early, so it's possible other initialization has not completed yet.
        ConcurrentGauge inflightRequests = MetricsCdiExtension.inflightRequests().get();
        if (inflightRequests != null) {
            inflightRequests.inc();
        }
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        // The filter is in-place very early, so it's possible other initialization has not completed yet.
        ConcurrentGauge inflightRequests = MetricsCdiExtension.inflightRequests().get();
        if (inflightRequests != null) {
            inflightRequests.dec();
        }
    }
}
