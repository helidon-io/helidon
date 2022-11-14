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

package io.helidon.pico.tools.types;

import io.helidon.pico.types.DefaultTypeName;
import io.helidon.pico.tools.processor.TypeTools;
import io.helidon.pico.types.TypeName;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TypeNameToolsTest {

    @Test
    public void moduleInfo() {
        TypeName moduleInfo = DefaultTypeName.create(null, SimpleModuleDescriptor.MODULE_INFO_NAME);
        assertEquals("module-info", moduleInfo.name());
        assertEquals("module-info", moduleInfo.className());
        assertEquals(null, moduleInfo.packageName());
        assertEquals("/module-info.java", TypeTools.getFilePath(moduleInfo));
    }

}
