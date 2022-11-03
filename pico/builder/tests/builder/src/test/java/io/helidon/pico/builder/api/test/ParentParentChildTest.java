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

package io.helidon.pico.builder.api.test;

import java.net.URI;

import io.helidon.pico.builder.test.testsubjects.ChildInterfaceIsABuilder;
import io.helidon.pico.builder.test.testsubjects.ChildInterfaceIsABuilderImpl;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class ParentParentChildTest {

    @Test
    void collapsedMiddleType() {
        ChildInterfaceIsABuilder child = ChildInterfaceIsABuilderImpl.builder()
                .childLevel(100)
                .parentLevel(99)
                .uri(URI.create("http://localhost"))
                .empty((String) null)
                .build();
        assertThat(new String(child.overrideMe()), equalTo("override"));
        assertThat(child.uri().get().toString(), equalTo("http://localhost"));
        assertThat(child.empty().isEmpty(), is(true));
        assertThat(child.childLevel(), is(100L));
        assertThat(child.parentLevel(), is(99L));
        assertThat(child.isChildLevel(), is(true));
    }

    /**
     * Presumably someone may want to keep a password in the bean, and if so we should not show it to callers in toString().
     */
    @Test
    void ensureCharArraysAreHiddenFromToStringOutput() {
        ChildInterfaceIsABuilderImpl val = ChildInterfaceIsABuilderImpl.builder()
                .overrideMe("password")
                .build();
        assertThat(val.toString(),
                equalTo("ChildInterfaceIsABuilder(uri=null, empty=null, parentLevel=0, childLevel=0, isChildLevel=true, "
                        + "overrideMe=not-null)"));

        val = ChildInterfaceIsABuilderImpl.toBuilder(val).overrideMe((char[]) null).build();
        assertThat(val.toString(),
                equalTo("ChildInterfaceIsABuilder(uri=null, empty=null, parentLevel=0, childLevel=0, isChildLevel=true, "
                        + "overrideMe=null)"));
    }

}
