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

package io.helidon.quic;

import org.junit.jupiter.api.Test;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

class QuicPathJmhRunnerTest {
    @Test
    void run() throws RunnerException {
        String include = System.getProperty("quic.path.jmh.include", ".*QuicPathJmhBenchmark.*");
        String result = System.getProperty("quic.path.jmh.result", "./target/quic-path-jmh-result.json");

        ChainedOptionsBuilder optionsBuilder = new OptionsBuilder()
                .include(include)
                .forks(Integer.getInteger("quic.path.jmh.forks", 1))
                .threads(Integer.getInteger("quic.path.jmh.threads", 4))
                .resultFormat(ResultFormatType.JSON)
                .result(result)
                .warmupIterations(Integer.getInteger("quic.path.jmh.warmupIterations", 3))
                .warmupTime(TimeValue.milliseconds(Long.getLong("quic.path.jmh.warmupMillis", 500)))
                .measurementIterations(Integer.getInteger("quic.path.jmh.measurementIterations", 5))
                .measurementTime(TimeValue.milliseconds(Long.getLong("quic.path.jmh.measurementMillis", 1000)))
                .shouldFailOnError(true);

        String output = System.getProperty("quic.path.jmh.output");
        if (output != null && !output.isBlank()) {
            optionsBuilder.output(output);
        }
        String connectionCount = System.getProperty("quic.path.jmh.connectionCount");
        if (connectionCount != null) {
            optionsBuilder.param("connectionCount", connectionCount);
        }
        String sendAsync = System.getProperty("quic.path.jmh.sendAsync");
        if (sendAsync != null) {
            optionsBuilder.param("sendAsync", sendAsync);
        }
        String inFlightPackets = System.getProperty("quic.path.jmh.inFlightPackets");
        if (inFlightPackets != null) {
            optionsBuilder.param("inFlightPackets", inFlightPackets);
        }
        String ackRangeCount = System.getProperty("quic.path.jmh.ackRangeCount");
        if (ackRangeCount != null) {
            optionsBuilder.param("ackRangeCount", ackRangeCount);
        }
        if (Boolean.getBoolean("quic.path.jmh.gcProfiler")) {
            optionsBuilder.addProfiler(GCProfiler.class);
        }

        Options options = optionsBuilder.build();
        new Runner(options).run();
    }
}
