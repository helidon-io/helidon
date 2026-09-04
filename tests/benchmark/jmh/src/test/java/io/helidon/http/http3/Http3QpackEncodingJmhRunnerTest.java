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

import org.junit.jupiter.api.Test;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

class Http3QpackEncodingJmhRunnerTest {
    @Test
    void run() throws RunnerException {
        String include = System.getProperty("http3.qpack.encoding.jmh.include",
                                            ".*Http3QpackEncodingJmhBenchmark.*");
        String result = System.getProperty("http3.qpack.encoding.jmh.result",
                                           "./target/http3-qpack-encoding-jmh-result.json");

        ChainedOptionsBuilder optionsBuilder = new OptionsBuilder()
                .include(include)
                .forks(Integer.getInteger("http3.qpack.encoding.jmh.forks", 1))
                .resultFormat(ResultFormatType.JSON)
                .result(result)
                .warmupIterations(Integer.getInteger("http3.qpack.encoding.jmh.warmupIterations", 3))
                .warmupTime(TimeValue.milliseconds(Long.getLong("http3.qpack.encoding.jmh.warmupMillis", 500)))
                .measurementIterations(Integer.getInteger("http3.qpack.encoding.jmh.measurementIterations", 5))
                .measurementTime(TimeValue.milliseconds(Long.getLong("http3.qpack.encoding.jmh.measurementMillis", 1000)))
                .shouldFailOnError(true);

        String output = System.getProperty("http3.qpack.encoding.jmh.output");
        if (output != null && !output.isBlank()) {
            optionsBuilder.output(output);
        }
        if (Boolean.getBoolean("http3.qpack.encoding.jmh.gcProfiler")) {
            optionsBuilder.addProfiler(GCProfiler.class);
        }

        Options options = optionsBuilder.build();
        new Runner(options).run();
    }
}
