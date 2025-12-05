/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.declarative.tests.metrics;

import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.http.Http;
import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.Metrics;
import io.helidon.service.registry.Service;
import io.helidon.webserver.http.RestServer;

@SuppressWarnings("deprecation")
@RestServer.Endpoint
@Http.Path("/endpoint")
@Service.Singleton
@Metrics.Tag(key = "endpoint", value = "TestEndpoint")
@Metrics.Tag(key = "application", value = "MyNiceApp")
class TestEndpoint {
    private final MeterRegistry meterRegistry;
    private final AtomicInteger gaugeValue = new AtomicInteger();

    @Service.Inject
    TestEndpoint(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Http.GET
    @Http.Path("/counted")
    @Metrics.Counted(tags = @Metrics.Tag(key = "location", value = "method"))
    String counted() {
        return "counted";
    }

    @Http.GET
    @Http.Path("/timed")
    @Metrics.Timed(value = "my-timed-metric", absoluteName = true)
    String timed() {
        return "timed";
    }

    @Http.GET
    @Http.Path("/time")
    String time() {
        return "time: " + meterRegistry.clock().monotonicTime();
    }

    @Http.POST
    @Http.Path("/gauge")
    String gauge(@Http.Entity String value) {
        gaugeValue.set(Integer.parseInt(value));
        return "gauge set";
    }

    @Metrics.Gauge(unit = Meter.BaseUnits.BYTES)
    int gaugeValue() {
        return gaugeValue.get();
    }
}
