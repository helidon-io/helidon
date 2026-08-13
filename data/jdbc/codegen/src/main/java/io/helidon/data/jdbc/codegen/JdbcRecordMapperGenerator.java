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
package io.helidon.data.jdbc.codegen;

import java.util.Optional;

import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;

/**
 * Generates direct canonical-constructor mappers for flat records.
 */
final class JdbcRecordMapperGenerator {

    /**
     * Prevents construction of the generator utility.
     */
    private JdbcRecordMapperGenerator() {
    }

    /**
     * Emits a reusable row mapper from record metadata validated during method
     * planning.
     *
     * @param plan method plan
     * @param fieldName generated mapper field name
     * @param classModel repository implementation
     */
    static void generate(JdbcMethodPlan plan,
                         String fieldName,
                         ClassModel.Builder classModel) {

        TypeName mapperType = TypeName.builder(JdbcPersistenceTypes.ROW_MAPPER)
                .addTypeArgument(plan.mappedType())
                .build();
        classModel.addField(field -> {
            field.name(fieldName)
                    .type(mapperType)
                    .isStatic(true)
                    .isFinal(true)
                    .addContent("row -> new ")
                    .addContent(plan.mappedType())
                    .addContent("(");
            // Record components use canonical constructor order. Labels allow the SQL columns to use a different order.
            for (int index = 0; index < plan.recordComponents().size(); index++) {
                if (index > 0) {
                    field.addContent(", ");
                }
                TypedElementInfo component = plan.recordComponents().get(index);
                Optional<TypeName> optionalType = JdbcScalarTypes.optionalScalarType(component.typeName());
                field.addContent("row.")
                        .addContent(optionalType.isPresent() ? "optional(" : "required(")
                        .addContentLiteral(component.elementName())
                        .addContent(", ")
                        .addContent(optionalType.orElse(component.typeName()).boxed())
                        .addContent(".class)");
            }
            field.addContent(")");
        });
    }

}
