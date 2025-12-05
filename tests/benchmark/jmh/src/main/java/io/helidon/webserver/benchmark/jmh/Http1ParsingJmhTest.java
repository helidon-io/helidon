/*
 * Copyright (c) 2022, 2025 Oracle and/or its affiliates.
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

package io.helidon.webserver.benchmark.jmh;

import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;

import io.helidon.common.buffers.DataReader;
import io.helidon.http.HeaderNames;
import io.helidon.http.HttpPrologue;
import io.helidon.http.WritableHeaders;
import io.helidon.webserver.http1.Http1Headers;
import io.helidon.webserver.http1.Http1Prologue;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

@State(Scope.Benchmark)
public class Http1ParsingJmhTest {
    private static final byte[] SINGLE_BUFFER = """
            GET /plaintext HTTP/1.1\r
            Host: localhost:8080\r
            User-Agent: curl/7.68.0\r
            Accept: */*\r
            \r
            """.getBytes(StandardCharsets.UTF_8);

    private static final List<byte[]> MULTI_BUFFER = List.of(
            "GET /plaintext HTTP/1.".getBytes(StandardCharsets.UTF_8),
            "1\r\nHost: localhost:8".getBytes(StandardCharsets.UTF_8),
            """
                    080\r
                    User-Agent: curl/7.68.0\r
                    Accept: */*""".getBytes(StandardCharsets.UTF_8),
            "\r\n\r\n".getBytes(StandardCharsets.UTF_8));

    private byte[] longMessage;

    @Setup
    public void setup() {
        String longHeaderValue = "v".repeat(1024);
        longMessage = (
                "GET /plaintext HTTP/1.1\r\n"
                        + "Host: localhost:8080\r\n"
                        + "User-Agent: curl/7.68.0\r\n"
                        + "Accept: */*\r\n"
                        + "Authorization: bearer " + longHeaderValue
                        + "\r\n\r\n").getBytes(StandardCharsets.UTF_8);
    }

    @Benchmark
    public void singleBuffer(Blackhole bh) {
        DataReader reader = DataReader.create(() -> SINGLE_BUFFER);
        readRequest(bh, reader);
    }

    @Benchmark
    public void multiBuffer(Blackhole bh) {
        Iterator<byte[]> iterator = MULTI_BUFFER.iterator();
        DataReader reader = DataReader.create(() -> {
            if (iterator.hasNext()) {
                return iterator.next();
            }
            return null;
        });
        readRequest(bh, reader);
    }

    @Benchmark
    public void longHeader(Blackhole bh) {
        DataReader reader = DataReader.create(() -> longMessage);
        readRequest(bh, reader);
    }

    private void readRequest(Blackhole bh, DataReader reader) {
        Http1Prologue prologue = new Http1Prologue(reader, 1024, false);
        Http1Headers headers = new Http1Headers(reader, 4096, false);

        HttpPrologue httpPrologue = prologue.readPrologue();
        WritableHeaders<?> httpHeaders = headers.readHeaders(httpPrologue);
        boolean hasContent = httpHeaders.contains(HeaderNames.CONTENT_LENGTH);
        String authority = httpHeaders.get(HeaderNames.HOST).value();

        bh.consume(hasContent);
        bh.consume(authority);
    }
}
