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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.infra.IterationParams;
import org.openjdk.jmh.results.AverageTimeResult;
import org.openjdk.jmh.results.BenchmarkResult;
import org.openjdk.jmh.results.IterationResult;
import org.openjdk.jmh.results.IterationResultMetaData;
import org.openjdk.jmh.results.ResultRole;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.results.ThroughputResult;
import org.openjdk.jmh.runner.IterationType;
import org.openjdk.jmh.runner.WorkloadParams;
import org.openjdk.jmh.runner.options.TimeValue;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ResourceLock(Resources.SYSTEM_PROPERTIES)
class JunitJmhResultTest {
    private static final String BENCHMARK_NAME = "io.helidon.benchmark.Transaction.commit";
    private static final String ERROR_MARGIN_PROPERTY = "webserver.jmh.errorMargin";

    private String originalErrorMargin;

    @BeforeEach
    void setErrorMargin() {
        originalErrorMargin = System.getProperty(ERROR_MARGIN_PROPERTY);
        System.setProperty(ERROR_MARGIN_PROPERTY, "15");
    }

    @AfterEach
    void restoreErrorMargin() {
        if (originalErrorMargin == null) {
            System.clearProperty(ERROR_MARGIN_PROPERTY);
        } else {
            System.setProperty(ERROR_MARGIN_PROPERTY, originalErrorMargin);
        }
    }

    @ParameterizedTest
    @CsvSource({
            "AverageTime, 10, 20, ns/op, false",
            "AverageTime, 10, 5, ns/op, true",
            "Throughput, 10, 20, ops/s, true",
            "Throughput, 10, 5, ops/s, false",
            "SampleTime, 10, 20, us/op, false",
            "SampleTime, 10, 5, us/op, true",
            "SingleShotTime, 10, 20, ms/op, false",
            "SingleShotTime, 10, 5, ms/op, true",
            "AverageTime, 10, 10, ns/op, true",
            "Throughput, 10, 10, ops/s, true",
            "AverageTime, 85, 100, ns/op, true",
            "AverageTime, 84.99, 100, ns/op, false",
            "Throughput, 115, 100, ops/s, true",
            "Throughput, 115.01, 100, ops/s, false"
    })
    void compareScores(Mode mode, double baseline, double current, String scoreUnit, boolean passes) {
        var histogram = new Histogram().add("Transaction.commit", baseline, current, mode, scoreUnit);
        var benchmark = histogram.benchmarks.getFirst();
        var runner = new JunitJmhRunnerTest();

        if (passes) {
            runner.renderResult(benchmark);
        } else {
            var error = assertThrows(AssertionError.class, () -> runner.renderResult(benchmark));
            assertThat(error.getMessage(), containsString("Transaction.commit regression detected"));
            assertThat(error.getMessage(), containsString(scoreUnit));
        }
    }

    @ParameterizedTest
    @CsvSource({
            "AverageTime, 10, 20, ns/op, -, ▒",
            "AverageTime, 10, 5, ns/op, +, █",
            "AverageTime, 10, 10.01, ns/op, -, ▒",
            "Throughput, 10, 20, ops/ms, +, █",
            "Throughput, 10, 5, ops/ms, -, ▒"
    })
    void renderUnitsAndDirection(Mode mode, double baseline, double current, String scoreUnit, String sign, String bar) {
        var histogram = new Histogram()
                .add("Other.largeThroughput", 1_000_000, 1_000_000, Mode.Throughput, "ops/s")
                .add("Transaction.commit", baseline, current, mode, scoreUnit);

        String rendered = histogram.benchmarks.getLast().render();

        assertThat(rendered, containsString("Transaction.commit (" + sign));
        assertThat(rendered, containsString(scoreUnit));
        assertThat(rendered, not(containsString("ops/s")));
        assertThat(rendered, containsString(bar.repeat(100)));
    }

    @ParameterizedTest
    @EnumSource(value = Mode.class, names = {"AverageTime", "Throughput"})
    void retainResultModeAndUnit(Mode mode) {
        RunResult result = runResult(mode);
        String unit = result.getPrimaryResult().getScoreUnit();
        Map<String, BaseLine> baselines = new HashMap<>();
        baselines.put(BENCHMARK_NAME, new BaseLine(BENCHMARK_NAME, 10, unit));

        var benchmark = Histogram.create(List.of(result), baselines).benchmarks.getFirst();

        assertThat(benchmark.mode(), is(mode));
        assertThat(benchmark.scoreUnit(), is(unit));
        assertThat(benchmark.baseLineScore(), is(10.0));
        assertThat(benchmark.currentScore(), is(20.0));
    }

    @ParameterizedTest
    @EnumSource(value = Mode.class, names = {"AverageTime", "Throughput"})
    void createMissingBaseline(Mode mode) {
        RunResult result = runResult(mode);
        Map<String, BaseLine> baselines = new HashMap<>();

        var benchmark = Histogram.create(List.of(result), baselines).benchmarks.getFirst();

        assertThat(benchmark.baseLineScore(), is(20.0));
        assertThat(baselines.get(BENCHMARK_NAME).getPrimaryMetric().getScoreUnit(), is(benchmark.scoreUnit()));
        new JunitJmhRunnerTest().renderResult(benchmark);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ops/s", "us/op"})
    void rejectIncompatibleBaselineUnit(String baselineUnit) {
        RunResult result = runResult(Mode.AverageTime);
        Map<String, BaseLine> baselines = new HashMap<>();
        baselines.put(BENCHMARK_NAME, new BaseLine(BENCHMARK_NAME, 10, baselineUnit));

        var error = assertThrows(IllegalArgumentException.class, () -> Histogram.create(List.of(result), baselines));

        assertThat(error.getMessage(), containsString(BENCHMARK_NAME));
        assertThat(error.getMessage(), containsString("baseline unit " + baselineUnit));
        assertThat(error.getMessage(), containsString("current unit ns/op"));
        assertThat(error.getMessage(), containsString("webserver.jmh.resetBaseline=true"));
    }

    private static RunResult runResult(Mode mode) {
        var warmup = new IterationParams(IterationType.WARMUP, 0, TimeValue.NONE, 1);
        var measurement = new IterationParams(IterationType.MEASUREMENT, 1, TimeValue.seconds(1), 1);
        var params = new BenchmarkParams(BENCHMARK_NAME, BENCHMARK_NAME, false, 1, new int[] {1}, List.of(),
                                         1, 0, warmup, measurement, mode, new WorkloadParams(), TimeUnit.NANOSECONDS, 1,
                                         "java", List.of(), "test", "test", "test", "test", TimeValue.seconds(1));
        var iteration = new IterationResult(params, measurement, new IterationResultMetaData(1, 1));
        if (mode == Mode.Throughput) {
            iteration.addResult(new ThroughputResult(ResultRole.PRIMARY, BENCHMARK_NAME, 20, 1, TimeUnit.NANOSECONDS));
        } else {
            iteration.addResult(new AverageTimeResult(ResultRole.PRIMARY, BENCHMARK_NAME, 1, 20, TimeUnit.NANOSECONDS));
        }
        return new RunResult(params, List.of(new BenchmarkResult(params, List.of(iteration))));
    }
}
