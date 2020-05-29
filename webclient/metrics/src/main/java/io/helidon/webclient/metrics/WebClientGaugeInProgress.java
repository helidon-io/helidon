/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
package io.helidon.webclient.metrics;

import io.helidon.common.reactive.Single;
import io.helidon.webclient.WebClientServiceRequest;

import org.eclipse.microprofile.metrics.ConcurrentGauge;
import org.eclipse.microprofile.metrics.MetricType;

/**
 * Gauge which counts all requests in progress.
 */
class WebClientGaugeInProgress extends WebClientMetric {

    WebClientGaugeInProgress(Builder builder) {
        super(builder);
    }

    @Override
    MetricType metricType() {
        return MetricType.CONCURRENT_GAUGE;
    }

    @Override
    public Single<WebClientServiceRequest> request(WebClientServiceRequest request) {
        ConcurrentGauge gauge = metricRegistry().concurrentGauge(createMetadata(request, null));
        boolean shouldBeHandled = handlesMethod(request.method());
        if (!shouldBeHandled) {
            return Single.just(request);
        } else {
            gauge.inc();
        }

        request.whenComplete()
                .thenAccept(response -> gauge.dec())
                .exceptionally(throwable -> {
                    gauge.dec();
                    return null;
                });

        return Single.just(request);
    }

}
