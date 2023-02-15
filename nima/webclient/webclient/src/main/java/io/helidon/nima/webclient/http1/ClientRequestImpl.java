/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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
import java.util.concurrent.LinkedBlockingDeque;
import java.util.function.Function;

import io.helidon.common.GenericType;
import io.helidon.common.buffers.BufferData;
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
import io.helidon.nima.webclient.ConnectionKey;
import io.helidon.nima.webclient.UriHelper;

import static java.lang.System.Logger.Level.DEBUG;

class ClientRequestImpl implements Http1ClientRequest {
    private static final System.Logger LOGGER = System.getLogger(ClientRequestImpl.class.getName());
    private static final byte[] TERMINATING_CHUNK = "0\r\n\r\n".getBytes(StandardCharsets.UTF_8);
    private static final byte[] CRLF_BYTES = "\r\n".getBytes(StandardCharsets.UTF_8);
    private static final String HTTPS = "https";
    private static final Map<KeepAliveKey, Queue<Http1ClientConnection>> CHANNEL_CACHE = new ConcurrentHashMap<>();

    private WritableHeaders<?> explicitHeaders = WritableHeaders.create();
    private final UriQueryWriteable query;
    private final Map<String, String> pathParams = new HashMap<>();

    private final Http1ClientImpl client;
    private final Http.Method method;
    private final UriHelper uri;
    private final boolean defaultKeepAlive = true;
    private final SocketOptions channelOptions;
    private final BufferData writeBuffer = BufferData.growing(128);

    private Tls tls;
    private String uriTemplate;
    private ClientConnection connection;
    private MediaContext mediaContext;
    private int maxHeaderSize;
    private int maxStatusLineLength;
    private boolean sendExpect100Continue;
    private boolean validateHeaders;

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

