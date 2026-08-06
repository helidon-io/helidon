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

package io.helidon.webclient.benchmark.jmh;

import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

class ClientRequestQueryJmhRunnerTest {
    @Test
    void runExactBenchmark() throws RunnerException {
        Options options = new OptionsBuilder()
                .include("^" + Pattern.quote(ClientRequestQueryBenchmark.class.getName())
                                 + "\\.(nonTemplate|relativeTemplate|relativeTemplateSkipEncoding|absoluteTemplate)$")
                .include("^" + Pattern.quote(WebClientSecurityQueryBenchmark.class.getName())
                                 + "\\.(defaultEncoding|skipEncoding|skipEncodingPlain)$")
                .include("^" + Pattern.quote(ClientRequestQueryAliasBenchmark.class.getName())
                                 + "\\.encodedAliasReplay$")
                .forks(1)
                .warmupIterations(3)
                .measurementIterations(5)
                .addProfiler(GCProfiler.class)
                .shouldFailOnError(true)
                .resultFormat(ResultFormatType.JSON)
                .result("target/client-request-query-jmh.json")
                .build();
        new Runner(options).run();
    }
}
