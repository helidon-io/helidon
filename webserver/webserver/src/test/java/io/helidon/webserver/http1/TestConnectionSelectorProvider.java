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

package io.helidon.webserver.http1;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.helidon.common.Weight;
import io.helidon.webserver.ProtocolConfigs;
import io.helidon.webserver.spi.ServerConnectionSelector;
import io.helidon.webserver.spi.ServerConnectionSelectorProvider;

import static org.mockito.Mockito.mock;

@Weight(10)
public class TestConnectionSelectorProvider implements ServerConnectionSelectorProvider<Http1Config> {

    private static final Map<String, Http1Config> CONFIGS = new ConcurrentHashMap<>();

    static Map<String, Http1Config> config() {
        return CONFIGS;
    }

    static void reset() {
        CONFIGS.clear();
    }

    @Override
    public Class<Http1Config> protocolConfigType() {
        return Http1Config.class;
    }

    @Override
    public String protocolType() {
        return "http_1_1";
    }

    @Override
    public ServerConnectionSelector create(String socketName, Http1Config config, ProtocolConfigs configs) {
        CONFIGS.put(socketName, config);
        return mock(ServerConnectionSelector.class);
    }

}
