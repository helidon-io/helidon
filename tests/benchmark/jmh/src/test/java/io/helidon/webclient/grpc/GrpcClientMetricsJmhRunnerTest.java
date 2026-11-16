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
package io.helidon.webclient.grpc;

import org.junit.jupiter.api.Test;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

class GrpcClientMetricsJmhRunnerTest {
    @Test
    void run() throws RunnerException {
        Options options = new OptionsBuilder()
                .include("^io\\.helidon\\.webclient\\.grpc\\.GrpcClientMetricsJmhBenchmark\\.startAndClose$")
                .forks(Integer.getInteger("grpc.client.metrics.jmh.forks", 1))
                .threads(Integer.getInteger("grpc.client.metrics.jmh.threads", 1))
                .resultFormat(ResultFormatType.JSON)
                .result("./target/grpc-client-metrics-jmh-result.json")
                .warmupIterations(Integer.getInteger("grpc.client.metrics.jmh.warmupIterations", 3))
                .warmupTime(TimeValue.milliseconds(Long.getLong("grpc.client.metrics.jmh.warmupMillis", 1000)))
                .measurementIterations(Integer.getInteger("grpc.client.metrics.jmh.measurementIterations", 5))
                .measurementTime(TimeValue.milliseconds(Long.getLong("grpc.client.metrics.jmh.measurementMillis", 1000)))
                .shouldFailOnError(true)
                .build();
        new Runner(options).run();
    }
}
