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

import io.helidon.http.Method;
import io.helidon.metrics.api.Counter;
import io.helidon.webclient.api.WebClientServiceRequest;
import io.helidon.webclient.api.WebClientServiceResponse;

/**
 * Client metric meter for all requests.
 */
public class WebClientMeter extends WebClientMetric {

    WebClientMeter(Builder builder) {
        super(builder);
    }

    @Override
    public WebClientServiceResponse handle(Chain chain, WebClientServiceRequest request) {
        Method method = request.method();
        try {
            WebClientServiceResponse response = chain.proceed(request);
            int code = response.status().code();
            if (shouldContinueOnSuccess(method, code) || shouldContinueOnError(method, code)) {
                updateMeter(createMetadata(request, null));
            }
            return response;
        } catch (Throwable ex) {
            if (shouldContinueOnError(method)) {
                updateMeter(createMetadata(request, null));
            }
            throw ex;
        }
    }

    private void updateMeter(Metadata metadata) {
        meterRegistry().getOrCreate(Counter.builder(metadata.name())
                                            .description(metadata.description()))
                .increment();
    }
}
