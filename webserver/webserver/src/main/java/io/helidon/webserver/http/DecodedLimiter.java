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

package io.helidon.webserver.http;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

import io.helidon.common.Api;
import io.helidon.http.HttpException;
import io.helidon.http.Status;

/**
 * Limits decoded request entity streams.
 */
@Api.Internal
public final class DecodedLimiter {
    private DecodedLimiter() {
    }

    /**
     * Limit a decoded stream to the specified size.
     *
     * @param stream  stream to limit
     * @param maxSize maximum size, or a negative number to disable limiting
     * @return limited stream, or the original stream when the limit is disabled
     */
    public static InputStream limit(InputStream stream, long maxSize) {
        if (maxSize < 0) {
            return stream;
        }
        return new LimitedInputStream(stream, maxSize);
    }

    private static final class LimitedInputStream extends InputStream {
        private final InputStream delegate;
        private final long maxSize;

        private long read;

        private LimitedInputStream(InputStream delegate, long maxSize) {
            this.delegate = Objects.requireNonNull(delegate);
            this.maxSize = maxSize;
        }

        @Override
        public int read() throws IOException {
            int next = delegate.read();
            if (next != -1) {
                count(1);
            }
            return next;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int bytes = delegate.read(b, off, len);
            if (bytes > 0) {
                count(bytes);
            }
            return bytes;
        }

        @Override
        public long skip(long n) throws IOException {
            long skipped = delegate.skip(n);
            if (skipped > 0) {
                count(skipped);
            }
            return skipped;
        }

        @Override
        public int available() throws IOException {
            return delegate.available();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }

        void count(long bytes) {
            read += bytes;
            if (read > maxSize) {
                throw new HttpException("Maximum decoded entity size exceeded", Status.REQUEST_ENTITY_TOO_LARGE_413, false);
            }
        }
    }
}
