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

package io.helidon.webclient.benchmark.jmh;

import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

class HttpRedirectJmhRunnerTest {
    private static final String METHODS = "(http1PutOutputStreamRedirect|http2PutOutputStreamRedirect)";

    @Test
    void runExactBenchmarks() throws RunnerException {
        String result = System.getProperty("http.redirect.jmh.result", "target/http-redirect-jmh.json");
        String include = System.getProperty(
                "http.redirect.jmh.include",
                "^" + Pattern.quote(HttpRedirectJmhBenchmark.class.getName()) + "\\." + METHODS + "$");

        Options options = new OptionsBuilder()
                .include(include)
                .threads(1)
                .forks(Integer.getInteger("http.redirect.jmh.forks", 3))
                .warmupIterations(Integer.getInteger("http.redirect.jmh.warmupIterations", 3))
                .warmupTime(TimeValue.milliseconds(Long.getLong("http.redirect.jmh.warmupMillis", 500)))
                .measurementIterations(Integer.getInteger("http.redirect.jmh.measurementIterations", 5))
                .measurementTime(TimeValue.milliseconds(Long.getLong("http.redirect.jmh.measurementMillis", 1_000)))
                .mode(Mode.AverageTime)
                .timeUnit(TimeUnit.MICROSECONDS)
                .addProfiler(GCProfiler.class)
                .shouldFailOnError(true)
                .resultFormat(ResultFormatType.JSON)
                .result(result)
                .timeout(TimeValue.seconds(Long.getLong("http.redirect.jmh.timeoutSeconds", 30)))
                .build();
        new Runner(options).run();
    }
}
