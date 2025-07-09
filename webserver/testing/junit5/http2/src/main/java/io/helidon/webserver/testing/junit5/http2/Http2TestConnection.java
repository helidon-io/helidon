/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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

package io.helidon.webserver.testing.junit5.http2;

import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.tls.Tls;
import io.helidon.http.Method;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http2.FlowControl;
import io.helidon.http.http2.Http2ConnectionWriter;
import io.helidon.http.http2.Http2ErrorCode;
import io.helidon.http.http2.Http2Flag;
import io.helidon.http.http2.Http2FrameData;
import io.helidon.http.http2.Http2FrameHeader;
import io.helidon.http.http2.Http2FrameType;
import io.helidon.http.http2.Http2FrameTypes;
import io.helidon.http.http2.Http2GoAway;
import io.helidon.http.http2.Http2Headers;
import io.helidon.http.http2.Http2HuffmanDecoder;
import io.helidon.http.http2.Http2RstStream;
import io.helidon.http.http2.Http2Setting;
import io.helidon.http.http2.Http2Settings;
import io.helidon.http.http2.Http2Util;
import io.helidon.http.http2.Http2WindowUpdate;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.ConnectionKey;
import io.helidon.webclient.api.DefaultDnsResolver;
import io.helidon.webclient.api.DnsAddressLookup;
import io.helidon.webclient.api.Proxy;
import io.helidon.webclient.api.TcpClientConnection;
import io.helidon.webclient.api.WebClient;

import org.hamcrest.Matchers;

import static java.lang.System.Logger.Level.DEBUG;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Http/2 low-level testing client connection.
 */
public class Http2TestConnection implements AutoCloseable {

    private static final System.Logger LOGGER = System.getLogger(Http2TestConnection.class.getName());
    private static final int FRAME_HEADER_LENGTH = 9;

    private final TcpClientConnection conn;
    private final Http2ConnectionWriter dataWriter;
    private final DataReader reader;
    private final ArrayBlockingQueue<Http2FrameData> readQueue = new ArrayBlockingQueue<>(100);
    private final Thread readThread;
    private final ClientUri clientUri;
    private final Http2Headers.DynamicTable requestDynamicTable =
            Http2Headers.DynamicTable.create(Http2Setting.HEADER_TABLE_SIZE.defaultValue());
    private final Http2HuffmanDecoder requestHuffman = Http2HuffmanDecoder.create();

    Http2TestConnection(URI uri) {
        clientUri = ClientUri.create(uri);
        ConnectionKey connectionKey = new ConnectionKey(clientUri.scheme(),
                                                        clientUri.host(),
                                                        clientUri.port(),
                                                        Tls.builder().enabled(false).build(),
                                                        DefaultDnsResolver.create(),
                                                        DnsAddressLookup.defaultLookup(),
                                                        Proxy.noProxy());

        conn = TcpClientConnection.create(WebClient.builder()
                                                  .baseUri(clientUri)
                                                  .build(),
                                          connectionKey,
                                          List.of(),
                                          connection -> false,
                                          connection -> {
                                          })
                .connect();

        conn.writer().writeNow(Http2Util.prefaceData());
        reader = conn.reader();
        dataWriter = new Http2ConnectionWriter(conn.helidonSocket(), conn.writer(), List.of());
        readThread = Thread
                .ofVirtual()
                .start(() -> {
                    try {
                        for (;;) {
                            if (Thread.interrupted()) {
                                return;
                            }
                            BufferData frameHeaderBuffer = reader.readBuffer(FRAME_HEADER_LENGTH);
                            Http2FrameHeader frameHeader = Http2FrameHeader.create(frameHeaderBuffer);
                            LOGGER.log(DEBUG, () -> "<-- " + frameHeader);
                            readQueue.add(new Http2FrameData(frameHeader, reader.readBuffer(frameHeader.length())));
                        }
                    } catch (DataReader.InsufficientDataAvailableException | UncheckedIOException e) {
                        // closed connection
                    }
                });

        sendSettings(Http2Settings.builder()
                             .add(Http2Setting.INITIAL_WINDOW_SIZE, 65535L)
                             .add(Http2Setting.MAX_FRAME_SIZE, 16384L)
                             .add(Http2Setting.ENABLE_PUSH, false)
                             .build());
    }

    /**
     * Send settings frame.
     *
     * @param http2Settings frame to send
     * @return this connection
     */
    public Http2TestConnection sendSettings(Http2Settings http2Settings) {
        Http2Flag.SettingsFlags flags = Http2Flag.SettingsFlags.create(0);
        Http2FrameData frameData = http2Settings.toFrameData(null, 0, flags);
        writer().write(frameData);
        return this;
    }

    /**
     * Return connection writer for direct frame sending.
     *
     * @return connection writer
     */
    public Http2ConnectionWriter writer() {
        return dataWriter;
    }

