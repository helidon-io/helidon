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
package io.helidon.webclient.metrics;

import java.util.concurrent.atomic.AtomicLong;

import io.helidon.metrics.api.Gauge;
import io.helidon.webclient.api.WebClientServiceRequest;
import io.helidon.webclient.api.WebClientServiceResponse;

/**
 * Gauge which counts all requests in progress.
 */
class WebClientGaugeInProgress extends WebClientMetric {

    private final AtomicLong holder = new AtomicLong();

    WebClientGaugeInProgress(Builder builder) {
        super(builder);
    }

    @Override
    public WebClientServiceResponse handle(Chain chain, WebClientServiceRequest request) {
        Metadata metadata = createMetadata(request, null);
        meterRegistry().getOrCreate(Gauge.builder(metadata.name(), holder, AtomicLong::get)
                                            .description(metadata.description()));
        boolean update = handlesMethod(request.method());
        try {
            if (update) {
                holder.incrementAndGet();
            }
            return chain.proceed(request);
        } finally {
            if (update) {
                holder.decrementAndGet();
            }
        }
    }
}
