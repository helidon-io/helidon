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

import io.helidon.tests.benchmark.ProcessAllocationProfiler;
import io.helidon.tests.benchmark.ProcessCpuProfiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

class QuicEndpointIngressJmhRunnerTest {
    @Test
    void run() throws RunnerException {
        String include = System.getProperty("quic.endpoint.ingress.jmh.include");
        Assumptions.assumeTrue(include != null && !include.isBlank(),
                               "Set quic.endpoint.ingress.jmh.include to select a bounded benchmark scenario");
        String result = System.getProperty("quic.endpoint.ingress.jmh.result",
                                           "./target/quic-endpoint-ingress-jmh-result.json");

        ChainedOptionsBuilder optionsBuilder = new OptionsBuilder()
                .include(include)
                .forks(Integer.getInteger("quic.endpoint.ingress.jmh.forks", 1))
                .threads(Integer.getInteger("quic.endpoint.ingress.jmh.threads", 1))
                .resultFormat(ResultFormatType.JSON)
                .result(result)
                .warmupIterations(Integer.getInteger("quic.endpoint.ingress.jmh.warmupIterations", 3))
                .warmupTime(TimeValue.milliseconds(Long.getLong("quic.endpoint.ingress.jmh.warmupMillis", 500)))
                .measurementIterations(Integer.getInteger("quic.endpoint.ingress.jmh.measurementIterations", 5))
                .measurementTime(TimeValue.milliseconds(
                        Long.getLong("quic.endpoint.ingress.jmh.measurementMillis", 1000)))
                .timeout(TimeValue.milliseconds(Long.getLong("quic.endpoint.ingress.jmh.timeoutMillis", 30_000)))
                .shouldFailOnError(true);

        String output = System.getProperty("quic.endpoint.ingress.jmh.output");
        if (output != null) {
            optionsBuilder.output(output);
        }
        applyParam(optionsBuilder, "strategy");
        applyParam(optionsBuilder, "routeCount");
        applyParam(optionsBuilder, "packetSize");
        applyParam(optionsBuilder, "batchSize");
        applyParam(optionsBuilder, "executorMode");
        applyParam(optionsBuilder, "routeAccess");
        applyParam(optionsBuilder, "offerMillis");
        applyParam(optionsBuilder, "targetPps");
        applyParam(optionsBuilder, "senderCount");
        applyParam(optionsBuilder, "senderWorkingSet");
        applyParam(optionsBuilder, "drainMillis");
        applyParam(optionsBuilder, "lateMicros");
        if (Boolean.getBoolean("quic.endpoint.ingress.jmh.gcProfiler")) {
            optionsBuilder.addProfiler(GCProfiler.class);
        }
        String profiler = System.getProperty("quic.endpoint.ingress.jmh.profiler");
        if (profiler != null) {
            optionsBuilder.addProfiler(profiler);
        }
        // Internal profilers stop in reverse registration order. Keep CPU last so it excludes other profiler scans,
        // and allocation after the open-loop profiler so its recording excludes the open-loop metrics scan. Both
        // process profilers omit the final measurement because that iteration includes trial teardown.
        optionsBuilder.addProfiler(QuicOpenLoopMetricsProfiler.class);
        if (Boolean.getBoolean("quic.endpoint.ingress.jmh.processAllocationProfiler")) {
            optionsBuilder.addProfiler(ProcessAllocationProfiler.class);
        }
        if (Boolean.parseBoolean(System.getProperty("quic.endpoint.ingress.jmh.processCpuProfiler", "true"))) {
            optionsBuilder.addProfiler(ProcessCpuProfiler.class);
        }

        Options options = optionsBuilder.build();
        new Runner(options).run();
    }

    private static void applyParam(ChainedOptionsBuilder optionsBuilder, String name) {
        String value = System.getProperty("quic.endpoint.ingress.jmh." + name);
        if (value != null) {
            optionsBuilder.param(name, value.split(","));
        }
    }
}
