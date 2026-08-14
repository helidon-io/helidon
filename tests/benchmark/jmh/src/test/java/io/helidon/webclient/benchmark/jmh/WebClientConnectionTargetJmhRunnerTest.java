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

import org.junit.jupiter.api.Test;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

class WebClientConnectionTargetJmhRunnerTest {
    @Test
    void runExactBenchmark() throws RunnerException {
        String benchmark = WebClientConnectionTargetBenchmark.class.getName();
        var optionsBuilder = new OptionsBuilder()
                .include(System.getProperty("webclient.target.jmh.include",
                                            "^" + benchmark + "\\.(http1CacheHit|http1OneOff|http2CacheHit)$"))
                .param("prefilledTargetCount",
                       System.getProperty("webclient.target.jmh.targetCounts", "1,64").split(","))
                .threads(Integer.getInteger("webclient.target.jmh.threads", 1))
                .forks(Integer.getInteger("webclient.target.jmh.forks", 1))
                .warmupIterations(Integer.getInteger("webclient.target.jmh.warmupIterations", 3))
                .warmupTime(TimeValue.milliseconds(Long.getLong("webclient.target.jmh.warmupMillis", 500)))
                .measurementIterations(Integer.getInteger("webclient.target.jmh.measurementIterations", 5))
                .measurementTime(TimeValue.milliseconds(Long.getLong("webclient.target.jmh.measurementMillis", 1000)))
                .timeUnit(TimeUnit.MICROSECONDS)
                .addProfiler(GCProfiler.class)
                .shouldFailOnError(true)
                .resultFormat(ResultFormatType.JSON)
                .result(System.getProperty("webclient.target.jmh.result", "target/webclient-target-cache.json"));
        String jvmArgument = System.getProperty("webclient.target.jmh.jvmArgument");
        if (jvmArgument != null) {
            optionsBuilder.jvmArgsAppend(jvmArgument);
        }
        Options options = optionsBuilder.build();
        new Runner(options).run();
    }
}
