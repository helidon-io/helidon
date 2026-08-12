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

import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

class ResponseFilterJmhRunnerTest {
    @Test
    void run() throws RunnerException {
        String benchmark = Pattern.quote(ResponseFilterJmhBenchmark.class.getName());
        String include = System.getProperty("response.filter.jmh.include", "^" + benchmark + ".*$");
        String result = System.getProperty("response.filter.jmh.result", "target/response-filter-jmh.json");

        Options options = new OptionsBuilder()
                .include(include)
                .forks(Integer.getInteger("response.filter.jmh.forks", 3))
                .threads(Integer.getInteger("response.filter.jmh.threads", 1))
                .warmupIterations(Integer.getInteger("response.filter.jmh.warmupIterations", 3))
                .warmupTime(TimeValue.milliseconds(Long.getLong("response.filter.jmh.warmupMillis", 1000)))
                .measurementIterations(Integer.getInteger("response.filter.jmh.measurementIterations", 5))
                .measurementTime(TimeValue.milliseconds(Long.getLong("response.filter.jmh.measurementMillis", 2000)))
                .addProfiler(GCProfiler.class)
                .shouldFailOnError(true)
                .resultFormat(ResultFormatType.JSON)
                .result(result)
                .build();
        new Runner(options).run();
    }
}
