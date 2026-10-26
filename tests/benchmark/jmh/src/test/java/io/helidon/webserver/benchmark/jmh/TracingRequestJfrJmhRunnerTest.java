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

import org.junit.jupiter.api.Test;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

class TracingRequestJfrJmhRunnerTest {
    @Test
    void run() throws RunnerException {
        new Runner(new OptionsBuilder()
                           .include("^" + Pattern.quote(TracingRequestJfrJmhBenchmark.class.getName())
                                            + "\\.tracedKeepAliveExchange$")
                           .threads(1)
                           .forks(Integer.getInteger("tracing.request.jfr.jmh.forks", 3))
                           .warmupIterations(Integer.getInteger("tracing.request.jfr.jmh.warmupIterations", 5))
                           .warmupTime(TimeValue.seconds(1))
                           .measurementIterations(Integer.getInteger("tracing.request.jfr.jmh.measurementIterations",
                                                                     8))
                           .measurementTime(TimeValue.seconds(1))
                           .timeout(TimeValue.seconds(30))
                           .timeUnit(TimeUnit.MICROSECONDS)
                           .jvmArgsAppend("-Xms1g", "-Xmx1g")
                           .addProfiler(GCProfiler.class)
                           .shouldFailOnError(true)
                           .resultFormat(ResultFormatType.JSON)
                           .result(System.getProperty("tracing.request.jfr.jmh.result",
                                                      "target/tracing-request-jfr-jmh.json"))
                           .build()).run();
    }
}
