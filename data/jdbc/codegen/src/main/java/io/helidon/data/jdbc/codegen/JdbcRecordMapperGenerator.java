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

import java.util.List;

import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeInfo;
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
     * Validates a record and emits its reusable row mapper.
     *
     * @param plan method plan
     * @param fieldName generated mapper field name
     * @param classModel repository implementation
     * @param context code-generation context
     */
    static void generate(JdbcMethodPlan plan,
                         String fieldName,
                         ClassModel.Builder classModel,
                         CodegenContext context) {
        TypeInfo recordInfo = context.typeInfo(plan.mappedType().genericTypeName())
                .orElseThrow(() -> JdbcMethodPlan.failure(plan.method(),
                                                          "Record type information is unavailable: "
                                                                  + plan.mappedType().resolvedName()));
        validateAccessibility(plan.method(), recordInfo, context);
        List<TypedElementInfo> components = components(recordInfo);
        validateComponents(plan, components);

        TypeName mapperType = TypeName.builder(JdbcPersistenceTypes.ROW_MAPPER)
                .addTypeArgument(plan.mappedType())
                .build();
        classModel.addField(field -> {
            field.name(fieldName)
                    .description("Maps one JDBC row to " + plan.mappedType().className() + ".")
                    .type(mapperType)
                    .isStatic(true)
                    .isFinal(true)
                    .addContent("row -> new ")
                    .addContent(plan.mappedType())
                    .addContent("(");
            // Components are in canonical constructor order. Labels let the SQL columns use a different order.
            for (int index = 0; index < components.size(); index++) {
                if (index > 0) {
                    field.addContent(", ");
                }
                TypedElementInfo component = components.get(index);
                TypeName optionalType = JdbcScalarTypes.optionalScalarType(component.typeName());
                field.addContent("row.")
                        .addContent(optionalType == null ? "required(" : "optional(")
                        .addContentLiteral(component.elementName())
                        .addContent(", ")
                        .addContent((optionalType == null ? component.typeName() : optionalType).boxed())
                        .addContent(".class)");
            }
            field.addContent(")");
        });
    }

    /**
     * Verifies that generated code can name and construct the record.
     *
     * @param method repository method
     * @param recordInfo record metadata
     * @param context code-generation context
     */
    private static void validateAccessibility(TypedElementInfo method,
                                              TypeInfo recordInfo,
                                              CodegenContext context) {
        String repositoryPackage = method.enclosingType()
                .map(TypeName::packageName)
                .orElse("");
        if (!JdbcTypeAccessibility.accessible(context, recordInfo, repositoryPackage)) {
            throw JdbcMethodPlan.failure(method,
                                         "Record type is not accessible to generated code: "
                                                 + recordInfo.typeName().resolvedName());
        }
    }

    /**
     * Returns canonical record components in declaration order.
     *
     * @param recordInfo record metadata
     * @return record components
     */
    private static List<TypedElementInfo> components(TypeInfo recordInfo) {
        return recordInfo.elementInfo()
                .stream()
                .filter(element -> element.kind() == ElementKind.RECORD_COMPONENT)
                .toList();
    }

    /**
     * Restricts record components to scalars and explicit Optional scalars.
     *
     * @param plan method plan
     * @param components record components
     */
    private static void validateComponents(JdbcMethodPlan plan, List<TypedElementInfo> components) {
        for (TypedElementInfo component : components) {
            if (!JdbcScalarTypes.isScalar(component.typeName())
                    && JdbcScalarTypes.optionalScalarType(component.typeName()) == null) {
                throw JdbcMethodPlan.failure(plan.method(),
                                             "Unsupported record component type "
                                                     + component.typeName().resolvedName() + " for "
                                                     + component.elementName());
            }
        }
    }
}
