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

package io.helidon.webclient.http3;

import java.util.function.Consumer;

import io.helidon.builder.api.RuntimeType;
import io.helidon.common.Api;
import io.helidon.config.Config;
import io.helidon.webclient.api.HttpClient;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.spi.Protocol;

/**
 * HTTP/3 client.
 * <p>
 * A client created directly through {@link #create()}, another {@code create} overload, or
 * {@link #builder()} owns the backing {@link WebClient} it creates. Calling {@link #closeResource()} closes both the
 * HTTP/3-specific resources owned by the client and that backing client.
 * <p>
 * A client obtained through {@link WebClient#client(Protocol)} or
 * {@link WebClient#client(Protocol, io.helidon.webclient.spi.ProtocolConfig)} is a borrowed view of the supplied
 * {@code WebClient}. Closing the HTTP/3 view releases its HTTP/3-specific resources but does not close the parent
 * {@code WebClient}. Closing the parent is not a substitute for closing the view; close the view first and then close
 * the parent. If a cached view obtained through {@link WebClient#client(Protocol)} is closed, obtaining it again returns
 * a new open view.
 */
@Api.Incubating
public interface Http3Client extends HttpClient<Http3ClientRequest>, RuntimeType.Api<Http3ClientConfig> {
    /**
     * HTTP/3 protocol ID, as used by ALPN.
     */
    String PROTOCOL_ID = "h3";

    /**
     * Protocol to use to obtain a borrowed HTTP/3-specific client view from
     * {@link io.helidon.webclient.api.WebClient#client(io.helidon.webclient.spi.Protocol)}.
     * The view must be closed separately and does not close the parent {@code WebClient}. Closing the cached view causes
     * the next lookup through {@code WebClient.client(PROTOCOL)} to create a new view.
     */
    Protocol<Http3Client, Http3ClientProtocolConfig> PROTOCOL = Http3ProtocolProvider::new;

    /**
     * A new fluent API builder to customize standalone client setup.
     * A client built by the returned builder owns its backing {@code WebClient} and must be closed.
     *
     * @return a new builder
     */
    static Http3ClientConfig.Builder builder() {
        return Http3ClientConfig.builder();
    }

    /**
     * Create a new standalone instance with custom configuration.
     * The returned client owns its backing {@code WebClient} and must be closed.
     *
     * @param clientConfig HTTP/3 client configuration
     * @return a new standalone HTTP/3 client
     */
    static Http3Client create(Http3ClientConfig clientConfig) {
        WebClient webClient = WebClient.create(it -> it.from(clientConfig));
        try {
            return new Http3ClientImpl(webClient, clientConfig, true);
        } catch (RuntimeException | Error failure) {
            try {
                webClient.closeResource();
            } catch (Throwable closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
    }

    /**
     * Create a new standalone instance, customizing its configuration.
     * The returned client owns its backing {@code WebClient} and must be closed.
     *
     * @param consumer HTTP/3 client configuration
     * @return a new standalone HTTP/3 client
     */
    static Http3Client create(Consumer<Http3ClientConfig.Builder> consumer) {
        return create(Http3ClientConfig.builder()
                              .update(consumer)
                              .buildPrototype());
    }

    /**
     * Create a new standalone instance with default configuration.
     * The returned client owns its backing {@code WebClient} and must be closed.
     *
     * @return a new standalone HTTP/3 client
     */
    static Http3Client create() {
        return create(Http3ClientConfig.create());
    }

    /**
     * Create a new standalone instance based on {@link io.helidon.config.Config}.
     * The returned client owns its backing {@code WebClient} and must be closed.
     *
     * @param config client config
     * @return a new standalone HTTP/3 client
     */
    static Http3Client create(Config config) {
        return create(it -> it.config(config));
    }

    /**
     * Releases all resources owned by this HTTP/3 client.
     * <p>
     * For a standalone client, this also closes the backing {@code WebClient}. For a borrowed view obtained through
     * {@link WebClient#client(Protocol)}, this does not close the parent {@code WebClient}. Calling this method more
     * than once has no additional effect. Requests must not be created or submitted after the client is closed.
     * <p>
     * Do not invoke this method from a DNS resolver or request task currently executing on behalf of this client. A
     * reentrant close that would wait for the current task is rejected before the client lifecycle changes; close the
     * client after the callback returns or from another thread.
     *
     * @throws IllegalStateException if a reentrant close would wait for the current client task
     */
    @Override
    void closeResource();
}
