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

package io.helidon.common.benchmark.jmh;

import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

class BufferTransferJmhRunnerTest {
    @Test
    void run() throws RunnerException {
        Options options = new OptionsBuilder()
                .include("^" + Pattern.quote(BufferTransferJmhBenchmark.class.getName())
                                 + "\\.(growingWrite|growingWriteLength|writeFromShared)$")
                .param("size", System.getProperty("buffer.transfer.jmh.sizes", "8192,65536").split(","))
                .param("sharedSize", System.getProperty("buffer.transfer.jmh.sharedSizes", "32,1024").split(","))
                .forks(Integer.getInteger("buffer.transfer.jmh.forks", 3))
                .threads(1)
                .resultFormat(ResultFormatType.JSON)
                .result(System.getProperty("buffer.transfer.jmh.result", "./target/buffer-transfer-jmh-result.json"))
                .warmupIterations(Integer.getInteger("buffer.transfer.jmh.warmupIterations", 5))
                .warmupTime(TimeValue.milliseconds(Long.getLong("buffer.transfer.jmh.warmupMillis", 500)))
                .measurementIterations(Integer.getInteger("buffer.transfer.jmh.measurementIterations", 8))
                .measurementTime(TimeValue.milliseconds(Long.getLong("buffer.transfer.jmh.measurementMillis", 1_000)))
                .addProfiler(GCProfiler.class)
                .shouldFailOnError(true)
                .build();

        new Runner(options).run();
    }
}
