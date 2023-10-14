/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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

import java.security.Principal;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import io.helidon.common.context.Context;
import io.helidon.common.security.SecurityContext;
import io.helidon.common.socket.PeerInfo;
import io.helidon.common.uri.UriFragment;
import io.helidon.common.uri.UriPath;
import io.helidon.common.uri.UriQuery;
import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.HttpPrologue;
import io.helidon.http.Method;
import io.helidon.http.ServerRequestHeaders;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.webserver.http.RoutingRequest;
import io.helidon.webserver.http.RoutingResponse;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link AccessLogFeature}.
 */
class AccessLogFeatureTest {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS ZZZ");
    private static final ZonedDateTime BEGIN_TIME = ZonedDateTime.parse("2007-12-03T10:15:30.000 +0000", FORMATTER);
    private static final ZonedDateTime END_TIME = ZonedDateTime.parse("2007-12-03T10:15:31.140 +0000", FORMATTER);
    private static final String REMOTE_IP = "192.168.1.1";
    private static final String METHOD = "PUT";
    private static final String PATH = "/greet/World";
    private static final String HTTP_VERSION = "HTTP/1.1";

    private static final int STATUS_CODE = Status.I_AM_A_TEAPOT_418.code();
    private static final String CONTENT_LENGTH = "0";
    private static final long TIME_TAKEN_MICROS = 1140000;
    private static final Header REFERER_HEADER = HeaderValues.create(HeaderNames.REFERER, "first", "second");

    @Test
    void testHelidonFormat() {
        AccessLogFeature accessLog = AccessLogFeature.create();

        Principal securityPrincipal = mock(Principal.class);
        when(securityPrincipal.getName()).thenReturn("admin");
        SecurityContext<Principal> securityContext = mock(SecurityContext.class);
        when(securityContext.userPrincipal()).thenReturn(Optional.of(securityPrincipal));

        Context requestContext = Context.create();
        requestContext.register(securityContext);

        RoutingRequest request = mock(RoutingRequest.class);
        PeerInfo pi = mock(PeerInfo.class);
        when(pi.host()).thenReturn(REMOTE_IP);
        when(request.remotePeer()).thenReturn(pi);
        when(request.context()).thenReturn(requestContext);
        HttpPrologue prologue = HttpPrologue.create("HTTP/1.1",
                                                    "HTTP",
                                                    "1.1",
                                                    Method.PUT,
                                                    UriPath.create(PATH),
                                                    UriQuery.empty(),
                                                    UriFragment.empty());
        when(request.prologue()).thenReturn(prologue);

        RoutingResponse response = mock(RoutingResponse.class);
        when(response.status()).thenReturn(Status.I_AM_A_TEAPOT_418);

        AccessLogContext accessLogContext = mock(AccessLogContext.class);
        when(accessLogContext.requestDateTime()).thenReturn(BEGIN_TIME);
        String expectedTimestamp = TimestampLogEntry.create().doApply(accessLogContext);

        AccessLogHttpFeature httpAccessLog = accessLog.httpFeature("@default");

        String logRecord = httpAccessLog.createLogRecord(request,
                                                         response,
                                                         BEGIN_TIME,
                                                         0L,
                                                         END_TIME,
                                                         TIME_TAKEN_MICROS * 1000);

        //192.168.0.104 - [18/Jun/2019:23:10:44 +0200] "GET /greet/test HTTP/1.1" 200 55 2248

        String expected = REMOTE_IP + " admin " + expectedTimestamp + " \"" + METHOD + " " + PATH + " " + HTTP_VERSION + "\" " +
                STATUS_CODE + " " + CONTENT_LENGTH + " " + TIME_TAKEN_MICROS;

        assertThat(logRecord, is(expected));
    }

    @Test
    void testCommonFormat() {
        AccessLogFeature accessLog = AccessLogFeature.builder()
                .commonLogFormat()
                .build();

        Principal securityPrincipal = mock(Principal.class);
        when(securityPrincipal.getName()).thenReturn("admin");
        SecurityContext<Principal> securityContext = mock(SecurityContext.class);
        when(securityContext.userPrincipal()).thenReturn(Optional.of(securityPrincipal));

        Context requestContext = Context.create();
        requestContext.register(securityContext);

        RoutingRequest request = mock(RoutingRequest.class);
        PeerInfo pi = mock(PeerInfo.class);
        when(pi.host()).thenReturn(REMOTE_IP);
        when(request.remotePeer()).thenReturn(pi);
        when(request.context()).thenReturn(requestContext);
        HttpPrologue prologue = HttpPrologue.create("HTTP/1.1",
                                                    "HTTP",
                                                    "1.1",
                                                    Method.PUT,
                                                    UriPath.create(PATH),
                                                    UriQuery.empty(),
                                                    UriFragment.empty());
        when(request.prologue()).thenReturn(prologue);

        RoutingResponse response = mock(RoutingResponse.class);
        when(response.status()).thenReturn(Status.I_AM_A_TEAPOT_418);

        AccessLogContext accessLogContext = mock(AccessLogContext.class);
        when(accessLogContext.requestDateTime()).thenReturn(BEGIN_TIME);
        String expectedTimestamp = TimestampLogEntry.create().doApply(accessLogContext);

        String logRecord = accessLog.httpFeature("@default")
                .createLogRecord(request,
                                 response,
                                 BEGIN_TIME,
                                 0L,
                                 END_TIME,
                                 TIME_TAKEN_MICROS * 1000);

        //192.168.0.104 - [18/Jun/2019:23:10:44 +0200] "GET /greet/test HTTP/1.1" 200 55 2248

        String expected = REMOTE_IP + " - admin " + expectedTimestamp + " \"" + METHOD + " " + PATH + " " + HTTP_VERSION + "\" " +
                STATUS_CODE + " " + CONTENT_LENGTH;

        assertThat(logRecord, is(expected));
    }

    @Test
    void testCustomFormat() {
        AccessLogFeature accessLog = AccessLogFeature.builder()
                .addEntry(TimestampLogEntry.builder().formatter(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")).build())
                .addEntry(HeaderLogEntry.create("Referer"))
                .build();

        RoutingRequest request = mock(RoutingRequest.class);
        PeerInfo pi = mock(PeerInfo.class);
        HttpPrologue prologue = HttpPrologue.create("HTTP/1.1",
                                                    "HTTP",
                                                    "1.1",
                                                    Method.PUT,
                                                    UriPath.create(PATH),
                                                    UriQuery.empty(),
                                                    UriFragment.empty());
        WritableHeaders<?> headers = WritableHeaders.create();
        headers.set(REFERER_HEADER);

        when(pi.host()).thenReturn(REMOTE_IP);
        when(request.remotePeer()).thenReturn(pi);
        when(request.prologue()).thenReturn(prologue);
        when(request.headers()).thenReturn(ServerRequestHeaders.create(headers));

        RoutingResponse response = mock(RoutingResponse.class);
        when(response.status()).thenReturn(Status.I_AM_A_TEAPOT_418);

        String logRecord = accessLog.httpFeature("@default")
                .createLogRecord(request,
                                 response,
                                 BEGIN_TIME,
                                 0L,
                                 END_TIME,
                                 TIME_TAKEN_MICROS * 1000);

        String expected = "20071203101530 \"first,second\"";

        assertThat(logRecord, is(expected));
    }
}
