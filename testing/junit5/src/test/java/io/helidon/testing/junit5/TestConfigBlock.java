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

package io.helidon.testing.junit5;

import io.helidon.config.Config;

import org.junit.jupiter.api.Test;

import static io.helidon.config.ConfigValues.simpleValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@Testing.Test
@Testing.AddConfigBlock(value = """
        test-a:
            test-c: value-c-in-parent-block
            test-d: value-d-in-parent-block
        """, type = "yaml")
class TestConfigBlock {

    @Test
    void mergeExisting(Config c) {
        assertThat(c.get("test-a.test-b").asString(), is(simpleValue("value-b-in-file")));
        assertThat(c.get("test-a.test-c").asString(), is(simpleValue("value-c-in-parent-block")));
    }

    @Test
    void overrideExisting(Config c) {
        assertThat(c.get("test-a.test-d").asString(), is(simpleValue("value-d-in-parent-block")));
    }
}
