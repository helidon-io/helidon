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

class QuicPacketProtectionJmhRunnerTest {
    @Test
    void run() throws RunnerException {
        String include = System.getProperty("quic.packet.protection.jmh.include",
                                            ".*QuicPacketProtectionJmhBenchmark.*");
        String result = System.getProperty("quic.packet.protection.jmh.result",
                                           "./target/quic-packet-protection-jmh-result.json");

        ChainedOptionsBuilder optionsBuilder = new OptionsBuilder()
                .include(include)
                .forks(Integer.getInteger("quic.packet.protection.jmh.forks", 1))
                .threads(Integer.getInteger("quic.packet.protection.jmh.threads", 4))
                .resultFormat(ResultFormatType.JSON)
                .result(result)
                .warmupIterations(Integer.getInteger("quic.packet.protection.jmh.warmupIterations", 3))
                .warmupTime(TimeValue.milliseconds(Long.getLong("quic.packet.protection.jmh.warmupMillis", 1000)))
                .measurementIterations(Integer.getInteger("quic.packet.protection.jmh.measurementIterations", 5))
                .measurementTime(TimeValue.milliseconds(
                        Long.getLong("quic.packet.protection.jmh.measurementMillis", 1000)))
                .shouldFailOnError(true);

        String output = System.getProperty("quic.packet.protection.jmh.output");
        if (output != null && !output.isBlank()) {
            optionsBuilder.output(output);
        }
        String cipherSuite = System.getProperty("quic.packet.protection.jmh.cipherSuite");
        if (cipherSuite != null) {
            optionsBuilder.param("cipherSuite", cipherSuite);
        }
        String datagramSize = System.getProperty("quic.packet.protection.jmh.datagramSize");
        if (datagramSize != null) {
            optionsBuilder.param("datagramSize", datagramSize);
        }
        if (Boolean.getBoolean("quic.packet.protection.jmh.gcProfiler")) {
            optionsBuilder.addProfiler(GCProfiler.class);
        }

        Options options = optionsBuilder.build();
        new Runner(options).run();
    }
}
