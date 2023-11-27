/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.common.processor.classmodel;

import java.io.IOException;
import java.util.Objects;
import java.util.Set;

import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;

/**
 * Field model representation.
 *
 * @deprecated use {@code helidon-codegen-class-model} instead.
 */
@Deprecated(forRemoval = true, since = "4.1.0")
public final class Field extends AnnotatedComponent {

    private final Content defaultValue;
    private final boolean isFinal;
    private final boolean isStatic;

    private Field(Builder builder) {
        super(builder);
        this.defaultValue = builder.defaultValueBuilder.build();
        this.isFinal = builder.isFinal;
        this.isStatic = builder.isStatic;
    }

    /**
     * Create new {@link Builder} instance.
     *
     * @return new builder instance
     */
    public static Builder builder() {
        return new Builder().accessModifier(AccessModifier.PRIVATE);
    }

    @Override
    void writeComponent(ModelWriter writer, Set<String> declaredTokens, ImportOrganizer imports, ClassType classType)
            throws IOException {
        if (javadoc().generate()) {
            javadoc().writeComponent(writer, declaredTokens, imports, classType);
            writer.write("\n");
        }
        for (Annotation annotation : annotations()) {
            annotation.writeComponent(writer, declaredTokens, imports, classType);
            writer.write("\n");
        }
        if (classType != ClassType.INTERFACE) {
            if (AccessModifier.PACKAGE_PRIVATE != accessModifier()) {
                writer.write(accessModifier().modifierName());
                writer.write(" ");
            }
            if (isStatic) {
                writer.write("static ");
            }
            if (isFinal) {
                writer.write("final ");
            }
        }
        type().writeComponent(writer, declaredTokens, imports, classType);
        writer.write(" ");
        writer.write(name());
        if (defaultValue.hasBody()) {
            writer.write(" = ");
            defaultValue.writeBody(writer, imports);
            writer.write(";");
        } else {
            writer.write(";");
        }
    }

    @Override
    void addImports(ImportOrganizer.Builder imports) {
        super.addImports(imports);
        type().addImports(imports);
        defaultValue.addImports(imports);
    }

    boolean isStatic() {
        return isStatic;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Field field = (Field) o;
        return name().equals(field.name())
                && type().equals(field.type())
                && isStatic == field.isStatic
                && isFinal == field.isFinal
                && accessModifier().equals(field.accessModifier());
    }

    @Override
    public int hashCode() {
        return Objects.hash(name(), type(), isFinal, isStatic, accessModifier());
    }

    @Override
    public String toString() {
        if (defaultValue.hasBody()) {
            return accessModifier().modifierName() + " " + type().fqTypeName() + " " + name() + " = " + defaultValue;
        }
        return accessModifier().modifierName() + " " + type().fqTypeName() + " " + name();
    }

    boolean isFinal() {
        return isFinal;
    }

    /**
     * Fluent API builder for {@link Field}.
     */
    public static final class Builder extends AnnotatedComponent.Builder<Builder, Field> {

        private final Content.Builder defaultValueBuilder = Content.builder();
        private boolean isFinal = false;
        private boolean isStatic = false;

        private Builder() {
        }

        @Override
        public Field build() {
            return new Field(this);
        }

        /**
         * Set default value this field should be initialized with, wrapping the value in double quotes
         * if the field type is String.
         *
         * @param defaultValue default value
         * @return updated builder instance
         */
        public Builder defaultValue(String defaultValue) {
            if (defaultValue != null
                    && type().equals(TypeNames.STRING)
                    && !type().isArray()
                    && !defaultValue.startsWith("\"")
                    && !defaultValue.endsWith("\"")) {
                defaultValueBuilder.content("\"" + defaultValue + "\"");
            } else {
                defaultValueBuilder.content(defaultValue);
            }
            return this;
        }

        /**
         * Configure a default value for this field as a string that will be copied verbatim to the generated sources.
         *
         * @param defaultValue default value
         * @return updated builder instance
         */
        public Builder defaultValueContent(String defaultValue) {
            defaultValueBuilder.content(defaultValue);
            return this;
        }

        /**
         * Whether this field is final.
         *
         * @param isFinal final field
         * @return updated builder instance
         */
        public Builder isFinal(boolean isFinal) {
            this.isFinal = isFinal;
            return this;
        }

        /**
         * Whether this field is static.
         *
         * @param isStatic static field
         * @return updated builder instance
         */
        public Builder isStatic(boolean isStatic) {
            this.isStatic = isStatic;
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

        @Override
        public Builder accessModifier(AccessModifier accessModifier) {
            return super.accessModifier(accessModifier);
        }
    }
}
