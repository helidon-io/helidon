/*
 * Copyright (c) 2022, 2025 Oracle and/or its affiliates.
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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.helidon.builder.test.testsubjects.ComplexCase;
import io.helidon.builder.test.testsubjects.MyConfigBean;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.hamcrest.collection.IsMapContaining.hasEntry;
import static org.junit.jupiter.api.Assertions.assertAll;

class ComplexCaseTest {
    @Test
    void testCustomAddName() {
        var configBean = MyConfigBean.builder()
                .setName("cfb")
                .build();

        var complexCase = ComplexCase.builder()
                .setName("name")
                .setClassType(Object.class)
                .allowConfigBean(configBean)                .build();

        assertThat(complexCase.getName(), is("name"));
        assertThat(complexCase.getListOfConfigBeans(), hasItem(configBean));
    }

    @Test
    void testIt() {
        Map<String, List<MyConfigBean>> mapWithNull = new HashMap<>();
        mapWithNull.put("key", null);

        ComplexCase val = ComplexCase.builder()
                .setName("name")
                .setMapOfKeyToConfigBeans(mapWithNull)
                .setSetOfLists(Set.of(Collections.singletonList(null)))
                .setClassType(Object.class)
                .build();

        assertAll(
                () -> assertThat(val.getName(), is("name")),
                () -> assertThat(val.isEnabled(), is(false)),
                () -> assertThat(val.getPort(), is(8080)),
                () -> assertThat(val.getListOfConfigBeans(), empty()),
                () -> assertThat(val.getSetOfLists(), hasSize(1)),
                () -> assertThat(val.getClassType(), is(Object.class))
        );

        assertThat(val.getSetOfLists().iterator().next(), hasItem(nullValue()));
        assertThat(val.getMapOfKeyToConfigBeans(), hasEntry(is("key"), nullValue()));
    }

}
