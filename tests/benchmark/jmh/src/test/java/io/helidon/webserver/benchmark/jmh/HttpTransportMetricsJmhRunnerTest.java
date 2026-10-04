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

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

class HttpTransportMetricsJmhRunnerTest {
    @Test
    void runDisabled() throws RunnerException {
        String benchmark = Pattern.quote(HttpTransportMetricsJmhTest.class.getName());
        String include = System.getProperty(
                "http.transport.metrics.jmh.disabled.include",
                "^" + benchmark
                        + "\\.(disabledHttp1KeepAliveExchange"
                        + "|disabledHttp1ConnectionLifecycle)$");
        String result = System.getProperty("http.transport.metrics.jmh.disabled.result",
                                           "target/http-transport-metrics-disabled-jmh.json");
        new Runner(options(include, result).build()).run();
    }

    @Test
    void runRecorder() throws RunnerException {
        String benchmark = Pattern.quote(HttpTransportMetricsJmhTest.class.getName());
        String include = System.getProperty(
                "http.transport.metrics.jmh.recorder.include",
                "^" + benchmark
                        + "\\.(observerHttp1StreamLifecycle"
                        + "|observerHttp1ConnectionLifecycle"
                        + "|observerTlsHttp1ConnectionLifecycle)$");
        String result = System.getProperty("http.transport.metrics.jmh.recorder.result",
                                           "target/http-transport-metrics-recorder-jmh.json");
        String[] observerModes = Arrays.stream(System.getProperty("http.transport.metrics.jmh.observerModes", "noop")
                                                       .split(","))
                .map(String::trim)
                .filter(mode -> !mode.isEmpty())
                .toArray(String[]::new);
        if (observerModes.length == 0) {
            throw new IllegalArgumentException("At least one HTTP transport metrics observer mode is required");
        }
        new Runner(options(include, result)
                           .param("observerMode", observerModes)
                           .build()).run();
    }

    private static ChainedOptionsBuilder options(String include, String result) {
        return new OptionsBuilder()
                .include(include)
                .forks(Integer.getInteger("http.transport.metrics.jmh.forks", 3))
                .threads(1)
                .mode(Mode.AverageTime)
                .timeUnit(TimeUnit.NANOSECONDS)
                .warmupIterations(Integer.getInteger("http.transport.metrics.jmh.warmupIterations", 5))
                .warmupTime(TimeValue.milliseconds(Long.getLong("http.transport.metrics.jmh.warmupMillis", 1_000)))
                .measurementIterations(Integer.getInteger("http.transport.metrics.jmh.measurementIterations", 8))
                .measurementTime(TimeValue.milliseconds(Long.getLong("http.transport.metrics.jmh.measurementMillis", 1_000)))
                .addProfiler(GCProfiler.class)
                .shouldFailOnError(true)
                .resultFormat(ResultFormatType.JSON)
                .result(result);
    }
}
