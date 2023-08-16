/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.webclient.http1;

import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.spi.ClientProtocolProvider;

class Http1ProtocolProvider implements ClientProtocolProvider<Http1Client, Http1ClientProtocolConfig> {
    static final String CONFIG_KEY = "http_1_1";

    @Override
    public String protocolId() {
        return Http1Client.PROTOCOL_ID;
    }

    @Override
    public Class<Http1ClientProtocolConfig> configType() {
        return Http1ClientProtocolConfig.class;
    }

    @Override
    public Http1ClientProtocolConfig defaultConfig() {
        return Http1ClientProtocolConfig.create();
    }

    @Override
    public Http1Client protocol(WebClient client, Http1ClientProtocolConfig config) {
        return new Http1ClientImpl(client, Http1ClientConfig.builder()
                                           .from(client.prototype())
                                           .protocolConfig(config)
                                           .buildPrototype());
    }
}
