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
package io.helidon.nima.webclient.metrics;

import java.time.Duration;

import io.helidon.common.http.Http;
import io.helidon.nima.webclient.WebClientServiceRequest;
import io.helidon.nima.webclient.WebClientServiceResponse;

import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Timer;

/**
 * Timer which measures the length of request.
 */
class WebClientTimer extends WebClientMetric {

    WebClientTimer(WebClientMetric.Builder builder) {
        super(builder);
    }

    @Override
    public WebClientServiceResponse handle(Chain chain, WebClientServiceRequest request) {
        long start = System.nanoTime();
        Http.Method method = request.method();
        try {
            WebClientServiceResponse response = chain.proceed(request);
            Http.Status status = response.status();
            if (shouldContinueOnError(method, status.code())) {
                updateTimer(createMetadata(request, response), start);
            }
            return response;
        } catch (Throwable ex) {
            if (shouldContinueOnError(method)) {
                updateTimer(createMetadata(request, null), start);
            }
            throw ex;
        }
    }

    private void updateTimer(Metadata metadata, long start) {
        long time = System.nanoTime() - start;
        Timer timer = metricRegistry().timer(metadata);
        timer.update(Duration.ofNanos(time));
    }

}
