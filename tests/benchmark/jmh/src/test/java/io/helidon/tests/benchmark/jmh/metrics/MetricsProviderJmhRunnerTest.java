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

package io.helidon.tests.benchmark.jmh.metrics;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.not;

class MetricsProviderJmhRunnerTest {
    private static final String DEFAULT_INCLUDE = "^(" + Pattern.quote(MetricsProviderJmhBenchmark.class.getName())
            + "|" + Pattern.quote(MetricsRegistryJmhBenchmark.class.getName()) + ")\\..*$";
    private static final double DEFAULT_ERROR_MARGIN = 5D;
    private static final double DEFAULT_MAX_RELATIVE_ERROR = 2.5D;
    private static final List<String> DEFAULT_GATE_OPERATIONS = List.of(
            "CounterSingle",
            "CounterMulti",
            "CounterCreateRemoveSingle",
            "CounterCreateRemoveMulti",
            "FunctionalCounterSingle",
            "FunctionalCounterMulti",
            "SupplierGaugeSingle",
            "SupplierGaugeMulti",
            "FunctionGaugeSingle",
            "FunctionGaugeMulti",
            "DistributionSummaryPlainSingle",
            "DistributionSummaryPlainMulti",
            "DistributionSummaryPercentilesSingle",
            "DistributionSummaryPercentilesMulti",
            "DistributionSummaryPlainFreshVirtualThreadSingle",
            "DistributionSummaryPlainFreshVirtualThreadMulti",
            "DistributionSummaryPercentilesFreshVirtualThreadSingle",
            "DistributionSummaryPercentilesFreshVirtualThreadMulti",
            "DistributionSummaryHistogramSmallSingle",
            "DistributionSummaryHistogramSmallMulti",
            "DistributionSummaryHistogramLargeSingle",
            "DistributionSummaryHistogramLargeMulti",
            "DistributionSummaryHistogramDenseSingle",
            "DistributionSummaryHistogramDenseMulti",
            "TimerPlainSingle",
            "TimerPlainMulti",
            "TimerPercentilesSingle",
            "TimerPercentilesMulti",
            "TimerPlainFreshVirtualThreadSingle",
            "TimerPlainFreshVirtualThreadMulti",
            "TimerPercentilesFreshVirtualThreadSingle",
            "TimerPercentilesFreshVirtualThreadMulti",
            "TimerHistogramSmallSingle",
            "TimerHistogramSmallMulti",
            "TimerHistogramLargeSingle",
            "TimerHistogramLargeMulti",
            "TimerHistogramDenseSingle",
            "TimerHistogramDenseMulti",
            "GetOrCreateHitSingle",
            "GetOrCreateHitMulti",
            "LookupHitSingle",
            "LookupHitMulti",
            "DbMetricServiceCachedCounterSingle",
            "DbMetricServiceCachedCounterMulti",
            "DistributionSummarySnapshotSingle",
            "DistributionSummarySnapshotMulti",
            "TimerSnapshotSingle",
            "TimerSnapshotMulti",
            "PrometheusFormatAllSingle",
            "PrometheusFormatAllMulti",
            "PrometheusFormatFilteredSingle",
            "PrometheusFormatFilteredMulti",
            "OpenMetricsFormatAllSingle",
            "OpenMetricsFormatAllMulti",
            "JsonFormatAllSingle",
            "JsonFormatAllMulti",
            "JsonFormatFilteredSingle",
            "JsonFormatFilteredMulti"
    );
    private static final List<String> REGISTRY_GATE_OPERATIONS = List.of(
            "RegistryGetOrCreateHitSingle",
            "RegistryGetOrCreateHitMulti",
            "RegistryLookupHitSingle",
            "RegistryLookupHitMulti",
            "RegistryLookupMissSingle",
            "RegistryLookupMissMulti",
            "RegistryCreateRemoveSingle",
            "RegistryCreateRemoveMulti"
    );

    @Test
    void compareHelidonAndMicrometer() throws RunnerException {
        String include = System.getProperty("metrics.jmh.include", DEFAULT_INCLUDE);
        String result = System.getProperty("metrics.jmh.result", "./target/metrics-provider-jmh-result.json");
        double errorMargin = Double.parseDouble(System.getProperty("metrics.jmh.errorMargin",
                                                                   Double.toString(DEFAULT_ERROR_MARGIN)));
        double maxRelativeError = Double.parseDouble(System.getProperty("metrics.jmh.maxRelativeError",
                                                                        Double.toString(DEFAULT_MAX_RELATIVE_ERROR)));
        assertThat("Throughput tolerance must be a finite percentage", errorMargin,
                   allOf(greaterThan(0D), lessThan(100D)));
        assertThat("Maximum relative error must be positive and at most half the throughput tolerance", maxRelativeError,
                   allOf(greaterThan(0D), lessThanOrEqualTo(errorMargin / 2D)));

        Options options = new OptionsBuilder()
                .include(include)
                .forks(Integer.getInteger("metrics.jmh.forks", 1))
                .resultFormat(ResultFormatType.JSON)
                .result(result)
                .warmupIterations(Integer.getInteger("metrics.jmh.warmupIterations", 3))
                .warmupTime(TimeValue.milliseconds(Long.getLong("metrics.jmh.warmupMillis", 500)))
                .measurementIterations(Integer.getInteger("metrics.jmh.measurementIterations", 5))
                .measurementTime(TimeValue.milliseconds(Long.getLong("metrics.jmh.measurementMillis", 1000)))
                .shouldFailOnError(true)
                .build();

        Collection<RunResult> runResults = new Runner(options).run();
        Map<BenchmarkCase, Map<String, Score>> cases = scores(runResults);
        assertThat("Helidon/Micrometer benchmark cases", cases.entrySet(), not(empty()));
        for (Map.Entry<BenchmarkCase, Map<String, Score>> entry : cases.entrySet()) {
            Map<String, Score> scores = entry.getValue();
            List<String> operations = operations(include, entry.getKey().className(), scores.keySet());
            assertThat("Comparable Helidon/Micrometer JMH result pairs: " + entry.getKey() + scores.keySet(),
                       operations, not(empty()));
            for (String operation : operations) {
                compare(entry.getKey(), operation, scores, errorMargin, maxRelativeError);
            }
        }
    }

