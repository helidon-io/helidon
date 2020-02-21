/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * TODO Javadoc
 */
public class SslTest {

    @Test
    public void sslDefaults() {
        Ssl ssl = Ssl.builder().build();

        assertThat(ssl.disableHostnameVerification(), is(false));
        assertThat(ssl.trustAll(), is(false));
        assertThat(ssl.certificates().size(), is(0));
        assertThat(ssl.clientCertificateChain().size(), is(0));
        assertThat(ssl.clientPrivateKey(), is(Optional.empty()));
        assertThat(ssl.sslContext(), is(Optional.empty()));
    }

    //@Test
    public void sslFromConfig() {
        Config config = Config.builder()
                .disableSystemPropertiesSource()
                .disableEnvironmentVariablesSource()
                .sources(ConfigSources.classpath("ssl-config.yaml"))
                .build();
        Ssl ssl = Ssl.builder().config(config.get("ssl")).build();

        assertThat(ssl.disableHostnameVerification(), is(true));
        assertThat(ssl.trustAll(), is(true));
    }

}
