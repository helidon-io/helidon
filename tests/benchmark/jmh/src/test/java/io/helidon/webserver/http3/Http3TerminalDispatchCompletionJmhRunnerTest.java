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

package io.helidon.webserver.http3;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

class Http3TerminalDispatchCompletionJmhRunnerTest {
    private static final String BENCHMARK = Http3TerminalDispatchCompletionJmhBenchmark.class.getName();
    private static final Map<String, Integer> EXPECTED_GRID = Map.of(
            BENCHMARK + ".completedReceipt", 1,
            BENCHMARK + ".completedReceiptConcurrent", 8,
            BENCHMARK + ".delayedReceiptBlockingBaseline", 1,
            BENCHMARK + ".delayedReceiptBlockingBaselineConcurrent", 8,
            BENCHMARK + ".delayedReceiptContinuation", 1,
            BENCHMARK + ".delayedReceiptContinuationConcurrent", 8);

    @Test
    void run() throws RunnerException {
        String include = System.getProperty("http3.terminal.dispatch.jmh.include",
                                            ".*Http3TerminalDispatchCompletionJmhBenchmark.*");
        String result = System.getProperty("http3.terminal.dispatch.jmh.result",
                                           "./target/http3-terminal-dispatch-jmh-result.json");

        ChainedOptionsBuilder optionsBuilder = new OptionsBuilder()
                .include(include)
                .forks(Integer.getInteger("http3.terminal.dispatch.jmh.forks", 1))
                .resultFormat(ResultFormatType.JSON)
                .result(result)
                .warmupIterations(Integer.getInteger("http3.terminal.dispatch.jmh.warmupIterations", 3))
                .warmupTime(TimeValue.milliseconds(Long.getLong("http3.terminal.dispatch.jmh.warmupMillis", 500)))
                .measurementIterations(Integer.getInteger("http3.terminal.dispatch.jmh.measurementIterations", 5))
                .measurementTime(TimeValue.milliseconds(Long.getLong("http3.terminal.dispatch.jmh.measurementMillis", 1000)))
                .shouldFailOnError(true);

        String output = System.getProperty("http3.terminal.dispatch.jmh.output");
        if (output != null && !output.isBlank()) {
            optionsBuilder.output(output);
        }
        if (Boolean.getBoolean("http3.terminal.dispatch.jmh.gcProfiler")) {
            optionsBuilder.addProfiler(GCProfiler.class);
        }

        Options options = optionsBuilder.build();
        Collection<RunResult> results = new Runner(options).run();
        verifyGrid(include, results);
    }

    private static void verifyGrid(String include, Collection<RunResult> results) {
        Pattern includePattern = Pattern.compile(include);
        Set<String> expected = new HashSet<>();
        EXPECTED_GRID.forEach((benchmark, threads) -> {
            if (includePattern.matcher(benchmark).find()) {
                expected.add(cell(benchmark, threads));
            }
        });
        Set<String> observed = new HashSet<>();
        for (RunResult result : results) {
            String cell = cell(result.getParams().getBenchmark(), result.getParams().getThreads());
            if (!observed.add(cell)) {
                throw new IllegalStateException("Duplicate terminal dispatch JMH result: " + cell);
            }
        }
        if (!observed.equals(expected)) {
            throw new IllegalStateException("Unexpected terminal dispatch JMH grid: expected=" + expected
                                                    + ", observed=" + observed);
        }
    }

    private static String cell(String benchmark, int threads) {
        return benchmark + "@" + threads;
    }
}
