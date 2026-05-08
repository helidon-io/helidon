/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import io.helidon.common.types.AnnotationProperty;
import io.helidon.common.types.AnnotationProperty.ConstantValue;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.EnumValue;
import io.helidon.common.types.TypeName;

/**
 * Annotation parameter model.
 */
public final class AnnotationParameter extends CommonComponent {

    private final Set<TypeName> importedTypes;
    private final Object objectValue;

    private AnnotationParameter(Builder builder) {
        super(builder);
        this.objectValue = value(builder.value);
        this.importedTypes = resolveImports(this);
    }

    /**
     * Create new {@link io.helidon.codegen.classmodel.AnnotationParameter.Builder}.
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
    void writeComponent(ModelWriter writer, Set<String> declaredTokens, ImportOrganizer imports, ElementKind classType) {
        writeComponent(writer, true, imports);
    }

    void writeComponent(ModelWriter writer, boolean multiline, ImportOrganizer imports) {
        writer.write(name());
        writer.write(" = ");
        writeValue(writer, multiline, imports);
    }

    @Override
    void addImports(ImportOrganizer.Builder imports) {
        for (var importedType : importedTypes) {
            imports.addImport(importedType);
        }
    }

    Object value() {
        return objectValue;
    }

    void writeValue(ModelWriter writer, boolean multiline, ImportOrganizer imports) {
        var value = value();
        switch (value) {
            case ConstantValue cv -> writer.writeValue(imports, cv);
            case Enum<?> ev -> writer.writeValue(imports, ev);
            default -> {
                var type = type();
                if (type != null && type.fqTypeName().equals(String.class.getName())) {
                    writer.writeValue(value.toString());
                } else if (type != null && type.fqTypeName().equals(Object.class.getName())) {
                    writer.write(value.toString());
                } else {
                    writeValue(writer, multiline, imports, value);
                }
            }
        }
    }

    void writeValue(ModelWriter writer, boolean multiline, ImportOrganizer imports, Object value) {
        switch (value) {
            case TypeName typeName -> writer.writeValue(imports, typeName);
            case Class<?> clazz -> writer.writeValue(imports, clazz);
            case EnumValue enumValue -> writer.writeValue(imports, enumValue);
            case Character character -> writer.writeValue(character);
            case String str -> writer.writeValue(str);
            case Number number -> writer.writeValue(number);
            case Collection<?> list -> writeValue(writer, multiline, imports, list);
            case Annotation annotation -> annotation.writeComponent(writer, multiline, imports);
            default -> writer.write(value.toString());
        }
    }

    void writeValue(ModelWriter writer, boolean multiline, ImportOrganizer imports, Collection<?> list) {
        if (list.isEmpty()) {
            writer.write("{}");
        } else {
            if (list.size() == 1) {
                var it = list.iterator();
                var e = it.next();
                if ("value".equals(name()) || isValueType(e)) {
                    writeValue(writer, multiline, imports, e);
                    return;
                }
            }

            writer.write("{");
            if (multiline) {
                writer.increasePaddingLevel();
                writer.write("\n");
            }
            var it = list.iterator();
            while (it.hasNext()) {
                var next = it.next();
                var nextMultiline = multiline && Annotation.isMultiline(writer, imports, next);
                writeValue(writer, nextMultiline, imports, next);
                if (it.hasNext()) {
                    writer.write(",");
                    if (multiline) {
                        writer.write("\n");
                    } else {
                        writer.write(" ");
                    }
                }
            }
            if (multiline) {
                writer.decreasePaddingLevel();
                writer.write("\n");
            }
            writer.write("}");
        }
    }

    private static Set<TypeName> resolveImports(Object value) {
        Set<TypeName> imports = new HashSet<>();
        resolveImports(imports, value);
        return imports;
    }

    private static void resolveImports(Set<TypeName> imports, Object value) {
        switch (value) {
            case AnnotationParameter p -> resolveImports(imports, p.objectValue);
            case Enum<?> e -> imports.add(TypeName.create(e.getClass()));
            case ConstantValue cv -> imports.add(cv.type());
            case TypeName tn -> imports.add(tn);
            case io.helidon.codegen.classmodel.Annotation an -> {
                imports.add(an.typeName());
                for (var e : an.parameters()) {
                    resolveImports(imports, e);
                }
            }
            case io.helidon.common.types.Annotation an -> {
                imports.add(an.typeName());
                for (var e : an.properties().values()) {
                    resolveImports(imports, e.value());
                }
            }
            case Collection<?> list -> {
                for (var e : list) {
                    resolveImports(imports, e);
                }
            }
            default -> {
                // ignore
            }
        }
    }

    private static Object value(Object value) {
        if (value instanceof Collection<?> values) {
            var list = new ArrayList<>();
            for (var e : values) {
                if (e instanceof io.helidon.common.types.Annotation a) {
                    list.add(annotation(a));
                } else {
                    list.add(e);
                }
            }
            return list;
        } else if (value instanceof io.helidon.common.types.Annotation a) {
            return annotation(a);
        } else {
            return value;
        }
    }

    private static Annotation annotation(io.helidon.common.types.Annotation a) {
        var builder = Annotation.builder().from(a);
        for (var entry : a.properties().entrySet()) {
            var name = entry.getKey();
            var value = value(entry.getValue().value());
            builder.addParameter(AnnotationParameter.builder()
                    .name(name)
                    .value(value)
                    .type(value instanceof TypeName ? Class.class : value.getClass())
                    .build());
        }
        return builder.build();
    }

    private static boolean isValueType(Object o) {
        return switch (o) {
            case TypeName ignored -> true;
            case Class<?> ignored -> true;
            case EnumValue ignored -> true;
            case Character ignored -> true;
            case String ignored -> true;
            case Number ignored -> true;
            default -> false;
        };
    }

    /**
     * Fluent API builder for {@link AnnotationParameter}.
     */
    public static final class Builder extends CommonComponent.Builder<Builder, AnnotationParameter> {

        private Object value;

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
            if (value instanceof AnnotationProperty p) {
                this.value = p.value();
            } else {
                this.value = Objects.requireNonNull(value);
            }
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
    }
}
