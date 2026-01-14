/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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

package io.helidon.json.schema.codegen;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Collection;

import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.CodegenException;
import io.helidon.codegen.RoundContext;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.TypeArgument;
import io.helidon.codegen.spi.CodegenExtension;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.Annotations;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.metadata.hson.Hson;

class SchemaCodegen implements CodegenExtension {

    private final CodegenContext ctx;

    SchemaCodegen(CodegenContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void process(RoundContext roundContext) {
        Collection<TypeInfo> schemas = roundContext.annotatedTypes(SchemaTypes.JSON_SCHEMA_SCHEMA);
        for (TypeInfo schema : schemas) {
            try {
                generateSchema(roundContext, schema);
            } catch (Throwable ex) {
                throw new CodegenException("Failed to generate JSON schema for the type: " + schema, ex, schema);
            }
        }

    }

    private void generateSchema(RoundContext roundContext, TypeInfo schema) {
        TypeName annotatedTypeName = schema.typeName();
        SchemaInfo schemaInfo = SchemaInfo.create(schema, ctx);
        TypeName typeName = schemaInfo.generatedSchema();
        Hson.Struct helidonSchema = schemaInfo.schema();
        TypeName returnType = TypeName.builder()
                .type(Class.class)
                .addTypeArgument(TypeArgument.create("?"))
                .build();
        ClassModel.Builder builder = ClassModel.builder()
                .type(typeName)
                .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                .addInterface(SchemaTypes.JSON_SCHEMA_PROVIDER)
                .addAnnotation(Annotation.create(SchemaTypes.SERVICE_SINGLETON))
                .addAnnotation(Annotation.builder()
                                       .typeName(SchemaTypes.SERVICE_NAMED_BY_TYPE)
                                       .putValue("value", annotatedTypeName)
                                       .build())
                .sortStaticFields(false)
                .addField(fieldBuilder -> fieldBuilder.isStatic(true)
                        .isFinal(true)
                        .accessModifier(AccessModifier.PRIVATE)
                        .name("STRING_SCHEMA")
                        .type(String.class)
                        .defaultValueContent("\"\"\"\n" + generateSchemaString(helidonSchema) + "\"\"\""))
                .addField(fieldBuilder -> fieldBuilder.isStatic(true)
                        .accessModifier(AccessModifier.PRIVATE)
                        .isFinal(true)
                        .type(SchemaTypes.LAZY_VALUE_SCHEMA)
                        .name("LAZY_SCHEMA")
                        .defaultValueContent("@io.helidon.common.LazyValue@.create(() -> "
                                                     + "@io.helidon.json.schema.Schema@.parse(STRING_SCHEMA))"))
                .addMethod(it -> it.name("schemaClass")
                        .returnType(returnType)
                        .addAnnotation(Annotations.OVERRIDE)
                        .addContent("return ")
                        .addContent(annotatedTypeName)
                        .addContentLine(".class;"))
                .addMethod(it -> it.name("jsonSchema")
                        .returnType(TypeNames.STRING)
                        .addAnnotation(Annotations.OVERRIDE)
                        .addContentLine("return STRING_SCHEMA;"))
                .addMethod(it -> it.name("schema")
                        .returnType(SchemaTypes.SCHEMA)
                        .addAnnotation(Annotations.OVERRIDE)
                        .addContent("return LAZY_SCHEMA.get();"));

        roundContext.addGeneratedType(typeName,
                                      builder,
                                      annotatedTypeName,
                                      schema.originatingElement().orElse(annotatedTypeName));
    }

    private String generateSchemaString(Hson.Struct helidonSchema) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PrintWriter writer = new PrintWriter(baos, true, StandardCharsets.UTF_8)) {
            helidonSchema.write(writer, true);
        }
        return baos.toString(StandardCharsets.UTF_8);
    }

}
