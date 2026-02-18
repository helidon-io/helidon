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

import io.helidon.common.LazyValue;
import io.helidon.config.Config;
import io.helidon.http.Status;
import io.helidon.service.registry.Services;
import io.helidon.telemetry.otelconfig.HelidonOpenTelemetry;
import io.helidon.webclient.api.WebClientServiceRequest;
import io.helidon.webclient.api.WebClientServiceResponse;
import io.helidon.webclient.spi.WebClientService;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;

/**
 * Webclient service for providing metrics which comply with the OpenTelemetry semantic conventions for
 * client metrics.
 */
public class WebClientTelemetryMetrics implements WebClientService {

    /*
    Bucket boundaries as recommended by the OpenTelemetry semantic conventions.
    https://opentelemetry.io/docs/specs/semconv/http/http-metrics/#metric-httpclientrequestduration
     */
    private static final List<Double> BUCKET_BOUNDARIES =
        List.of(0.005, 0.01, 0.025, 0.05, 0.075, 0.1, 0.25, 0.5, 0.75, 1.0, 2.5, 5.0, 7.5, 10.0);

    static final String REQUEST_DURATION = "http.client.request.duration";

    static final String HTTP_REQUEST_METHOD = "http.client.request.method";
    static final String SERVER_ADDRESS = "server.address";
    static final String SERVER_PORT = "server.port";
    static final String ERROR_TYPE = "error.type";
    static final String HTTP_RESPONSE_STATUS_CODE = "http.response.status.code";
    static final String URL_SCHEME = "url.scheme";
    static final String URL_TEMPLATE = "url.template";

    private final LazyValue<DoubleHistogram> outboundHttpRequestDuration =
            LazyValue.create(WebClientTelemetryMetrics::createHistogram);

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
        long startTime = System.nanoTime();

        String errorType = "";
        int statusCodeTagValue = 0;
        try {
            var response = chain.proceed(clientRequest);
            statusCodeTagValue = response.status().code();
            errorType = response.status().family() == Status.Family.SUCCESSFUL ? "" : Integer.toString(statusCodeTagValue);
            return response;
        } catch (Exception ex) {
            errorType = ex.getClass().getSimpleName();
            throw ex;
        } finally {
            long endTime = System.nanoTime();
            var attributes = Attributes.builder()
                    .put(HTTP_REQUEST_METHOD, clientRequest.method().text())
                    .put(SERVER_ADDRESS, clientRequest.uri().host())
                    .put(SERVER_PORT, clientRequest.uri().port())
                    .put(ERROR_TYPE, errorType)
                    .put(HTTP_RESPONSE_STATUS_CODE, statusCodeTagValue)
                    .put(URL_SCHEME, clientRequest.uri().scheme())
                    .put(URL_TEMPLATE, clientRequest.uri().path().path())
                    .build();

            outboundHttpRequestDuration.get().record(endTime - startTime, attributes);
        }
    }

    private static DoubleHistogram createHistogram() {
        var config = Services.get(Config.class);
        HelidonOpenTelemetry helidonOpenTelemetry = HelidonOpenTelemetry.builder()
                .config(config.get(HelidonOpenTelemetry.CONFIG_KEY))
                .build();
        return Services.get(OpenTelemetry.class)
                .getMeterProvider()
                .get(helidonOpenTelemetry.prototype().service())
                .histogramBuilder(REQUEST_DURATION)
                .setDescription("Outbound HTTP request duration")
                .setUnit("s") // seconds
                .setExplicitBucketBoundariesAdvice(BUCKET_BOUNDARIES)
                .build();
    }
}
