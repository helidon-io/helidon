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

package io.helidon.webserver.websocket;

import java.io.UncheckedIOException;
import java.net.SocketException;
import java.util.List;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.http.Headers;
import io.helidon.http.HttpPrologue;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.ListenerConfig;
import io.helidon.webserver.ListenerContext;
import io.helidon.webserver.ServerConnectionException;
import io.helidon.websocket.WsListener;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WsConnectionTest {

    @Test
    void sendWrapsUncheckedIOException() {
        DataWriter dataWriter = mock(DataWriter.class);
        doThrow(new UncheckedIOException(new SocketException("Broken pipe")))
                .when(dataWriter)
                .writeNow(any(BufferData.class));

        WsConnection connection = createConnection(dataWriter);

        ServerConnectionException exception = assertThrows(ServerConnectionException.class,
                                                           () -> connection.send("hello", true));

        assertAll(
                () -> assertThat(exception.getCause(), instanceOf(UncheckedIOException.class)),
                () -> assertThat(exception.getCause().getCause(), instanceOf(SocketException.class))
        );
    }

    private static WsConnection createConnection(DataWriter dataWriter) {
        ListenerConfig listenerConfig = mock(ListenerConfig.class);
        when(listenerConfig.protocols()).thenReturn(List.of(WsConfig.builder().build()));

        ListenerContext listenerContext = mock(ListenerContext.class);
        when(listenerContext.config()).thenReturn(listenerConfig);

        ConnectionContext ctx = mock(ConnectionContext.class);
        when(ctx.listenerContext()).thenReturn(listenerContext);
        when(ctx.dataReader()).thenReturn(mock(DataReader.class));
        when(ctx.dataWriter()).thenReturn(dataWriter);

        return WsConnection.create(ctx,
                                   mock(HttpPrologue.class),
                                   mock(Headers.class),
                                   "key",
                                   mock(WsListener.class));
    }
}
