/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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

package io.helidon.webserver.accesslog;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

import io.helidon.common.context.Context;
import io.helidon.common.http.Http;
import io.helidon.common.http.HttpRequest;
import io.helidon.webserver.RequestHeaders;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link AccessLogSupport}.
 */
class AccessLogSupportTest {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS ZZZ");
    private static final ZonedDateTime BEGIN_TIME = ZonedDateTime.parse("2007-12-03T10:15:30.000 +0000", FORMATTER);
    private static final ZonedDateTime END_TIME = ZonedDateTime.parse("2007-12-03T10:15:31.140 +0000", FORMATTER);
    private static final String REMOTE_IP = "192.168.1.1";
    private static final String METHOD = "PUT";
    private static final String PATH = "/greet/World";
    private static final String HTTP_VERSION = "HTTP/1.1";

    private static final int STATUS_CODE = Http.Status.I_AM_A_TEAPOT.code();
    private static final String CONTENT_LENGTH = "-";
    private static final long TIME_TAKEN_MICROS = 1140000;

    @Test
    void testHelidonFormat() {
        AccessLogSupport accessLog = AccessLogSupport.create();

        ServerRequest request = mock(ServerRequest.class);
        Context context = Context.create();
        when(request.remoteAddress()).thenReturn(REMOTE_IP);
        when(request.context()).thenReturn(context);
        when(request.method()).thenReturn(Http.Method.PUT);
        HttpRequest.Path path = mock(HttpRequest.Path.class);
        when(path.toRawString()).thenReturn(PATH);
        when(request.path()).thenReturn(path);
        when(request.version()).thenReturn(Http.Version.V1_1);

        ServerResponse response = mock(ServerResponse.class);
        when(response.status()).thenReturn(Http.Status.I_AM_A_TEAPOT);

        AccessLogContext accessLogContext = mock(AccessLogContext.class);
        when(accessLogContext.requestDateTime()).thenReturn(BEGIN_TIME);
        String expectedTimestamp = TimestampLogEntry.create().doApply(accessLogContext);

        String logRecord = accessLog.createLogRecord(request,
                                                     response,
                                                     BEGIN_TIME,
                                                     0L,
                                                     END_TIME,
                                                     TIME_TAKEN_MICROS * 1000);

        //192.168.0.104 - [18/Jun/2019:23:10:44 +0200] "GET /greet/test HTTP/1.1" 200 55 2248

        String expected = REMOTE_IP + " - " + expectedTimestamp + " \"" + METHOD + " " + PATH + " " + HTTP_VERSION + "\" " +
                STATUS_CODE + " " + CONTENT_LENGTH + " " + TIME_TAKEN_MICROS;

        assertThat(logRecord, is(expected));
    }

    @Test
    void testCommonFormat() {
        AccessLogSupport accessLog = AccessLogSupport.builder()
                .commonLogFormat()
                .build();

        ServerRequest request = mock(ServerRequest.class);
        Context context = Context.create();
        when(request.remoteAddress()).thenReturn(REMOTE_IP);
        when(request.context()).thenReturn(context);
        when(request.method()).thenReturn(Http.Method.PUT);
        HttpRequest.Path path = mock(HttpRequest.Path.class);
        when(path.toRawString()).thenReturn(PATH);
        when(request.path()).thenReturn(path);
        when(request.version()).thenReturn(Http.Version.V1_1);

        ServerResponse response = mock(ServerResponse.class);
        when(response.status()).thenReturn(Http.Status.I_AM_A_TEAPOT);

        AccessLogContext accessLogContext = mock(AccessLogContext.class);
        when(accessLogContext.requestDateTime()).thenReturn(BEGIN_TIME);
        String expectedTimestamp = TimestampLogEntry.create().doApply(accessLogContext);

        String logRecord = accessLog.createLogRecord(request,
                                                     response,
                                                     BEGIN_TIME,
                                                     0L,
                                                     END_TIME,
                                                     TIME_TAKEN_MICROS * 1000);

        //192.168.0.104 - [18/Jun/2019:23:10:44 +0200] "GET /greet/test HTTP/1.1" 200 55 2248

        String expected = REMOTE_IP + " - - " + expectedTimestamp + " \"" + METHOD + " " + PATH + " " + HTTP_VERSION + "\" " +
                STATUS_CODE + " " + CONTENT_LENGTH;

        assertThat(logRecord, is(expected));
    }

    @Test
    void testCustomFormat() {
        AccessLogSupport accessLog = AccessLogSupport.builder()
                .add(TimestampLogEntry.builder().formatter(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")).build())
                .add(HeaderLogEntry.create("Referer"))
                .build();

        ServerRequest request = mock(ServerRequest.class);
        Context context = Context.create();
        when(request.remoteAddress()).thenReturn(REMOTE_IP);
        when(request.context()).thenReturn(context);
        when(request.method()).thenReturn(Http.Method.PUT);
        HttpRequest.Path path = mock(HttpRequest.Path.class);
        when(path.toRawString()).thenReturn(PATH);
        when(request.path()).thenReturn(path);
        when(request.version()).thenReturn(Http.Version.V1_1);
        RequestHeaders headers = mock(RequestHeaders.class);
        when(headers.all("Referer")).thenReturn(Arrays.asList("first", "second"));
        when(request.headers()).thenReturn(headers);

        ServerResponse response = mock(ServerResponse.class);
        when(response.status()).thenReturn(Http.Status.I_AM_A_TEAPOT);

        String logRecord = accessLog.createLogRecord(request,
                                                     response,
                                                     BEGIN_TIME,
                                                     0L,
                                                     END_TIME,
                                                     TIME_TAKEN_MICROS * 1000);

        String expected = "20071203101530 \"first,second\"";

        assertThat(logRecord, is(expected));
    }
}
