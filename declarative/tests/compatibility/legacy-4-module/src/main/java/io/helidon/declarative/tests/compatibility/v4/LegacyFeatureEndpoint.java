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

package io.helidon.declarative.tests.compatibility.v4;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import io.helidon.common.Default;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Configuration;
import io.helidon.faulttolerance.Ft;
import io.helidon.http.HeaderNames;
import io.helidon.http.Http;
import io.helidon.http.HttpException;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.Metrics;
import io.helidon.service.registry.Service;
import io.helidon.tracing.Span;
import io.helidon.tracing.Tracing;
import io.helidon.validation.Validation;
import io.helidon.webserver.cors.Cors;
import io.helidon.webserver.http.RestServer;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

@SuppressWarnings("deprecation")
@RestServer.Endpoint
@Service.Singleton
@Cors.AllowOrigins("${legacy.cors.allow-origins:http://allowed.example}")
@Cors.AllowHeaders({"X-Legacy", "X-Client-Static"})
@Cors.AllowMethods({Method.GET_NAME, Method.POST_NAME})
@Cors.AllowCredentials
@Cors.MaxAgeSeconds(0)
@Metrics.Tag(key = "application", value = "Compatibility")
@Metrics.Tag(key = "endpoint", value = "LegacyFeatureEndpoint")
public class LegacyFeatureEndpoint implements LegacyHttpApi {
    private final String greeting;
    private final Supplier<ServerRequest> requestSupplier;
    private final AtomicInteger retryCalls = new AtomicInteger();
    private final AtomicInteger gaugeValue = new AtomicInteger();

    @Service.Inject
    public LegacyFeatureEndpoint(@Configuration.Value("legacy.greeting") @Default.Value("Bonjour") String greeting,
                                 Supplier<ServerRequest> requestSupplier) {
        this.greeting = greeting;
        this.requestSupplier = requestSupplier;
    }

    @Override
    public String hello(String name, String prefix, String header) {
        String requestPrefix = requestSupplier.get()
                .query()
                .first("prefix")
                .orElse(prefix);
        return greeting + " " + prefix + " " + name + " " + header + " " + requestPrefix;
    }

    @Override
    public String entity(String entity) {
        return "entity:" + entity;
    }

    @Override
    public Optional<String> optional() {
        return Optional.of("optional");
    }

    @Override
    @Ft.Fallback(value = "fallbackResult", applyOn = IllegalStateException.class)
    public String fallback() {
        throw new IllegalStateException("legacy failure");
    }

    String fallbackResult() {
        return "fallback";
    }

    @Override
    @Ft.Retry(calls = 2, delay = "PT0.01S", overallTimeout = "PT1S")
    public String retry() {
        int call = retryCalls.incrementAndGet();
        if (call % 2 == 1) {
            throw new IllegalStateException("retry " + call);
        }
        return "retry:" + call;
    }

    @Override
    @Ft.CircuitBreaker(name = "legacy-circuit", volume = 2, errorRatio = 50)
    public String circuit() {
        throw new HttpException("legacy circuit", Status.FORBIDDEN_403);
    }

    @Override
    @Ft.Timeout(time = "PT1S")
    public String timeout(Optional<Integer> sleepMillis) {
        try {
            Thread.sleep(sleepMillis.orElse(0));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "interrupted";
        }
        return "timeout";
    }

    @Override
    @Ft.Bulkhead(limit = 1, queueLength = 1)
    public String bulkhead() {
        return "bulkhead";
    }

    @Http.GET
    @Http.Path("/client-header")
    @Http.Produces(MediaTypes.TEXT_PLAIN_VALUE)
    public String clientHeader(@Http.HeaderParam("X-Client-Static") String header) {
        return header;
    }

    @Http.GET
    @Http.Path("/metrics/counted")
    @Http.Produces(MediaTypes.TEXT_PLAIN_VALUE)
    @Metrics.Counted(tags = @Metrics.Tag(key = "location", value = "method"))
    public String counted() {
        return "counted";
    }

    @Http.GET
    @Http.Path("/metrics/timed")
    @Http.Produces(MediaTypes.TEXT_PLAIN_VALUE)
    @Metrics.Timed(value = "legacy-timed", absoluteName = true)
    public String timed() {
        return "timed";
    }

    @Http.POST
    @Http.Path("/metrics/gauge")
    @Http.Consumes(MediaTypes.TEXT_PLAIN_VALUE)
    @Http.Produces(MediaTypes.TEXT_PLAIN_VALUE)
    public String gauge(@Http.Entity String value) {
        gaugeValue.set(Integer.parseInt(value));
        return "gauge:" + value;
    }

    @Metrics.Gauge(unit = Meter.BaseUnits.BYTES)
    public int gaugeValue() {
        return gaugeValue.get();
    }

    @Http.GET
    @Http.Path("/tracing/greet")
    @Http.Produces(MediaTypes.TEXT_PLAIN_VALUE)
    @Tracing.Traced(value = "legacy-4-traced",
                    tags = @Tracing.Tag(key = "module", value = "4"),
                    kind = Span.Kind.SERVER)
    public String traced(@Http.HeaderParam(HeaderNames.USER_AGENT_NAME) @Tracing.ParamTag String userAgent) {
        return "traced:" + userAgent;
    }

    @Http.OPTIONS
    @Http.Path("/cors")
    public void cors(ServerResponse response) {
        response.status(Status.OK_200);
    }

    public String validated(@Validation.String.NotBlank String value) {
        return "validated:" + value;
    }
}
