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

package io.helidon.webclient.tests.http3;

import io.helidon.webclient.api.WebClientConfig;
import io.helidon.webclient.http3.Http3ClientConfig;
import io.helidon.webclient.http3.Http3ClientProtocolConfig;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

class Http3ClientTestSupportTest {

    @Test
    void shouldConfigureStrictHttp3Client() {
        Http3ClientConfig config = Http3ClientTestSupport.strictClientBuilder().buildPrototype();

        assertThat(config.shareConnectionCache(), equalTo(false));
        assertThat(config.protocolConfig().priorKnowledge(), equalTo(true));
    }

    @Test
    void shouldConfigureStrictGenericClient() {
        WebClientConfig config = Http3ClientTestSupport.strictWebClientBuilder().buildPrototype();
        Http3ClientProtocolConfig protocolConfig = config.protocolConfigs()
                .stream()
                .filter(Http3ClientProtocolConfig.class::isInstance)
                .map(Http3ClientProtocolConfig.class::cast)
                .findFirst()
                .orElseThrow();

        assertThat(config.shareConnectionCache(), equalTo(false));
        assertThat(protocolConfig.priorKnowledge(), equalTo(true));
    }
}
