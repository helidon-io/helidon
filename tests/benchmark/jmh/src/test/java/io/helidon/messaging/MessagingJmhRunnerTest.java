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

package io.helidon.messaging;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.results.Result;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

class MessagingJmhRunnerTest {
    private static final String PROPERTY_PREFIX = "messaging.jmh.";
    private static final String SATURATION_MESSAGING_PREFIX =
            MessagingSaturationJmhBenchmark.class.getName() + ".messaging";

    @Test
    void runtime() throws RunnerException {
        RunSettings settings = RunSettings.create();
        String result = System.getProperty(PROPERTY_PREFIX + "runtimeResult",
                                           "target/messaging-runtime-jmh-result.json");
        Options options = options(MessagingRuntimeJmhBenchmark.class, result, "runtimeInclude", settings)
                .threads(1)
                .addProfiler(GCProfiler.class)
                .build();

        new Runner(options).run();
    }

    @Test
    void saturation() throws RunnerException {
        RunSettings settings = RunSettings.create();
        String resultPrefix = System.getProperty(PROPERTY_PREFIX + "saturationResultPrefix",
                                                 "target/messaging-saturation-jmh-result");
        for (int threads : saturationThreads()) {
            Options options = options(MessagingSaturationJmhBenchmark.class,
                                      resultPrefix + "-t" + threads + ".json",
                                      "saturationInclude",
                                      settings)
                    .threads(threads)
                    .addProfiler(MessagingSaturationJmhBenchmark.SaturationProfiler.class.getName())
                    .build();

            verifySaturationCounters(new Runner(options).run());
        }
    }

    private static void verifySaturationCounters(Collection<RunResult> results) {
        for (RunResult result : results) {
            if (!result.getParams().getBenchmark().startsWith(SATURATION_MESSAGING_PREFIX)) {
                continue;
            }
            Map<String, Result> counters = result.getSecondaryResults();
            Result deliveries = counters.get("deliveries");
            Result retries = counters.get("saturatedRetries");
            if (deliveries == null || deliveries.getScore() <= 0 || retries == null || retries.getScore() < 0) {
                throw new IllegalStateException("Missing or invalid messaging saturation counters for "
                                                        + result.getParams().getBenchmark());
            }
            long measuredOperations = result.getBenchmarkResults()
                    .stream()
                    .flatMap(benchmarkResult -> benchmarkResult.getIterationResults().stream())
                    .mapToLong(iterationResult -> iterationResult.getMetadata().getMeasuredOps())
                    .sum();
            if (deliveries.getScore() != measuredOperations) {
                throw new IllegalStateException("Messaging delivery counter does not match measured operations for "
                                                        + result.getParams().getBenchmark()
                                                        + ": " + deliveries.getScore() + " != " + measuredOperations);
            }
        }
    }

    private static ChainedOptionsBuilder options(Class<?> benchmarkClass,
                                                 String result,
                                                 String includeProperty,
                                                 RunSettings settings) {
        String configuredInclude = System.getProperty(PROPERTY_PREFIX + includeProperty, ".*");
        String heap = System.getProperty(PROPERTY_PREFIX + "heap", "1g");
        String benchmarkPrefix = Pattern.quote(benchmarkClass.getName()) + "\\.";
        String include = "^(?=" + benchmarkPrefix + ")(?:" + configuredInclude + ")$";
        return new OptionsBuilder()
                .include(include)
                .forks(settings.forks())
                .jvmArgsAppend("-Xms" + heap, "-Xmx" + heap)
                .resultFormat(ResultFormatType.JSON)
                .result(result)
                .warmupIterations(settings.warmupIterations())
                .warmupTime(TimeValue.milliseconds(settings.warmupMillis()))
                .measurementIterations(settings.measurementIterations())
                .measurementTime(TimeValue.milliseconds(settings.measurementMillis()))
                .shouldFailOnError(true);
    }

    private static int[] saturationThreads() {
        String configuredThreads = System.getProperty(PROPERTY_PREFIX + "saturationThreads", "1,8,32");
        int[] result = Arrays.stream(configuredThreads.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .mapToInt(Integer::parseInt)
                .toArray();
        if (result.length == 0) {
            throw new IllegalArgumentException(PROPERTY_PREFIX + "saturationThreads must not be empty");
        }
        for (int threads : result) {
            if (threads < 1) {
                throw new IllegalArgumentException(PROPERTY_PREFIX
                                                           + "saturationThreads must contain positive integers");
            }
        }
        return result;
    }

    private record RunSettings(int forks,
                               int warmupIterations,
                               long warmupMillis,
                               int measurementIterations,
                               long measurementMillis) {
        private static RunSettings create() {
            boolean smoke = Boolean.getBoolean(PROPERTY_PREFIX + "smoke");
            int defaultForks = smoke ? 1 : 3;
            int defaultWarmupIterations = smoke ? 1 : 5;
            long defaultWarmupMillis = smoke ? 100 : 1000;
            int defaultMeasurementIterations = smoke ? 1 : 8;
            long defaultMeasurementMillis = smoke ? 100 : 1000;
            return new RunSettings(Integer.getInteger(PROPERTY_PREFIX + "forks", defaultForks),
                                   Integer.getInteger(PROPERTY_PREFIX + "warmupIterations", defaultWarmupIterations),
                                   Long.getLong(PROPERTY_PREFIX + "warmupMillis", defaultWarmupMillis),
                                   Integer.getInteger(PROPERTY_PREFIX + "measurementIterations",
                                                      defaultMeasurementIterations),
                                   Long.getLong(PROPERTY_PREFIX + "measurementMillis", defaultMeasurementMillis));
        }
    }
}
