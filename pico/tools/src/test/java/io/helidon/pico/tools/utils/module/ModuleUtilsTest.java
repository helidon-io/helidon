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

package io.helidon.pico.tools.utils.module;

import io.helidon.pico.tools.types.SimpleModuleDescriptor;
import io.helidon.pico.tools.utils.ModuleUtils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for {@link io.helidon.pico.tools.utils.ModuleUtils}.
 */
public class ModuleUtilsTest {

    /**
     * Tests for {@link io.helidon.pico.tools.utils.ModuleUtils#toSuggestedModuleName(String, String, String)}.
     */
    @Test
    public void toModuleName() {
        assertEquals("unnamed", ModuleUtils.toSuggestedModuleName(null, null, SimpleModuleDescriptor.DEFAULT_MODULE_NAME));
        assertEquals("unnamed/test", ModuleUtils.toSuggestedModuleName(null, "test", null));
        assertEquals("foo/bar", ModuleUtils.toSuggestedModuleName("foo", "bar", SimpleModuleDescriptor.DEFAULT_MODULE_NAME));
    }

}
