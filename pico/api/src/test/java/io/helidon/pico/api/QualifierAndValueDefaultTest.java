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

package io.helidon.pico.api;

import java.lang.annotation.Annotation;

import io.helidon.common.types.AnnotationAndValueDefault;

import jakarta.inject.Named;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

class QualifierAndValueDefaultTest {

    @Test
    void buildAndCompare() {
        QualifierAndValueDefault qav1 = QualifierAndValueDefault.builder()
                .type(Named.class)
                .value("x.y")
                .build();
        AnnotationAndValueDefault qav2 = QualifierAndValueDefault.builder()
                .type(Named.class)
                .value("x.y")
                .build();
        assertThat(qav1.compareTo(qav2),
                   is(0));
    }

    @Test
    public void buildAndCompareClassNamed() {
        QualifierAndValueDefault qav1 = QualifierAndValueDefault.createNamed(new FakeNamed());
        QualifierAndValueDefault qav2 = QualifierAndValueDefault.createNamed(new FakeClassNamed());

        assertThat(qav1.compareTo(qav2),
                   is(0));
        assertThat(qav2.compareTo(qav1),
                   is(0));
    }

    @Named("io.helidon.pico.api.DefaultQualifierAndValueTest")
    @ClassNamed(QualifierAndValueDefaultTest.class)
    @Test
    public void createClassNamed() throws Exception {
        QualifierAndValueDefault qav1 = QualifierAndValueDefault.createClassNamed(QualifierAndValueDefaultTest.class);
        QualifierAndValueDefault qav2 = QualifierAndValueDefault.builder()
                .type(Named.class)
                .value(QualifierAndValueDefault.class.getName())
                .build();
        assertThat(qav1.compareTo(qav2),
                   is(0));

        assertThat("runtime retention expected for " + ClassNamed.class,
                   getClass().getMethod("createClassNamed").getAnnotation(ClassNamed.class),
                   notNullValue());
    }

    class FakeNamed implements Named {
        @Override
        public String value() {
            return QualifierAndValueDefaultTest.class.getName();
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return Named.class;
        }
    }

    class FakeClassNamed implements ClassNamed {
        @Override
        public Class value() {
            return QualifierAndValueDefaultTest.class;
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return ClassNamed.class;
        }
    }

}
