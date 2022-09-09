/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

import io.helidon.common.GenericType;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.Bytes;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.http.ClientRequestHeaders;
import io.helidon.common.http.ClientResponseHeaders;
import io.helidon.common.http.Headers;
import io.helidon.common.http.Http;
import io.helidon.common.http.Http.Header;
import io.helidon.common.http.Http.HeaderValue;
import io.helidon.common.http.Http.HeaderValues;
import io.helidon.common.http.Http1HeadersParser;
import io.helidon.common.http.WritableHeaders;
import io.helidon.common.socket.SocketOptions;
import io.helidon.common.uri.UriEncoding;
import io.helidon.common.uri.UriQueryWriteable;
import io.helidon.nima.common.tls.Tls;
import io.helidon.nima.http.media.EntityWriter;
import io.helidon.nima.http.media.MediaContext;
import io.helidon.nima.webclient.ClientConnection;
import io.helidon.nima.webclient.UriHelper;

import static java.lang.System.Logger.Level.DEBUG;

class ClientRequestImpl implements Http1ClientRequest {
    private static final System.Logger LOGGER = System.getLogger(ClientRequestImpl.class.getName());
    private static final byte[] TERMINATING_CHUNK = "0\r\n\r\n".getBytes(StandardCharsets.UTF_8);
    private static final byte[] CRLF_BYTES = "\r\n".getBytes(StandardCharsets.UTF_8);
    private static final String HTTPS = "https";
    private static final Map<KeepAliveKey, Queue<Http1ClientConnection>> CHANNEL_CACHE = new ConcurrentHashMap<>();

    private final WritableHeaders<?> explicitHeaders = WritableHeaders.create();
    private final UriQueryWriteable query;
    private final Map<String, String> pathParams = new HashMap<>();

    private final Http1ClientImpl client;
    private final Http.Method method;
    private final UriHelper uri;
    private final boolean defaultKeepAlive = true;
    private final SocketOptions channelOptions;
    private final BufferData writeBuffer = BufferData.growing(128);
    // todo configurable
    private MediaContext mediaContext = MediaContext.create();

    private Tls tls;
    private String uriTemplate;
    private ClientConnection connection;

    ClientRequestImpl(Http1ClientImpl client,
                      Http.Method method,
                      UriHelper helper,
                      UriQueryWriteable query) {
        this.client = client;
        this.method = method;
        this.uri = helper;

        this.tls = client.tls();
        this.channelOptions = client.socketOptions();
        this.query = query;
    }

    @Override
    public Http1ClientRequest uri(String uri) {
        if (uri.indexOf('{') > -1) {
            this.uriTemplate = uri;
        } else {
            uri(URI.create(UriEncoding.encodeUri(uri)));
        }

        return this;
    }

    @Override
    public Http1ClientRequest tls(Tls tls) {
        this.tls = tls;
        return this;
    }

    @Override
    public Http1ClientRequest uri(URI uri) {
        this.uriTemplate = null;
        this.uri.resolve(uri, query);
        return this;
    }

    @Override
    public Http1ClientRequest header(HeaderValue header) {
        this.explicitHeaders.set(header);
        return this;
    }

    @Override
    public Http1ClientRequest pathParam(String name, String value) {
        pathParams.put(name, value);
        return this;
    }

    @Override
    public Http1ClientRequest queryParam(String name, String... values) {
        query.set(name, values);
        return this;
    }

    @Override
    public Http1ClientResponse request() {
        return submit(BufferData.EMPTY_BYTES);
    }

    @Override
    public Http1ClientResponse submit(Object entity) {
        // todo validate request ok
        if (uriTemplate != null) {
            String resolved = resolvePathParams(uriTemplate);
            this.uri.resolve(URI.create(UriEncoding.encodeUri(resolved)), query);
        }
        ClientRequestHeaders headers = ClientRequestHeaders.create(explicitHeaders);
        boolean keepAlive = handleKeepAlive(headers);

        ClientConnection connection = getConnection(keepAlive);
        DataWriter writer = connection.writer();
        DataReader reader = connection.reader();

        writeBuffer.clear();
        // I have a valid connection
        prologue(writeBuffer);

        headers.setIfAbsent(Header.create(Header.HOST, uri.authority()));

        byte[] entityBytes;
        if (entity == BufferData.EMPTY_BYTES) {
            entityBytes = BufferData.EMPTY_BYTES;
        } else {
            entityBytes = entityBytes(entity, headers);
        }

        headers.setIfAbsent(Header.create(Header.CONTENT_LENGTH, String.valueOf(entityBytes.length)));

        writeHeaders(headers, writeBuffer);
        if (entityBytes.length > 0) {
            writeBuffer.write(entityBytes);
        }
        writer.write(writeBuffer);

        return readResponse(headers, connection, reader);
    }

