/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.webserver.netty;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.http.ContextualRegistry;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerConfiguration;
import io.helidon.webserver.SocketConfiguration;
import io.helidon.webserver.WebServer;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.JdkSslContext;
import io.netty.util.concurrent.Future;

/**
 * The Netty based WebServer implementation.
 */
class NettyWebServer implements WebServer {

    private static final Logger LOGGER = Logger.getLogger(NettyWebServer.class.getName());

    private final EventLoopGroup bossGroup;
    private final EventLoopGroup workerGroup;
    private final Map<String, ServerBootstrap> bootstraps = new HashMap<>();
    private final ServerConfiguration configuration;
    private final CompletableFuture<WebServer> startFuture = new CompletableFuture<>();
    private final CompletableFuture<WebServer> shutdownFuture = new CompletableFuture<>();
    private final CompletableFuture<WebServer> channelsUpFuture = new CompletableFuture<>();
    private final CompletableFuture<WebServer> channelsCloseFuture = new CompletableFuture<>();
    private final CompletableFuture<WebServer> threadGroupsShutdownFuture = new CompletableFuture<>();
    private final ContextualRegistry contextualRegistry = ContextualRegistry.create();
    private final ConcurrentMap<String, Channel> channels = new ConcurrentHashMap<>();
    private final List<HttpInitializer> initializers = new LinkedList<>();

    private volatile boolean started;
    private final AtomicBoolean shutdownThreadGroupsInitiated = new AtomicBoolean(false);

    NettyWebServer(ServerConfiguration config,
                   Routing routing,
                   Map<String, Routing> namedRoutings) {
        Set<Map.Entry<String, SocketConfiguration>> sockets = config.sockets().entrySet();

        this.bossGroup = new NioEventLoopGroup(sockets.size());
        this.workerGroup = config.workersCount() <= 0 ? new NioEventLoopGroup() : new NioEventLoopGroup(config.workersCount());

        this.configuration = config;

        for (Map.Entry<String, SocketConfiguration> entry : sockets) {
            String name = entry.getKey();
            SocketConfiguration soConfig = entry.getValue();
            ServerBootstrap bootstrap = new ServerBootstrap();
            // Transform java SSLContext into Netty SslContext
            JdkSslContext sslContext = null;
            if (soConfig.ssl() != null) {
                // TODO configuration support for CLIENT AUTH (btw, ClientAuth.REQUIRE doesn't seem to work with curl nor with
                // Chrome)
                sslContext = new JdkSslContext(soConfig.ssl(), false, ClientAuth.NONE);
            }

            if (soConfig.backlog() > 0) {
                bootstrap.option(ChannelOption.SO_BACKLOG, soConfig.backlog());
            }
            if (soConfig.timeoutMillis() > 0) {
                bootstrap.option(ChannelOption.SO_TIMEOUT, soConfig.backlog());
            }
            if (soConfig.receiveBufferSize() > 0) {
                bootstrap.option(ChannelOption.SO_RCVBUF, soConfig.receiveBufferSize());
            }

            HttpInitializer childHandler = new HttpInitializer(sslContext, namedRoutings.getOrDefault(name, routing), this);
            initializers.add(childHandler);
            bootstrap.group(bossGroup, workerGroup)
                     .channel(NioServerSocketChannel.class)
                     .handler(new LoggingHandler(LogLevel.DEBUG))
                     .childHandler(childHandler);

            bootstraps.put(name, bootstrap);
        }
    }

    @Override
    public ServerConfiguration configuration() {
        return configuration;
    }

