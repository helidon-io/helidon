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
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

class Http2ResetCancellationJmhRunnerTest {
    @Test
    void run() throws RunnerException {
        String benchmark = Pattern.quote(Http2ResetCancellationJmhBenchmark.class.getName());
        String include = System.getProperty("http2.reset.jmh.include",
                                            "^" + benchmark + ".resetConnectionWindowWaiters$");
        String result = System.getProperty("http2.reset.jmh.result",
                                           "target/http2-reset-cancellation-jmh.json");

        Options options = new OptionsBuilder()
                .include(include)
                .forks(Integer.getInteger("http2.reset.jmh.forks", 3))
                .threads(1)
                .warmupIterations(Integer.getInteger("http2.reset.jmh.warmupIterations", 1))
                .measurementIterations(Integer.getInteger("http2.reset.jmh.measurementIterations", 3))
                .shouldFailOnError(true)
                .resultFormat(ResultFormatType.JSON)
                .result(result)
                .build();
        new Runner(options).run();
    }
}
