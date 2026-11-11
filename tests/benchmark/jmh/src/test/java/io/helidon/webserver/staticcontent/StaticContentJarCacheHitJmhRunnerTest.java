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

package io.helidon.webserver.staticcontent;

import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

class StaticContentJarCacheHitJmhRunnerTest {
    @Test
    void runWarmJarGetAndHead() throws RunnerException {
        Options options = new OptionsBuilder()
                .include("^" + Pattern.quote(StaticContentPreCompressedJmhTest.class.getName())
                                 + "\\.warmJar(Head|Get)(Hot|Distributed)$")
                .threads(Integer.getInteger("static.content.jar.jmh.threads", 1))
                .forks(Integer.getInteger("static.content.jar.jmh.forks", 2))
                .warmupIterations(Integer.getInteger("static.content.jar.jmh.warmupIterations", 3))
                .warmupTime(TimeValue.milliseconds(Long.getLong("static.content.jar.jmh.warmupMillis", 300)))
                .measurementIterations(Integer.getInteger("static.content.jar.jmh.measurementIterations", 4))
                .measurementTime(TimeValue.milliseconds(Long.getLong("static.content.jar.jmh.measurementMillis", 500)))
                .addProfiler(GCProfiler.class)
                .shouldFailOnError(true)
                .resultFormat(ResultFormatType.JSON)
                .result(System.getProperty("static.content.jar.jmh.result",
                                           "target/static-content-jar-cache-hits.json"))
                .build();
        new Runner(options).run();
    }
}
