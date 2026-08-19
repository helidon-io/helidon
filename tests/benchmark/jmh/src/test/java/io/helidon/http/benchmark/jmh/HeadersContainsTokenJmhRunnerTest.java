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

package io.helidon.http.benchmark.jmh;

import org.junit.jupiter.api.Test;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

class HeadersContainsTokenJmhRunnerTest {
    @Test
    void run() throws RunnerException {
        String result = System.getProperty("headers.contains-token.jmh.result",
                                           "./target/headers-contains-token-jmh-result.json");
        Options options = new OptionsBuilder()
                .include(".*HeadersContainsTokenJmhBenchmark.*")
                .forks(3)
                .threads(1)
                .resultFormat(ResultFormatType.JSON)
                .result(result)
                .warmupIterations(5)
                .warmupTime(TimeValue.milliseconds(500))
                .measurementIterations(8)
                .measurementTime(TimeValue.seconds(1))
                .addProfiler(GCProfiler.class)
                .shouldFailOnError(true)
                .build();

        new Runner(options).run();
    }
}
