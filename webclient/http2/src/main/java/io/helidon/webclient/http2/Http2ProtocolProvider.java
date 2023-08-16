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

package io.helidon.webclient.http2;

import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.spi.ClientProtocolProvider;

class Http2ProtocolProvider implements ClientProtocolProvider<Http2Client, Http2ClientProtocolConfig> {
    static final String CONFIG_KEY = "h2";

    @Override
    public String protocolId() {
        return Http2Client.PROTOCOL_ID;
    }

    @Override
    public Class<Http2ClientProtocolConfig> configType() {
        return Http2ClientProtocolConfig.class;
    }

    @Override
    public Http2ClientProtocolConfig defaultConfig() {
        return Http2ClientProtocolConfig.create();
    }

    @Override
    public Http2Client protocol(WebClient client, Http2ClientProtocolConfig config) {
        return new Http2ClientImpl(client,
                                   Http2ClientConfig.builder()
                                           .from(client.prototype())
                                           .protocolConfig(config)
                                           .buildPrototype());
    }
}
