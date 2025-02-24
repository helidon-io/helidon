/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.microprofile.telemetry;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.logging.LogRecord;
import java.util.logging.StreamHandler;

public class MemoryLogHandler extends StreamHandler {

    private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    private static MemoryLogHandler create() {
        try {
            return new MemoryLogHandler();
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public MemoryLogHandler() throws UnsupportedEncodingException {
        setOutputStream(outputStream);
        setEncoding(StandardCharsets.UTF_8.name());
    }

    @Override
    public synchronized void publish(LogRecord record) {
        super.publish(record);
        flush();        // forces flush on writer
    }

    public String logAsString() {
        return outputStream.toString(StandardCharsets.UTF_8);
    }
}
