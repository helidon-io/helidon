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

import io.helidon.common.GenericType;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;
import io.helidon.http.WritableHeaders;
import io.helidon.http.media.MediaContext;

class ReadablePartLength extends ReadablePartAbstract {
    private final MediaContext context;
    private final DataReader dataReader;

    private long partLength;
    private PartInputStream inputStream;

    ReadablePartLength(MediaContext context,
                       WritableHeaders<?> headers,
                       DataReader dataReader,
                       int index,
                       long partLength) {
        super(headers, index);
        this.context = context;
        this.dataReader = dataReader;
        this.partLength = partLength;
    }

    @Override
    public PartInputStream inputStream() {
        if (inputStream == null) {
            this.inputStream = new PartInputStream(dataReader, partLength);
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
        private final DataReader dataReader;

        private long partRemaining;

        PartInputStream(DataReader dataReader, long partLength) {
            this.dataReader = dataReader;
            this.partRemaining = partLength;
        }

        @Override
        public int read() {
            if (partRemaining == 0) {
                return -1;
            }

            partRemaining--;
            return dataReader.read();
        }

        @Override
        public int read(byte[] b) {
            if (partRemaining == 0) {
                return -1;
            }
            int toRead = Math.min(b.length, (int) partRemaining);

            BufferData buffer = dataReader.readBuffer(toRead);
            int read = buffer.read(b, 0, toRead);
            partRemaining -= read;
            return read;
        }

        @Override
        public int read(byte[] b, int off, int len) {
            if (partRemaining == 0) {
                return -1;
            }
            int toRead = Math.min(len, (int) partRemaining);

            BufferData buffer = dataReader.readBuffer(toRead);
            int read = buffer.read(b, 0, toRead);
            partRemaining -= read;
            return read;
        }

        @Override
        public long skip(long n) {
            long toSkip = Math.min(n, partRemaining);
            long skipped = toSkip;
            partRemaining -= toSkip;
            while (toSkip > Integer.MAX_VALUE) {
                dataReader.skip(Integer.MAX_VALUE);
                toSkip -= Integer.MAX_VALUE;
            }
            dataReader.skip((int) toSkip);

            return skipped;
        }

        @Override
        public int available() {
            return dataReader.available();
        }

        @Override
        public void close() {
            finish();
        }

        private boolean consumed() {
            return partRemaining == 0;
        }

        private void finish() {
            long toSkip = partRemaining;
            long skipped = skip(toSkip);
            if (skipped != toSkip) {
                throw new IllegalStateException("Failed to finish part, could not skip bytes");
            }
        }
    }
}
