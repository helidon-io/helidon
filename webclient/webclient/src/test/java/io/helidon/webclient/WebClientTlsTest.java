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

import java.util.Optional;
import java.util.Set;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * TODO Javadoc
 */
public class WebClientTlsTest {

    @Test
    public void sslDefaults() {
        WebClientTls webClientTls = WebClientTls.builder().build();

        assertThat(webClientTls.disableHostnameVerification(), is(false));
        assertThat(webClientTls.trustAll(), is(false));
        assertThat(webClientTls.certificates().size(), is(0));
        assertThat(webClientTls.clientCertificateChain().size(), is(0));
        assertThat(webClientTls.clientPrivateKey(), is(Optional.empty()));
        assertThat(webClientTls.sslContext(), is(Optional.empty()));
        assertThat(webClientTls.allowedCipherSuite(), is(Set.of()));
    }

    //@Test
    public void sslFromConfig() {
        Config config = Config.builder()
                .disableSystemPropertiesSource()
                .disableEnvironmentVariablesSource()
                .sources(ConfigSources.classpath("tls-config.yaml"))
                .build();
        WebClientTls webClientTls = WebClientTls.builder().config(config.get("tls")).build();

        assertThat(webClientTls.disableHostnameVerification(), is(true));
        assertThat(webClientTls.trustAll(), is(true));
    }

}
