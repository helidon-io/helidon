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

package io.helidon.webserver.spi;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;

import javax.net.ssl.SSLContext;

import io.helidon.webserver.ConfiguredTlsManager;
import io.helidon.webserver.WebServerTls;

@SuppressWarnings("deprecation")
public class FakeReloadableTlsManager extends ConfiguredTlsManager {
    private final Set<Consumer<SSLContext>> subscribers = new LinkedHashSet<>();
    private WebServerTls tlsConfig;

    FakeReloadableTlsManager(String name) {
        super(name, "fake-type");
    }

    @Override // TlsManager
    public void init(WebServerTls tlsConfig) {
        super.init(tlsConfig);
    }

    @Override // TlsManager
    public void subscribe(Consumer<SSLContext> sslContextConsumer) {
        super.subscribe(sslContextConsumer);
        subscribers.add(sslContextConsumer);
    }

    public Set<Consumer<SSLContext>> subscribers() {
        return subscribers;
    }

    @Override
    protected void configureAndSet(WebServerTls tlsConfig,
                                   SSLContext sslContext) {
        super.configureAndSet(tlsConfig, sslContext);

        this.tlsConfig = tlsConfig;
    }

    public WebServerTls tlsConfig() {
        return tlsConfig;
    }

}
