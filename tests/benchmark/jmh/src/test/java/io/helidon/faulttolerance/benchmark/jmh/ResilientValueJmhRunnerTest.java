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

package io.helidon.faulttolerance.benchmark.jmh;

import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

class ResilientValueJmhRunnerTest {
    @Test
    void runExactBenchmark() throws RunnerException {
        String result = System.getProperty("resilient.value.jmh.result",
                                           "./target/resilient-value-jmh-result.json");
        OptionsBuilder optionsBuilder = new OptionsBuilder();
        optionsBuilder.include("^" + Pattern.quote(ResilientValueJmhBenchmark.class.getName())
                                       + "\\.(directFieldRead|cachedValueRead|openBreakerRejection|firstLoad|"
                                       + "contendedFirstLoad|halfOpenRecovery)$")
                .forks(Integer.getInteger("resilient.value.jmh.forks", 3))
                .resultFormat(ResultFormatType.JSON)
                .result(result)
                .warmupIterations(Integer.getInteger("resilient.value.jmh.warmupIterations", 20))
                .warmupTime(TimeValue.milliseconds(Long.getLong("resilient.value.jmh.warmupMillis", 100)))
                .measurementIterations(Integer.getInteger("resilient.value.jmh.measurementIterations", 50))
                .measurementTime(TimeValue.milliseconds(Long.getLong("resilient.value.jmh.measurementMillis", 200)))
                .addProfiler(GCProfiler.class)
                .shouldFailOnError(true);
        Integer threads = Integer.getInteger("resilient.value.jmh.threads");
        if (threads != null) {
            optionsBuilder.threads(threads);
        }
        Options options = optionsBuilder.build();

        new Runner(options).run();
    }
}
