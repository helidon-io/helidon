/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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

import java.time.Duration;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.LazyValue;
import io.helidon.common.Version;
import io.helidon.common.http.Http;
import io.helidon.config.Config;
import io.helidon.media.common.MediaContext;

import io.netty.channel.nio.NioEventLoopGroup;

/*
 * This class must be:
 *   - thread safe
 *   - graalVm native-image safe (e.g. you must be able to store this class statically)
 *       - what about the base URI? only would work with prod config
 */
final class NettyClient implements WebClient {
    private static final Config EMPTY_CONFIG = Config.empty();
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofMinutes(1);
    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofMinutes(10);
    private static final boolean DEFAULT_FOLLOW_REDIRECTS = false;
    private static final boolean DEFAULT_KEEP_ALIVE = true;
    private static final boolean DEFAULT_VALIDATE_HEADERS = true;
    private static final int DEFAULT_NUMBER_OF_REDIRECTS = 5;
    private static final LazyValue<String> DEFAULT_USER_AGENT = LazyValue
            .create(() -> "Helidon/" + Version.VERSION + " (java " + System.getProperty("java.runtime.version") + ")");
    private static final Proxy DEFAULT_PROXY = Proxy.noProxy();
    private static final MediaContext DEFAULT_MEDIA_SUPPORT = MediaContext.create();
    private static final WebClientTls DEFAULT_TLS = WebClientTls.builder().build();

    private static final AtomicBoolean DEFAULTS_CONFIGURED = new AtomicBoolean();

    private static final WebClientConfiguration DEFAULT_CONFIGURATION =
            WebClientConfiguration.builder()
                    .config(EMPTY_CONFIG)
                    .connectTimeout(DEFAULT_CONNECT_TIMEOUT)
                    .readTimeout(DEFAULT_READ_TIMEOUT)
                    .followRedirects(DEFAULT_FOLLOW_REDIRECTS)
                    .maxRedirects(DEFAULT_NUMBER_OF_REDIRECTS)
                    .userAgent(DEFAULT_USER_AGENT)
                    .readerContextParent(DEFAULT_MEDIA_SUPPORT.readerContext())
                    .writerContextParent(DEFAULT_MEDIA_SUPPORT.writerContext())
                    .proxy(DEFAULT_PROXY)
                    .tls(DEFAULT_TLS)
                    .keepAlive(DEFAULT_KEEP_ALIVE)
                    .validateHeaders(DEFAULT_VALIDATE_HEADERS)
                    .build();

    // configurable per client instance
    static final AtomicReference<WebClientConfiguration> SHARED_CONFIGURATION = new AtomicReference<>(DEFAULT_CONFIGURATION);

    // shared by all client instances
    private static LazyValue<NioEventLoopGroup> eventGroup = LazyValue.create(() -> {
        throw new IllegalStateException("Value supplier not yet set");
    });

    // this instance configuration
    private final WebClientConfiguration configuration;

    /**
     * Creates new instance.
     *
     * @param builder client builder
     */
    NettyClient(Builder builder) {
        this.configuration = builder.configuration();

        // we need to configure these - if user wants to override, they must
        // do it before first usage
        configureDefaults(EMPTY_CONFIG);
    }

    static LazyValue<NioEventLoopGroup> eventGroup() {
        return eventGroup;
    }

    @Override
    public WebClientRequestBuilder put() {
        return method(Http.Method.PUT);
    }

    @Override
    public WebClientRequestBuilder get() {
        return method(Http.Method.GET);
    }

    @Override
    public WebClientRequestBuilder post() {
        return method(Http.Method.POST);
    }

    @Override
    public WebClientRequestBuilder delete() {
        return method(Http.Method.DELETE);
    }

    @Override
    public WebClientRequestBuilder options() {
        return method(Http.Method.OPTIONS);
    }

    @Override
    public WebClientRequestBuilder trace() {
        return method(Http.Method.TRACE);
    }

    @Override
    public WebClientRequestBuilder head() {
        return method(Http.Method.HEAD);
    }

    @Override
    public WebClientRequestBuilder method(String method) {
        return WebClientRequestBuilderImpl.create(eventGroup, configuration, Http.RequestMethod.create(method));
    }

    @Override
    public WebClientRequestBuilder method(Http.Method method) {
        return WebClientRequestBuilderImpl.create(eventGroup, configuration, method);
    }

    static void configureDefaults(Config globalConfig) {
        if (DEFAULTS_CONFIGURED.compareAndSet(false, true)) {
            Config config = globalConfig.get("client");
            WebClientConfiguration.Builder<?, ?> builder = DEFAULT_CONFIGURATION.derive();
            Config eventLoopConfig = config.get("event-loop");
            int numberOfThreads = eventLoopConfig.get("workers")
                    .asInt()
                    .orElse(1);
            String threadNamePrefix = eventLoopConfig.get("name-prefix")
                    .asString()
                    .orElse("helidon-client-");
            AtomicInteger threadCounter = new AtomicInteger();

            ThreadFactory threadFactory =
                    r -> {
                        Thread result = new Thread(r, threadNamePrefix + threadCounter.getAndIncrement());
                        // we should exit the VM if client event loop is the only thread(s) running
                        result.setDaemon(true);
                        return result;
                    };

            eventGroup = LazyValue.create(new NioEventLoopGroup(numberOfThreads, threadFactory));

            builder.config(config);

            SHARED_CONFIGURATION.set(builder.build());
        }
    }

}
