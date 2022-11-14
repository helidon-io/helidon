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

package io.helidon.pico.testsupport;

import io.helidon.pico.PicoServices;
import io.helidon.pico.spi.impl.DefaultPicoServices;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestableServicesTest {

    @Test
    public void sanity() {
        TestablePicoServices testablePicoServices = new TestablePicoServices();
        assertFalse(testablePicoServices.isGlobal());
        assertTrue(testablePicoServices.isDynamic());
        TestableServices services = testablePicoServices.services();
        assertNotNull(services);
        TestablePicoServicesConfig config = testablePicoServices.config().get();
        assertNotNull(config);

        PicoServices global = DefaultPicoServices.getInstance();
        assertNotNull(global);
        assertNotSame(testablePicoServices, global);
        assertFalse(global instanceof TestablePicoServices);
        assertFalse(global.config().get() instanceof TestablePicoServicesConfig);
        assertFalse(global.services() instanceof TestablePicoServices);
    }

}
