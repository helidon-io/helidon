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

import io.helidon.common.http.Http;
import io.helidon.common.reactive.Single;
import io.helidon.webclient.WebClientServiceRequest;

import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Meter;
import org.eclipse.microprofile.metrics.MetricType;

/**
 * Client metric meter for all requests.
 */
public class WebClientMeter extends WebClientMetric {

    WebClientMeter(Builder builder) {
        super(builder);
    }

    @Override
    MetricType metricType() {
        return MetricType.METERED;
    }

    @Override
    public Single<WebClientServiceRequest> request(WebClientServiceRequest request) {
        Http.RequestMethod method = request.method();
        request.whenResponseReceived()
                .thenAccept(response -> {
                    if (shouldContinueOnError(method, response.status().code())) {
                        updateMeter(createMetadata(request, null));
                    }
                });

        request.whenComplete()
                .thenAccept(response -> {
                    if (shouldContinueOnSuccess(method, response.status().code())) {
                        updateMeter(createMetadata(request, null));
                    }
                })
                .exceptionally(throwable -> {
                    if (shouldContinueOnError(method)) {
                        updateMeter(createMetadata(request, null));
                    }
                    return null;
                });

        return Single.just(request);
    }

    private void updateMeter(Metadata metadata) {
        Meter meter = metricRegistry().meter(metadata);
        meter.mark();
    }
}
