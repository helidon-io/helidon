/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.webserver;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

import io.helidon.common.http.DataChunk;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.EmptyHttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;
import org.mockito.Mockito;
import org.reactivestreams.tck.TestEnvironment;
import org.reactivestreams.tck.flow.FlowSubscriberWhiteboxVerification;

public class BareResponseSubscriberTckTest extends FlowSubscriberWhiteboxVerification<DataChunk> {

    protected BareResponseSubscriberTckTest() {
        super(new TestEnvironment(200));
    }

    @Override
    public Flow.Subscriber<DataChunk> createFlowSubscriber(WhiteboxSubscriberProbe<DataChunk> probe) {
        ChannelHandlerContext ctx = Mockito.mock(ChannelHandlerContext.class);
        HttpRequest httpRequest = Mockito.mock(HttpRequest.class);
        RequestContext requestContext = Mockito.mock(RequestContext.class);
        Channel channel = Mockito.mock(Channel.class);
        ChannelFuture channelFuture = Mockito.mock(ChannelFuture.class);

        Mockito.when(httpRequest.headers()).thenReturn(EmptyHttpHeaders.INSTANCE);
        Mockito.when(httpRequest.protocolVersion()).thenReturn(HttpVersion.HTTP_1_0);
        Mockito.when(ctx.channel()).thenReturn(channel);
        Mockito.when(channel.closeFuture()).thenReturn(channelFuture);

        return new BareResponseImpl(ctx,
                httpRequest,
                requestContext,
                () -> true,
                () -> false,
                CompletableFuture.completedFuture(null),
                CompletableFuture.completedFuture(null),
                0L) {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                super.onSubscribe(subscription);
                probe.registerOnSubscribe(new SubscriberPuppet() {
                    @Override
                    public void triggerRequest(long elements) {
                        subscription.request(elements);
                    }

                    @Override
                    public void signalCancel() {
                        subscription.cancel();
                    }
                });
            }

            @Override
            public void onNext(DataChunk data) {
                super.onNext(data);
                probe.registerOnNext(data);
            }

            @Override
            public void onError(Throwable thr) {
                super.onError(thr);
                probe.registerOnError(thr);
            }

            @Override
            public void onComplete() {
                super.onComplete();
                probe.registerOnComplete();
            }
        };
    }

    @Override
    public DataChunk createElement(int i) {
        return DataChunk.create(String.valueOf(i).getBytes(StandardCharsets.UTF_8));
    }
}
