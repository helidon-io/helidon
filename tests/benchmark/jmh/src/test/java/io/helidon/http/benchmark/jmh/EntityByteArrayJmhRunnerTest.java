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

package io.helidon.http.benchmark.jmh;

import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

class EntityByteArrayJmhRunnerTest {
    @Test
    void runExactBenchmark() throws RunnerException {
        var options = new OptionsBuilder()
                .include("^" + Pattern.quote(EntityByteArrayJmhBenchmark.class.getName()) + "\\.(unknownLength|exactLength)$")
                .forks(Integer.getInteger("entity.byte-array.jmh.forks", 3))
                .threads(1)
                .warmupIterations(Integer.getInteger("entity.byte-array.jmh.warmupIterations", 5))
                .warmupTime(TimeValue.seconds(1))
                .measurementIterations(Integer.getInteger("entity.byte-array.jmh.measurementIterations", 5))
                .measurementTime(TimeValue.seconds(1))
                .resultFormat(ResultFormatType.JSON)
                .result(System.getProperty("entity.byte-array.jmh.result", "./target/entity-byte-array-jmh-result.json"))
                .addProfiler(GCProfiler.class)
                .shouldFailOnError(true);
        new Runner(options.build()).run();
    }
}
