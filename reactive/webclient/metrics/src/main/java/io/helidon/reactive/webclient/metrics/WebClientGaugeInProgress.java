/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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
package io.helidon.reactive.webclient.metrics;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import io.helidon.common.reactive.Single;
import io.helidon.reactive.webclient.WebClientServiceRequest;

import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Metric;

/**
 * Gauge which counts all requests in progress.
 */
class WebClientGaugeInProgress extends WebClientMetric {

    private final Map<Metadata, AtomicLong> values = new HashMap<>();

    WebClientGaugeInProgress(Builder builder) {
        super(builder);
    }

    @Override
    Class<? extends Metric> metricType() {
        return Gauge.class;
    }

    @Override
    public Single<WebClientServiceRequest> request(WebClientServiceRequest request) {
        // Used to use ConcurrentGauge. Converted to use a Gauge monitoring an AtomicLong instead.
        Metadata metadata = createMetadata(request, null);
        var value = values.computeIfAbsent(metadata, m -> new AtomicLong());
        metricRegistry().gauge(metadata, value, AtomicLong::get);
        boolean shouldBeHandled = handlesMethod(request.method());
        if (!shouldBeHandled) {
            return Single.just(request);
        } else {
            value.addAndGet(1);
        }

        request.whenComplete()
                .thenAccept(response -> value.decrementAndGet())
                .exceptionally(throwable -> {
                    value.decrementAndGet();
                    return null;
                });

        return Single.just(request);
    }

}
