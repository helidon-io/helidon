/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.pico.config.fake.helidon;

import java.util.Map;
import java.util.TreeMap;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.pico.config.fake.helidon.config.DefaultFakeServerConfig;
import io.helidon.pico.config.spi.ConfigBeanAttributeVisitor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This test ensures that we can use the generated config beans from this module that should only have an spi
 * dependency.
 */
class ConfigBeanBuilderSpiTest {

    @Test
    void testBuilderWithoutConfig() {
        DefaultFakeServerConfig cfg = DefaultFakeServerConfig.builder().build();
        cfg.__overrideInstanceId("0");
        assertEquals(
                "DefaultFakeServerConfig{0}(name=@default, port=0, bindAddress=null, backlog=1024, timeoutMillis=0, "
                        + "receiveBufferSize=0, tls=null, ssl=null, enabledSslProtocols=[], allowedCipherSuite=[], "
                        + "clientAuth=null, enabled=true, maxHeaderSize=8192, maxInitialLineLength=4096, "
                        + "maxChunkSize=0, validateHeaders=false, enableCompression=false, maxPayloadSize=-1, "
                        + "initialBufferSize=0, maxUpgradeContentLength=65536, workersCount=0, sockets={}, "
                        + "printFeatureDetails=false)",
                cfg.toString());

        Map<String, Object> values = new TreeMap<>();
        ConfigBeanAttributeVisitor<Object> visitor =
                (attrName, valueSupplier, meta, userDefinedCtx, type, typeArgument) -> {
            assertNotNull(meta);
            assertFalse(meta.isEmpty());
            values.put(attrName, valueSupplier.get());
        };

        cfg.visitAttributes(visitor::visit, null);
        assertEquals(
                "{allowedCipherSuite=[], backlog=1024, bindAddress=null, clientAuth=null, enableCompression=false, "
                        + "enabled=true, enabledSslProtocols=[], initialBufferSize=0, maxChunkSize=0, "
                        + "maxHeaderSize=8192, maxInitialLineLength=4096, maxPayloadSize=-1, "
                        + "maxUpgradeContentLength=65536, name=@default, port=0, printFeatureDetails=false, "
                        + "receiveBufferSize=0, sockets={}, ssl=null, timeoutMillis=0, tls=null, "
                        + "validateHeaders=false, workersCount=0}",
                values.toString());

        DefaultFakeServerConfig cfg2 = DefaultFakeServerConfig.toBuilder(cfg).build();
        cfg2.__overrideInstanceId("0");
        assertNotSame(cfg, cfg2);
        assertEquals(cfg, cfg2);

        assertTrue(cfg.__config().isEmpty());
    }

    @Test
    void testBuilderWithConfig() {
        Config config = Config.create(
                ConfigSources.create(Map.of("bind-address", "123"))
        );

        DefaultFakeServerConfig cfg = DefaultFakeServerConfig.toBuilder(config).build();
        assertNotNull(cfg);
        assertEquals("123", cfg.bindAddress());
        assertSame(config, cfg.__config().get());
    }

}
