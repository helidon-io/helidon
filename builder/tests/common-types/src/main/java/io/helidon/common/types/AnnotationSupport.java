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

package io.helidon.common.types;

import java.lang.reflect.Type;
import java.util.Map;

import io.helidon.builder.api.Prototype;

final class AnnotationSupport {
    private AnnotationSupport() {
    }

    /**
     * Creates an instance for an annotation with no value.
     *
     * @param annoType the annotation type
     * @return the new instance
     */
    @Prototype.FactoryMethod
    static Annotation create(Class<? extends java.lang.annotation.Annotation> annoType) {
        return Annotation.builder()
                .typeName(TypeName.create(annoType))
                .build();
    }

    /**
     * Creates an instance for an annotation with no value.
     *
     * @param annoType the annotation type
     * @return the new instance
     */
    @Prototype.FactoryMethod
    static Annotation create(TypeName annoType) {
        return Annotation.builder()
                .typeName(annoType)
                .build();
    }

    /**
     * Creates an instance for an annotation with a value.
     *
     * @param annoType the annotation type
     * @param value the annotation value
     * @return the new instance
     */
    @Prototype.FactoryMethod
    static Annotation create(Class<? extends java.lang.annotation.Annotation> annoType,
                             String value) {
        return create(TypeName.create(annoType), value);
    }

    /**
     * Creates an instance for an annotation with a value.
     *
     * @param annoType the annotation type
     * @param values the annotation values
     * @return the new instance
     */
    @Prototype.FactoryMethod
    static Annotation create(Class<? extends java.lang.annotation.Annotation> annoType,
                             Map<String, String> values) {
        return create(TypeName.create(annoType), values);
    }

    /**
     * Creates an instance for an annotation with a value.
     *
     * @param annoTypeName the annotation type name
     * @param value the annotation value
     * @return the new instance
     */
    @Prototype.FactoryMethod
    public static Annotation create(TypeName annoTypeName,
                                    String value) {
        return Annotation.builder().typeName(annoTypeName).value(value).build();
    }

    /**
     * Creates an instance for annotation with zero or more values.
     *
     * @param annoTypeName the annotation type name
     * @param values the annotation values
     * @return the new instance
     */
    @Prototype.FactoryMethod
    public static Annotation create(TypeName annoTypeName,
                                    Map<String, String> values) {
        return Annotation.builder().typeName(annoTypeName).values(values).build();
    }

    @Prototype.PrototypeMethod
    @Prototype.Annotated("java.lang.Override")
    static int compareTo(Annotation me, Annotation o) {
        return me.typeName().compareTo(o.typeName());
    }

    /**
     * Annotation type name from annotation type.
     *
     * @param builder builder to update
     * @param annoType annotation class
     */
    @Prototype.BuilderMethod
    static void type(Annotation.BuilderBase<?, ?> builder, Type annoType) {
        builder.typeName(TypeName.create(annoType));
    }

    /**
     * Configure the value of this annotation (property of name {@code value}).
     *
     * @param builder builder to update
     * @param value value of the annotation
     */
    @Prototype.BuilderMethod
    static void value(Annotation.BuilderBase<?, ?> builder, String value) {
        builder.putValue("value", value);
    }
}
