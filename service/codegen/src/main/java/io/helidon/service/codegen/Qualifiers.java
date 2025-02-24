/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.service.codegen;

import java.util.Iterator;
import java.util.Set;
import java.util.stream.Collectors;

import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.Field;
import io.helidon.common.types.Annotated;
import io.helidon.common.types.Annotation;

import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_ANNOTATION_QUALIFIER;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_QUALIFIER;
import static io.helidon.service.codegen.ServiceCodegenTypes.SET_OF_QUALIFIERS;

final class Qualifiers {
    private Qualifiers() {
    }

    static Set<Annotation> qualifiers(Annotated annotated) {
        return annotated.annotations()
                .stream()
                .filter(it -> it.hasMetaAnnotation(SERVICE_ANNOTATION_QUALIFIER))
                .collect(Collectors.toUnmodifiableSet());
    }

    static void generateQualifiersConstant(ClassModel.Builder classModel, Set<Annotation> qualifiers) {
        classModel.addField(qualifiersField -> qualifiersField
                .isStatic(true)
                .isFinal(true)
                .name("QUALIFIERS")
                .type(SET_OF_QUALIFIERS)
                .addContent(Set.class)
                .addContent(".of(")
                .update(it -> {
                    Iterator<Annotation> iterator = qualifiers.iterator();
                    while (iterator.hasNext()) {
                        codeGenQualifier(it, iterator.next());
                        if (iterator.hasNext()) {
                            it.addContent(", ");
                        }
                    }
                })
                .addContent(")"));
    }

    private static void codeGenQualifier(Field.Builder field, Annotation qualifier) {
        if (qualifier.value().isPresent()) {
            field.addContent(SERVICE_QUALIFIER)
                    .addContent(".create(")
                    .addContentCreate(qualifier.typeName())
                    .addContent(", \"" + qualifier.value().get() + "\")");
            return;
        }

        field.addContent(SERVICE_QUALIFIER)
                .addContent(".create(")
                .addContentCreate(qualifier.typeName())
                .addContent(")");
    }
}