        this.mediaContext = client.builder.mediaContext();
        this.maxHeaderSize = client.builder.maxHeaderSize();
        this.maxStatusLineLength = client.builder.maxStatusLineLength();
        this.sendExpect100Continue = client.builder.sendExpect100Continue();
        this.validateHeaders = client.builder.validateHeaders();
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
    public Http1ClientRequest headers(Function<ClientRequestHeaders, WritableHeaders<?>> headersConsumer) {
        this.explicitHeaders = headersConsumer.apply(ClientRequestHeaders.create(explicitHeaders));
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

        headers.set(Header.create(Header.CONTENT_LENGTH, entityBytes.length));

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

        ClientConnectionOutputStream cos = new ClientConnectionOutputStream(writer, reader, headers, writeBuffer,
                                                                            maxStatusLineLength, sendExpect100Continue);

        try {
            streamHandler.handle(cos);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        if (!cos.closed()) {
            throw new IllegalStateException("Output stream was not closed in handler");
        }

        // TODO: This is a temporary workaround to remove garbage data or until https://github.com/helidon-io/helidon/issues/6121 is resolved
        if (cos.chunked) {
            System.out.println("Garbage data from reader:" + reader.readBuffer().debugDataHex(true));
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

    static void writeHeaders(Headers headers, BufferData bufferData) {
        for (HeaderValue header : headers) {
            header.writeHttp1Header(bufferData);
        }
        bufferData.write(CRLF_BYTES);
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
        Http.Status responseStatus = Http1StatusParser.readStatus(reader, maxStatusLineLength);
        ClientResponseHeaders responseHeaders = readHeaders(reader);

        return new ClientResponseImpl(responseStatus, usedHeaders, responseHeaders, connection, reader);
    }

    private ClientResponseHeaders readHeaders(DataReader reader) {
        WritableHeaders<?> writable = Http1HeadersParser.readHeaders(reader, maxHeaderSize, validateHeaders);

        return ClientResponseHeaders.create(writable);
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
                                                                it -> new LinkedBlockingDeque<>(5));

            // TODO we must limit the queue in size
            // Note: Can we use LinkedBlockingQueue  instead of ConcurrentLinkedDeque which is an optionally bounded blocking queue?
            while ((connection = connectionQueue.poll()) != null && !connection.isConnected()) {
            }

            if (connection == null) {
                connection = new Http1ClientConnection(channelOptions,
                                                       connectionQueue,
                                                       new ConnectionKey(uri.scheme(),
                                                                         uri.host(),
                                                                         uri.port(),
                                                                         tls,
                                                                         client.dnsResolver(),
                                                                         client.dnsAddressLookup())).connect();
            } else {
                if (LOGGER.isLoggable(DEBUG)) {
                    LOGGER.log(DEBUG, String.format("[%s] client connection obtained %s",
                                                    connection.channelId(),
                                                    Thread.currentThread().getName()));
                }
            }
        } else {
            connection = new Http1ClientConnection(channelOptions, new ConnectionKey(uri.scheme(),
                                                                                     uri.host(),
                                                                                     uri.port(),
                                                                                     tls,
                                                                                     client.dnsResolver(),
                                                                                     client.dnsAddressLookup())).connect();
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
        private final DataWriter writer;
        private final DataReader reader;
        private boolean chunked;
        private WritableHeaders<?> headers;
        private BufferData firstPacket;
        private BufferData writeBuffer;
        private int maxStatusLineLength;
        private boolean sendExpect100Continue;
        private long bytesWritten;
        private long contentLength;
        private boolean noData = true;

        private boolean closed;

        private ClientConnectionOutputStream(DataWriter writer, DataReader reader, WritableHeaders<?> headers,
                                             BufferData writeBuffer, int maxStatusLineLength, boolean sendExpect100Continue) {
            this.writer = writer;
            this.reader = reader;
            this.headers = headers;
            this.writeBuffer = writeBuffer;
            this.maxStatusLineLength = maxStatusLineLength;
            this.sendExpect100Continue = sendExpect100Continue;
            this.contentLength = headers.contentLength().orElse(-1);
            this.chunked = contentLength == -1 || headers.contains(HeaderValues.TRANSFER_ENCODING_CHUNKED);
        }

        @Override
        public void write(int b) throws IOException {
            // this method should not be called, as we are wrapped with a buffered stream
            byte data[] = {(byte) b};
            write(data, 0, 1);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            if (closed) {
                throw new IOException("Output stream already closed");
            }

            BufferData data = BufferData.create(b, off, len);

            if (!chunked) {
                if (firstPacket == null) {
                    firstPacket = data;
                } else {
                    chunked = true;
                    sendFirstChunk();
                }
                noData = false;
            }

            if (chunked) {
                if (noData) {
                    noData = false;
                    sendHeader();
                }
                writeChunked(data);
            }
        }

        @Override
        public void close() throws IOException {
            if (closed) {
                return;
            }
            this.closed = true;
            if (chunked) {
                if (firstPacket != null) {
                    sendFirstChunk();
                }
                writer.write(BufferData.create(TERMINATING_CHUNK));
            } else {
                headers.remove(Http.Header.TRANSFER_ENCODING);
                if (noData) {
                    headers.set(HeaderValues.CONTENT_LENGTH_ZERO);
                    contentLength = 0;
                }
                if (noData || firstPacket != null) {
                    sendHeader();
                }
                if (firstPacket != null) {
                    writeContent(firstPacket);
                }
            }
            super.close();
        }

        boolean closed() {
            return closed;
        }

        private void writeChunked(BufferData buffer) {
            int available = buffer.available();
            byte[] hex = Integer.toHexString(available).getBytes(StandardCharsets.UTF_8);

            BufferData toWrite = BufferData.create(available + hex.length + 4); // \r\n after size, another after chunk
            toWrite.write(hex);
            toWrite.write(CRLF_BYTES);
            toWrite.write(buffer);
            toWrite.write(CRLF_BYTES);

            writer.writeNow(toWrite);
        }

        private void writeContent(BufferData buffer) throws IOException {
            bytesWritten += buffer.available();
            if (contentLength != -1 && bytesWritten > contentLength) {
                throw new IOException("Content length was set to " + contentLength
                                              + ", but you are writing additional " + (bytesWritten - contentLength) + " "
                                              + "bytes");
            }

            writer.writeNow(buffer);
        }

        private void sendHeader() {
            boolean expects100Continue = sendExpect100Continue && chunked && !noData;
            if (expects100Continue) {
                headers.add(HeaderValues.EXPECT_100);
            }

            if (chunked) {
                // Add chunked encoding, if there is no other transfer-encoding headers
                if (!headers.contains(Http.Header.TRANSFER_ENCODING)) {
                    headers.set(HeaderValues.TRANSFER_ENCODING_CHUNKED);
                } else {
                    // Add chunked encoding, if it's not part of existing transfer-encoding headers
                    if (!headers.contains(HeaderValues.TRANSFER_ENCODING_CHUNKED)) {
                        headers.add(HeaderValues.TRANSFER_ENCODING_CHUNKED);
                    }
                }
                headers.remove(Header.CONTENT_LENGTH);
            }
            writeHeaders(headers, writeBuffer);
            writer.writeNow(writeBuffer);

            if (expects100Continue) {
                Http.Status responseStatus = Http1StatusParser.readStatus(reader, maxStatusLineLength);
                if (responseStatus != Http.Status.CONTINUE_100) {
                    throw new IllegalStateException("Expected a status of '100 Continue' but received a '" +
                                                            responseStatus + "' instead");
                }
            }
        }

        private void sendFirstChunk() {
            sendHeader();
            writeChunked(firstPacket);
            firstPacket = null;
        }
    }
}
