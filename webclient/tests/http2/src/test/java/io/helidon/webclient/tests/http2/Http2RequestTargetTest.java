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

package io.helidon.webclient.tests.http2;

import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.http.Status;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.Proxy;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.http2.Http2ClientProtocolConfig;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame;
import io.netty.handler.codec.http2.Http2Frame;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import io.netty.handler.codec.http2.Http2HeadersFrame;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@Execution(ExecutionMode.SAME_THREAD)
class Http2RequestTargetTest {
    private static final List<String> TARGETS = List.of("/proxy?a=1",
                                                       "/proxy?b=2&a=1&b=3",
                                                       "/proxy?flag",
                                                       "/proxy?x=1&&y=2",
                                                       "/proxy?=value&empty=&=",
                                                       "/proxy?x=1&",
                                                       "/proxy?value=%2f%2F%7e%7E%25%3f%3F%23&space=+%20",
                                                       "/proxy/a%2Fb%2fc?sig=one",
                                                       "/proxy/%7e%7E%41%61",
                                                       "/proxy/%25/%3F/%3f/%23",
                                                       "/proxy;a=1/b;c=2?x=3",
                                                       "/proxy/a+b//c/./d/../e",
                                                       "/proxy?",
                                                       "/proxy?flag#not-sent",
                                                       "?flag",
                                                       "?",
                                                       "#not-sent",
                                                       "");
    private static final LinkedBlockingQueue<String> RECEIVED_TARGETS = new LinkedBlockingQueue<>();
    private static NioEventLoopGroup eventLoop;
    private static Channel listener;

    @BeforeAll
    static void startServer() throws InterruptedException {
        eventLoop = new NioEventLoopGroup(1);
        listener = new ServerBootstrap()
                .group(eventLoop)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel channel) {
                        channel.pipeline().addLast(Http2FrameCodecBuilder.forServer().build());
                        channel.pipeline().addLast(new SimpleChannelInboundHandler<Http2Frame>() {
                            @Override
                            protected void channelRead0(ChannelHandlerContext context, Http2Frame frame) {
                                if (frame instanceof Http2HeadersFrame headers) {
                                    RECEIVED_TARGETS.add(headers.headers().path().toString());
                                    context.writeAndFlush(new DefaultHttp2HeadersFrame(new DefaultHttp2Headers().status("204"),
                                                                                     true).stream(headers.stream()));
                                }
                            }
                        });
                    }
                })
                .bind(new InetSocketAddress("127.0.0.1", 0))
                .sync().channel();
    }

    @AfterAll
    static void stopServer() throws InterruptedException {
        try {
            if (listener != null) {
                assertThat("HTTP/2 listener closed", listener.close().await(10, TimeUnit.SECONDS), is(true));
            }
        } finally {
            if (eventLoop != null) {
                assertThat("HTTP/2 event loop terminated",
                           eventLoop.shutdownGracefully(0, 5, TimeUnit.SECONDS).await(10, TimeUnit.SECONDS), is(true));
            }
        }
    }

    @Test
    void preservesRequestUri() throws Exception {
        checkTargets(Construction.REQUEST_URI);
    }

    @Test
    void preservesClientUriCopy() throws Exception {
        checkTargets(Construction.CLIENT_URI_COPY);
    }

    @Test
    void preservesBaseUri() throws Exception {
        checkTargets(Construction.BASE_URI);
    }

    @Test
    void preservesBaseUriString() throws Exception {
        checkTargets(Construction.BASE_URI_STRING);
    }

    private static void checkTargets(Construction construction) throws Exception {
        String origin = "http://127.0.0.1:" + ((InetSocketAddress) listener.localAddress()).getPort();
        for (String target : TARGETS) {
            URI uri = URI.create(origin + target);
            String expectedTarget = rawTarget(uri);
            var signerUri = new AtomicReference<URI>();
            var builder = WebClient.builder()
                    .proxy(Proxy.noProxy())
                    .shareConnectionCache(false)
                    .connectTimeout(Duration.ofSeconds(5))
                    .readTimeout(Duration.ofSeconds(5))
                    .addProtocolConfig(Http2ClientProtocolConfig.builder().priorKnowledge(true).build())
                    .addService((chain, request) -> {
                        signerUri.set(request.uri().toUri());
                        return chain.proceed(request);
                    });
            switch (construction) {
            case BASE_URI -> builder.baseUri(uri);
            case BASE_URI_STRING -> builder.baseUri(uri.toString());
            default -> builder.baseUri(origin);
            }
            WebClient client = builder.build();
            try {
                var request = client.get().protocolId("h2");
                switch (construction) {
                case REQUEST_URI -> request.uri(uri);
                case CLIENT_URI_COPY -> request.uri(ClientUri.create(ClientUri.create(uri)));
                default -> {
                }
                }
                URI resolvedUri = request.resolvedUri().toUri();
                try (var response = request.request()) {
                    assertThat(response.status(), is(Status.NO_CONTENT_204));
                }
                assertThat("HTTP/2 :path for " + target,
                           RECEIVED_TARGETS.poll(10, TimeUnit.SECONDS), is(expectedTarget));
                assertThat("signer-visible URI for " + target, rawTarget(signerUri.get()), is(expectedTarget));
                assertThat("resolved URI for " + target, rawTarget(resolvedUri), is(expectedTarget));
            } finally {
                client.closeResource();
            }
        }
    }

    private static String rawTarget(URI uri) {
        String path = uri.getRawPath();
        String query = uri.getRawQuery();
        return (path.isEmpty() ? "/" : path) + (query == null ? "" : "?" + query);
    }

    private enum Construction {
        REQUEST_URI,
        CLIENT_URI_COPY,
        BASE_URI,
        BASE_URI_STRING
    }
}
