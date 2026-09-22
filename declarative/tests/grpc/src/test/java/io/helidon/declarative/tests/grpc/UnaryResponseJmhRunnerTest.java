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

package io.helidon.declarative.tests.grpc;

import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

@EnabledIfSystemProperty(named = "grpc.unary.jmh", matches = "true")
class UnaryResponseJmhRunnerTest {
    @Test
    void run() throws RunnerException {
        var options = new OptionsBuilder()
                .include("^" + Pattern.quote(UnaryResponseJmhBenchmark.class.getName()) + "\\.unary$")
                .threads(1)
                .forks(1)
                .warmupIterations(Integer.getInteger("grpc.unary.jmh.warmupIterations", 3))
                .warmupTime(TimeValue.milliseconds(Long.getLong("grpc.unary.jmh.iterationMillis", 1000)))
                .measurementIterations(Integer.getInteger("grpc.unary.jmh.measurementIterations", 5))
                .measurementTime(TimeValue.milliseconds(Long.getLong("grpc.unary.jmh.iterationMillis", 1000)))
                .addProfiler(GCProfiler.class)
                .shouldFailOnError(true)
                .build();
        new Runner(options).run();
    }
}
