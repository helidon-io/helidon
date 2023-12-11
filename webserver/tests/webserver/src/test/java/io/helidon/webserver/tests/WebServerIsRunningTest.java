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
package io.helidon.webserver.tests;

import java.util.List;

import io.helidon.common.configurable.Resource;
import io.helidon.common.pki.Keys;
import io.helidon.common.tls.Tls;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class WebServerIsRunningTest {

    /**
     * The invalid cipher name should cause the server's listener to
     * fail; the server should reflect its internal state correctly.
     */
    @Test
    void notRunningTest() {
        Keys keys = Keys.builder()
                .keystore(store -> store
                        .passphrase("helidon")
                        .keystore(Resource.create("certificate.p12")))
                .build();
        Tls tls = Tls.builder()
                .privateKey(keys)
                .privateKeyCertChain(keys)
                .addEnabledCipherSuites(List.of("INVALID_CIPHER_NAME"))     // fails listener
                .build();
        WebServerConfig.Builder builder = WebServer.builder()
                .routing(rb -> rb.get("/", (req, res) -> { }));
        builder.tls(tls);
        WebServer server = builder.build().start();
        assertThat(server.isRunning(), is(false));
    }
}
