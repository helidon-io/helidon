/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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
package io.helidon.codegen.classmodel;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import io.helidon.common.types.Annotation;
import io.helidon.common.types.AnnotationProperty;
import io.helidon.common.types.EnumValue;
import io.helidon.common.types.TypeName;

/**
 * Annotation parameter model.
 */
public final class AnnotationParameter extends CommonComponent {

    private final Set<TypeName> importedTypes;
    private final Object objectValue;
    private final AnnotationProperty.ConstantValue constantValue;

    private AnnotationParameter(Builder builder) {
        super(builder);

        this.objectValue = builder.value;
        this.constantValue = builder.constantValue;
        this.importedTypes = resolveImports(builder.value, constantValue);
    }

    /**
     * Create new {@link Builder}.
     *
     * @return new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String toString() {
        return objectValue + " (" + type().simpleTypeName() + ")";
    }

    @Override
    void writeComponent(ModelWriter writer, Set<String> declaredTokens, ImportOrganizer imports, ClassType classType)
            throws IOException {
        writer.write(name() + " = ");
        writeValue(writer, imports);
    }

    @Override
    void addImports(ImportOrganizer.Builder imports) {
        importedTypes.forEach(imports::addImport);
    }

    void writeValue(ModelWriter writer, ImportOrganizer imports) throws IOException {
        writer.write(resolveValueToString(imports, type(), objectValue));
    }

    Object value() {
        return objectValue;
    }

    private Set<TypeName> resolveImports(Object value, AnnotationProperty.ConstantValue constantValue) {
        Set<TypeName> imports = new HashSet<>();

        resolveImports(imports, value, constantValue);

        return imports;
    }

    private void resolveImports(Set<TypeName> imports, Object value, AnnotationProperty.ConstantValue constantValue) {
        if (constantValue != null) {
            // we only care about the constant value, as other stuff is ignored during code generation
            imports.add(constantValue.type());
            return;
        }
        if (value.getClass().isEnum()) {
            imports.add(TypeName.create(value.getClass()));
            return;
        }
        switch (value) {
        case TypeName tn -> imports.add(tn);
        case EnumValue ev -> imports.add(ev.type());
        case Annotation an -> {
            imports.add(an.typeName());
            an.properties()
                    .values()
                    .forEach(nestedValue -> resolveImports(imports, nestedValue, nestedValue.constantValue().orElse(null)));
        }
        default -> {
        }
        }
    }

    // takes the annotation value objects and converts it to its string representation (as seen in class source)
    private String resolveValueToString(ImportOrganizer imports, Type type, Object value) {
        if (constantValue != null) {
            return imports.typeName(Type.fromTypeName(constantValue.type()), true)
                    + "." + constantValue.name();
        }

        Class<?> valueClass = value.getClass();
        if (valueClass.isEnum()) {
            return imports.typeName(Type.fromTypeName(TypeName.create(valueClass)), true)
                    + "." + ((Enum<?>) value).name();
        }
        if (type != null && type.fqTypeName().equals(String.class.getName())) {
            String stringValue = value.toString();
            if (!stringValue.startsWith("\"") && !stringValue.endsWith("\"")) {
                return quoteString(stringValue);
            }
            return stringValue;
        }

        if (type != null && type.fqTypeName().equals(Object.class.getName())) {
            // we expect this to be "as is" - such as when parsing annotations
            return value.toString();
        }

        return switch (value) {
            case TypeName typeName -> imports.typeName(Type.fromTypeName(typeName), true) + ".class";
            case EnumValue enumValue -> imports.typeName(Type.fromTypeName(enumValue.type()), true)
                    + "." + enumValue.name();
            case Character character -> "'" + escapeCharacter(character) + "'";
            case Long longValue -> longValue + "L";
            case Float floatValue -> floatValue + "F";
            case Double doubleValue -> doubleValue + "D";
            case Byte byteValue -> "(byte) " + byteValue;
            case Short shortValue -> "(short) " + shortValue;
            case Class<?> clazz -> imports.typeName(Type.fromTypeName(TypeName.create(clazz)), true) + ".class";
            case Annotation annotation -> nestedAnnotationValue(imports, annotation);
            case List<?> list -> nestedListValue(imports, list);
            case String str -> str.startsWith("\"") && str.endsWith("\"") ? str : quoteString(str);
            default -> value.toString();
        };

    }

    private String quoteString(String stringValue) {
        return "\"" + stringValue.replaceAll("\"", "\\\\\"") + "\"";
    }

    private String escapeCharacter(Character character) {
        return switch (character) {
            case '\'' -> "\\'";
            case '\t' -> "\\t";
            case '\n' -> "\\n";
            case '\r' -> "\\r";
            default -> String.valueOf(character);
        };
    }

    private String nestedListValue(ImportOrganizer imports, List<?> list) {
        if (list.isEmpty()) {
            return "{}";
        }
        StringBuilder result = new StringBuilder();
        if (list.size() > 1) {
            result.append("{");
        }

        result.append(list.stream()
                .map(it -> resolveValueToString(imports, null, it))
                .collect(Collectors.joining(", ")));

        if (list.size() > 1) {
            result.append("}");
        }
        return result.toString();
    }

    private String nestedAnnotationValue(ImportOrganizer imports, Annotation annotation) {
        StringBuilder sb = new StringBuilder("@");
        sb.append(imports.typeName(Type.fromTypeName(annotation.typeName()), true));

        Map<String, AnnotationProperty> values = annotation.properties();
        if (values.isEmpty()) {
            return sb.toString();
        }

        sb.append("(");
        if (values.size() == 1 && values.containsKey("value")) {
            sb.append(resolveValueToString(imports, null, values.get("value").value()));
        } else {
            values.forEach((key, value) -> {
                sb.append(key)
                        .append(" = ")
                        .append(resolveValueToString(imports, null, value.value()))
                        .append(", ");
            });
            sb.delete(sb.length() - 2, sb.length());
        }
        sb.append(")");
        return sb.toString();
    }

    /**
     * Fluent API builder for {@link AnnotationParameter}.
     */
    public static final class Builder extends CommonComponent.Builder<Builder, AnnotationParameter> {

        private Object value;
        private AnnotationProperty.ConstantValue constantValue;

        private Builder() {
        }

        @Override
        public AnnotationParameter build() {
            if (value == null || name() == null) {
                throw new ClassModelException("Annotation parameter needs to have value and type set");
            }
            return new AnnotationParameter(this);
        }

        @Override
        public Builder name(String name) {
            return super.name(name);
        }

        /**
         * Set annotation parameter value.
         *
         * @param value annotation parameter value
         * @return updated builder instance
         */
        public Builder value(Object value) {
            this.value = Objects.requireNonNull(value);
            return this;
        }

        @Override
        public Builder type(TypeName type) {
            return super.type(type);
        }

        @Override
        public Builder type(String type) {
            return super.type(type);
        }

        @Override
        public Builder type(Class<?> type) {
            return super.type(type);
        }

        /**
         * Configure a constant value, to generate a reference to a constant, rather then explicit value.
         *
         * @param constantValue type and constant name
         * @return updated builder
         */
        public Builder constantValue(AnnotationProperty.ConstantValue constantValue) {
            this.constantValue = constantValue;
            return this;
        }
    }
}
