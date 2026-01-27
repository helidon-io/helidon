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

import java.util.Set;

import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.ElementKind;

/**
 * Constructor model.
 */
public final class Constructor extends Executable {

    private Constructor(Builder builder) {
        super(builder);
    }

    /**
     * Create new {@link Builder} instance.
     *
     * @return new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    void writeComponent(ModelWriter writer, Set<String> declaredTokens, ImportOrganizer imports, ElementKind classType) {
        if (javadoc().generate()) {
            javadoc().writeComponent(writer, declaredTokens, imports, classType);
            writer.write("\n");
        }
        for (Annotation annotation : annotations()) {
            annotation.writeComponent(writer, declaredTokens, imports, classType);
            writer.write("\n");
        }
        if (AccessModifier.PACKAGE_PRIVATE != accessModifier()) {
            if (classType != ElementKind.ENUM) {
                writer.write(accessModifier().modifierName() + " ");
            }
        }
        if (classType == ElementKind.ENUM) {
            writer.write(AccessModifier.PRIVATE.modifierName() + " ");
        }
        String typeName = type().simpleTypeName();
        writer.write(typeName + "(");
        boolean first = true;
        for (Parameter parameter : parameters()) {
            if (first) {
                first = false;
            } else {
                writer.write(", ");
            }
            parameter.writeComponent(writer, declaredTokens, imports, classType);
        }
        writer.write(")");
        writeThrows(writer, declaredTokens, imports, classType);
        writer.write(" {");
        if (hasBody()) {
            writeBody(writer, imports);
        } else {
            writer.write("\n");
        }
        writer.write("}");
    }

    /**
     * Fluent API builder for {@link Constructor}.
     */
    public static final class Builder extends Executable.Builder<Builder, Constructor> {

        private Builder() {
        }

        @Override
        public Constructor build() {
            return new Constructor(this);
        }

    }
}
