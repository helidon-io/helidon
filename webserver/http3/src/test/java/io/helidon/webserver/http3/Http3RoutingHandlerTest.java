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

package io.helidon.webserver.http3;

import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.uri.UriAuthority;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.WritableHeaders;
import io.helidon.http.encoding.ContentEncodingContext;
import io.helidon.http.http3.Http3Protocol;
import io.helidon.webserver.ListenerConfig;
import io.helidon.webserver.ListenerContext;
import io.helidon.webserver.Router;
import io.helidon.webserver.SniContext;
import io.helidon.webserver.TransportBindingContext;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.HttpSecurity;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class Http3RoutingHandlerTest {
    @Test
    void routesLargeTransportStreamIdWithConnectionLocalRequestId() {
        long transportStreamId = (long) Integer.MAX_VALUE + 1;
        AtomicInteger routedRequestId = new AtomicInteger();
        AtomicReference<Optional<String>> routedCommonName = new AtomicReference<>();
        TransportBindingContext listenerBindingContext = mock(TransportBindingContext.class);
        ListenerContext listenerContext = mock(ListenerContext.class);
        ListenerConfig listenerConfig = mock(ListenerConfig.class);
        ContentEncodingContext encodingContext = mock(ContentEncodingContext.class);
        Router router = mock(Router.class);
        HttpRouting routing = mock(HttpRouting.class);
        Http3ServerStream stream = mock(Http3ServerStream.class);
        Http3ConnectionContext connectionContext = mock(Http3ConnectionContext.class);
        Http3ServerResponse response = mock(Http3ServerResponse.class);
        Http3Protocol.DecodedRequestHead request = mock(Http3Protocol.DecodedRequestHead.class);
        SniContext sniContext = mock(SniContext.class);
        String rawAuthority = "Api.Example.COM:443";
        UriAuthority parsedAuthority = UriAuthority.create(rawAuthority);

        when(listenerBindingContext.listenerContext()).thenReturn(listenerContext);
        when(listenerBindingContext.router()).thenReturn(router);
        when(listenerContext.config()).thenReturn(listenerConfig);
        when(listenerContext.contentEncodingContext()).thenReturn(encodingContext);
        when(listenerConfig.maxPayloadSize()).thenReturn(-1L);
        when(encodingContext.contentDecodingEnabled()).thenReturn(false);
        when(router.routing(eq(HttpRouting.class), any(HttpRouting.class))).thenReturn(routing);
        when(routing.security()).thenReturn(mock(HttpSecurity.class));
        doAnswer(invocation -> {
            Http3ServerRequest routedRequest = invocation.getArgument(1);
            routedRequestId.set(routedRequest.id());
            routedCommonName.set(routedRequest.headers().first(HeaderNames.X_HELIDON_CN));
            return null;
        }).when(routing).route(any(), any(), any());

        when(stream.streamId()).thenReturn(transportStreamId);
        when(stream.requestId()).thenReturn(1);
        when(stream.request()).thenReturn(request);
        when(stream.context()).thenReturn(connectionContext);
        when(stream.contentLength()).thenReturn(OptionalLong.empty());
        when(stream.requestBodyReader(anyLong())).thenReturn(_ -> BufferData.empty());
        when(stream.response(any(Http3ServerRequest.class))).thenReturn(response);
        when(response.isSent()).thenReturn(true);

        when(connectionContext.sniContext()).thenReturn(Optional.of(sniContext));
        when(connectionContext.tlsCommonName()).thenReturn(Optional.of("test-client"));
        when(sniContext.checkAuthority(parsedAuthority))
                .thenReturn(SniContext.AuthorityCheck.ALLOWED);

        when(request.method()).thenReturn("GET");
        when(request.authority()).thenReturn(rawAuthority);
        when(request.parsedAuthority()).thenReturn(parsedAuthority);
        when(request.path()).thenReturn(Optional.of("/"));
        WritableHeaders<?> requestHeaders = WritableHeaders.create();
        requestHeaders.set(HeaderValues.create(HeaderNames.X_HELIDON_CN, "spoofed"));
        when(request.headers()).thenReturn(requestHeaders);

        Optional<Http3Handler.BufferedResponse> bufferedResponse =
                new Http3RoutingHandler(listenerBindingContext, 1024, true).handle(stream);

        assertThat(bufferedResponse.isEmpty(), is(true));
        assertThat(routedRequestId.get(), equalTo(1));
        assertThat(routedCommonName.get(), is(Optional.of("test-client")));
        verify(connectionContext).tlsCommonName();
        verify(sniContext).checkAuthority(parsedAuthority);
    }
}
