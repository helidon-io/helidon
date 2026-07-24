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

package io.helidon.webserver.graphql;

import org.junit.jupiter.api.Test;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

class GraphQlJmhRunnerTest {
    @Test
    void run() throws RunnerException {
        String include = System.getProperty("graphql.jmh.include", ".*GraphQl.*JmhBenchmark.*");
        String result = System.getProperty("graphql.jmh.result", "./target/graphql-jmh-result.json");

        ChainedOptionsBuilder optionsBuilder = new OptionsBuilder()
                .include(include)
                .forks(Integer.getInteger("graphql.jmh.forks", 1))
                .resultFormat(ResultFormatType.JSON)
                .result(result)
                .warmupIterations(Integer.getInteger("graphql.jmh.warmupIterations", 3))
                .warmupTime(TimeValue.milliseconds(Long.getLong("graphql.jmh.warmupMillis", 500)))
                .measurementIterations(Integer.getInteger("graphql.jmh.measurementIterations", 5))
                .measurementTime(TimeValue.milliseconds(Long.getLong("graphql.jmh.measurementMillis", 1000)))
                .shouldFailOnError(true);

        Integer threads = Integer.getInteger("graphql.jmh.threads");
        if (threads != null) {
            optionsBuilder.threads(threads);
        }

        Options options = optionsBuilder.build();

        new Runner(options).run();
    }
}
