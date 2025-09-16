/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.codegen;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class ModuleInfoParserTest {
    private static final String SOURCE_1 = """
            /*
             * Copyright (c) 2018, 2025 Oracle and/or its affiliates.
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
            
            import io.helidon.common.features.api.Features;
            import io.helidon.common.features.api.HelidonFlavor;
            
            /**
             * Header based authentication provider.
             */
            @Features.Name("Header")
            @Features.Description("Security provider for header based authentication")
            @Features.Flavor({HelidonFlavor.SE, HelidonFlavor.MP})
            @Features.Path({
                    "Security",
                    "Provider",
                    "Header"
                })
            module io.helidon.security.providers.header {
            
                requires io.helidon.common;
                requires io.helidon.security.providers.common;
                requires io.helidon.security.util;
            
                requires static io.helidon.common.features.api;
                requires static io.helidon.config.metadata;
            
                requires transitive io.helidon.config;
                requires transitive io.helidon.security;
            
                exports io.helidon.security.providers.header;
            
                provides io.helidon.security.spi.SecurityProviderService with io.helidon.security.providers.header.HeaderAtnService;
            
            }
            """;
    private static final String SOURCE_2 = """            
            open module io.helidon.service.tests.service.lifecycle {
                requires io.helidon.service.registry;
            }
            """;

    @Test
    public void testParsing1() {
        ModuleInfo moduleInfo = ModuleInfoSourceParser.parse(new BufferedReader(new StringReader(SOURCE_1)));

        assertThat(moduleInfo.name(), is("io.helidon.security.providers.header"));
        assertThat(moduleInfo.exports(), is(Map.of("io.helidon.security.providers.header", List.of())));
    }

    @Test
    public void testParsing2() {
        ModuleInfo moduleInfo = ModuleInfoSourceParser.parse(new BufferedReader(new StringReader(SOURCE_2)));

        assertThat(moduleInfo.name(), is("io.helidon.service.tests.service.lifecycle"));
        assertThat(moduleInfo.isOpen(), is(true));
        assertThat(moduleInfo.requires(), hasItem(new ModuleInfoRequires("io.helidon.service.registry", false, false)));
    }
}
