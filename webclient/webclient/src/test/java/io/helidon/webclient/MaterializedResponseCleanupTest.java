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
package io.helidon.webclient;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import io.helidon.common.context.Context;
import io.helidon.media.common.MediaContext;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static io.helidon.webclient.WebClientRequestBuilderImpl.COMPLETED;
import static io.helidon.webclient.WebClientRequestBuilderImpl.RECEIVED;
import static io.helidon.webclient.WebClientRequestBuilderImpl.REQUEST;
import static io.helidon.webclient.WebClientRequestBuilderImpl.REQUEST_ID;
import static io.helidon.webclient.WebClientRequestBuilderImpl.RESULT;
import static io.helidon.webclient.WebClientRequestBuilderImpl.WILL_CLOSE;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MaterializedResponseCleanupTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @ParameterizedTest
    @ValueSource(classes = {String.class, byte[].class})
    void queuedResponseBuffersAreReleasedWhenMaterializationFails(Class<?> type) throws Exception {
        URI uri = URI.create("http://localhost/entity");
        RequestConfiguration configuration = RequestConfiguration.builder(uri)
                .context(Context.create())
                .readerContext(MediaContext.create().readerContext())
                .maxBufferedEntitySize(8)
                .build();
        WebClientRequestImpl request = mock(WebClientRequestImpl.class);
        when(request.configuration()).thenReturn(configuration);
        CompletableFuture<WebClientResponse> result = new CompletableFuture<>();
        EmbeddedChannel channel = new EmbeddedChannel(new NettyClientHandler());
        channel.attr(REQUEST).set(request);
        channel.attr(REQUEST_ID).set(1L);
        channel.attr(RECEIVED).set(new CompletableFuture<>());
        channel.attr(COMPLETED).set(new CompletableFuture<>());
        channel.attr(RESULT).set(result);
        channel.attr(WILL_CLOSE).set(true);
        ByteBuf rejected = Unpooled.copiedBuffer("123456789", StandardCharsets.UTF_8);
        ByteBuf queued = Unpooled.copiedBuffer("queued", StandardCharsets.UTF_8);
        ByteBuf late = Unpooled.copiedBuffer("late", StandardCharsets.UTF_8);
        try {
            DefaultHttpResponse headers = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            headers.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain");
            channel.writeInbound(headers);
            channel.writeInbound(new DefaultHttpContent(rejected), new DefaultHttpContent(queued));
            WebClientResponse response = result.get(10, TimeUnit.SECONDS);
            try {
                ExecutionException failure = assertThrows(ExecutionException.class,
                        () -> response.content().as(type).get(10, TimeUnit.SECONDS));
                assertThat(failure.getCause(), instanceOf(IllegalStateException.class));
                assertThat(rejected.refCnt(), is(0));
                assertThat(queued.refCnt(), is(0));
                channel.writeInbound(new DefaultHttpContent(late));
                assertThat(late.refCnt(), is(0));
            } finally {
                response.close().await(TIMEOUT);
            }
        } finally {
            channel.finishAndReleaseAll();
            for (ByteBuf buffer : new ByteBuf[] {rejected, queued, late}) {
                if (buffer.refCnt() > 0) {
                    buffer.release(buffer.refCnt());
                }
            }
        }
    }
}
