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

package io.helidon.config;

import org.junit.jupiter.api.Test;

import static io.helidon.config.PrimordialConfig.getProp;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PrimordialConfigTest {

    @Test
    public void getPropTest() {
        assertNull(getProp("DEFINITELY_NOT_SET", null));
        assertTrue(getProp("DEFINITELY_NOT_SET", true));
        assertFalse(getProp("DEFINITELY_NOT_SET", false));
        assertEquals(0, getProp("DEFINITELY_NOT_SET", 0));
        String expandTilde = getProp("DEFINITELY_NOT_SET", "~/foo");
        assertTrue(expandTilde.endsWith("foo"));
        assertTrue(expandTilde.startsWith(System.getProperty("user.home")));
        assertEquals("This Is My ENV VARS Value.", getProp("CONFIG_SOURCE_TEST_PROPERTY", null));
    }

}
