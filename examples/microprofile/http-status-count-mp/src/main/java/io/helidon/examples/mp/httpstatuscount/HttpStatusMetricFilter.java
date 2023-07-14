/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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
package io.helidon.examples.mp.httpstatuscount;

import java.io.IOException;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.ws.rs.ConstrainedTo;
import jakarta.ws.rs.RuntimeType;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.Tag;

/**
 * REST service filter to update a family of counters based on the HTTP status of each response.
 * <p>
 *     The filter uses one {@link org.eclipse.microprofile.metrics.Counter} for each HTTP status family (1xx, 2xx, etc.).
 *     All counters share the same name--{@value STATUS_COUNTER_NAME}--and each has the tag {@value STATUS_TAG_NAME} with
 *     value {@code 1xx}, {@code 2xx}, etc.
 * </p>
 */
@ConstrainedTo(RuntimeType.SERVER)
@Provider
public class HttpStatusMetricFilter implements ContainerResponseFilter {

    static final String STATUS_COUNTER_NAME = "httpStatus";
    static final String STATUS_TAG_NAME = "range";

    @Inject
    private MetricRegistry metricRegistry;

    private final Counter[] responseCounters = new Counter[6];

    @PostConstruct
    private void init() {
        Metadata metadata = Metadata.builder()
                .withName(STATUS_COUNTER_NAME)
                .withDescription("Counts the number of HTTP responses in each status category (1xx, 2xx, etc.)")
                .withUnit(MetricUnits.NONE)
                .build();
        // Declare the counters and keep references to them.
        for (int i = 1; i < responseCounters.length; i++) {
            responseCounters[i] = metricRegistry.counter(metadata, new Tag(STATUS_TAG_NAME, i + "xx"));
        }
    }

    @Override
    public void filter(ContainerRequestContext containerRequestContext, ContainerResponseContext containerResponseContext)
            throws IOException {
        updateCountForStatus(containerResponseContext.getStatus());
    }

    private void updateCountForStatus(int statusCode) {
        int range = statusCode / 100;
        if (range > 0 && range < responseCounters.length) {
            responseCounters[range].inc();
        }
    }
}
