/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.webclient.telemetry.metrics;

import java.util.List;
import java.util.concurrent.TimeUnit;

import io.helidon.common.LazyValue;
import io.helidon.config.Config;
import io.helidon.http.Status;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.Tag;
import io.helidon.metrics.api.Timer;
import io.helidon.service.registry.Services;
import io.helidon.webclient.api.WebClientServiceRequest;
import io.helidon.webclient.api.WebClientServiceResponse;
import io.helidon.webclient.spi.WebClientService;

/**
 * Webclient service for providing metrics which comply with the OpenTelemetry semantic conventions for
 * client metrics.
 */
public class WebClientTelemetryMetrics implements WebClientService {

    static final String REQUEST_DURATION = "http.client.request.duration";

    static final String HTTP_REQUEST_METHOD = "http.client.request.method";
    static final String SERVER_ADDRESS = "server.address";
    static final String SERVER_PORT = "server.port";
    static final String ERROR_TYPE = "error.type";
    static final String HTTP_RESPONSE_STATUS_CODE = "http.response.status.code";
    static final String URL_SCHEME = "url.scheme";

    private final LazyValue<MeterRegistry>  meterRegistry = LazyValue.create(() -> Services.get(MeterRegistry.class));

    private WebClientTelemetryMetrics() {
    }

    /**
     * Creates a new instance of the telemetry metrics service.
     *
     * @return telemetry metrics service instance
     */
    public static WebClientTelemetryMetrics create() {
        return new WebClientTelemetryMetrics();
    }

    /**
     * Creates a new instance of the telemetry metrics service using the provided configuration.
     *
     * @param config telemetry metrics configuration
     * @return telemetry metrics service initialized using the configuration
     */
    public static WebClientTelemetryMetrics create(Config config) {
        return create();
    }

    @Override
    public WebClientServiceResponse handle(Chain chain, WebClientServiceRequest clientRequest) {
        long start = System.nanoTime();

        String errorType = "";
        String statusCodeTagValue = "";
        try {
            var response = chain.proceed(clientRequest);
            statusCodeTagValue = response.status().codeText();
            errorType = response.status().family() == Status.Family.SUCCESSFUL ? "" : statusCodeTagValue;
            return response;
        } catch (Exception ex) {
            errorType = ex.getClass().getSimpleName();
            throw ex;
        } finally {
            var tags = List.of(
                    Tag.create(HTTP_REQUEST_METHOD, clientRequest.method().text()),
                    Tag.create(SERVER_ADDRESS, clientRequest.uri().host()),
                    Tag.create(SERVER_PORT, Integer.toString(clientRequest.uri().port())),
                    Tag.create(ERROR_TYPE, errorType),
                    Tag.create(HTTP_RESPONSE_STATUS_CODE, statusCodeTagValue),
                    Tag.create(URL_SCHEME, clientRequest.uri().scheme()));

            var timer = meterRegistry.get().getOrCreate(Timer.builder(REQUEST_DURATION)
                    .tags(tags));
            timer.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
        }
    }
}
