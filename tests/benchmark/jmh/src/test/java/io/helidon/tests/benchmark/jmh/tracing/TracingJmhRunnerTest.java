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

package io.helidon.tests.benchmark.jmh.tracing;

import org.junit.jupiter.api.Test;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

class TracingJmhRunnerTest {
    @Test
    void run() throws RunnerException {
        String include = System.getProperty("tracing.jmh.include",
                                            ".*TracingJmhBenchmark\\."
                                                    + "(servicesGetTracer|cachedTracer|servicesGetTracerSpanBuilder|"
                                                    + "cachedTracerSpanBuilder|contextFallbackCachedTracer|"
                                                    + "dataPropagationProviderData|"
                                                    + "contextAwareExecutorSubmit|"
                                                    + "contextAwareExecutorSubmitContended)");
        String result = System.getProperty("tracing.jmh.result", "./target/tracing-jmh-result.json");

        ChainedOptionsBuilder optionsBuilder = new OptionsBuilder()
                .include(include)
                .forks(Integer.getInteger("tracing.jmh.forks", 0))
                .resultFormat(ResultFormatType.JSON)
                .result(result)
                .warmupIterations(Integer.getInteger("tracing.jmh.warmupIterations", 1))
                .warmupTime(TimeValue.milliseconds(Long.getLong("tracing.jmh.warmupMillis", 100)))
                .measurementIterations(Integer.getInteger("tracing.jmh.measurementIterations", 1))
                .measurementTime(TimeValue.milliseconds(Long.getLong("tracing.jmh.measurementMillis", 100)))
                .shouldFailOnError(true);

        String threads = System.getProperty("tracing.jmh.threads");
        if (threads != null) {
            optionsBuilder.threads(Integer.parseInt(threads));
        }

        Options options = optionsBuilder.build();

        new Runner(options).run();
    }
}
