/*
 * Copyright (c) 2024, 2026 Oracle and/or its affiliates.
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

package io.helidon.webserver.http2;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.buffers.DataWriter;
import io.helidon.http.http2.Http2Exception;
import io.helidon.http.http2.Http2FrameData;
import io.helidon.http.http2.Http2FrameHeader;
import io.helidon.http.http2.Http2FrameType;
import io.helidon.http.http2.Http2GoAway;
import io.helidon.http.HeaderValues;
import io.helidon.http.HttpPrologue;
import io.helidon.http.Method;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http2.Http2Flag;
import io.helidon.http.http2.Http2ErrorCode;
import io.helidon.http.http2.Http2Settings;
import io.helidon.http.http2.Http2Util;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.ListenerContext;
import io.helidon.webserver.Router;

import org.junit.jupiter.api.Test;

import static io.helidon.http.http2.Http2Setting.ENABLE_PUSH;
import static io.helidon.http.http2.Http2Setting.HEADER_TABLE_SIZE;
import static io.helidon.http.http2.Http2Setting.INITIAL_WINDOW_SIZE;
import static io.helidon.http.http2.Http2Setting.MAX_CONCURRENT_STREAMS;
import static io.helidon.http.http2.Http2Setting.MAX_FRAME_SIZE;
import static io.helidon.http.http2.Http2Setting.MAX_HEADER_LIST_SIZE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UpgradeSettingsTest {

    static final long MAX_UNSIGNED_INT = 0xFFFFFFFFL;

    private final ConnectionContext ctx;
    private final HttpPrologue prologue;

    public UpgradeSettingsTest() {
        ctx = mock(ConnectionContext.class);
        prologue = HttpPrologue.create("http/1.1",
                                       "http",
                                       "1.1",
                                       Method.GET,
                                       "/resource.txt",
                                       false);
        DataWriter dataWriter = mock(DataWriter.class);
        when(ctx.router()).thenReturn(Router.empty());
        when(ctx.listenerContext()).thenReturn(mock(ListenerContext.class));
        when(ctx.dataWriter()).thenReturn(dataWriter);
        when(ctx.dataReader()).thenReturn(mock(DataReader.class));
    }

    @Test
    void urlEncodedSettingsGH8399() {
        Http2Settings s = upgrade("AAEAABAAAAIAAAABAAN_____AAQAAP__AAUAAEAAAAYAACAA");
        assertThat(s.presentValue(HEADER_TABLE_SIZE).orElseThrow(), is(4096L));
        assertThat(s.presentValue(ENABLE_PUSH).orElseThrow(), is(true));
        assertThat(s.presentValue(MAX_CONCURRENT_STREAMS).orElseThrow(), is(MAX_UNSIGNED_INT / 2));
        assertThat(s.presentValue(INITIAL_WINDOW_SIZE).orElseThrow(), is(65_535L));
        assertThat(s.presentValue(MAX_FRAME_SIZE).orElseThrow(), is(16_384L));
        assertThat(s.presentValue(MAX_HEADER_LIST_SIZE).orElseThrow(), is(8192L));
    }

    @Test
    void urlEncodedSettings() {
        Http2Settings settings2 = Http2Settings.builder()
                .add(HEADER_TABLE_SIZE, 4096L)
                .add(ENABLE_PUSH, false)
                .add(MAX_CONCURRENT_STREAMS, MAX_UNSIGNED_INT - 5)
                .add(INITIAL_WINDOW_SIZE, 65535L)
                .add(MAX_FRAME_SIZE, 16384L)
                .add(MAX_HEADER_LIST_SIZE, 256L)
                .build();
        String encSett = Base64.getUrlEncoder().encodeToString(settingsToBytes(settings2));
        Http2Settings s = upgrade(encSett);
        assertThat(s.presentValue(MAX_CONCURRENT_STREAMS).orElseThrow(), is(MAX_UNSIGNED_INT - 5));
        assertThat(s.presentValue(MAX_HEADER_LIST_SIZE).orElseThrow(), is(256L));
    }

    @Test
    void invalidMaxFrameSizeDoesNotReplaceLastValidSettings() {
        Http2Connection connection = new Http2Connection(ctx, Http2Config.create(), List.of());

        Http2Settings validSettings = Http2Settings.builder()
                .add(MAX_FRAME_SIZE, 16384L)
                .build();
        connection.clientSettings(validSettings);

        Http2Settings invalidSettings = Http2Settings.builder()
                .add(MAX_FRAME_SIZE, 0L)
                .build();

        Http2Exception exception = assertThrows(Http2Exception.class,
                                                () -> connection.clientSettings(invalidSettings));

        assertThat(exception.code(), is(io.helidon.http.http2.Http2ErrorCode.PROTOCOL));
        assertThat(connection.clientSettings().value(MAX_FRAME_SIZE), is(16384L));
    }

    @Test
    void invalidMaxFrameSizeHandledWithProtocolGoAway() throws InterruptedException {
        List<BufferData> writtenFrames = new ArrayList<>();
        DataWriter dataWriter = mock(DataWriter.class);
        doAnswer(invocation -> {
            BufferData data = invocation.getArgument(0);
            writtenFrames.add(data.copy());
            return null;
        }).when(dataWriter).writeNow(any(BufferData.class));

        ConnectionContext connectionContext = mock(ConnectionContext.class);
        when(connectionContext.router()).thenReturn(Router.empty());
        when(connectionContext.listenerContext()).thenReturn(mock(ListenerContext.class));
        when(connectionContext.dataWriter()).thenReturn(dataWriter);
        when(connectionContext.dataReader()).thenReturn(invalidMaxFrameSizeReader());

        Http2Connection connection = new Http2Connection(connectionContext,
                                                         Http2Config.builder().sendErrorDetails(true).build(),
                                                         List.of());
        connection.expectPreface();
        connection.handle(mock(io.helidon.common.concurrency.limits.Limit.class));

        assertThat(writtenFrames.size(), greaterThanOrEqualTo(2));

        BufferData goAwayData = writtenFrames.get(writtenFrames.size() - 1);
        byte[] headerBytes = new byte[Http2FrameHeader.LENGTH];
        goAwayData.read(headerBytes);
        Http2FrameHeader frameHeader = Http2FrameHeader.create(BufferData.create(headerBytes));
        assertThat(frameHeader.type(), is(Http2FrameType.GO_AWAY));

        byte[] payloadBytes = new byte[frameHeader.length()];
        goAwayData.read(payloadBytes);
        Http2GoAway goAway = Http2GoAway.create(BufferData.create(payloadBytes));
        assertThat(goAway, notNullValue());
        assertThat(goAway.errorCode(), is(Http2ErrorCode.PROTOCOL));
        assertThat(new String(payloadBytes, 8, payloadBytes.length - 8, StandardCharsets.UTF_8),
                   is("Frame size must be between 2^14 and 2^24-1, but is: 0"));
    }

    Http2Settings upgrade(String http2Settings) {
        WritableHeaders<?> headers = WritableHeaders.create().add(HeaderValues.create("HTTP2-Settings", http2Settings));
        Http2Upgrader http2Upgrader = Http2Upgrader.create(Http2Config.create());
        Http2Connection connection = (Http2Connection) http2Upgrader.upgrade(ctx, prologue, headers);
        return connection.clientSettings();
    }

    byte[] settingsToBytes(Http2Settings settings) {
        BufferData settingsFrameData =
                settings.toFrameData(null, 0, Http2Flag.SettingsFlags.create(0)).data();
        byte[] b = new byte[settingsFrameData.available()];
        settingsFrameData.read(b);
        return b;
    }

    private static DataReader invalidMaxFrameSizeReader() {
        Http2FrameData frameData = Http2Settings.builder()
                .add(MAX_FRAME_SIZE, 0L)
                .build()
                .toFrameData(null, 0, Http2Flag.SettingsFlags.create(0));
        BufferData input = BufferData.create(Http2Util.prefaceData(),
                                             BufferData.create(frameData.header().write(), frameData.data()));
        byte[] bytes = input.readBytes();
        AtomicBoolean delivered = new AtomicBoolean();
        return DataReader.create(() -> delivered.compareAndSet(false, true) ? bytes : null);
    }
}
