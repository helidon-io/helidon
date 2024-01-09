/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.inject.tests.configbeans;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.junit5.OptionalMatcher.optionalEmpty;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasEntry;

class EnumRelatedConfigBeanTest {

    @Test
    void testIt() {
        EnumRelatedConfig cfg = EnumRelatedConfig.builder()
                .clientAuth(FakeClientAuth.OPTIONAL)
                .list(List.of(EnumRelatedConfig.InlinedEnum.TEST))
                .addSet(Set.of(EnumRelatedConfig.InlinedEnum.TEST))
                .putMap("test", EnumRelatedConfig.InlinedEnum.TEST)
                .build();

        assertThat(cfg.clientAuth(),
                   equalTo(FakeClientAuth.OPTIONAL));
        assertThat(cfg.optionalClientAuth(),
                   optionalEmpty());
        assertThat(cfg.set(),
                   contains(EnumRelatedConfig.InlinedEnum.TEST));
        assertThat(cfg.list(),
                   contains(EnumRelatedConfig.InlinedEnum.TEST));
        assertThat(cfg.map(),
                   hasEntry("test", EnumRelatedConfig.InlinedEnum.TEST));
    }

}
