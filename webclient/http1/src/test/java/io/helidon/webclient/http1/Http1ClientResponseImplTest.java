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

package io.helidon.webclient.http1;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.socket.HelidonSocket;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.ClientResponseHeaders;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.media.MediaContext;
import io.helidon.webclient.api.ClientConnection;
import io.helidon.webclient.api.ClientUri;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Http1ClientResponseImplTest {
    @Test
    void inputStreamReadsDirectlyIntoCallerBuffer() throws IOException {
        byte[] content = "response content".getBytes(StandardCharsets.UTF_8);
        var delegate = new TrackingInputStream(content);
        var connection = new TestConnection();
        var complete = new CompletableFuture<Void>();
        var response = response(delegate, content.length, connection, complete);
        byte[] target = new byte[content.length + 4];

        try (InputStream input = response.inputStream()) {
            int read = input.read(target, 2, content.length);

            assertThat(read, is(content.length));
            assertThat(delegate.destination(), sameInstance(target));
            assertThat(Arrays.copyOfRange(target, 2, 2 + content.length), is(content));
        }

        assertThat(complete.isDone(), is(true));
        assertThat(connection.releaseCount(), is(1));
        assertThat(connection.closeCount(), is(0));
        assertThat(delegate.closeCount(), is(1));
    }

    @Test
    void inputStreamClosesResponseAtEndOfStream() throws IOException {
        var delegate = new TrackingInputStream(new byte[] {1});
        var connection = new TestConnection();
        var complete = new CompletableFuture<Void>();
        var response = response(delegate, 1, connection, complete);
        InputStream input = response.inputStream();

        assertThat(input.read(), is(1));
        assertThat(input.available(), is(0));
        assertThat(input.read(), is(-1));
        assertThat(complete.isDone(), is(true));
        assertThat(connection.releaseCount(), is(1));
        assertThat(connection.closeCount(), is(0));

        input.close();
        assertThat(delegate.closeCount(), is(1));
        assertThat(connection.releaseCount(), is(1));
    }

    @Test
    void inputStreamDoesNotUseEncodedContentLengthToDetectEnd() throws IOException {
        var delegate = new TrackingInputStream(new byte[] {1, 2});
        var connection = new TestConnection();
        var complete = new CompletableFuture<Void>();
        var responseHeaders = ClientResponseHeaders.create(WritableHeaders.create()
                .add(HeaderValues.create(HeaderNames.CONTENT_LENGTH, 1))
                .add(HeaderValues.create(HeaderNames.CONTENT_ENCODING, "test")));
        InputStream input = response(delegate, responseHeaders, connection, complete).inputStream();

        assertThat(input.read(), is(1));
        assertThat(complete.isDone(), is(false));
        assertThat(input.read(), is(2));
        assertThat(complete.isDone(), is(false));
        assertThat(input.read(), is(-1));

        assertThat(complete.isDone(), is(true));
        assertThat(connection.releaseCount(), is(1));
        assertThat(connection.closeCount(), is(0));
    }

    @Test
    void inputStreamClosesConnectionWhenClosedBeforeEndOfStream() throws IOException {
        var delegate = new TrackingInputStream(new byte[] {1, 2});
        var connection = new TestConnection();
        var complete = new CompletableFuture<Void>();
        InputStream input = response(delegate, 2, connection, complete).inputStream();

        assertThat(input.read(), is(1));
        input.close();

        assertThat(complete.isDone(), is(true));
        assertThat(connection.releaseCount(), is(0));
        assertThat(connection.closeCount(), is(1));
        assertThat(delegate.closeCount(), is(1));
    }

    @Test
    void inputStreamHonorsBulkReadContract() throws IOException {
        var delegate = new TrackingInputStream(new byte[0]);
        var connection = new TestConnection();
        InputStream input = response(delegate, 0, connection, new CompletableFuture<>()).inputStream();

        assertThat(input.read(), is(-1));

        byte[] target = new byte[1];
        assertThat(input.read(target, 0, 0), is(0));
        assertThrows(IndexOutOfBoundsException.class, () -> input.read(target, 1, 1));
        assertThrows(NullPointerException.class, () -> input.read(null, 0, 1));
        assertThat(delegate.destination(), nullValue());
        assertThat(connection.releaseCount(), is(1));
        assertThat(delegate.closeCount(), is(1));
    }

    @Test
    void inputStreamClosesResponseWhenDelegateCloseFails() throws IOException {
        var delegate = new FailingCloseInputStream();
        var connection = new TestConnection();
        var complete = new CompletableFuture<Void>();
        InputStream input = response(delegate, 1, connection, complete).inputStream();

        IOException exception = assertThrows(IOException.class, input::close);

        assertThat(exception.getMessage(), is("delegate close failed"));
        assertThat(delegate.closeCount(), is(1));
        assertThat(complete.isDone(), is(true));
        assertThat(connection.releaseCount(), is(0));
        assertThat(connection.closeCount(), is(1));

        input.close();
        assertThat(delegate.closeCount(), is(1));
        assertThat(connection.closeCount(), is(1));
    }

    @Test
    void inputStreamReportsDelegateCloseFailureAsIOExceptionAtEndOfStream() throws IOException {
        var delegate = new FailingCloseInputStream();
        var connection = new TestConnection();
        var complete = new CompletableFuture<Void>();
        InputStream input = response(delegate, 1, connection, complete).inputStream();

        IOException exception = assertThrows(IOException.class, input::read);

        assertThat(exception.getMessage(), is("delegate close failed"));
        assertThat(delegate.closeCount(), is(1));
        assertThat(complete.isDone(), is(true));
        assertThat(connection.releaseCount(), is(1));
        assertThat(connection.closeCount(), is(0));

        input.close();
        assertThat(delegate.closeCount(), is(1));
        assertThat(connection.releaseCount(), is(1));
    }

    private static Http1ClientResponseImpl response(InputStream inputStream,
                                                    long contentLength,
                                                    TestConnection connection,
                                                    CompletableFuture<Void> complete) {
        var responseHeaders = ClientResponseHeaders.create(WritableHeaders.create()
                .add(HeaderValues.create(HeaderNames.CONTENT_LENGTH, contentLength)));
        return response(inputStream, responseHeaders, connection, complete);
    }

    private static Http1ClientResponseImpl response(InputStream inputStream,
                                                    ClientResponseHeaders responseHeaders,
                                                    TestConnection connection,
                                                    CompletableFuture<Void> complete) {
        var config = Http1ClientConfig.builder().buildPrototype();
        return new Http1ClientResponseImpl(config,
                                           config.protocolConfig(),
                                           Status.OK_200,
                                           ClientRequestHeaders.create(WritableHeaders.create()),
                                           responseHeaders,
                                           connection,
                                           inputStream,
                                           MediaContext.create(),
                                           ClientUri.create(URI.create("http://localhost")),
                                           complete);
    }

    private static final class TestConnection implements ClientConnection {
        private final DataReader reader = DataReader.create(() -> null);
        private int releaseCount;
        private int closeCount;

        @Override
        public DataReader reader() {
            return reader;
        }

        @Override
        public DataWriter writer() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String channelId() {
            return "test";
        }

        @Override
        public HelidonSocket helidonSocket() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void readTimeout(Duration readTimeout) {
        }

        @Override
        public void releaseResource() {
            releaseCount++;
        }

        @Override
        public void closeResource() {
            closeCount++;
        }

        private int releaseCount() {
            return releaseCount;
        }

        private int closeCount() {
            return closeCount;
        }
    }

    private static final class TrackingInputStream extends InputStream {
        private final byte[] content;
        private int offset;
        private byte[] destination;
        private int closeCount;
        private boolean closed;

        private TrackingInputStream(byte[] content) {
            this.content = content;
        }

        @Override
        public int read() throws IOException {
            checkOpen();
            if (offset == content.length) {
                return -1;
            }
            return content[offset++] & 0xff;
        }

        @Override
        public int read(byte[] bytes, int destinationOffset, int length) throws IOException {
            checkOpen();
            destination = bytes;
            if (offset == content.length) {
                return -1;
            }
            int read = Math.min(length, content.length - offset);
            System.arraycopy(content, offset, bytes, destinationOffset, read);
            offset += read;
            return read;
        }

        @Override
        public void close() {
            closeCount++;
            closed = true;
        }

        @Override
        public int available() throws IOException {
            checkOpen();
            return content.length - offset;
        }

        private byte[] destination() {
            return destination;
        }

        private int closeCount() {
            return closeCount;
        }

        private void checkOpen() throws IOException {
            if (closed) {
                throw new IOException("stream closed");
            }
        }
    }

    private static final class FailingCloseInputStream extends InputStream {
        private int closeCount;

        @Override
        public int read() {
            return -1;
        }

        @Override
        public void close() throws IOException {
            closeCount++;
            throw new IOException("delegate close failed");
        }

        private int closeCount() {
            return closeCount;
        }
    }
}
