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

package io.helidon.webclient.tests.http2;

import java.util.List;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.http1.Http1ClientProtocolConfig;
import io.helidon.webclient.http2.Http2ClientProtocolConfig;
import io.helidon.webclient.spi.ProtocolConfig;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;

class ProtocolConfigTest {
    @Test
    void testProtocolConfigWorks() {
        Config config = Config.just(ConfigSources.classpath("protocol-config-test.yaml"));
        // reproducer for #7802 - h2 protocol not recognized
        WebClient client = WebClient.builder()
                .config(config.get("client"))
                .build();
        assertThat(client, notNullValue());

        List<ProtocolConfig> protocolConfigs = client.prototype()
                .protocolConfigs();

        assertThat(protocolConfigs, hasSize(2));

        /*
        Should be ordered by weight, first HTTP/2
         */
        ProtocolConfig http2Config = protocolConfigs.get(0);
        assertThat(http2Config, instanceOf(Http2ClientProtocolConfig.class));

        ProtocolConfig http1Config = protocolConfigs.get(1);
        assertThat(http1Config, instanceOf(Http1ClientProtocolConfig.class));

        Http2ClientProtocolConfig http2cast = (Http2ClientProtocolConfig) http2Config;
        assertThat(http2cast.priorKnowledge(), is(true));

        Http1ClientProtocolConfig http1cast = (Http1ClientProtocolConfig) http1Config;
        assertThat(http1cast.maxHeaderSize(), is(20000));
        assertThat(http1cast.validateRequestHeaders(), is(false));
    }
}
