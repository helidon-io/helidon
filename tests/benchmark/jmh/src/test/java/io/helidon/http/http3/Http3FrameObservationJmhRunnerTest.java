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

import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

class Http3FrameObservationJmhRunnerTest {
    private static final String PREFIX = "http3.frame.observation.jmh.";

    @Test
    void run() throws RunnerException {
        String include = System.getProperty(PREFIX + "include",
                                            "^" + Pattern.quote(Http3FrameObservationJmhBenchmark.class.getName())
                                                    + "\\.(writeDataFrame|constructWriter)$");
        ChainedOptionsBuilder optionsBuilder = new OptionsBuilder()
                .include(include)
                .forks(Integer.getInteger(PREFIX + "forks", 1))
                .threads(Integer.getInteger(PREFIX + "threads", 1))
                .resultFormat(ResultFormatType.JSON)
                .result(System.getProperty(PREFIX + "result", "./target/http3-frame-observation-jmh-result.json"))
                .warmupIterations(Integer.getInteger(PREFIX + "warmupIterations", 3))
                .warmupTime(TimeValue.milliseconds(Long.getLong(PREFIX + "warmupMillis", 500)))
                .measurementIterations(Integer.getInteger(PREFIX + "measurementIterations", 5))
                .measurementTime(TimeValue.milliseconds(Long.getLong(PREFIX + "measurementMillis", 1000)))
                .shouldFailOnError(true);
        for (String parameter : List.of("observation", "payloadSize", "layout")) {
            String values = System.getProperty(PREFIX + parameter);
            if (values != null && !values.isBlank()) {
                optionsBuilder.param(parameter, values.split(","));
            }
        }
        String output = System.getProperty(PREFIX + "output");
        if (output != null && !output.isBlank()) {
            optionsBuilder.output(output);
        }
        if (Boolean.getBoolean(PREFIX + "gcProfiler")) {
            optionsBuilder.addProfiler(GCProfiler.class);
        }
        new Runner(optionsBuilder.build()).run();
    }
}
