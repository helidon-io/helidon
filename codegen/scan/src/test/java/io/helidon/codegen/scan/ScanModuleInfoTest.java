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

package io.helidon.codegen.scan;

import java.lang.module.ModuleFinder;
import java.util.List;

import io.helidon.codegen.ModuleInfo;
import io.helidon.codegen.ModuleInfoRequires;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;

class ScanModuleInfoTest {
    @Test
    void mapsJdkModuleReference() {
        var reference = ModuleFinder.ofSystem().find("java.logging").orElseThrow();
        ModuleInfo module = ScanModuleInfo.map(reference).orElseThrow();

        assertThat(module.name(), is("java.logging"));
        assertThat(module.exports().get("java.util.logging"), is(List.of()));
        assertThat(module.requires().stream().map(ModuleInfoRequires::module).toList(), hasItem("java.base"));
        assertThat(module.isOpen(), is(false));
    }
}
