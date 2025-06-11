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

package io.helidon.builder.test;

import io.helidon.builder.test.testsubjects.VarargConfig;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.collection.IsEmptyCollection.empty;

class VarargTest {
    @Test
    void testNone() {
        VarargConfig config = VarargConfig.create();
        assertThat(config.options(), empty());
    }

    @Test
    void testOne() {
        VarargConfig config = VarargConfig.create("option1");
        assertThat(config.options(), hasItem("option1"));
    }

    @Test
    void testMore() {
        VarargConfig config = VarargConfig.create("option1", "option2");
        assertThat(config.options(), hasItems("option1", "option2"));
    }

}
