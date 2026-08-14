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
        boolean contention = Boolean.getBoolean("webclient.target.jmh.contention");
        String include = contention
                ? "^" + benchmark
                        + "\\.(http1DistinctTargetPerThread|http1RotatingTargetsPerThread"
                        + "|http2DistinctTargetPerThread|http2RotatingTargetsPerThread)$"
                : System.getProperty("webclient.target.jmh.include",
                                     "^" + benchmark + "\\.(http1CacheHit|http1OneOff|http2CacheHit)$");
        String[] targetCounts = contention
                ? new String[] {"64"}
                : System.getProperty("webclient.target.jmh.targetCounts", "1,64").split(",");
        int[] threadCounts = contention
                ? new int[] {1, 2, 4, 8, 16}
                : new int[] {Integer.getInteger("webclient.target.jmh.threads", 1)};
        int forks = contention ? 3 : Integer.getInteger("webclient.target.jmh.forks", 1);
        String contentionResultPrefix = System.getProperty("webclient.target.jmh.contentionResultPrefix",
                                                           "target/webclient-target-cache-contention");

        for (int threadCount : threadCounts) {
            String result = contention
                    ? contentionResultPrefix + "-" + threadCount + "-threads.json"
                    : System.getProperty("webclient.target.jmh.result", "target/webclient-target-cache.json");
            var optionsBuilder = new OptionsBuilder()
                    .include(include)
                    .param("prefilledTargetCount", targetCounts)
                    .threads(threadCount)
                    .forks(forks)
                    .warmupIterations(Integer.getInteger("webclient.target.jmh.warmupIterations", 3))
                    .warmupTime(TimeValue.milliseconds(Long.getLong("webclient.target.jmh.warmupMillis", 500)))
                    .measurementIterations(Integer.getInteger("webclient.target.jmh.measurementIterations", 5))
                    .measurementTime(TimeValue.milliseconds(Long.getLong("webclient.target.jmh.measurementMillis", 1000)))
                    .timeUnit(TimeUnit.MICROSECONDS)
                    .addProfiler(GCProfiler.class)
                    .shouldFailOnError(true)
                    .resultFormat(ResultFormatType.JSON)
                    .result(result);
            String jvmArgument = System.getProperty("webclient.target.jmh.jvmArgument");
            if (jvmArgument != null) {
                optionsBuilder.jvmArgsAppend(jvmArgument);
            }
            Options options = optionsBuilder.build();
            new Runner(options).run();
        }
    }
}
