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
package io.helidon.examples.se.httpstatuscount;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.Metrics;
import io.helidon.metrics.api.Tag;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

/**
 * Helidon SE service to update a family of counters based on the HTTP status of each response. Add an instance of this service
 * to the application's routing.
 * <p>
 *     The service uses one {@link io.helidon.metrics.api.Counter} for each HTTP status family (1xx, 2xx, etc.).
 *     All counters share the same name--{@value STATUS_COUNTER_NAME}--and each has the tag {@value STATUS_TAG_NAME} with
 *     value {@code 1xx}, {@code 2xx}, etc.
 * </p>
 */
public class HttpStatusMetricService implements HttpService {

    static final String STATUS_COUNTER_NAME = "httpStatus";

    static final String STATUS_TAG_NAME = "range";

    private static final String COUNTER_DESCR = "Counts the number of HTTP responses in each status category (1xx, 2xx, etc.)";

    private static final AtomicInteger IN_PROGRESS = new AtomicInteger();

    private final Counter[] responseCounters = new Counter[6];

    static HttpStatusMetricService create() {
        return new HttpStatusMetricService();
    }

    private HttpStatusMetricService() {
        MeterRegistry registry = Metrics.globalRegistry();

        // Declare the counters and keep references to them.
        for (int i = 1; i < responseCounters.length; i++) {

            responseCounters[i] = registry.getOrCreate(Counter.builder(STATUS_COUNTER_NAME)
                                                               .tags(Set.of(Tag.create(STATUS_TAG_NAME, i + "xx")))
                                                               .description(COUNTER_DESCR));
        }
    }

    @Override
    public void routing(HttpRules rules) {
        rules.any(this::updateRange);
    }

    // for testing
    static boolean isInProgress() {
        return IN_PROGRESS.get() != 0;
    }

    // Edited to adopt Ciaran's fix later in the thread.
    private void updateRange(ServerRequest request, ServerResponse response) {
        IN_PROGRESS.incrementAndGet();
        response.next();
        logMetric(response);
    }

    private void logMetric(ServerResponse response) {
        int range = response.status().code() / 100;
        if (range > 0 && range < responseCounters.length) {
            responseCounters[range].increment();
        }
        IN_PROGRESS.decrementAndGet();
    }
}
