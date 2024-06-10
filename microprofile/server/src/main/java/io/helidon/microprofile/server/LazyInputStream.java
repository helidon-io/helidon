/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.microprofile.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import io.helidon.common.LazyValue;
import io.helidon.webserver.http.ServerRequest;

/*
 Only obtains the input stream from request when used.
 This is to work around the case where we have a Jersey application before other routing for requests with entity.
 Before this fix, we would request the input stream, and the next component would fail with input stream already requested.
 */
class LazyInputStream extends InputStream {
    private final LazyValue<InputStream> delegate;

    LazyInputStream(ServerRequest req) {
        delegate = LazyValue.create(() -> req.content().inputStream());
    }

    @Override
    public int read() throws IOException {
        return delegate.get().read();
    }

    @Override
    public int read(byte[] b) throws IOException {
        return delegate.get().read(b);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        return delegate.get().read(b, off, len);
    }

    @Override
    public byte[] readAllBytes() throws IOException {
        return delegate.get().readAllBytes();
    }

    @Override
    public byte[] readNBytes(int len) throws IOException {
        return delegate.get().readNBytes(len);
    }

    @Override
    public int readNBytes(byte[] b, int off, int len) throws IOException {
        return delegate.get().readNBytes(b, off, len);
    }

    @Override
    public long skip(long n) throws IOException {
        return delegate.get().skip(n);
    }

    @Override
    public void skipNBytes(long n) throws IOException {
        delegate.get().skipNBytes(n);
    }

    @Override
    public int available() throws IOException {
        return delegate.get().available();
    }

    @Override
    public void close() throws IOException {
        delegate.get().close();
    }

    @Override
    public void mark(int readlimit) {
        delegate.get().mark(readlimit);
    }

    @Override
    public void reset() throws IOException {
        delegate.get().reset();
    }

    @Override
    public boolean markSupported() {
        return delegate.get().markSupported();
    }

    @Override
    public long transferTo(OutputStream out) throws IOException {
        return delegate.get().transferTo(out);
    }
}
