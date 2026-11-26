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
package io.helidon.data.jdbc;

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class JdbcClientCreationJmhRunnerTest {

    @Test
    void callableValues() {
        JdbcCallableJmhBenchmark benchmark = new JdbcCallableJmhBenchmark();
        for (int count : new int[] {1, 8, 64}) {
            JdbcCallableJmhBenchmark.OutputState state = new JdbcCallableJmhBenchmark.OutputState();
            state.outputCount(count);
            state.setup();
            benchmark.registerOutputs(state);
            benchmark.execute(state);
            assertThat("Scoped lookup with " + count + " outputs", benchmark.lookupOutputs(state), is(count * (count + 1) / 2));
            JdbcClient.OutParameters outputs = benchmark.snapshotOutputs(state);
            for (int index = 0; index < count; index++) {
                String name = "output" + index;
                assertThat("Snapshot with " + count + " outputs at " + name, outputs.get(name, Integer.class), is(index + 1));
            }
        }
    }

    @Test
    void mappedRows() {
        JdbcCallableJmhBenchmark benchmark = new JdbcCallableJmhBenchmark();
        for (int count : new int[] {1, 128}) {
            JdbcCallableJmhBenchmark.RowState state = new JdbcCallableJmhBenchmark.RowState();
            state.rowCount(count);
            state.setup();
            List<Integer> expected = IntStream.range(0, count).boxed().toList();
            for (int execution = 1; execution <= 2; execution++) {
                String description = " with " + count + " rows on execution " + execution;
                assertThat("CALL" + description, benchmark.mapCallableRows(state), is(expected));
                assertThat("SELECT" + description, benchmark.mapQueryRows(state), is(expected));
                assertThat("Generated keys" + description, benchmark.mapGeneratedKeys(state), is(expected));
            }
        }
    }

    @Test
    void run() throws RunnerException {
        String include = System.getProperty("jdbc.client.creation.jmh.include",
                                            ".*JdbcClientCreationJmhBenchmark.*");
        int[] threadCounts = Arrays.stream(System.getProperty("jdbc.client.creation.jmh.threads",
                                                               "1,8,32,128")
                                                   .split(","))
                .map(String::trim)
                .mapToInt(Integer::parseInt)
                .toArray();

        for (int threadCount : threadCounts) {
            Options options = new OptionsBuilder()
                    .include(include)
                    .forks(Integer.getInteger("jdbc.client.creation.jmh.forks", 1))
                    .threads(threadCount)
                    .resultFormat(ResultFormatType.JSON)
                    .result("./target/jdbc-client-creation-jmh-result-t" + threadCount + ".json")
                    .warmupIterations(Integer.getInteger("jdbc.client.creation.jmh.warmupIterations", 3))
                    .warmupTime(TimeValue.milliseconds(
                            Long.getLong("jdbc.client.creation.jmh.warmupMillis", 500)))
                    .measurementIterations(Integer.getInteger("jdbc.client.creation.jmh.measurementIterations", 5))
                    .measurementTime(TimeValue.milliseconds(
                            Long.getLong("jdbc.client.creation.jmh.measurementMillis", 1000)))
                    .shouldFailOnError(true)
                    .build();

            new Runner(options).run();
        }
    }
}
