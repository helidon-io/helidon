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

package io.helidon.webserver.observe.telemetry.metrics;

import java.util.Map;

import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.Timer;
import io.helidon.service.registry.Service;
import io.helidon.webserver.http.FilterChain;
import io.helidon.webserver.http.RoutingRequest;
import io.helidon.webserver.http.RoutingResponse;
import io.helidon.webserver.observe.metrics.MetricsHttpSemanticConventions;

@Service.Singleton
class OpenTelemetryMetricsHttpSemanticConventions implements MetricsHttpSemanticConventions {

    private final MeterRegistry meterRegistry;

    @Service.Inject
    OpenTelemetryMetricsHttpSemanticConventions(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void filter(FilterChain chain, RoutingRequest req, RoutingResponse res) {
//        if (!measureRequest(req)) {
//            chain.proceed();
//            return;
//        }
        var startTime = System.nanoTime();
        Exception exception;
        try {
            chain.proceed();
        } catch (Exception e) {
            exception = e;
        } finally {


        }
    }
}
