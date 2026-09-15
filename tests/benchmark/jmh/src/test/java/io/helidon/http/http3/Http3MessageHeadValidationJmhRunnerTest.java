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

package io.helidon.http.http3;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

class Http3MessageHeadValidationJmhRunnerTest {
    private static final String PREFIX = "http3.message.head.validation.jmh.";
    private static final String BENCHMARK = Http3MessageHeadValidationJmhBenchmark.class.getName();

    @Test
    void run() throws RunnerException {
        String include = System.getProperty(PREFIX + "include",
                                            "^" + Pattern.quote(BENCHMARK) + "\\.(readRequestHead|readResponseHead)$");
        String[] headerCounts = System.getProperty(PREFIX + "headerCount", "4,32").split(",");
        String[] valueSizes = System.getProperty(PREFIX + "valueSize", "16,256").split(",");
        int threads = Integer.getInteger(PREFIX + "threads", 1);
        boolean gcProfiler = Boolean.parseBoolean(System.getProperty(PREFIX + "gcProfiler", "true"));
        ChainedOptionsBuilder optionsBuilder = new OptionsBuilder()
                .include(include)
                .forks(Integer.getInteger(PREFIX + "forks", 1))
                .threads(threads)
                .param("headerCount", headerCounts)
                .param("valueSize", valueSizes)
                .resultFormat(ResultFormatType.JSON)
                .result(System.getProperty(PREFIX + "result", "./target/http3-message-head-validation-jmh-1.json"))
                .warmupIterations(Integer.getInteger(PREFIX + "warmupIterations", 3))
                .warmupTime(TimeValue.milliseconds(Long.getLong(PREFIX + "warmupMillis", 500)))
                .measurementIterations(Integer.getInteger(PREFIX + "measurementIterations", 5))
                .measurementTime(TimeValue.milliseconds(Long.getLong(PREFIX + "measurementMillis", 1000)))
                .shouldFailOnError(true);
        String output = System.getProperty(PREFIX + "output");
        if (output != null && !output.isBlank()) {
            optionsBuilder.output(output);
        }
        if (gcProfiler) {
            optionsBuilder.addProfiler(GCProfiler.class);
        }
        Collection<RunResult> results = new Runner(optionsBuilder.build()).run();
        verifyGrid(include, headerCounts, valueSizes, threads, gcProfiler, results);
    }

    private static void verifyGrid(String include,
                                   String[] headerCounts,
                                   String[] valueSizes,
                                   int threads,
                                   boolean gcProfiler,
                                   Collection<RunResult> results) {
        Pattern includePattern = Pattern.compile(include);
        Set<String> expected = new HashSet<>();
        for (String method : List.of("readRequestHead", "readResponseHead")) {
            String benchmark = BENCHMARK + "." + method;
            if (includePattern.matcher(benchmark).find()) {
                for (String headerCount : headerCounts) {
                    for (String valueSize : valueSizes) {
                        expected.add(cell(benchmark, headerCount, valueSize, threads));
                    }
                }
            }
        }
        Set<String> observed = new HashSet<>();
        for (RunResult result : results) {
            String cell = cell(result.getParams().getBenchmark(),
                               result.getParams().getParam("headerCount"),
                               result.getParams().getParam("valueSize"),
                               result.getParams().getThreads());
            if (!observed.add(cell)) {
                throw new IllegalStateException("Duplicate HTTP/3 message-head JMH result: " + cell);
            }
            if (gcProfiler && !result.getSecondaryResults().containsKey("gc.alloc.rate.norm")) {
                throw new IllegalStateException("Missing allocated bytes per operation for " + cell);
            }
        }
        if (!observed.equals(expected)) {
            throw new IllegalStateException("Unexpected HTTP/3 message-head JMH grid: expected=" + expected
                                                    + ", observed=" + observed);
        }
    }

    private static String cell(String benchmark, String headerCount, String valueSize, int threads) {
        return benchmark + "[headers=" + headerCount + ",valueSize=" + valueSize + "]@" + threads;
    }
}
