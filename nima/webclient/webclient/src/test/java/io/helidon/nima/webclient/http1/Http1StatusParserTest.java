/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.nima.webclient.http1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.helidon.common.buffers.DataReader;
import io.helidon.common.http.Http.Status;

import org.junit.jupiter.api.Test;

class Http1StatusParserTest {

    @Test
    public void http10() {
        String response = "HTTP/1.0 200 Connection established\r\n";
        Status status = Http1StatusParser.readStatus(new DataReader(() -> response.getBytes()), 256);
        assertEquals(200, status.code());
    }

    @Test
    public void http11() {
        String response = "HTTP/1.1 200 Connection established\r\n";
        Status status = Http1StatusParser.readStatus(new DataReader(() -> response.getBytes()), 256);
        assertEquals(200, status.code());
    }

    @Test
    public void wrong() {
        String response = "HTTP/1.2 200 Connection established\r\n";
        assertThrows(IllegalStateException.class,
                () -> Http1StatusParser.readStatus(new DataReader(() -> response.getBytes()), 256));
    }
}
