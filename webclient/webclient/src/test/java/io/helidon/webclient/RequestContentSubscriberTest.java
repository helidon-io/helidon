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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

import io.helidon.common.context.Context;
import io.helidon.common.http.DataChunk;
import io.helidon.media.common.MediaContext;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static io.helidon.webclient.WebClientRequestBuilderImpl.RECEIVED;
import static io.helidon.webclient.WebClientRequestBuilderImpl.REQUEST;
import static io.helidon.webclient.WebClientRequestBuilderImpl.REQUEST_ID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class RequestContentSubscriberTest {
    private static final Flow.Subscription SUBSCRIPTION = new Flow.Subscription() {
        @Override
        public void request(long count) {
        }

        @Override
        public void cancel() {
        }
    };

    @ParameterizedTest
    @ValueSource(ints = {1, 16})
    void uploadsEveryChunk(int chunks) {
        byte[] payload = new byte[1024];
        RequestConfiguration configuration = RequestConfiguration.builder(URI.create("http://localhost/upload"))
                .context(Context.create())
                .readerContext(MediaContext.create().readerContext())
                .build();
        WebClientRequestImpl clientRequest = mock(WebClientRequestImpl.class, withSettings().stubOnly());
        when(clientRequest.configuration()).thenReturn(configuration);

        EmbeddedChannel channel = new EmbeddedChannel();
        channel.attr(REQUEST).set(clientRequest);
        channel.attr(REQUEST_ID).set(1L);
        channel.attr(RECEIVED).set(new CompletableFuture<>());
        DefaultHttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/upload");
        RequestContentSubscriber subscriber = new RequestContentSubscriber(request,
                                                                           channel,
                                                                           new CompletableFuture<>(),
                                                                           new CompletableFuture<>(),
                                                                           true);
        try {
            subscriber.onSubscribe(SUBSCRIPTION);
            for (int chunk = 0; chunk < chunks; chunk++) {
                subscriber.onNext(DataChunk.create(payload));
            }
            subscriber.onComplete();
            channel.runPendingTasks();
            Object outbound;
            int bytes = 0;
            while ((outbound = channel.readOutbound()) != null) {
                if (outbound instanceof HttpContent content) {
                    bytes += content.content().readableBytes();
                }
                ReferenceCountUtil.release(outbound);
            }
            assertThat(bytes, is(chunks * payload.length));
        } finally {
            channel.finishAndReleaseAll();
        }
    }
}
