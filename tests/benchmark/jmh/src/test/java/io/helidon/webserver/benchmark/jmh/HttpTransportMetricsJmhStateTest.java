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

package io.helidon.webserver.benchmark.jmh;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class HttpTransportMetricsJmhStateTest {
    @ParameterizedTest
    @CsvSource({
            "noop, false", "noop, true",
            "metrics, false", "metrics, true",
            "composed-metrics, false", "composed-metrics, true"
    })
    void http1StreamLifecycle(String observerMode, boolean percentiles) throws Exception {
        var state = new HttpTransportMetricsJmhTest.ObserverStreamState();
        state.observerMode = observerMode;
        state.percentiles = percentiles;
        try (AutoCloseable _ = state::tearDown) {
            state.setup();
            new HttpTransportMetricsJmhTest().observerHttp1StreamLifecycle(state);
        }
    }

    @ParameterizedTest
    @CsvSource({
            "absent, false", "absent, true",
            "disabled, false", "disabled, true",
            "enabled, false", "enabled, true"
    })
    void http2KeepAliveExchange(String metricsMode, boolean percentiles) throws Exception {
        var benchmark = new Http2TransportMetricsJmhBenchmark();
        benchmark.metricsMode = metricsMode;
        benchmark.expectHttp2Streams = true;
        benchmark.percentiles = percentiles;
        benchmark.setup();
        try (AutoCloseable _ = benchmark::tearDown) {
            benchmark.keepAliveExchange();
        }
    }
}
