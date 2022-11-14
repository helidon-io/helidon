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

package io.helidon.pico.spi.impl;

import io.helidon.pico.PicoServices;
import io.helidon.pico.PicoServicesConfig;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests for {@link io.helidon.pico.spi.impl.DefaultPicoServicesConfig}.
 */
public class DefaultPicoServicesConfigTest {
    private PicoServices picoServices = PicoServices.picoServices().get();
    private PicoServicesConfig config = picoServices.config().get();

    /**
     * Properties test.
     */
    @Test
    public void defaultConfigViaProps() {
        assertEquals("{io.helidon.pico.provider=oracle, io.helidon.pico.version=1.0-SNAPSHOT, io.helidon.pico.deadlock"
                        + ".timeout.millis=10000, io.helidon.pico.activation.logs.enabled=false, io.helidon.pico"
                        + ".supports.dynamic=false, io.helidon.pico.supports.reflection=false, io.helidon.pico"
                        + ".supports.compiletime=true, io.helidon.pico.supports.jsr330=true, io.helidon.pico.supports"
                        + ".jsr330.static=false, io.helidon.pico.supports.jsr330.private=false, io.helidon.pico"
                        + ".supports.threadsafe.activation=true, io.helidon.pico.bind.application=true, io.helidon"
                        + ".pico.bind.modules=true}",
                config.toString());
    }

    /**
     * getStringValue() test.
     */
    @Test
    public void defaultConfigViaGetValue() {
        assertEquals("oracle", config.stringValue(PicoServicesConfig.KEY_PROVIDER));
        assertNotNull(config.stringValue(PicoServicesConfig.KEY_VERSION));
    }

}
