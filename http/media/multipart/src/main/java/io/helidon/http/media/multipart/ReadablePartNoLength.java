/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

package io.helidon.http.media.multipart;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import io.helidon.common.GenericType;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;
import io.helidon.http.WritableHeaders;
import io.helidon.http.media.MediaContext;

class ReadablePartNoLength extends ReadablePartAbstract {
    private final MediaContext context;
    private final DataReader dataReader;
    private final String boundary;
    private final String endBoundary;

    private PartInputStream inputStream;

    ReadablePartNoLength(MediaContext context,
                         WritableHeaders<?> headers,
                         DataReader dataReader,
                         int index,
                         String boundary,
                         String endBoundary) {
        super(headers, index);
        this.context = context;
        this.dataReader = dataReader;
        this.boundary = boundary;
        this.endBoundary = endBoundary;
    }

    @Override
    public PartInputStream inputStream() {
        if (inputStream == null) {
            this.inputStream = new PartInputStream(dataReader, boundary, endBoundary);
            return inputStream;
        }
        throw new IllegalStateException("Cannot request input stream more than once");
    }

    @Override
    public <T> T as(GenericType<T> type) {
        return context.reader(type, partHeaders())
                .read(type, inputStream(), partHeaders());
    }

    @Override
    public void consume() {
        if (inputStream == null) {
            inputStream = inputStream();
        }
        try {
            byte[] buffer = new byte[2048];
            while (inputStream.read(buffer) > 0) {
                // ignore
            }
        } finally {
            inputStream.close();
        }
    }

    @Override
    public boolean consumed() {
        if (inputStream == null) {
            inputStream = inputStream();
        }
        return inputStream.consumed();
    }

    @Override
    protected void finish() {
        if (inputStream == null) {
            inputStream = inputStream();
        }
        inputStream.finish();
    }

    private static class PartInputStream extends InputStream {
        private static final byte[] EOL = "\r\n".getBytes(StandardCharsets.US_ASCII);

        private final DataReader dataReader;
        private final String boundary;
        private final String endBoundary;
        private final int maxEol;

        private boolean finished;
        private BufferData nextBuffer;
        private boolean trailingEol;

        PartInputStream(DataReader dataReader, String boundary, String endBoundary) {
            this.dataReader = dataReader;
            this.boundary = boundary;
            this.endBoundary = endBoundary;
            this.maxEol = Math.max(endBoundary.length() + 3, 512);
        }

        @Override
        public int read() {
            ensureBuffer();
            if (finished) {
                return -1;
            }

            return nextBuffer.read();
        }

        @Override
        public int read(byte[] b) {
            ensureBuffer();
            if (finished) {
                return -1;
            }
            return nextBuffer.read(b);
        }

        @Override
        public int read(byte[] b, int off, int len) {
            ensureBuffer();
            if (finished) {
                return -1;
            }
            return nextBuffer.read(b, off, len);
        }

        @Override
        public long skip(long n) {
            int toSkip = (int) Math.min(n, nextBuffer.available());

            nextBuffer.skip(toSkip);

            return toSkip;
        }

        @Override
        public int available() {
            return dataReader.available();
        }

        @Override
        public void close() {
            finish();
        }

        private void ensureBuffer() {
            if (nextBuffer == null || nextBuffer.consumed()) {
                this.nextBuffer = readBuffer();
                if (trailingEol) {
                    // skip it, as it will be part of next buffer
                    dataReader.skip(2);
                }
            }
        }

        private BufferData readBuffer() {
            int nextEol = dataReader.findNewLine(maxEol);
            if (nextEol == maxEol) {
                if (trailingEol) {
                    trailingEol = false;
                    return BufferData.create(BufferData.create(EOL), dataReader.readBuffer(maxEol));
                } else {
                    return dataReader.readBuffer(maxEol);
                }
            } else {
                if (nextEol == boundary.length() || nextEol == endBoundary.length()) {
                    BufferData untilEol = dataReader.getBuffer(nextEol);
                    String theString = untilEol.readString(nextEol, StandardCharsets.US_ASCII);
                    if (boundary.equals(theString) || endBoundary.equals(theString)) {
                        // we cannot skip the beginning of the boundary (as we call getBuffer, not readBuffer)
                        trailingEol = false;
                        finished = true;
                        return null;
                    }
                    if (trailingEol) {
                        return BufferData.create(BufferData.create(EOL), dataReader.readBuffer(nextEol));
                    } else {
                        trailingEol = true;
                        return dataReader.readBuffer(nextEol);
                    }

                } else {
                    if (trailingEol) {
                        return BufferData.create(BufferData.create(EOL), dataReader.readBuffer(nextEol));
                    } else {
                        trailingEol = true;
                        return dataReader.readBuffer(nextEol);
                    }
                }
            }
        }

        private boolean consumed() {
            return finished;
        }

        private void finish() {
            while (!finished) {
                ensureBuffer();
                if (!finished) {
                    // we may have finished in ensureBuffer()
                    nextBuffer.skip(nextBuffer.available());
                }
            }
        }
    }

}
