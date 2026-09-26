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

package io.helidon.webserver.benchmark.jmh;

import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

class TlsNioSocketJmhRunnerTest {
    @Test
    void runExactBenchmarks() throws RunnerException {
        String benchmark = Pattern.quote(TlsNioSocketJmhTest.class.getName());
        String include = System.getProperty("tls.nio.socket.jmh.include",
                                            "^" + benchmark
                                                    + "\\.(tlsInitialHandshake"
                                                    + "|tlsInitialRead"
                                                    + "|tlsInitialWrite"
                                                    + "|tlsPostHandshakeRead"
                                                    + "|tlsPostHandshakeWrite)$");
        String result = System.getProperty("tls.nio.socket.jmh.result",
                                           "target/tls-nio-socket-jmh.json");
        Options options = new OptionsBuilder()
                .include(include)
                .forks(Integer.getInteger("tls.nio.socket.jmh.forks", 3))
                .threads(1)
                .mode(Mode.AverageTime)
                .timeUnit(TimeUnit.NANOSECONDS)
                .warmupIterations(Integer.getInteger("tls.nio.socket.jmh.warmupIterations", 5))
                .warmupTime(TimeValue.milliseconds(Long.getLong("tls.nio.socket.jmh.warmupMillis", 1_000)))
                .measurementIterations(Integer.getInteger("tls.nio.socket.jmh.measurementIterations", 8))
                .measurementTime(TimeValue.milliseconds(Long.getLong("tls.nio.socket.jmh.measurementMillis", 1_000)))
                .addProfiler(GCProfiler.class)
                .shouldFailOnError(true)
                .resultFormat(ResultFormatType.JSON)
                .result(result)
                .build();
        new Runner(options).run();
    }
}
