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

package io.helidon.http.http3.qpack;

import org.junit.jupiter.api.Test;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

class HttpStaticTableJmhRunnerTest {
    @Test
    void run() throws RunnerException {
        var builder = new OptionsBuilder()
                .include("^io\\.helidon\\.http\\.http3\\.qpack\\.HttpStaticTableJmhBenchmark\\.(?:"
                                 + System.getProperty("http.static.table.jmh.methods", ".*") + ")$")
                .forks(Integer.getInteger("http.static.table.jmh.forks", 3))
                .threads(Integer.getInteger("http.static.table.jmh.threads", 1))
                .warmupIterations(Integer.getInteger("http.static.table.jmh.warmupIterations", 3))
                .warmupTime(TimeValue.milliseconds(Long.getLong("http.static.table.jmh.warmupMillis", 1000)))
                .measurementIterations(Integer.getInteger("http.static.table.jmh.measurementIterations", 5))
                .measurementTime(TimeValue.milliseconds(Long.getLong("http.static.table.jmh.measurementMillis", 1000)))
                .addProfiler(GCProfiler.class)
                .resultFormat(ResultFormatType.JSON)
                .result(System.getProperty("http.static.table.jmh.result", "target/http-static-table-jmh-1.json"))
                .shouldFailOnError(true);

        String workload = System.getProperty("http.static.table.jmh.workload");
        if (workload != null && !workload.isBlank()) {
            builder.param("workload", workload.split(","));
        }
        String output = System.getProperty("http.static.table.jmh.output");
        if (output != null && !output.isBlank()) {
            builder.output(output);
        }
        new Runner(builder.build()).run();
    }
}
