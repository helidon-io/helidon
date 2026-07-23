/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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

package io.helidon.webclient.http1;

import java.nio.charset.StandardCharsets;

import io.helidon.common.buffers.DataReader;
import io.helidon.http.Status;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Http1StatusParserTest {

    @Test
    public void http10() {
        String response = "HTTP/1.0 200 Connection established\r\n";
        Status status = Http1StatusParser.readStatus(DataReader.create(() -> response.getBytes()), 256);
        assertThat(status.code(), is(200));
    }

    @Test
    public void http11() {
        String response = "HTTP/1.1 200 Connection established\r\n";
        Status status = Http1StatusParser.readStatus(DataReader.create(() -> response.getBytes()), 256);
        assertThat(status.code(), is(200));
    }

    @Test
    public void obsTextReasonPhrase() {
        byte[] response = "HTTP/1.1 200 Caf\u00e9\r\n".getBytes(StandardCharsets.ISO_8859_1);
        Status status = Http1StatusParser.readStatus(DataReader.create(() -> response), 256);
        assertThat(status.reasonPhrase(), is("Caf\u00e9"));
    }

    @Test
    public void invalidReasonPhrase() {
        byte[] response = "HTTP/1.1 200 unsafe\u0000value\r\n".getBytes(StandardCharsets.ISO_8859_1);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> Http1StatusParser.readStatus(DataReader.create(() -> response), 256));
        assertThat(exception.getCause(), instanceOf(IllegalArgumentException.class));
    }

    @Test
    public void wrong() {
        String response = "HTTP/1.2 200 Connection established\r\n";
        assertThrows(IllegalStateException.class,
                () -> Http1StatusParser.readStatus(DataReader.create(() -> response.getBytes()), 256));
    }
}
