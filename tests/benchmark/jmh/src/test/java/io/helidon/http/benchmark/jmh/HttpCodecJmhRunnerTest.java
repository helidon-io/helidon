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

package io.helidon.http.benchmark.jmh;

import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import io.helidon.http.http2.HttpCodecJmhBenchmark;

import org.junit.jupiter.api.Test;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

class HttpCodecJmhRunnerTest {
    private static final String METHODS =
            "(parseSimpleContentType|parseQuotedContentType|writeHttp1Ascii|writeHttp1Latin1|"
                    + "encodeHpackAscii|encodeHpackLatin1|writeHpackRequestAscii|writeHpackRequestLatin1|"
                    + "writeHpackResponseAscii|writeHpackResponseLatin1)";

    @Test
    void runExactBenchmarks() throws RunnerException {
        String result = System.getProperty("http.codec.jmh.result", "target/http-codec-jmh.json");
        String include = System.getProperty("http.codec.jmh.include",
                                            exactMethods(HttpCodecJmhBenchmark.class, METHODS));

        Options options = optionsBuilder()
                .include(include)
                .result(result)
                .build();
        new Runner(options).run();
    }

    private static ChainedOptionsBuilder optionsBuilder() {
        ChainedOptionsBuilder builder = new OptionsBuilder()
                .threads(1)
                .forks(Integer.getInteger("http.codec.jmh.forks", 3))
                .warmupIterations(Integer.getInteger("http.codec.jmh.warmupIterations", 3))
                .warmupTime(TimeValue.milliseconds(Long.getLong("http.codec.jmh.warmupMillis", 500)))
                .measurementIterations(Integer.getInteger("http.codec.jmh.measurementIterations", 5))
                .measurementTime(TimeValue.milliseconds(Long.getLong("http.codec.jmh.measurementMillis", 1_000)))
                .mode(Mode.AverageTime)
                .timeUnit(TimeUnit.NANOSECONDS)
                .addProfiler(GCProfiler.class)
                .shouldFailOnError(true)
                .resultFormat(ResultFormatType.JSON)
                .timeout(TimeValue.seconds(Long.getLong("http.codec.jmh.timeoutSeconds", 30)));
        String jvmArgument = System.getProperty("http.codec.jmh.jvmArgument");
        if (jvmArgument != null) {
            builder.jvmArgsAppend(jvmArgument);
        }
        return builder;
    }

    private static String exactMethods(Class<?> benchmarkClass, String methods) {
        return "^" + Pattern.quote(benchmarkClass.getName()) + "\\." + methods + "$";
    }
}
