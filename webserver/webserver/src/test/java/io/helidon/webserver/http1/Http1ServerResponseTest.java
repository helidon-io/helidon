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

package io.helidon.webserver.http1;

import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;

import javax.net.ssl.SSLException;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataWriter;
import io.helidon.common.socket.SocketWriterException;
import io.helidon.http.ServerRequestHeaders;
import io.helidon.http.WritableHeaders;
import io.helidon.http.encoding.ContentEncodingContext;
import io.helidon.http.media.MediaContext;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.ListenerContext;
import io.helidon.webserver.ServerConnectionException;
import io.helidon.webserver.WebServer;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Http1ServerResponseTest {

    @Test
    void directSendWrapsUncheckedIOException() {
        Http1ServerResponse response = createResponse(new UncheckedIOException(new SocketException("Broken pipe")));

        ServerConnectionException exception = assertThrows(ServerConnectionException.class,
                                                           () -> response.send("hello".getBytes(StandardCharsets.UTF_8)));

        assertAll(
                () -> assertThat(exception.getCause(), instanceOf(UncheckedIOException.class)),
                () -> assertThat(exception.getCause().getCause(), instanceOf(SocketException.class))
        );
    }

    @Test
    void directSendWrapsSocketWriterException() {
        Http1ServerResponse response =
                createResponse(new SocketWriterException(new UncheckedIOException(new SSLException("Engine is closed"))));

        ServerConnectionException exception = assertThrows(ServerConnectionException.class,
                                                           () -> response.send("hello".getBytes(StandardCharsets.UTF_8)));

        assertAll(
                () -> assertThat(exception.getCause(), instanceOf(SocketWriterException.class)),
                () -> assertThat(exception.getCause().getCause(), instanceOf(UncheckedIOException.class)),
                () -> assertThat(exception.getCause().getCause().getCause(), instanceOf(SSLException.class))
        );
    }

    @Test
    void streamingCommitWrapsSocketWriterException() throws Exception {
        Http1ServerResponse response = createResponse(
                new SocketWriterException(new UncheckedIOException(new SocketException("Connection reset by peer"))));

        OutputStream outputStream = response.outputStream();
        outputStream.write("hello".getBytes(StandardCharsets.UTF_8));

        ServerConnectionException exception = assertThrows(ServerConnectionException.class, response::commit);

        assertAll(
                () -> assertThat(exception.getCause(), instanceOf(SocketWriterException.class)),
                () -> assertThat(exception.getCause().getCause(), instanceOf(UncheckedIOException.class)),
                () -> assertThat(exception.getCause().getCause().getCause(), instanceOf(SocketException.class))
        );
    }

    private static Http1ServerResponse createResponse(RuntimeException writerFailure) {
        DataWriter dataWriter = mock(DataWriter.class);
        doThrow(writerFailure).when(dataWriter).write(any(BufferData.class));

        Http1ServerRequest request = mock(Http1ServerRequest.class);
        when(request.headers()).thenReturn(ServerRequestHeaders.create(WritableHeaders.create()));

        ContentEncodingContext contentEncodingContext = mock(ContentEncodingContext.class);
        when(contentEncodingContext.contentEncodingEnabled()).thenReturn(false);

        ListenerContext listenerContext = mock(ListenerContext.class);
        when(listenerContext.contentEncodingContext()).thenReturn(contentEncodingContext);
        when(listenerContext.mediaContext()).thenReturn(MediaContext.create());
        when(listenerContext.config()).thenReturn(WebServer.builder().buildPrototype());

        ConnectionContext ctx = mock(ConnectionContext.class);
        when(ctx.listenerContext()).thenReturn(listenerContext);

        return new Http1ServerResponse(ctx,
                                       mock(Http1ConnectionListener.class),
                                       dataWriter,
                                       request,
                                       true,
                                       true);
    }
}
