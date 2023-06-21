/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.metrics.microprofile.feature;

import java.util.Optional;

import io.helidon.common.http.Http;
import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.metrics.microprofile.PrometheusFormatter;
import io.helidon.nima.servicecommon.HelidonFeatureSupport;
import io.helidon.nima.webserver.http.HttpRules;
import io.helidon.nima.webserver.http.HttpService;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.nima.webserver.http.ServerResponse;

/**
 * MP metrics feature implementation.
 */
public class MpMetricsFeature extends HelidonFeatureSupport {

    private static final System.Logger LOGGER = System.getLogger(MpMetricsFeature.class.getName());

    /**
     * Creates a new builder for the MP metrics feature.
     *
     * @return new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a new default MP metrics feature.
     *
     * @return newly-created feature
     */
    public static MpMetricsFeature create() {
        return builder().build();
    }

    /**
     * Create a new instance.
     *
     * @param logger logger for the feature
     * @param builder builder to use
     * @param serviceName name of the service
     */
    protected MpMetricsFeature(System.Logger logger, Builder builder, String serviceName) {
        super(logger, builder, serviceName);
    }

    @Override
    public Optional<HttpService> service() {
        if (enabled()) {
            return Optional.of(this::configureRoutes);
        } else {
            return Optional.of(this::configureDisabledRoutes);
        }
    }

    protected void context(String componentPath) {
        super.context(componentPath);
    }

    private void configureRoutes(HttpRules rules) {
        rules.get("/", this::prepareResponse);
    }

    private void configureDisabledRoutes(HttpRules rules) {
        rules.get("/", this::prepareDisabledResponse);
    }

    private void prepareDisabledResponse(ServerRequest req, ServerResponse resp) {
        resp.status(Http.Status.NOT_IMPLEMENTED_501)
                .header(Http.Header.CONTENT_TYPE, MediaTypes.TEXT_PLAIN.text())
                .send("Metrics is disabled");
    }

    private void prepareResponse(ServerRequest req, ServerResponse resp) {

        Optional<MediaType> requestedMediaType = req.headers()
                .bestAccepted(PrometheusFormatter.MEDIA_TYPE_TO_FORMAT
                                      .keySet()
                                      .toArray(new MediaType[0]));
        if (requestedMediaType.isEmpty()) {
            LOGGER.log(System.Logger.Level.TRACE,
                       "Unable to compose Prometheus format response; request accepted types were "
                               + req.headers().acceptedTypes());
            resp.status(Http.Status.UNSUPPORTED_MEDIA_TYPE_415).send();
        }

        PrometheusFormatter.Builder formatterBuilder = PrometheusFormatter.builder().resultMediaType(requestedMediaType.get());
        scope(req).ifPresent(formatterBuilder::scope);
        metricName(req).ifPresent(formatterBuilder::meterName);

        try {
            MediaType resultMediaType = requestedMediaType.get();
            resp.status(Http.Status.OK_200);
            resp.headers().contentType(resultMediaType);
            resp.send(formatterBuilder.build().filteredOutput());
        } catch (Exception ex) {
            resp.status(Http.Status.INTERNAL_SERVER_ERROR_500);
            resp.send("Error preparing metrics output; " + ex.getMessage());
            logger().log(System.Logger.Level.ERROR, "Error preparing metrics output", ex);
        }
    }

    private Optional<String> scope(ServerRequest req) {
        return req.query().first("scope");
    }

    private Optional<String> metricName(ServerRequest req) {
        return req.query().first("name");
    }

    /**
     * Builder for the MP metrics feature.
     */
    public static class Builder extends HelidonFeatureSupport.Builder<Builder, MpMetricsFeature> {

        private static final String DEFAULT_WEB_CONTEXT = "/metrics";

        Builder() {
            super(DEFAULT_WEB_CONTEXT);
        }

        @Override
        public MpMetricsFeature build() {
            return new MpMetricsFeature(LOGGER, this, "MP-metrics");
        }
    }
}
