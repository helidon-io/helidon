/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Stream;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

public class JunitJmhRunnerTest {

    private static final String ERROR_MARGIN_PERCENT_DEFAULT = "15";
    private static final int ERROR_MARGIN =
            Integer.parseInt(System.getProperty("webserver.jmh.errorMargin", ERROR_MARGIN_PERCENT_DEFAULT));

    static Stream<Histogram.Benchmark> httpBenchmarks() throws RunnerException, IOException {
        Options opt = new OptionsBuilder()
                .include(".*JmhTest")
                .forks(1)
                .threads(8)
                .resultFormat(ResultFormatType.JSON)
                .result("./target/benchmark-result.json")
                .warmupIterations(10)
                .warmupTime(TimeValue.seconds(1))
                .measurementIterations(8)
                .measurementTime(TimeValue.seconds(2))
                .build();

        Collection<RunResult> runResults = new Runner(opt).run();

        boolean resetBaseline = Boolean.parseBoolean(System.getProperty("webserver.jmh.resetBaseline", "false"));

        File baseLineFile = Path.of("jmh-baseline.json").toFile();
        Map<String, BaseLine> baseLineMap;
        if (resetBaseline || !baseLineFile.exists()) {
            Files.deleteIfExists(baseLineFile.toPath());
            baseLineMap = BaseLine.load(Path.of("./target/benchmark-result.json"));
            Files.copy(Path.of("./target/benchmark-result.json"), baseLineFile.toPath());
        } else {
            baseLineMap = BaseLine.load(baseLineFile.toPath());
        }

        Histogram histogram = Histogram.create(runResults, baseLineMap);

        BaseLine.save(baseLineFile.toPath(), baseLineMap);

        return histogram.benchmarks.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("httpBenchmarks")
    void renderResult(Histogram.Benchmark benchmark) {
        String render = benchmark.render();

        double errMargin = (benchmark.currentScore() / 100) * ERROR_MARGIN;
        String errMarginDesc = String.format("%s%%(%.2f)", ERROR_MARGIN, errMargin);
        MatcherAssert.assertThat(
                "\n" + render + "\n" +
                        benchmark.name() + " regression detected. Error margin " + errMarginDesc,
                benchmark.currentScore() + errMargin,
                Matchers.greaterThanOrEqualTo(benchmark.baseLineScore()));

        System.out.println(render);

        if (benchmark.currentScore() < benchmark.baseLineScore()) {
            System.out.println("⚠ Still within the error margin " + errMarginDesc);
        }
    }
}