    /**
     * Send HTTP request with given stream id with single data frame created from supplied buffer data,
     * dataframe has end of stream flag.
     *
     * @param streamId send request as given stream id
     * @param method   http method
     * @param path     context path
     * @param headers  http headers
     * @param payload  payload data which has to fit in single frame
     * @return this connection
     */
    public Http2TestConnection request(int streamId, Method method, String path, WritableHeaders<?> headers, BufferData payload) {
        Http2Headers h2Headers = Http2Headers.create(headers);
        h2Headers.method(method);
        h2Headers.path(path);
        h2Headers.scheme(clientUri().scheme());

        writer().writeHeaders(h2Headers,
                              streamId,
                              Http2Flag.HeaderFlags.create(Http2Flag.END_OF_HEADERS),
                              FlowControl.Outbound.NOOP);

        Http2FrameData frameDataData =
                new Http2FrameData(Http2FrameHeader.create(payload.available(),
                                                           Http2FrameTypes.DATA,
                                                           Http2Flag.DataFlags.create(Http2Flag.END_OF_STREAM),
                                                           streamId),
                                   payload);
        writer().writeData(frameDataData, FlowControl.Outbound.NOOP);
        return this;
    }

    /**
     * Await next frame, blocks until next frame arrive.
     *
     * @param timeout timeout for blocking
     * @return next frame in order of reading from socket
     */
    public Http2FrameData awaitNextFrame(Duration timeout) {
        try {
            return readQueue.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Wait for the next frame and assert its frame type to be RST_STREAM.
     * @param streamId stream id asserted from retrieved RST_STREAM frame.
     * @param timeout timeout for blocking
     * @return the frame
     */
    public Http2RstStream assertRstStream(int streamId, Duration timeout) {
        Http2FrameData frame = assertNextFrame(Http2FrameType.RST_STREAM, timeout);
        assertThat("Stream ID doesn't match.", frame.header().streamId(), Matchers.equalTo(streamId));
        return Http2RstStream.create(frame.data());
    }

    /**
     * Wait for the next frame and assert its frame type to be SETTINGS.
     * @param timeout timeout for blocking
     * @return the frame
     */
    public Http2Settings assertSettings(Duration timeout) {
        Http2FrameData frame = assertNextFrame(Http2FrameType.SETTINGS, timeout);
        return Http2Settings.create(frame.data());
    }

    /**
     * Wait for the next frame and assert its frame type to be WINDOWS_UPDATE.
     * @param streamId stream id asserted from retrieved WINDOWS_UPDATE frame.
     * @param timeout timeout for blocking
     * @return the frame
     */
    public Http2WindowUpdate assertWindowsUpdate(int streamId, Duration timeout) {
        Http2FrameData frame = assertNextFrame(Http2FrameType.WINDOW_UPDATE, timeout);
        assertThat(frame.header().streamId(), Matchers.equalTo(streamId));
        return Http2WindowUpdate.create(frame.data());
    }

    /**
     * Wait for the next frame and assert its frame type to be HEADERS.
     * @param streamId stream id asserted from retrieved HEADERS frame.
     * @param timeout timeout for blocking
     * @return the frame
     */
    public Http2Headers assertHeaders(int streamId, Duration timeout) {
        Http2FrameData frame = assertNextFrame(Http2FrameType.HEADERS, timeout);
        assertThat(frame.header().streamId(), Matchers.equalTo(streamId));
        return Http2Headers.create(null, requestDynamicTable, requestHuffman, frame);
    }

    /**
     * Wait for the next frame and assert its frame type.
     * @param frameType expected type of frame
     * @param timeout timeout for blocking
     * @return the frame
     */
    public Http2FrameData assertNextFrame(Http2FrameType frameType, Duration timeout) {
        Http2FrameData frame = awaitNextFrame(timeout);
        assertThat(frame.header().type(), Matchers.equalTo(frameType));
        return frame;
    }

    /**
     * Wait for the next frame and assert its frame type to be GO_AWAY.
     * @param errorCode expected error code
     * @param message expected go away message
     * @param timeout timeout for blocking
     * @return the frame
     */
    public Http2FrameData assertGoAway(Http2ErrorCode errorCode, String message, Duration timeout) {
        Http2FrameData frame = assertNextFrame(Http2FrameType.GO_AWAY, timeout);

        Http2GoAway goAway = Http2GoAway.create(frame.data());
        assertThat(goAway.errorCode(), is(errorCode));
        assertThat(frame.data().readString(frame.data().available()), is(message));
        return frame;
    }

    @Override
    public void close() {
        readThread.interrupt();
        conn.closeResource();
    }

    /**
     * Client uri used for connection, derived from Helidon test server.
     * @return client uri
     */
    public ClientUri clientUri() {
        return clientUri;
    }
}
