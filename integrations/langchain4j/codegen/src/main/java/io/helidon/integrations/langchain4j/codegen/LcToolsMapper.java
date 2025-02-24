/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.integrations.langchain4j.codegen;

import java.util.Optional;

import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.ElementInfoPredicates;
import io.helidon.codegen.spi.TypeMapper;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeInfo;

import static io.helidon.integrations.langchain4j.codegen.LangchainTypes.AI_TOOL;
import static io.helidon.integrations.langchain4j.codegen.LangchainTypes.LC_TOOL;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_ANNOTATION_QUALIFIER;

class LcToolsMapper implements TypeMapper {
    private static final Annotation AI_TOOL_QUALIFIER = Annotation.builder()
            .typeName(AI_TOOL)
            .addMetaAnnotation(Annotation.create(SERVICE_ANNOTATION_QUALIFIER))
            .build();

    LcToolsMapper() {
    }

    @Override
    public boolean supportsType(TypeInfo typeInfo) {
        return typeInfo.elementInfo()
                .stream()
                .filter(ElementInfoPredicates::isMethod)
                .anyMatch(ElementInfoPredicates.hasAnnotation(LC_TOOL));
    }

    @Override
    public Optional<TypeInfo> map(CodegenContext codegenContext, TypeInfo typeInfo) {
        return Optional.of(TypeInfo.builder(typeInfo)
                                   .addAnnotation(AI_TOOL_QUALIFIER)
                                   .build());
    }
}
