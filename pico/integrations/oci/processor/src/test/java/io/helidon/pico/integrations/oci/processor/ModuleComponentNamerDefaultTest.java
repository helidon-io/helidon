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

package io.helidon.pico.integrations.oci.processor;

import java.util.Set;

import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.junit5.OptionalMatcher.optionalEmpty;
import static io.helidon.common.testing.junit5.OptionalMatcher.optionalValue;
import static io.helidon.common.types.TypeNameDefault.createFromTypeName;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

class ModuleComponentNamerDefaultTest {

    @Test
    void suggestedPackageName() {
        ModuleComponentNamerDefault namer = new ModuleComponentNamerDefault();
        assertThat(namer.suggestedPackageName(Set.of()),
                   optionalEmpty());
        assertThat(namer.suggestedPackageName(Set.of(createFromTypeName("com.oracle.bmc.whatever.Service"))),
                   optionalEmpty());
        assertThat(namer.suggestedPackageName(Set.of(createFromTypeName("com.oracle.another.whatever.Service"))),
                   optionalValue(equalTo("com.oracle.another.whatever")));
        assertThat(namer.suggestedPackageName(Set.of(createFromTypeName("com.oracle.bmc.Service"),
                                                     createFromTypeName("com.oracle.another.whatever.Service"))),
                   optionalValue(equalTo("com.oracle.another.whatever")));
    }

}
