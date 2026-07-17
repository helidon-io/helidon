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

package io.helidon.tests.benchmark.jmh.grpc;

import org.junit.jupiter.api.Test;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

class GrpcStreamingJmhRunnerTest {
    @Test
    void run() throws RunnerException {
        String include = System.getProperty("grpc.streaming.jmh.include", ".*GrpcStreamingJmhBenchmark.*");
        String result = System.getProperty("grpc.streaming.jmh.result", "./target/grpc-streaming-jmh-result.json");

        ChainedOptionsBuilder options = new OptionsBuilder()
                .include(include)
                .forks(Integer.getInteger("grpc.streaming.jmh.forks", 1))
                .threads(Integer.getInteger("grpc.streaming.jmh.threads", 1))
                .resultFormat(ResultFormatType.JSON)
                .result(result)
                .warmupIterations(Integer.getInteger("grpc.streaming.jmh.warmupIterations", 3))
                .warmupTime(TimeValue.milliseconds(Long.getLong("grpc.streaming.jmh.warmupMillis", 1000)))
                .measurementIterations(Integer.getInteger("grpc.streaming.jmh.measurementIterations", 5))
                .measurementTime(TimeValue.milliseconds(Long.getLong("grpc.streaming.jmh.measurementMillis", 2000)))
                .addProfiler(GCProfiler.class)
                .shouldFailOnError(true);
        String payloadSizes = System.getProperty("grpc.streaming.jmh.payloadSizes");
        if (payloadSizes != null && !payloadSizes.isBlank()) {
            options.param("payloadSize", payloadSizes.split(","));
        }

        new Runner(options.build()).run();
    }
}
