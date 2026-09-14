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

package io.helidon.webserver.sse;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

final class SseFields {
    private SseFields() {
    }

    static void writeSingleLineField(OutputStream outputStream, byte[] field, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        outputStream.write(field);
        int start = 0;
        for (int i = 0; i < bytes.length; i++) {
            byte b = bytes[i];
            if (b == '\r' || b == '\n') {
                outputStream.write(bytes, start, i - start);
                outputStream.write(' ');
                if (b == '\r' && i + 1 < bytes.length && bytes[i + 1] == '\n') {
                    i++;
                }
                start = i + 1;
            }
        }
        outputStream.write(bytes, start, bytes.length - start);
        outputStream.write('\n');
    }

    static void writeMultiLineField(OutputStream outputStream, byte[] field, byte[] value) throws IOException {
        int start = 0;
        boolean lineWritten = false;
        for (int i = 0; i < value.length; i++) {
            byte b = value[i];
            if (b == '\r' || b == '\n') {
                outputStream.write(field);
                outputStream.write(value, start, i - start);
                outputStream.write('\n');
                lineWritten = true;
                if (b == '\r' && i + 1 < value.length && value[i + 1] == '\n') {
                    i++;
                }
                start = i + 1;
            }
        }
        if (!lineWritten || start < value.length) {
            outputStream.write(field);
            outputStream.write(value, start, value.length - start);
            outputStream.write('\n');
        }
    }
}
