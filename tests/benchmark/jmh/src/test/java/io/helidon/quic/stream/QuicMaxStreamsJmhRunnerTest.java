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

package io.helidon.quic.stream;

import org.junit.jupiter.api.Test;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

class QuicMaxStreamsJmhRunnerTest {
    private static final String PREFIX = "quic.max.streams.jmh.";

    @Test
    void run() throws RunnerException {
        String include = System.getProperty(PREFIX + "include", ".*QuicMaxStreamsJmhBenchmark.*");
        String result = System.getProperty(PREFIX + "result", "./target/quic-max-streams-jmh-result.json");

        ChainedOptionsBuilder optionsBuilder = new OptionsBuilder()
                .include(include)
                .forks(Integer.getInteger(PREFIX + "forks", 0))
                .threads(Integer.getInteger(PREFIX + "threads", 1))
                .resultFormat(ResultFormatType.JSON)
                .result(result)
                .warmupIterations(Integer.getInteger(PREFIX + "warmupIterations", 1))
                .warmupTime(TimeValue.milliseconds(Long.getLong(PREFIX + "warmupMillis", 100)))
                .measurementIterations(Integer.getInteger(PREFIX + "measurementIterations", 1))
                .measurementTime(TimeValue.milliseconds(Long.getLong(PREFIX + "measurementMillis", 100)))
                .shouldFailOnError(true);

        String output = System.getProperty(PREFIX + "output");
        if (output != null && !output.isBlank()) {
            optionsBuilder.output(output);
        }
        String initialLimit = System.getProperty(PREFIX + "initialLimit");
        if (initialLimit != null && !initialLimit.isBlank()) {
            optionsBuilder.param("initialLimit", initialLimit);
        }
        if (Boolean.getBoolean(PREFIX + "gcProfiler")) {
            optionsBuilder.addProfiler(GCProfiler.class);
        }

        Options options = optionsBuilder.build();
        new Runner(options).run();
    }
}
