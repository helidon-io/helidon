/*
 * Copyright (c) 2017, 2024 Oracle and/or its affiliates.
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

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.security.Principal;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLPeerUnverifiedException;

import io.helidon.webserver.ReferenceHoldingQueue.IndirectReference;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.Future;

/**
 * The HttpInitializer.
 */
class HttpInitializer extends ChannelInitializer<SocketChannel> {
    private static final Logger LOGGER = Logger.getLogger(HttpInitializer.class.getName());
    static final AttributeKey<String> CLIENT_CERTIFICATE_NAME = AttributeKey.valueOf("client_certificate_name");
    static final AttributeKey<X509Certificate> CLIENT_CERTIFICATE = AttributeKey.valueOf("client_certificate");
    static final AttributeKey<Certificate[]> CLIENT_CERTIFICATE_CHAIN = AttributeKey.valueOf("client_certificate_chain");

    private final NettyWebServer webServer;
    private final DirectHandlers directHandlers;
    private final SocketConfiguration soConfig;
    private final Router router;
    private final AtomicBoolean clearLock = new AtomicBoolean();
    private volatile SslContext sslContext;

    /**
     * Reference queue that collects ReferenceHoldingQueue's when they become
     * ready for garbage collection. ReferenceHoldingQueue's extracted from
     * this collection that cannot be fully released (some buffers still in
     * use) will be added to {@code unreleasedQueues} for later retries.
     */
    private final ReferenceQueue<Object> queues = new ReferenceQueue<>();

    /**
     * Concurrent queue to track all ReferenceHoldingQueue's not fully released
     * (some buffers still in use) after becoming ready for garbage collection.
     */
    private final Queue<ReferenceHoldingQueue<?>> unreleasedQueues = new ConcurrentLinkedQueue<>();

    HttpInitializer(SocketConfiguration soConfig,
                    SslContext sslContext,
                    Router router,
                    NettyWebServer webServer,
                    DirectHandlers directHandlers) {
        this.soConfig = soConfig;
        this.router = router;
        this.sslContext = sslContext;
        this.webServer = webServer;
        this.directHandlers = directHandlers;
    }

    /**
     * Calls release on every ReferenceHoldingQueue that has been deemed ready for
     * garbage collection and added to {@code queues}. The ReferenceHoldingQueue's not
     * fully released are added to {@code unreleasedQueues} for later retries. Uses
     * a lock to avoid concurrent modifications to {@code queues}.
     */
    @SuppressWarnings("unchecked")
    private void clearQueues() {
        if (clearLock.get() || !clearLock.compareAndSet(false, true)) {
            return;
        }
        try {
            for (Reference<?> r = queues.poll(); r != null; r = queues.poll()) {
                if (!(r instanceof IndirectReference<?, ?>)) {
                    log("Unexpected reference in queues", null);
                    continue;
                }
                ReferenceHoldingQueue<?> q = ((IndirectReference<?, ReferenceHoldingQueue<?>>) r).acquire();
                if (q == null) {
                    continue;       // no longer referenced
                }
                if (!q.release()) {
                    unreleasedQueues.add(q);
                }
            }
            unreleasedQueues.removeIf(ReferenceHoldingQueue::release);
        } finally {
            clearLock.lazySet(false);
        }
    }

    /**
     * Clears and shuts down all remaining ReferenceHoldingQueue's still being tracked.
     */
    void queuesShutdown() {
        clearQueues();
        unreleasedQueues.removeIf(queue -> {
            queue.shutdown();
            return true;
        });
    }

    void updateSslContext(SslContext context) {
        if (sslContext == null) {
            throw new IllegalStateException("Current TLS context is not set, update not allowed");
        }
        sslContext = context;
    }

    boolean hasTls() {
        return sslContext != null;
    }

