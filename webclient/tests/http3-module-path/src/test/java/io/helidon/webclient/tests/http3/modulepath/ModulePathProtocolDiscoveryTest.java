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

package io.helidon.webclient.tests.http3.modulepath;

import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http3.Http3Client;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

class ModulePathProtocolDiscoveryTest {
    private static final String TEST_MODULE = "io.helidon.webclient.tests.http3.modulepath.test";

    @Test
    void createsHttp3ClientUsingOwnerProtocolInventoryOnModulePath() {
        Module testModule = getClass().getModule();

        assertThat(testModule.isNamed(), is(true));
        assertThat(testModule.getName(), is(TEST_MODULE));

        WebClient webClient = WebClient.builder()
                .addProtocolPreference(Http1Client.PROTOCOL_ID)
                .build();
        Http3Client http3Client = null;
        try {
            assertThat(webClient.tcpProtocolIds(),
                       contains(Http1Client.PROTOCOL_ID));

            http3Client = webClient.client(Http3Client.PROTOCOL);

            assertThat(http3Client, notNullValue());
        } finally {
            if (http3Client != null) {
                http3Client.closeResource();
            }
            webClient.closeResource();
        }
    }
}
