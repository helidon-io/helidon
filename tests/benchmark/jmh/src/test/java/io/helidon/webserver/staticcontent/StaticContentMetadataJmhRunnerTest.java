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

package io.helidon.webserver.staticcontent;

import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

class StaticContentMetadataJmhRunnerTest {
    @Test
    void runExactBenchmark() throws RunnerException {
        String result = System.getProperty("static.content.metadata.jmh.result",
                                           "target/static-content-metadata-jmh.json");
        Options options = new OptionsBuilder()
                .include("^" + Pattern.quote(StaticContentMetadataBenchmark.class.getName())
                                 + "\\.(fileSystem|inMemory)(Before|After)"
                                 + "(UnconditionalHead|MatchingIfNoneMatch)$")
                .forks(Integer.getInteger("static.content.metadata.jmh.forks", 3))
                .threads(1)
                .warmupIterations(Integer.getInteger("static.content.metadata.jmh.warmupIterations", 5))
                .warmupTime(TimeValue.milliseconds(Long.getLong("static.content.metadata.jmh.warmupMillis", 1_000)))
                .measurementIterations(Integer.getInteger("static.content.metadata.jmh.measurementIterations", 8))
                .measurementTime(TimeValue.milliseconds(Long.getLong("static.content.metadata.jmh.measurementMillis", 1_000)))
                .addProfiler(GCProfiler.class)
                .shouldFailOnError(true)
                .resultFormat(ResultFormatType.JSON)
                .result(result)
                .build();
        new Runner(options).run();
    }
}