    /**
     * Initializes pipeline for new socket channel.
     *
     * @param ch the socket channel.
     */
    @Override
    public void initChannel(SocketChannel ch) {
        log("Initializing channel", ch);

        final ChannelPipeline p = ch.pipeline();

        SSLEngine sslEngine = null;
        SslContext context = sslContext;
        if (context != null) {
            SslHandler sslHandler = context.newHandler(ch.alloc());
            sslEngine = sslHandler.engine();
            p.addLast(sslHandler);
            sslHandler.handshakeFuture().addListener(future -> obtainClientCN(future, ch, sslHandler));
        }

        if (LOGGER.isLoggable(Level.FINE)) {
            p.addLast(new LoggingHandler(LogLevel.DEBUG));
        }

        ServerConfiguration serverConfig = webServer.configuration();
        HttpServerCodec sourceCodec = new HttpServerCodec(
                soConfig.maxInitialLineLength(),
                soConfig.maxHeaderSize(),
                soConfig.maxChunkSize(),
                soConfig.validateHeaders(),
                soConfig.initialBufferSize()
        );

        UpgradeManager.addUpgradeHandler(p, router, sourceCodec, soConfig.maxUpgradeContentLength());

        // Enable compression via "Accept-Encoding" header if configured
        if (serverConfig.enableCompression()) {
            log("Compression negotiation enabled (gzip, deflate)", ch);
            p.addLast(new HttpContentCompressor());
        }

        RequestRouting requestRouting = router.routing(RequestRouting.class, null);
        if (requestRouting != null) {
            // Helidon's forwarding handler
            p.addLast(new ForwardingHandler(requestRouting,
                    webServer,
                    sslEngine,
                    queues,
                    this::clearQueues,
                    soConfig,
                    directHandlers));
        }

        // Set up idle handler to close inactive connections based on config
        int idleTimeout = soConfig.connectionIdleTimeout();
        if (idleTimeout > 0) {
            p.addLast(new IdleStateHandler(idleTimeout, idleTimeout, idleTimeout));
            p.addLast(new ChannelDuplexHandler() {
                @Override
                public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                    if (evt instanceof IdleStateEvent) {
                        LOGGER.finer(() -> "Closing idle connection on channel" + ctx.channel());
                        ctx.close();        // async close of idle connection
                    }
                }
            });
        }

        // Cleanup queues as part of event loop
        ch.eventLoop().execute(this::clearQueues);
    }

    /**
     * Sets {@code CERTIFICATE_NAME} in socket channel.
     *
     * @param future future passed to listener
     * @param ch the socket channel
     * @param sslHandler the SSL handler
     */
    private void obtainClientCN(Future<? super Channel> future, SocketChannel ch, SslHandler sslHandler) {
        if (future.cause() == null) {
            try {
                Certificate[] peerCertificates = sslHandler.engine().getSession().getPeerCertificates();
                if (peerCertificates.length >= 1) {
                    Certificate certificate = peerCertificates[0];
                    X509Certificate cert = (X509Certificate) certificate;
                    Principal principal = cert.getSubjectX500Principal();

                    int start = principal.getName().indexOf("CN=");
                    String tmpName = "Unknown CN";
                    if (start >= 0) {
                        tmpName = principal.getName().substring(start + 3);
                        int end = tmpName.indexOf(",");
                        if (end > 0) {
                            tmpName = tmpName.substring(0, end);
                        }
                    }
                    ch.attr(CLIENT_CERTIFICATE_NAME).set(tmpName);
                    ch.attr(CLIENT_CERTIFICATE).set(cert);
                    ch.attr(CLIENT_CERTIFICATE_CHAIN).set(peerCertificates);
                }
            } catch (SSLPeerUnverifiedException ignored) {
                //User not authenticated. Client authentication probably set to OPTIONAL or NONE
            }

        }
    }

    private void log(String msg, Channel channel) {
        if (LOGGER.isLoggable(Level.FINER)) {
            String channelId = channel != null ? channel.id().toString() : "N/A";
            LOGGER.finer("[Initializer: " + System.identityHashCode(this) + ", Channel: 0x" + channelId + "] " + msg);
        }
    }
}
