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

package io.helidon.pico.types.test;

import io.helidon.pico.types.DefaultAnnotationAndValue;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class DefaultAnnotationAndValueTest {

    @Test
    void sanity() {
        DefaultAnnotationAndValue val1 = DefaultAnnotationAndValue.create(Test.class);
        assertThat(val1.typeName().toString(), equalTo("org.junit.jupiter.api.Test"));
        assertThat(val1.toString(), equalTo("DefaultAnnotationAndValue(typeName=org.junit.jupiter.api.Test, values={})"));

        DefaultAnnotationAndValue val2 = DefaultAnnotationAndValue.create(Test.class);
        assertThat(val2, equalTo(val1));
        assertThat(val2.compareTo(val1), is(0));
    }

}