    @Override
    public Http1ClientResponse outputStream(OutputStreamHandler streamHandler) {
        // todo validate request ok

        WritableHeaders<?> headers = WritableHeaders.create(explicitHeaders);
        boolean keepAlive = handleKeepAlive(headers);

        ClientConnection connection = getConnection(keepAlive);
        DataWriter writer = connection.writer();
        DataReader reader = connection.reader();

        // I have a valid connection
        writeBuffer.clear();
        prologue(writeBuffer);

        headers.setIfAbsent(Header.create(Header.HOST, uri.authority()));
        // hardcoded chunked encoding, as we have an output stream
        // todo we may optimize small amounts
        boolean chunked = false;
        if (!headers.contains(Header.CONTENT_LENGTH)) {
            chunked = true;
            headers.set(HeaderValues.TRANSFER_ENCODING_CHUNKED);
        }

        writeHeaders(headers, writeBuffer);
        writer.writeNow(writeBuffer);

        // todo use 100 continue, so we know the endpoint exists before we start sending
        // entity

        // we have written the prologue and headers, now it is time to handle the output stream
        ClientConnectionOutputStream cos = new ClientConnectionOutputStream(connection, writer, chunked);

        try {
            streamHandler.handle(cos);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        if (!cos.closed()) {
            throw new IllegalStateException("Output stream was not closed in handler");
        }

        return readResponse(ClientRequestHeaders.create(headers), connection, reader);
    }

    @Override
    public URI resolvedUri() {
        if (uriTemplate != null) {
            String resolved = resolvePathParams(uriTemplate);
            this.uri.resolve(URI.create(UriEncoding.encodeUri(resolved)), query);
        }
        return URI.create(this.uri.scheme() + "://"
                                  + uri.authority()
                                  + uri.pathWithQuery(query));

    }

    @Override
    public Http1ClientRequest connection(ClientConnection connection) {
        this.connection = connection;
        return this;
    }

    void writeHeaders(Headers headers, BufferData bufferData) {
        for (HeaderValue header : headers) {
            header.writeHttp1Header(bufferData);
        }
        bufferData.write(Bytes.CR_BYTE);
        bufferData.write(Bytes.LF_BYTE);
    }

    UriHelper uriHelper() {
        return uri;
    }

    private void prologue(BufferData nonEntityData) {
        nonEntityData.writeAscii(method.text() + " " + uri.pathWithQuery(query) + " HTTP/1.1\r\n");
    }

    private String resolvePathParams(String path) {
        String result = path;
        for (Map.Entry<String, String> entry : pathParams.entrySet()) {
            String name = entry.getKey();
            String value = entry.getValue();

            result = result.replace("{" + name + "}", value);
        }

        if (result.contains("{")) {
            throw new IllegalArgumentException("Not all path parameters are defined. Template after resolving parameters: "
                                                       + result);
        }

        return result;
    }

    private Http1ClientResponse readResponse(ClientRequestHeaders usedHeaders, ClientConnection connection, DataReader reader) {
        Http.Status responseStatus = readStatus(reader);
        ClientResponseHeaders responseHeaders = readHeaders(reader);

        return new ClientResponseImpl(responseStatus, usedHeaders, responseHeaders, connection, reader);
    }

    private ClientResponseHeaders readHeaders(DataReader reader) {
        // todo configurable max headers and validate headers
        int maxHeaderSize = 8192;
        boolean validateHeaders = true;

        WritableHeaders<?> writable = Http1HeadersParser.readHeaders(reader, maxHeaderSize, validateHeaders);

        return ClientResponseHeaders.create(writable);
    }

    private Http.Status readStatus(DataReader reader) {
        // todo configurable max status line length
        int newLine = reader.findNewLine(256);
        if (newLine == 4096) {
            throw new IllegalStateException("HTTP Response did not contain HTTP status line. Line: \n"
                                                    + reader.readBuffer(newLine).debugDataHex());
        }
        int slash = reader.findOrNewLine(Bytes.SLASH_BYTE, newLine);
        if (slash == newLine) {
            throw new IllegalStateException("HTTP Response did not contain HTTP status line. Line: \n"
                                                    + reader.readBuffer(newLine).debugDataHex());
        }
        String protocol = reader.readAsciiString(slash);
        if (!protocol.equals("HTTP")) {
            throw new IllegalStateException("HTTP response did not contain correct status line. Protocol is not HTTP: \n"
                                                    + BufferData.create(protocol.getBytes(StandardCharsets.US_ASCII))
                    .debugDataHex());
        }
        reader.skip(1); // /
        newLine -= slash;
        newLine--;
        int space = reader.findOrNewLine(Bytes.SPACE_BYTE, newLine);
        if (space == newLine) {
            throw new IllegalStateException("HTTP Response did not contain HTTP status line. Line: HTTP/\n"
                                                    + reader.readBuffer(newLine).debugDataHex());
        }
        String protocolVersion = reader.readAsciiString(space);
        reader.skip(1); // space
        newLine -= space;
        newLine--;
        if (!protocolVersion.equals("1.1")) {
            throw new IllegalStateException("HTTP response did not contain correct status line. Version is not 1.1: \n"
                                                    + BufferData.create(protocolVersion.getBytes(StandardCharsets.US_ASCII))
                    .debugDataHex());
        }
        // HTTP/1.1 200 OK
        space = reader.findOrNewLine(Bytes.SPACE_BYTE, newLine);
        if (space == newLine) {
            throw new IllegalStateException("HTTP Response did not contain HTTP status line. Line: HTTP/1.1\n"
                                                    + reader.readBuffer(newLine).debugDataHex());
        }
        String code = reader.readAsciiString(space);
        reader.skip(1); // the new line
        newLine -= space;
        newLine--;
        String phrase = reader.readAsciiString(newLine); // the rest of the line is reason phrase
        reader.skip(2); // skip the last CRLF

        try {
            return Http.Status.create(Integer.parseInt(code), phrase);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("HTTP Response did not cntain HTTP status line. Line HTTP/1.1 \n"
                                                    + BufferData.create(code.getBytes(StandardCharsets.US_ASCII)) + "\n"
                                                    + BufferData.create(phrase.getBytes(StandardCharsets.US_ASCII)));
        }
    }

    private boolean handleKeepAlive(WritableHeaders<?> headers) {
        if (headers.contains(HeaderValues.CONNECTION_CLOSE)) {
            return false;
        }
        if (defaultKeepAlive) {
            headers.setIfAbsent(HeaderValues.CONNECTION_KEEP_ALIVE);
            return true;
        }
        if (headers.contains(HeaderValues.CONNECTION_KEEP_ALIVE)) {
            return true;
        }
        headers.set(HeaderValues.CONNECTION_CLOSE);
        return false;
    }

    private ClientConnection getConnection(boolean keepAlive) {
        if (this.connection != null) {
            return this.connection;
        }

        Http1ClientConnection connection;
        Tls tls;
        if (uri.scheme().equals(HTTPS)) {
            tls = this.tls;
        } else {
            tls = null;
        }

        if (keepAlive) {
            // todo add timeouts, proxy and tls to the key
            KeepAliveKey keepAliveKey = new KeepAliveKey(uri.scheme(), uri.authority(), tls);
            var connectionQueue = CHANNEL_CACHE.computeIfAbsent(keepAliveKey,
                                                                it -> new ConcurrentLinkedDeque<>());

            // TODO we must limit the queue in size
            while ((connection = connectionQueue.poll()) != null && !connection.isConnected()) {
            }

            if (connection == null) {
                connection = new Http1ClientConnection(channelOptions,
                                                       connectionQueue,
                                                       uri.host(),
                                                       uri.port(),
                                                       tls).connect();
            } else {
                if (LOGGER.isLoggable(DEBUG)) {
                    LOGGER.log(DEBUG, String.format("[%s] client connection obtained %s",
                                                    connection.channelId(),
                                                    Thread.currentThread().getName()));
                }
            }
        } else {
            connection = new Http1ClientConnection(channelOptions, uri.host(), uri.port(), tls).connect();
        }
        return connection;
    }

    private byte[] entityBytes(Object entity, ClientRequestHeaders headers) {
        if (entity instanceof byte[]) {
            return (byte[]) entity;
        }
        GenericType<Object> genericType = GenericType.create(entity);
        EntityWriter<Object> writer = mediaContext.writer(genericType, headers);

        // todo this should use output stream of client, but that would require delaying header write
        // to first byte written
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        writer.write(genericType, entity, bos, headers);
        return bos.toByteArray();
    }

    private record KeepAliveKey(String scheme, String authority, Tls tlsConfig) {
    }

    private static class ClientConnectionOutputStream extends OutputStream {
        private final ClientConnection connection;
        private final DataWriter writer;
        private final boolean chunked;

        private boolean closed;

        private ClientConnectionOutputStream(ClientConnection connection, DataWriter writer, boolean chunked) {
            this.connection = connection;
            this.writer = writer;
            this.chunked = chunked;
        }

        @Override
        public void write(int b) throws IOException {
            // this method should not be called, as we are wrapped with a buffered stream
            chunk(0, 1, (byte) b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            chunk(off, len, b);
        }

        @Override
        public void close() throws IOException {
            if (closed) {
                return;
            }
            this.closed = true;
            if (chunked) {
                writer.write(BufferData.create(TERMINATING_CHUNK));
            }
            super.close();
        }

        boolean closed() {
            return closed;
        }

        private void chunk(int offset, int length, byte... bytes) {
            if (chunked) {
                byte[] hexLen = Integer.toHexString(length).getBytes(StandardCharsets.UTF_8);
                // cheaper to copy in memory than to write twice to channel
                byte[] buffer = new byte[hexLen.length + length + 4]; // add CRLF after each
                System.arraycopy(hexLen, 0, buffer, 0, hexLen.length);
                System.arraycopy(CRLF_BYTES, 0, buffer, hexLen.length, 2);
                System.arraycopy(bytes, offset, buffer, hexLen.length + 2, length);
                System.arraycopy(CRLF_BYTES, 0, buffer, hexLen.length + 2 + length, 2);
                writer.writeNow(BufferData.create(buffer));
            } else {
                writer.writeNow(BufferData.create(bytes, offset, length));
            }
        }
    }
}
