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

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import io.helidon.data.jdbc.JdbcRowMappingJmhBenchmark.NamedRow;
import io.helidon.data.jdbc.JdbcRowMappingJmhBenchmark.RowState;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class JdbcCallableBenchmarkTest {

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
    void mapsNamedRowsAndKeys() {
        JdbcRowMappingJmhBenchmark benchmark = new JdbcRowMappingJmhBenchmark();
        RowState state = new RowState();
        state.mapping("named");
        state.rowCount(2);
        state.setup();
        List<NamedRow> expected = List.of(new NamedRow(0, "bench", 47L), new NamedRow(1, "bench", 47L));
        assertThat("Named SELECT rows", benchmark.select(state), is(expected));
        assertThat("Named generated keys", benchmark.generatedKeys(state), is(expected));
    }

    @Test
    void mapsSingularCalls() {
        JdbcCallableJmhBenchmark benchmark = new JdbcCallableJmhBenchmark();
        for (String channel : List.of("direct", "cursor")) {
            JdbcCallableJmhBenchmark.SingularState state = new JdbcCallableJmhBenchmark.SingularState();
            for (String terminal : List.of("OptionalEmpty", "OptionalOne", "One")) {
                state.workload(channel + terminal);
                state.setup();
                Object expected = switch (terminal) {
                case "OptionalEmpty" -> Optional.empty();
                case "OptionalOne" -> Optional.of(0);
                default -> 0;
                };
                assertThat(channel + terminal, benchmark.mapSingularCall(state), is(expected));
            }
        }
    }
}
