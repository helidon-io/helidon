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

import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.openjdk.jmh.annotations.Mode;
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
        boolean altSvcMatched = Boolean.getBoolean("webclient.target.jmh.altSvcMatched");
        if (contention && altSvcMatched) {
            throw new IllegalArgumentException("Contention and matched Alt-Svc modes are mutually exclusive");
        }
        String include = include(benchmark, contention, altSvcMatched);
        WebClientConnectionTargetBenchmark.ExpectedImplementation expectedImplementation = altSvcMatched
                ? expectedImplementation()
                : WebClientConnectionTargetBenchmark.ExpectedImplementation.BASE;
        String[] targetCounts = contention
                ? new String[] {"64"}
                : System.getProperty("webclient.target.jmh.targetCounts", "1,64").split(",");
        String[] proxyModes = System.getProperty("webclient.target.jmh.proxyModes", "NONE").split(",");
        int[] threadCounts = contention
                ? new int[] {1, 2, 4, 8, 16}
                : altSvcMatched
                        ? threadCounts("webclient.target.jmh.altSvcMatchedThreads", "1,8")
                        : new int[] {Integer.getInteger("webclient.target.jmh.threads", 1)};
        int forks = contention
                ? 3
                : altSvcMatched
                        ? Integer.getInteger("webclient.target.jmh.altSvcMatchedForks", 3)
                        : Integer.getInteger("webclient.target.jmh.forks", 1);
        String contentionResultPrefix = System.getProperty("webclient.target.jmh.contentionResultPrefix",
                                                           "target/webclient-target-cache-contention");
        String altSvcMatchedResultPrefix = System.getProperty("webclient.target.jmh.altSvcMatchedResultPrefix",
                                                              "target/webclient-alt-svc-matched");

        for (int threadCount : threadCounts) {
            String result = contention
                    ? contentionResultPrefix + "-" + threadCount + "-threads.json"
                    : altSvcMatched
                            ? altSvcMatchedResultPrefix + "-" + expectedImplementation.name().toLowerCase(Locale.ROOT)
                                    + "-" + threadCount + "-threads.json"
                            : System.getProperty("webclient.target.jmh.result", "target/webclient-target-cache.json");
            var optionsBuilder = new OptionsBuilder()
                    .include(include)
                    .threads(threadCount)
                    .forks(forks)
                    .mode(Mode.valueOf(System.getProperty("webclient.target.jmh.mode", "AverageTime")))
                    .warmupIterations(Integer.getInteger("webclient.target.jmh.warmupIterations", 3))
                    .warmupTime(TimeValue.milliseconds(Long.getLong("webclient.target.jmh.warmupMillis", 500)))
                    .measurementIterations(Integer.getInteger("webclient.target.jmh.measurementIterations", 5))
                    .measurementTime(
                            TimeValue.milliseconds(Long.getLong("webclient.target.jmh.measurementMillis", 1000)))
                    .timeUnit(TimeUnit.MICROSECONDS)
                    .addProfiler(GCProfiler.class)
                    .shouldFailOnError(true)
                    .resultFormat(ResultFormatType.JSON)
                    .result(result);
            if (altSvcMatched) {
                optionsBuilder.param("expectedImplementation", expectedImplementation.name())
                        .param("scenario", scenarios(expectedImplementation));
            } else {
                optionsBuilder.param("prefilledTargetCount", targetCounts)
                        .param("proxyMode", proxyModes);
            }
            String jvmArgument = System.getProperty("webclient.target.jmh.jvmArgument");
            if (jvmArgument != null) {
                optionsBuilder.jvmArgsAppend(jvmArgument);
            }
            Options options = optionsBuilder.build();
            new Runner(options).run();
        }
    }

    private static String include(String benchmark, boolean contention, boolean altSvcMatched) {
        if (contention) {
            return "^" + benchmark
                    + "\\.(http1DistinctTargetPerThread|http1RotatingTargetsPerThread"
                    + "|http2DistinctTargetPerThread|http2RotatingTargetsPerThread)$";
        }
        if (altSvcMatched) {
            return "^" + benchmark + "\\.altSvcMatched$";
        }
        return System.getProperty("webclient.target.jmh.include",
                                  "^" + benchmark + "\\.(http1CacheHit|http1OneOff|http2CacheHit)$");
    }

    private static WebClientConnectionTargetBenchmark.ExpectedImplementation expectedImplementation() {
        String value = System.getProperty("webclient.target.jmh.altSvcExpected");
        if (value == null) {
            throw new IllegalArgumentException("Matched Alt-Svc mode requires webclient.target.jmh.altSvcExpected");
        }
        try {
            return WebClientConnectionTargetBenchmark.ExpectedImplementation.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("webclient.target.jmh.altSvcExpected must be BASE or HEAD", e);
        }
    }

    private static String[] scenarios(
            WebClientConnectionTargetBenchmark.ExpectedImplementation expectedImplementation) {
        String configured = System.getProperty("webclient.target.jmh.altSvcMatchedScenarios");
        if (configured != null) {
            return configured.split(",");
        }
        if (expectedImplementation == WebClientConnectionTargetBenchmark.ExpectedImplementation.BASE) {
            return new String[] {
                    "TLS_H1",
                    "TLS_H2",
                    "DIRECT_H2_ALTERNATIVE",
                    "ENABLED_NO_ENTRY",
                    "DIRECT_H2_ALTERNATIVE_REPEATED_AD",
                    "DISABLED_CAPTURE"
            };
        }
        return new String[] {
                "TLS_H1",
                "TLS_H2",
                "DIRECT_H2_ALTERNATIVE",
                "ENABLED_NO_ENTRY",
                "ACTIVE",
                "DIRECT_H2_ALTERNATIVE_REPEATED_AD",
                "ACTIVE_REPEATED_AD",
                "DISABLED_CAPTURE"
        };
    }

    private static int[] threadCounts(String property, String defaultValue) {
        return Arrays.stream(System.getProperty(property, defaultValue).split(","))
                .mapToInt(Integer::parseInt)
                .toArray();
    }
}
