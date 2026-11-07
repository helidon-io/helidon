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

package io.helidon.http.media.multipart.benchmark.jmh;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import io.helidon.config.Config;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Headers;
import io.helidon.http.WritableHeaders;
import io.helidon.http.media.MediaContext;
import io.helidon.http.media.multipart.MultiPart;
import io.helidon.http.media.multipart.MultiPartSupport;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/**
 * Parses a complete multipart message and extracts the uploaded filename using public media APIs.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class MultiPartFileNameJmhBenchmark {
    private static final String BOUNDARY = "benchmark-boundary";

    @Param({"ASCII", "PERCENT_ENCODED_UTF8", "RAW_UTF8", "ISO_8859_1"})
    public String encoding;

    private MediaContext context;
    private Headers headers;
    private byte[] body;

    @Setup
    public void setup() {
        context = MediaContext.builder()
                .mediaSupportsDiscoverServices(false)
                .addMediaSupport(MultiPartSupport.create(Config.empty()))
                .build();
        headers = WritableHeaders.create()
                .set(HeaderValues.create(HeaderNames.CONTENT_TYPE, "multipart/form-data; boundary=" + BOUNDARY));
        String filename = switch (encoding) {
            case "ASCII" -> "prufenfile1.json";
            case "PERCENT_ENCODED_UTF8" -> "pr%C3%BCfenfile1.json";
            case "RAW_UTF8", "ISO_8859_1" -> "prüfenfile1.json";
            default -> throw new IllegalArgumentException("Unknown filename encoding: " + encoding);
        };
        var charset = "ISO_8859_1".equals(encoding) ? StandardCharsets.ISO_8859_1 : StandardCharsets.UTF_8;
        body = ("--" + BOUNDARY + "\r\n"
                + "Content-Disposition: form-data; name=\"upload\"; filename=\"" + filename + "\"\r\n"
                + "Content-Type: application/json\r\n\r\n"
                + "{\"value\":1}\r\n"
                + "--" + BOUNDARY + "--\r\n").getBytes(charset);
    }

    @Benchmark
    public String parseFileName() {
        var multipart = context.reader(MultiPart.GENERIC_TYPE, headers)
                .read(MultiPart.GENERIC_TYPE, new ByteArrayInputStream(body), headers);
        String filename = multipart.next().fileName().orElseThrow();
        // Advancing drains the part body and parses the closing boundary.
        if (multipart.hasNext()) {
            throw new IllegalStateException("Expected a single multipart part");
        }
        return filename;
    }
}
