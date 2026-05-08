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

import java.io.OutputStream;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataWriter;

class DataWriterOutputStream extends OutputStream {

    private final DataWriter dataWriter;
    private final BufferData bufferData = BufferData.growing(1024);

    private boolean closed;

    DataWriterOutputStream(DataWriter dataWriter) {
        this.dataWriter = dataWriter;
    }

    @Override
    public void write(int b) {
        ensureOpen();
        bufferData.write(b);
    }

    @Override
    public void write(byte[] b) {
        ensureOpen();
        bufferData.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) {
        ensureOpen();
        bufferData.write(b, off, len);
    }

    @Override
    public void flush() {
        ensureOpen();
        dataWriter.writeNow(bufferData);
        bufferData.reset();
    }

    @Override
    public void close() {
        if (!closed) {
            flush();
            dataWriter.close();
            closed = true;
        }
    }

    private void ensureOpen() throws IllegalStateException {
        if (closed) {
            throw new IllegalStateException("DataWrite output stream closed");
        }
    }
}