    private static Map<BenchmarkCase, Map<String, Score>> scores(Collection<RunResult> runResults) {
        Map<BenchmarkCase, Map<String, Score>> result = new LinkedHashMap<>();
        for (RunResult runResult : runResults) {
            String benchmark = runResult.getParams().getBenchmark();
            Map<String, String> parameters = new LinkedHashMap<>();
            for (String name : runResult.getParams().getParamsKeys()) {
                parameters.put(name, runResult.getParams().getParam(name));
            }
            var benchmarkCase = new BenchmarkCase(benchmark.substring(0, benchmark.lastIndexOf('.')),
                                                  Map.copyOf(parameters));
            Map<String, Score> scores = result.computeIfAbsent(benchmarkCase, _ -> new LinkedHashMap<>());
            String method = benchmark.substring(benchmark.lastIndexOf('.') + 1);
            Score previous = scores.put(method, new Score(runResult.getPrimaryResult().getScore(),
                                                         runResult.getPrimaryResult().getScoreError()));
            if (previous != null) {
                throw new IllegalStateException("Duplicate benchmark result: " + benchmarkCase + " " + method);
            }
        }
        return result;
    }

    private static List<String> operations(String include, String className, Set<String> benchmarkNames) {
        String configuredOperations = System.getProperty("metrics.jmh.operations");
        if (configuredOperations != null && !configuredOperations.isBlank()) {
            return Arrays.stream(configuredOperations.split(","))
                    .map(String::trim)
                    .filter(operation -> !operation.isEmpty())
                    .toList();
        }
        if (!DEFAULT_INCLUDE.equals(include)) {
            return discoveredOperations(benchmarkNames);
        }
        return className.equals(MetricsRegistryJmhBenchmark.class.getName())
                ? REGISTRY_GATE_OPERATIONS : DEFAULT_GATE_OPERATIONS;
    }

    private static List<String> discoveredOperations(Set<String> benchmarkNames) {
        Set<String> result = new LinkedHashSet<>();
        for (String benchmarkName : benchmarkNames) {
            if (benchmarkName.startsWith("helidon")) {
                result.add(benchmarkName.substring("helidon".length()));
            } else if (benchmarkName.startsWith("micrometer")) {
                result.add(benchmarkName.substring("micrometer".length()));
            }
        }
        return List.copyOf(result);
    }

    private static void compare(BenchmarkCase benchmarkCase,
                                String operation,
                                Map<String, Score> scores,
                                double errorMargin,
                                double maxRelativeError) {
        String helidonBenchmark = "helidon" + operation;
        String micrometerBenchmark = "micrometer" + operation;
        Score helidon = score(scores, helidonBenchmark);
        Score micrometer = score(scores, micrometerBenchmark);
        assertStable(benchmarkCase + " " + helidonBenchmark, helidon, maxRelativeError);
        assertStable(benchmarkCase + " " + micrometerBenchmark, micrometer, maxRelativeError);
        double minimumAllowed = (micrometer.score() + micrometer.error()) * ((100D - errorMargin) / 100D);

        assertThat(benchmarkCase + " " + helidonBenchmark + " throughput " + helidon
                           + " ops/s lower confidence bound must be within "
                           + errorMargin + "% of " + micrometerBenchmark + " throughput " + micrometer
                           + " ops/s upper confidence bound",
                   helidon.score() - helidon.error(), greaterThanOrEqualTo(minimumAllowed));
    }

    private static Score score(Map<String, Score> scores, String benchmark) {
        Score result = scores.get(benchmark);
        assertThat("JMH result for " + benchmark + "; actual results: " + scores.keySet(), result, notNullValue());
        return result;
    }

    private static void assertStable(String benchmark, Score score, double maxRelativeError) {
        assertThat(benchmark + " finite throughput", Double.isFinite(score.score()), is(true));
        assertThat(benchmark + " positive throughput", score.score(), greaterThan(0D));
        assertThat(benchmark + " finite confidence interval", Double.isFinite(score.error()), is(true));
        assertThat(benchmark + " non-negative confidence interval", score.error(), greaterThanOrEqualTo(0D));
        double relativeError = score.relativeError();
        assertThat(benchmark + " relative error percentage for score " + score,
                   relativeError, lessThanOrEqualTo(maxRelativeError));
    }

    private record BenchmarkCase(String className, Map<String, String> parameters) {
    }

    private record Score(double score, double error) {
        private double relativeError() {
            return error / score * 100D;
        }

        @Override
        public String toString() {
            return score + " +/- " + error;
        }
    }
}
