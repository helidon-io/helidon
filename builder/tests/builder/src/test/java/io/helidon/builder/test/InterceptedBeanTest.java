/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

import io.helidon.builder.test.testsubjects.InterceptedBean;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

class InterceptedBeanTest {

    @Test
    void testMutation() {
        InterceptedBean val = InterceptedBean.builder()
                .name("Larry")
                .build();
        assertThat(val.name(), equalTo("Larry"));
        assertThat(val.helloMessage(), equalTo("Hello Larry"));

        InterceptedBean val2 = InterceptedBean.builder()
                .name("Larry")
                .build();
        assertThat(val, equalTo(val2));
    }

}
