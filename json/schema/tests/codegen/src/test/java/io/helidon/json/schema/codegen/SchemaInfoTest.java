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

package io.helidon.json.schema.codegen;

import java.util.List;
import java.util.Set;

import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.CodegenException;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.Modifier;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.json.JsonObject;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyZeroInteractions;

class SchemaInfoTest {
    private static final TypeName MODEL = TypeName.create("example.Model");
    private static final TypeName JSONB_PROPERTY = TypeName.create("jakarta.json.bind.annotation.JsonbProperty");
    private static final TypeName JSONB_TRANSIENT = TypeName.create("jakarta.json.bind.annotation.JsonbTransient");
    private static final TypeName JSONB_CREATOR = TypeName.create("jakarta.json.bind.annotation.JsonbCreator");

    @Test
    void testFieldMetadata() {
        var renamed = element(ElementKind.FIELD, "original")
                .addAnnotation(Annotation.create(JSONB_PROPERTY, "renamed"))
                .build();
        var ignored = element(ElementKind.FIELD, "ignored")
                .addAnnotation(Annotation.create(JSONB_TRANSIENT))
                .build();

        JsonObject properties = schema(renamed, ignored).objectValue("properties").orElseThrow();
        assertThat(properties.keysAsStrings(), is(Set.of("renamed")));
        assertThat(properties.objectValue("renamed").orElseThrow().stringValue("type").orElseThrow(), is("string"));
    }

    @Test
    void testSetterMetadata() {
        var renamed = setter("setOriginal")
                .addAnnotation(Annotation.create(JSONB_PROPERTY, "renamed"))
                .build();
        var ignored = setter("setIgnored")
                .addAnnotation(Annotation.create(JSONB_TRANSIENT))
                .build();

        JsonObject properties = schema(renamed, ignored).objectValue("properties").orElseThrow();
        assertThat(properties.keysAsStrings(), is(Set.of("renamed")));
        assertThat(properties.objectValue("renamed").orElseThrow().stringValue("type").orElseThrow(), is("string"));
    }

    @Test
    void testCreatorParameterMetadata() {
        var parameter = element(ElementKind.PARAMETER, "original")
                .addAnnotation(Annotation.create(JSONB_PROPERTY, "created"))
                .build();
        var creator = creator(parameter).build();
        var field = element(ElementKind.FIELD, "created")
                .accessModifier(AccessModifier.PRIVATE)
                .addAnnotation(Annotation.create(SchemaTypes.JSON_SCHEMA_REQUIRED))
                .build();

        JsonObject schema = schema(creator, field);
        JsonObject properties = schema.objectValue("properties").orElseThrow();
        assertThat(properties.keysAsStrings(), is(Set.of("created")));
        assertThat(properties.objectValue("created").orElseThrow().stringValue("type").orElseThrow(), is("string"));
        assertThat(schema.arrayValue("required").orElseThrow().get(0).orElseThrow().asString().value(), is("created"));
    }

    @Test
    void testSchemaNamesTakePrecedence() {
        var field = element(ElementKind.FIELD, "field")
                .addAnnotation(Annotation.create(JSONB_PROPERTY, "jsonbField"))
                .addAnnotation(Annotation.create(SchemaTypes.JSON_SCHEMA_PROPERTY_NAME, "schemaField"))
                .build();
        var setter = setter("setValue")
                .addAnnotation(Annotation.create(JSONB_PROPERTY, "jsonbSetter"))
                .addAnnotation(Annotation.create(SchemaTypes.JSON_SCHEMA_PROPERTY_NAME, "schemaSetter"))
                .build();
        var parameter = element(ElementKind.PARAMETER, "parameter")
                .addAnnotation(Annotation.create(JSONB_PROPERTY, "jsonbParameter"))
                .build();

        JsonObject properties = schema(field, setter, creator(parameter).build()).objectValue("properties").orElseThrow();
        assertThat(properties.keysAsStrings(), is(Set.of("schemaField", "schemaSetter", "jsonbParameter")));
    }

    @Test
    void testTransientFieldCannotSupplyCreatorParameter() {
        var field = element(ElementKind.FIELD, "value")
                .addAnnotation(Annotation.create(JSONB_TRANSIENT))
                .build();
        var creator = creator(element(ElementKind.PARAMETER, "value").build()).build();

        CodegenException exception = assertThrows(CodegenException.class, () -> schema(field, creator));
        assertThat(exception.getMessage(), containsString("Ignored set on the field 'value'"));
    }

    private static TypedElementInfo.Builder element(ElementKind kind, String name) {
        return TypedElementInfo.builder()
                .kind(kind)
                .elementName(name)
                .typeName(TypeNames.STRING)
                .accessModifier(AccessModifier.PUBLIC);
    }

    private static TypedElementInfo.Builder setter(String name) {
        return element(ElementKind.METHOD, name)
                .typeName(TypeNames.PRIMITIVE_VOID)
                .addParameterArgument(element(ElementKind.PARAMETER, "value").build());
    }

    private static TypedElementInfo.Builder creator(TypedElementInfo parameter) {
        return element(ElementKind.METHOD, "create")
                .typeName(MODEL)
                .addElementModifier(Modifier.STATIC)
                .addAnnotation(Annotation.create(JSONB_CREATOR))
                .addParameterArgument(parameter);
    }

    private static JsonObject schema(TypedElementInfo... elements) {
        var context = mock(CodegenContext.class);
        var type = TypeInfo.builder()
                .typeName(MODEL)
                .kind(ElementKind.CLASS)
                .elementInfo(List.of(elements))
                .build();
        JsonObject schema = SchemaInfo.create(type, context).schema();
        verifyZeroInteractions(context);
        return schema;
    }
}
