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

import java.nio.charset.StandardCharsets;

import io.helidon.common.buffers.DataReader;
import io.helidon.http.Status;
import io.helidon.webclient.http1.Http1StatusParser;

import org.openjdk.jmh.annotations.Benchmark;

public class Http1StatusParsingJmhTest {
    private static final byte[] KNOWN_REASON = "HTTP/1.1 200 OK\r\n".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] CUSTOM_REASON = "HTTP/1.1 200 Custom reason\r\n".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] OBS_TEXT_REASON = "HTTP/1.1 200 Caf\u00e9\r\n".getBytes(StandardCharsets.ISO_8859_1);

    @Benchmark
    public Status knownReason() {
        return readStatus(KNOWN_REASON);
    }

    @Benchmark
    public Status customReason() {
        return readStatus(CUSTOM_REASON);
    }

    @Benchmark
    public Status obsTextReason() {
        return readStatus(OBS_TEXT_REASON);
    }

    private static Status readStatus(byte[] response) {
        return Http1StatusParser.readStatus(DataReader.create(() -> response), 256);
    }
}
