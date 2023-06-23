/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
package io.helidon.examples.se.httpstatuscount;

import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.metrics.api.RegistryFactory;
import io.helidon.reactive.webserver.Routing;
import io.helidon.reactive.webserver.ServerRequest;
import io.helidon.reactive.webserver.ServerResponse;
import io.helidon.reactive.webserver.Service;

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.Tag;

/**
 * Helidon SE service to update a family of counters based on the HTTP status of each response. Add an instance of this service
 * to the application's routing.
 * <p>
 *     The service uses one {@link org.eclipse.microprofile.metrics.Counter} for each HTTP status family (1xx, 2xx, etc.).
 *     All counters share the same name--{@value STATUS_COUNTER_NAME}--and each has the tag {@value STATUS_TAG_NAME} with
 *     value {@code 1xx}, {@code 2xx}, etc.
 * </p>
 */
public class HttpStatusMetricService implements Service {

    static final String STATUS_COUNTER_NAME = "httpStatus";

    static final String STATUS_TAG_NAME = "range";

    private static final AtomicInteger IN_PROGRESS = new AtomicInteger();

    private final Counter[] responseCounters = new Counter[6];

    static HttpStatusMetricService create() {
        return new HttpStatusMetricService();
    }

    private HttpStatusMetricService() {
        MetricRegistry appRegistry = RegistryFactory.getInstance().getRegistry(Registry.APPLICATION_SCOPE);
        Metadata metadata = Metadata.builder()
                .withName(STATUS_COUNTER_NAME)
                .withDisplayName("HTTP response values")
                .withDescription("Counts the number of HTTP responses in each status category (1xx, 2xx, etc.)")
                .withType(MetricType.COUNTER)
                .withUnit(MetricUnits.NONE)
                .build();
        // Declare the counters and keep references to them.
        for (int i = 1; i < responseCounters.length; i++) {
            responseCounters[i] = appRegistry.counter(metadata, new Tag(STATUS_TAG_NAME, i + "xx"));
        }
    }

    @Override
    public void update(Routing.Rules rules) {
        rules.any(this::updateRange);
    }

    // for testing
    static boolean isInProgress() {
        return IN_PROGRESS.get() != 0;
    }

    // Edited to adopt Ciaran's fix later in the thread.
    private void updateRange(ServerRequest request, ServerResponse response) {
        IN_PROGRESS.incrementAndGet();
        response.whenSent()
                .thenAccept(this::logMetric);
        request.next();
    }

    private void logMetric(ServerResponse response) {
        int range = response.status().code() / 100;
        if (range > 0 && range < responseCounters.length) {
            responseCounters[range].inc();
        }
        IN_PROGRESS.decrementAndGet();
    }
}
