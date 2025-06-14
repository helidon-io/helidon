/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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

package io.helidon.common.types;

import java.lang.annotation.Target;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class AnnotationTest {

    @Test
    void sanity() {
        Annotation val1 = Annotation.create(Test.class);
        assertThat(val1.typeName().toString(), equalTo(Test.class.getName()));
        assertThat(val1.toString(),
                   containsString(Test.class.getName()));

        Annotation val2 = Annotation.create(Test.class);
        assertThat(val2, equalTo(val1));
        assertThat(val2.compareTo(val1), is(0));

        Annotation val3 = Annotation.create(Target.class, "name");
        assertThat(val3.toString(),
                   containsString("java.lang.annotation.Target"));
        assertThat(val3.toString(),
                   containsString("value=name"));

        Annotation val4 = Annotation.create(Test.class, Map.of("a", "1"));
        assertThat(val4.toString(),
                   containsString(Test.class.getName()));
        assertThat(val4.toString(),
                   containsString("{a=1}"));
    }

    @Test
    void testConstantValue() {
        Annotation annotation = Annotation.builder()
                .type(Target.class)
                .putValue("value", AnnotationProperty.create("name", TypeName.create(AnnotationTest.class), "CONSTANT_NAME"))
                .build();

        String toString = annotation.toString();
        assertThat(toString,
                   containsString("java.lang.annotation.Target"));
        assertThat(toString,
                   containsString("value=" + AnnotationTest.class.getName() + ".CONSTANT_NAME"));
    }

}
