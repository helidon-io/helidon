/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

import java.util.function.Consumer;

import javax.net.ssl.SSLContext;

/**
 * Implementors of this contract are responsible for managing the {@link javax.net.ssl.SSLContext} instance lifecycle. When the
 * context changes, it then has the responsible to notify {@link WebServer#updateTls(WebServerTls)}.
 * <p>
 * How context changes are observed is based upon the implementation of the manager.
 */
public interface TlsManager {

    /**
     * Always called before any other method on this type. This method is only called when TLS is enabled and no explicit
     * SSLContext was passed to the builder.
     * In case the TLS is disabled, none of the methods on this type can be called.
     *
     * @param tls TLS configuration
     */
    void init(WebServerTls tls);

    /**
     * Callers can subscribe to updates to be notified when the SSL context changes.
     *
     * @param sslContextConsumer the consumer that will receive the new/update context after it has been reloaded by the manager
     * @deprecated this method will be removed in a future release.
     */
    @Deprecated
    void subscribe(Consumer<SSLContext> sslContextConsumer);

    /**
     * SSL context created by this manager.
     * This method is called only after {@link #init(WebServerTls)} and only if {@link WebServerTls#enabled()} is
     * {@code true}.
     *
     * @return the SSL context to use
     */
    SSLContext sslContext();

}
