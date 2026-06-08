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

import org.junit.jupiter.api.Test;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

class RoutingJmhRunnerTest {
    @Test
    void run() throws RunnerException {
        String include = System.getProperty("routing.jmh.include", ".*RoutingJmhTest.*");
        String result = System.getProperty("routing.jmh.result", "./target/routing-jmh-result.json");

        Options options = new OptionsBuilder()
                .include(include)
                .forks(Integer.getInteger("routing.jmh.forks", 1))
                .threads(Integer.getInteger("routing.jmh.threads", 8))
                .resultFormat(ResultFormatType.JSON)
                .result(result)
                .warmupIterations(Integer.getInteger("routing.jmh.warmupIterations", 10))
                .warmupTime(TimeValue.milliseconds(Long.getLong("routing.jmh.warmupMillis", 1000)))
                .measurementIterations(Integer.getInteger("routing.jmh.measurementIterations", 8))
                .measurementTime(TimeValue.milliseconds(Long.getLong("routing.jmh.measurementMillis", 2000)))
                .shouldFailOnError(true)
                .build();

        new Runner(options).run();
    }
}
