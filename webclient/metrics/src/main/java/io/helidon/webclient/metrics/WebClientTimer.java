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

import java.util.concurrent.TimeUnit;

import io.helidon.common.http.Http;
import io.helidon.common.reactive.Single;
import io.helidon.webclient.WebClientServiceRequest;

import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.Timer;

/**
 * Timer which measures the length of request.
 */
class WebClientTimer extends WebClientMetric {

    WebClientTimer(WebClientMetric.Builder builder) {
        super(builder);
    }

    @Override
    MetricType metricType() {
        return MetricType.TIMER;
    }

    @Override
    public Single<WebClientServiceRequest> request(WebClientServiceRequest request) {
        long start = System.nanoTime();
        Http.RequestMethod method = request.method();

        request.whenResponseReceived()
                .thenAccept(response -> {
                    if (shouldContinueOnError(method, response.status().code())) {
                        updateTimer(createMetadata(request, response), start);
                    }
                });
        request.whenComplete()
                .thenAccept(response -> {
                    if (shouldContinueOnSuccess(method, response.status().code())) {
                        updateTimer(createMetadata(request, response), start);
                    }
                })
                .exceptionally(throwable -> {
                    if (shouldContinueOnError(method)) {
                        updateTimer(createMetadata(request, null), start);
                    }
                    return null;
                });

        return Single.just(request);
    }

    private void updateTimer(Metadata metadata, long start) {
        long time = System.nanoTime() - start;
        Timer timer = metricRegistry().timer(metadata);
        timer.update(time, TimeUnit.NANOSECONDS);
    }

}
