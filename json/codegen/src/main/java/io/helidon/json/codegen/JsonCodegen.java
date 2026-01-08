/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.json.codegen;

import java.util.Collection;

import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.CodegenException;
import io.helidon.codegen.RoundContext;
import io.helidon.codegen.classmodel.Annotation;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.spi.CodegenExtension;
import io.helidon.common.Weighted;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;

class JsonCodegen implements CodegenExtension {

    private final CodegenContext ctx;

    JsonCodegen(CodegenContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void process(RoundContext roundContext) {
        Collection<TypeInfo> typeInfos = roundContext.annotatedTypes(JsonTypes.JSON_ENTITY);
        for (TypeInfo typeInfo : typeInfos) {
            try {
                process(typeInfo, roundContext);
            } catch (Throwable ex) {
                throw new CodegenException("Failed to generate JSON code for the type: " + typeInfo, ex, typeInfo);
            }
        }
    }

    private void process(TypeInfo typeInfo, RoundContext roundContext) {
        TypeName annotatedTypeName = typeInfo.typeName();
        TypeName generatedType;
        ClassModel.Builder builder;
        if (annotatedTypeName.typeArguments().isEmpty()) {
            //We can create just regular Converter, no generics need to be resolved later
            ConvertedTypeInfo convertedTypeInfo = ConvertedTypeInfo.create(typeInfo, ctx);
            generatedType = convertedTypeInfo.converterType();
            builder = ClassModel.builder()
                    .type(generatedType)
                    .addAnnotation(b -> b.type(JsonTypes.SERVICE_REGISTRY_PER_LOOKUP))
                    .addAnnotation(Annotation.builder()
                                           .type(TypeNames.WEIGHT)
                                           .addParameter("value", Weighted.DEFAULT_WEIGHT - 5)
                                           .build());
            JsonConverterGenerator.generateConverter(builder, convertedTypeInfo, false);
        } else {
            String classNameWithEnclosingNames = typeInfo.typeName().classNameWithEnclosingNames();
            String replacedDot = classNameWithEnclosingNames.replace(".", "_");
            String typeBaseName = typeInfo.typeName().fqName().replace(classNameWithEnclosingNames, replacedDot);
            TypeName converterTypeName = TypeName.create(typeBaseName + "_BindingFactory");
            generatedType = TypeName.builder()
                    .from(converterTypeName)
                    .build();
            builder = ClassModel.builder().type(generatedType);
            JsonBindingFactoryGenerator.generateBindingFactory(builder, typeInfo, ctx);
        }

        roundContext.addGeneratedType(generatedType,
                                      builder,
                                      annotatedTypeName,
                                      typeInfo.originatingElement().orElse(annotatedTypeName));
    }

}