    @Override
    public synchronized CompletionStage<WebServer> start() {
        if (!started) {

            channelsUpFuture.thenAccept(startFuture::complete)
                            .exceptionally(throwable -> {
                                if (channels.isEmpty()) {
                                    startFailureHandler(throwable);
                                }
                                for (Channel channel : channels.values()) {
                                    channel.close();
                                }
                                return null;
                            });

            channelsCloseFuture.whenComplete((webServer, throwable) -> shutdown(throwable));

            Set<Map.Entry<String, ServerBootstrap>> bootstrapEntries = bootstraps.entrySet();
            int bootstrapsSize = bootstrapEntries.size();
            for (Map.Entry<String, ServerBootstrap> entry : bootstrapEntries) {
                ServerBootstrap bootstrap = entry.getValue();
                String name = entry.getKey();
                SocketConfiguration socketConfig = configuration.socket(name);
                if (socketConfig == null) {
                    throw new IllegalStateException(
                            "no socket configuration found for name: " + name);
                }
                int port = socketConfig.port() <= 0 ? 0 : socketConfig.port();
                if (channelsUpFuture.isCompletedExceptionally()) {
                    // break because one of the previous channels already failed
                    break;
                }

                try {
                    bootstrap.bind(configuration.bindAddress(), port).addListener(channelFuture -> {
                        if (!channelFuture.isSuccess()) {
                            LOGGER.info(() -> "Channel '" + name + "' startup failed. Startup failure routine initiated.");
                            channelsUpFuture.completeExceptionally(new IllegalStateException("Channel startup failed: " + name,
                                                                                             channelFuture.cause()));
                            return;
                        }

                        Channel channel = ((ChannelFuture) channelFuture).channel();
                        LOGGER.info(() -> "Channel '" + name + "' started: " + channel);
                        channels.put(name, channel);

                        channel.closeFuture().addListener(future -> {
                            LOGGER.info(() -> "Channel '" + name + "' closed: " + channel);
                            channels.remove(name);
                            if (channelsUpFuture.isCompletedExceptionally()) {
                                // we're in a startup failure handler
                                if (channels.isEmpty()) {
                                    channelsUpFuture.exceptionally(this::startFailureHandler);
                                    // all the channels are down
                                } else if (future.cause() != null) {
                                    LOGGER.log(Level.WARNING,
                                               "Startup failure channel close failure",
                                               new IllegalStateException(future.cause()));
                                }
                            } else {
                                if (!future.isSuccess()) {
                                    channelsCloseFuture.completeExceptionally(new IllegalStateException("Channel stop failure.",
                                                                                                        future.cause()));
                                } else if (channels.isEmpty()) {
                                    channelsCloseFuture.complete(this);
                                }
                                // else we're waiting for the rest of the channels to start, successful branch
                            }
                        });

                        if (channelsUpFuture.isCompletedExceptionally()) {
                            channel.close();
                        }

                        if (channels.size() >= bootstrapsSize) {
                            LOGGER.finer(() -> "All channels started: " + channels.size());
                            channelsUpFuture.complete(this);
                        }
                    });
                } catch (RejectedExecutionException e) {
                    if (shutdownThreadGroupsInitiated.get()) {
                        // a rejected exception is expected and we shall stop starting the rest of the sockets
                        break;
                    } else {
                        throw e;
                    }
                }
            }

            started = true;
            LOGGER.fine(() -> "All channels startup routine initiated: " + bootstrapsSize);
        }
        return startFuture;
    }

    private WebServer startFailureHandler(Throwable throwable) {
        shutdownThreadGroups()
                .whenComplete((webServer, t) -> {
                    if (t != null) {
                        LOGGER.log(Level.WARNING, "Netty Thread Groups were unable to shutdown.", t);
                    }
                    startFuture.completeExceptionally(new IllegalStateException("WebServer was unable to start.",
                                                                                throwable));
                    shutdownFuture.complete(this);
                });
        return null;
    }

    private void shutdown(Throwable cause) {
        shutdownThreadGroups()
                .whenComplete((webServer, throwable) -> {
                    if (cause == null && throwable == null) {
                        shutdownFuture.complete(this);
                    } else if (cause != null) {
                        if (throwable != null) {
                            LOGGER.log(Level.WARNING, "Netty Thread Groups were unable to shutdown.", throwable);
                        }
                        shutdownFuture.completeExceptionally(
                                new IllegalStateException("WebServer was unable to stop.", cause));
                    } else {
                        shutdownFuture.completeExceptionally(
                                new IllegalStateException("WebServer was unable to stop.", throwable));
                    }
                });
    }

    private CompletionStage<WebServer> shutdownThreadGroups() {
        if (shutdownThreadGroupsInitiated.getAndSet(true)) {
            return threadGroupsShutdownFuture;
        }

        forceQueuesRelease();

        // there's no need for a quiet time as the channel is not expected to be used from now on
        Future<?> bossGroupFuture = bossGroup.shutdownGracefully(0, 10, TimeUnit.SECONDS);
        Future<?> workerGroupFuture = workerGroup.shutdownGracefully(0, 10, TimeUnit.SECONDS);

        workerGroupFuture.addListener(workerFuture -> {
            bossGroupFuture.addListener(bossFuture -> {
                if (workerFuture.isSuccess() && bossFuture.isSuccess()) {
                    threadGroupsShutdownFuture.complete(this);
                } else {
                    StringBuilder sb = new StringBuilder();
                    sb.append(workerFuture.cause() != null ? "Worker Group problem: " + workerFuture.cause().getMessage() : "")
                      .append(bossFuture.cause() != null ? "Boss Group problem: " + bossFuture.cause().getMessage() : "");
                    threadGroupsShutdownFuture
                            .completeExceptionally(new IllegalStateException("Unable to shutdown Netty thread groups: " + sb));
                }
            });
        });
        return threadGroupsShutdownFuture;
    }

    private void forceQueuesRelease() {
        initializers.removeIf(httpInitializer -> {
            httpInitializer.queuesShutdown();
            return true;
        });
    }

    @Override
    public CompletionStage<WebServer> shutdown() {
        if (!startFuture.isDone()) {
            startFuture.cancel(true);
        }
        if (channels.isEmpty()) {
            channelsCloseFuture.complete(this);
        }
        for (Channel channel : channels.values()) {
            channel.close();
        }
        return shutdownFuture;
    }

    @Override
    public CompletionStage<WebServer> whenShutdown() {
        return shutdownFuture;
    }

    @Override
    public boolean isRunning() {
        return startFuture.isDone() && !shutdownFuture.isDone();
    }

    @Override
    public ContextualRegistry context() {
        return contextualRegistry;
    }

    public int port(String name) {
        Channel channel = channels.get(name);
        if (channel == null) {
            return -1;
        }
        SocketAddress address = channel.localAddress();
        return address instanceof InetSocketAddress ? ((InetSocketAddress) address).getPort() : -1;
    }
}
