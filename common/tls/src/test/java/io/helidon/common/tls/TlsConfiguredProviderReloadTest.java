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

package io.helidon.common.tls;

import java.util.Map;

import javax.net.ssl.SSLContext;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;

class TlsConfiguredProviderReloadTest {
    private static final String MANAGER_NAME = "server-listener";

    @BeforeEach
    void resetProvider() {
        StaticReloadTlsManagerProvider.reset();
    }

    @Test
    void configuredProviderIsCreatedFromConfig() {
        Tls tls = Tls.create(config("configured"));
        TlsManager manager = StaticReloadTlsManagerProvider.manager(MANAGER_NAME);

        assertThat(manager.name(), is(MANAGER_NAME));
        assertThat(manager.type(), is(StaticReloadTlsManagerProvider.TYPE));
        assertThat(issuer(tls), is("configured"));
    }

    @Test
    void staticProviderReloadUpdatesCreatedTlsManager() {
        Tls tls = Tls.create(config("configured"));
        SSLContext sslContext = tls.sslContext();

        StaticReloadTlsManagerProvider.reload(MANAGER_NAME, "reloaded");

        assertThat(tls.sslContext(), sameInstance(sslContext));
        assertThat(issuer(tls), is("reloaded"));
        assertThat(tls.newEngine(), notNullValue());
    }

    private static Config config(String initialIssuer) {
        return Config.just(ConfigSources.create(Map.of(
                "manager." + MANAGER_NAME + ".type", StaticReloadTlsManagerProvider.TYPE,
                "manager." + MANAGER_NAME + ".initial-issuer", initialIssuer)));
    }

    private static String issuer(Tls tls) {
        return ((StaticReloadTlsManagerProvider.TrackingTrustManager) tls.trustManager().orElseThrow()).issuer();
    }
}
