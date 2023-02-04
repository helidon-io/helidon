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

package io.helidon.builder.types;

import java.util.Map;

import jakarta.inject.Named;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class DefaultAnnotationAndValueTest {

    @Test
    void sanity() {
        DefaultAnnotationAndValue val1 = DefaultAnnotationAndValue.create(Test.class);
        assertThat(val1.typeName().toString(), equalTo(Test.class.getName()));
        assertThat(val1.toString(),
                   equalTo("DefaultAnnotationAndValue(typeName=" + Test.class.getName() + ")"));

        DefaultAnnotationAndValue val2 = DefaultAnnotationAndValue.create(Test.class);
        assertThat(val2, equalTo(val1));
        assertThat(val2.compareTo(val1), is(0));

        DefaultAnnotationAndValue val3 = DefaultAnnotationAndValue.create(Named.class, "name");
        assertThat(val3.toString(),
                   equalTo("DefaultAnnotationAndValue(typeName=jakarta.inject.Named, value=name)"));

        DefaultAnnotationAndValue val4 = DefaultAnnotationAndValue.create(Test.class, Map.of("a", "1"));
        assertThat(val4.toString(),
                   equalTo("DefaultAnnotationAndValue(typeName=" + Test.class.getName() + ", values={a=1})"));
    }

}
