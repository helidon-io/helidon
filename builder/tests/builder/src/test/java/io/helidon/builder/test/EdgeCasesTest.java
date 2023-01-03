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

package io.helidon.builder.test;

import io.helidon.builder.test.testsubjects.DefaultEdgeCases;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class EdgeCasesTest {

    @Test
    void testIt() {
        DefaultEdgeCases val = DefaultEdgeCases.builder().build();
        assertThat(val.optionalIntegerWithDefault().get(), is(-1));
        assertThat(val.optionalStringWithDefault().get(), equalTo("test"));

        val = DefaultEdgeCases.toBuilder(val).optionalIntegerWithDefault(-2).build();
        assertThat(val.optionalIntegerWithDefault().get(), is(-2));
        assertThat(val.optionalStringWithDefault().get(), equalTo("test"));
    }

}
