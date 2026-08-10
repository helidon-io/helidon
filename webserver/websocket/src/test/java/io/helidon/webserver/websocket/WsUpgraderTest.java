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

import io.helidon.common.Size;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.HttpPrologue;
import io.helidon.http.Method;
import io.helidon.http.WritableHeaders;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.ListenerConfig;
import io.helidon.webserver.ListenerContext;
import io.helidon.webserver.Router;
import io.helidon.webserver.ServerConnectionException;
import io.helidon.webserver.http1.spi.Http1RoutedUpgrade;
import io.helidon.webserver.http1.spi.Http1RoutedUpgrader;
import io.helidon.webserver.http1.spi.Http1UpgradeResponse;
import io.helidon.webserver.http1.spi.Http1UpgradeResult;
import io.helidon.websocket.WsListener;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WsUpgraderTest {

    @Test
    void routedUpgradeUsesProgrammaticProtocolConfig() {
        WsConfig listenerWsConfig = WsConfig.builder().build();
        ListenerConfig listenerConfig = mock(ListenerConfig.class);
        when(listenerConfig.protocols()).thenReturn(List.of(listenerWsConfig));

        ListenerContext listenerContext = mock(ListenerContext.class);
        when(listenerContext.config()).thenReturn(listenerConfig);

        ConnectionContext ctx = mock(ConnectionContext.class);
        when(ctx.listenerContext()).thenReturn(listenerContext);
        when(ctx.dataReader()).thenReturn(mock(DataReader.class));
        when(ctx.router()).thenReturn(Router.builder()
                                           .addRouting(WsRouting.builder()
                                                               .endpoint("/chat", new WsListener() {
                                                               }))
                                           .build());

        WritableHeaders<?> headers = WritableHeaders.create()
                .add(HeaderValues.create(HeaderNames.CONNECTION, "Upgrade"))
                .add(HeaderValues.create(HeaderNames.UPGRADE, "websocket"))
                .add(HeaderValues.create(WsUpgrader.WS_KEY, "dGhlIHNhbXBsZSBub25jZQ=="))
                .add(HeaderValues.create(WsUpgrader.WS_VERSION, WsUpgrader.SUPPORTED_VERSION));
        HttpPrologue prologue = HttpPrologue.create("http/1.1",
                                                    "http",
                                                    "1.1",
                                                    Method.GET,
                                                    "/chat",
                                                    false);
        WsConfig upgraderWsConfig = WsConfig.builder()
                .maxBufferedMessageSize(Size.create(16, Size.Unit.BYTE))
                .build();
        Http1RoutedUpgrader upgrader = (Http1RoutedUpgrader) WsUpgrader.create(upgraderWsConfig);
        Http1RoutedUpgrade routedUpgrade = upgrader.routedUpgrade(ctx, prologue, headers).orElseThrow();
        Http1UpgradeResult result = routedUpgrade.upgrade(mock(Http1UpgradeResponse.class));
        WsConnection connection = (WsConnection) result.connection().orElseThrow();

        assertThat(connection.protocolConfig(), sameInstance(upgraderWsConfig));
    }

    @Test
    void upgradeWriteWrapsUncheckedIOException() {
        DataWriter dataWriter = mock(DataWriter.class);
        doThrow(new UncheckedIOException(new SocketException("Broken pipe")))
                .when(dataWriter)
                .writeNow(any(BufferData.class));

        ConnectionContext ctx = mock(ConnectionContext.class);
        when(ctx.dataWriter()).thenReturn(dataWriter);
        when(ctx.router()).thenReturn(Router.builder()
                                           .addRouting(WsRouting.builder()
                                                               .endpoint("/chat", new WsListener() {
                                                               }))
                                           .build());

        WritableHeaders<?> headers = WritableHeaders.create()
                .add(HeaderValues.create(WsUpgrader.WS_KEY, "dGhlIHNhbXBsZSBub25jZQ=="))
                .add(HeaderValues.create(WsUpgrader.WS_VERSION, WsUpgrader.SUPPORTED_VERSION));
        HttpPrologue prologue = HttpPrologue.create("http/1.1",
                                                    "http",
                                                    "1.1",
                                                    Method.GET,
                                                    "/chat",
                                                    false);

        ServerConnectionException exception = assertThrows(ServerConnectionException.class,
                                                           () -> WsUpgrader.create(WsConfig.builder().build())
                                                                   .upgrade(ctx, prologue, headers));

        assertAll(
                () -> assertThat(exception.getCause(), instanceOf(UncheckedIOException.class)),
                () -> assertThat(exception.getCause().getCause(), instanceOf(SocketException.class))
        );
    }
}
