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

import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import io.helidon.webclient.http1.Http1ClientRequestTargetBenchmark;
import io.helidon.webclient.http2.Http2ClientRequestTargetBenchmark;

import org.junit.jupiter.api.Test;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

class RequestTargetJmhRunnerTest {
    @Test
    void throughputAndAllocation() throws RunnerException {
        Options options = options()
                .mode(Mode.Throughput)
                .timeUnit(TimeUnit.SECONDS)
                .addProfiler(GCProfiler.class)
                .result("target/request-target-throughput.json")
                .build();
        new Runner(options).run();
    }

    @Test
    void sampleLatency() throws RunnerException {
        Options options = options()
                .mode(Mode.SampleTime)
                .timeUnit(TimeUnit.NANOSECONDS)
                .result("target/request-target-sample.json")
                .build();
        new Runner(options).run();
    }

    private static ChainedOptionsBuilder options() {
        return new OptionsBuilder()
                .include(exact(Http1ServerRequestTargetBenchmark.class, "parse"))
                .include(exact(Http2ServerRequestTargetBenchmark.class, "exchange"))
                .include(exact(Http1ClientRequestTargetBenchmark.class, "prologue"))
                .include(exact(Http2ClientRequestTargetBenchmark.class, "prepareHeaders"))
                .forks(3)
                .threads(1)
                .warmupIterations(5)
                .warmupTime(TimeValue.seconds(1))
                .measurementIterations(8)
                .measurementTime(TimeValue.seconds(1))
                .shouldFailOnError(true)
                .resultFormat(ResultFormatType.JSON);
    }

    private static String exact(Class<?> benchmarkClass, String method) {
        return "^" + Pattern.quote(benchmarkClass.getName() + "." + method) + "$";
    }
}
