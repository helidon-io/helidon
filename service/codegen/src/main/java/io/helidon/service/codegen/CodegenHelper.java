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
import java.util.List;

import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeInfo;

import static io.helidon.service.codegen.ServiceCodegenTypes.GENERATED_ANNOTATION;
import static io.helidon.service.codegen.ServiceCodegenTypes.LIST_OF_ANNOTATIONS;

/**
 * Helper methods to code generate that are used from more than one type.
 */
final class CodegenHelper {
    private CodegenHelper() {
    }

    static void annotationsField(ClassModel.Builder classModel, TypeInfo service) {
        classModel.addField(annotations -> annotations
                .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                .isStatic(true)
                .isFinal(true)
                .name("ANNOTATIONS")
                .type(LIST_OF_ANNOTATIONS)
                .addContent(List.class)
                .addContent(".of(")
                .update(it -> {
                    Iterator<Annotation> iterator = service.annotations()
                            .stream()
                            .filter(annot -> !annot.typeName().equals(GENERATED_ANNOTATION))
                            .iterator();
                    while (iterator.hasNext()) {
                        Annotation next = iterator.next();
                        it.addContentCreate(next);
                        if (iterator.hasNext()) {
                            it.addContent(", ");
                        }
                    }
                })
                .addContent(")"));
    }
}
