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

package io.helidon.common.benchmark.jmh;

import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

class CommonWebServerHotPathJmhRunnerTest {
    @Test
    void run() throws RunnerException {
        String result = System.getProperty("common.hotpath.jmh.result", "./target/common-hot-path-jmh-result.json");
        String method = System.getProperty("common.hotpath.jmh.method");
        OptionsBuilder builder = new OptionsBuilder();
        if (method == null) {
            builder.include(exact("dataReader"))
                    .include(exact("dataReaderPipelined"))
                    .include(exact("growingBufferData"))
                    .include(exact("growingBufferData1K"))
                    .include(exact("growingBufferDataReused"))
                    .include(exact("growingBufferDataReused1K"))
                    .include(exact("nioSocket"))
                    .include(exact("nioSocketComposite"))
                    .include(exact("nioSocketRead"))
                    .include(exact("socketWriter"))
                    .include(exact("socketWriterDirect"))
                    .include(exact("uriPathHelperAndNoParam"))
                    .include(exact("uriPathHelperAndNoParamFallback"))
                    .include(exact("uriPathHelperAndNoParamFallbackValidated"))
                    .include(exact("uriPathHelperAndNoParamValidated"));
        } else {
            builder.include(exact(method));
        }
        Options options = builder
                .forks(3)
                .threads(1)
                .resultFormat(ResultFormatType.JSON)
                .result(result)
                .warmupIterations(5)
                .warmupTime(TimeValue.milliseconds(500))
                .measurementIterations(8)
                .measurementTime(TimeValue.seconds(1))
                .addProfiler(GCProfiler.class)
                .shouldFailOnError(true)
                .build();

        new Runner(options).run();
    }

    private static String exact(String method) {
        return "^" + Pattern.quote(CommonWebServerHotPathJmhBenchmark.class.getName() + "." + method) + "$";
    }
}
