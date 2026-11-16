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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import io.helidon.common.parameters.Parameters;
import io.helidon.http.HeaderNames;
import io.helidon.http.Headers;
import io.helidon.http.WritableHeaders;
import io.helidon.http.media.EntityReader;
import io.helidon.http.media.FormParamsSupport;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/**
 * Reads URL-encoded forms through the public media reader, including body decoding and parameter construction.
 * Reader selection and payload preparation happen outside measurement; each operation uses a fresh input stream.
 * The returned parameters keep the complete parse observable to JMH.
 *
 * <p>NONEMPTY, EMPTY_VALUES and ENCODED provide inputs for comparisons between readers that recognize the same fields.
 * BARE_NAMES, EMPTY_NAMES and MIXED_SEPARATORS exercise corrected behavior; timings across revisions recognizing
 * different fields are not equivalent work. Repetitions preserve the same fields as repeated values.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class FormParamsSupportJmhBenchmark {
    @Param({"NONEMPTY", "EMPTY_VALUES", "ENCODED", "BARE_NAMES", "EMPTY_NAMES", "MIXED_SEPARATORS"})
    public String form;

    @Param({"1", "32"})
    public int repetitions;

    private EntityReader<Parameters> reader;
    private Headers headers;
    private byte[] body;

    @Setup
    public void setup() {
        headers = WritableHeaders.create()
                .set(HeaderNames.CONTENT_TYPE, "application/x-www-form-urlencoded;charset=UTF-8");
        reader = FormParamsSupport.create().reader(Parameters.GENERIC_TYPE, headers).supplier().get();
        String fields = switch (form) {
            case "NONEMPTY" -> "name=Alice&city=Prague&count=42";
            case "EMPTY_VALUES" -> "q=&other=&q=";
            case "ENCODED" -> "a%26b=c%3Dd%26e&caf%C3%A9=%E2%82%AC&plus=one+two%2Bthree";
            case "BARE_NAMES" -> "q&other&last";
            case "EMPTY_NAMES" -> "=first&=&=last";
            case "MIXED_SEPARATORS" -> "&q&&q=&q=one+two&&=value&";
            default -> throw new IllegalArgumentException("Unknown form: " + form);
        };
        body = String.join("&", Collections.nCopies(repetitions, fields)).getBytes(StandardCharsets.UTF_8);
    }

    @Benchmark
    public Parameters readUrlEncoded() {
        return reader.read(Parameters.GENERIC_TYPE, new ByteArrayInputStream(body), headers);
    }
}
