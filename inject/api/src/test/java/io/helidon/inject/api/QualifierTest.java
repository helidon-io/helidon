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

package io.helidon.inject.api;

import io.helidon.common.types.Annotation;

import jakarta.inject.Named;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

class QualifierTest {

    @Test
    void buildAndCompare() {
        Qualifier qav1 = Qualifier.builder()
                .type(Named.class)
                .value("x.y")
                .build();
        Annotation qav2 = Qualifier.builder()
                .type(Named.class)
                .value("x.y")
                .build();
        assertThat(qav1.compareTo(qav2),
                   is(0));
    }

    @Named("io.helidon.inject.api.DefaultQualifierTest")
    @ClassNamed(QualifierTest.class)
    @Test
    public void createClassNamed() throws Exception {
        Qualifier qav1 = Qualifier.createNamed(QualifierTest.class);
        Qualifier qav2 = Qualifier.builder()
                .type(Named.class)
                .value(Qualifier.class.getName())
                .build();
        assertThat(qav1.compareTo(qav2),
                   is(0));

        assertThat("runtime retention expected for " + ClassNamed.class,
                   getClass().getMethod("createClassNamed").getAnnotation(ClassNamed.class),
                   notNullValue());

    }

    @Test
    public void buildAndCompareClassNamed() {
        Qualifier qav1 = Qualifier.createNamed(new FakeNamed());
        Qualifier qav2 = Qualifier.createNamed(new FakeClassNamed());

        assertThat(qav1.compareTo(qav2),
                   is(0));
        assertThat(qav2.compareTo(qav1),
                   is(0));
    }

    static class FakeNamed implements Named {
        @Override
        public String value() {
            return QualifierTest.class.getName();
        }

        @Override
        public Class<? extends java.lang.annotation.Annotation> annotationType() {
            return Named.class;
        }
    }

    static class FakeClassNamed implements ClassNamed {
        @Override
        public Class value() {
            return QualifierTest.class;
        }

        @Override
        public Class<? extends java.lang.annotation.Annotation> annotationType() {
            return ClassNamed.class;
        }
    }

}
