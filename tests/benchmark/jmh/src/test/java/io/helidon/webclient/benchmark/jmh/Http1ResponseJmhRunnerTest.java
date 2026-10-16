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

class Http1ResponseJmhRunnerTest {
    @Test
    void runExactBenchmark() throws RunnerException {
        Options options = new OptionsBuilder()
                .include("^" + Pattern.quote(Http1ResponseJmhBenchmark.class.getName()) + "\\.response$")
                .param("responseMode", System.getProperty("http1.response.jmh.modes",
                                                         "FIXED_BUFFERED,FIXED_FLUSHED,CHUNKED_BUFFERED,CHUNKED_FLUSHED,"
                                                                 + "ADVERTISED_BUFFERED,ADVERTISED_FLUSHED,"
                                                                 + "UNADVERTISED_BUFFERED,UNADVERTISED_FLUSHED").split(","))
                .param("payloadSize", System.getProperty("http1.response.jmh.payloadSizes", "1024").split(","))
                .param("validateHeaders", System.getProperty("http1.response.jmh.validateHeaders", "false,true").split(","))
                .param("clientService", System.getProperty("http1.response.jmh.clientServices", "NONE").split(","))
                .forks(Integer.getInteger("http1.response.jmh.forks", 3))
                .threads(1)
                .mode(Mode.Throughput)
                .timeUnit(TimeUnit.SECONDS)
                .warmupIterations(Integer.getInteger("http1.response.jmh.warmupIterations", 3))
                .warmupTime(TimeValue.milliseconds(Long.getLong("http1.response.jmh.warmupMillis", 1_000)))
                .measurementIterations(Integer.getInteger("http1.response.jmh.measurementIterations", 5))
                .measurementTime(TimeValue.milliseconds(Long.getLong("http1.response.jmh.measurementMillis", 1_000)))
                .jvmArgsAppend("-Xms512m", "-Xmx512m")
                .addProfiler(GCProfiler.class)
                .shouldFailOnError(true)
                .resultFormat(ResultFormatType.JSON)
                .result(System.getProperty("http1.response.jmh.result", "target/http1-response-jmh.json"))
                .timeout(TimeValue.seconds(30))
                .build();
        new Runner(options).run();
    }
}
