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

package io.helidon.tests.benchmark.jmh.service.registry;

import org.junit.jupiter.api.Test;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

class ServiceRegistryJmhRunnerTest {
    @Test
    void run() throws RunnerException {
        String include = System.getProperty("service.registry.jmh.include", ".*ServiceRegistryJmhBenchmark.*");
        String result = System.getProperty("service.registry.jmh.result", "./target/service-registry-jmh-result.json");

        Options options = new OptionsBuilder()
                .include(include)
                .forks(Integer.getInteger("service.registry.jmh.forks", 1))
                .threads(Integer.getInteger("service.registry.jmh.threads", 1))
                .resultFormat(ResultFormatType.JSON)
                .result(result)
                .warmupIterations(Integer.getInteger("service.registry.jmh.warmupIterations", 3))
                .warmupTime(TimeValue.milliseconds(Long.getLong("service.registry.jmh.warmupMillis", 500)))
                .measurementIterations(Integer.getInteger("service.registry.jmh.measurementIterations", 5))
                .measurementTime(TimeValue.milliseconds(Long.getLong("service.registry.jmh.measurementMillis", 1000)))
                .shouldFailOnError(true)
                .build();

        new Runner(options).run();
    }
}
