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

package io.helidon.metrics.benchmark.jmh;

import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.OptionsBuilder;

class HttpTransportMetricsDispatchJmhRunnerTest {
    @Test
    void runDispatch() throws RunnerException {
        String benchmark = Pattern.quote(HttpTransportMetricsDispatchJmhBenchmark.class.getName());
        String resultPrefix = System.getProperty("http.transport.dispatch.jmh.resultPrefix",
                                                 "target/http-transport-dispatch-jmh");
        for (String value : System.getProperty("http.transport.dispatch.jmh.threads", "1,4").split(",")) {
            int threads = Integer.parseInt(value.trim());
            new Runner(new OptionsBuilder()
                               .include("^" + benchmark + "\\.(producerAdmission|completeWave)$")
                               .mode(Mode.SingleShotTime)
                               .timeUnit(TimeUnit.MICROSECONDS)
                               .threads(threads)
                               .forks(Integer.getInteger("http.transport.dispatch.jmh.forks", 3))
                               .warmupIterations(Integer.getInteger("http.transport.dispatch.jmh.warmupIterations", 1000))
                               .warmupBatchSize(1)
                               .measurementIterations(Integer.getInteger("http.transport.dispatch.jmh.measurementIterations", 100))
                               .measurementBatchSize(1)
                               .jvmArgsAppend("-Xms1g", "-Xmx1g", "--enable-native-access=ALL-UNNAMED")
                               .addProfiler(GCProfiler.class)
                               .shouldFailOnError(true)
                               .resultFormat(ResultFormatType.JSON)
                               .result(resultPrefix + "-t" + threads + ".json")
                               .build())
                    .run();
        }
    }
}
