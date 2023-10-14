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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import io.helidon.common.types.TypeName;

/**
 * Concrete type representation.
 */
class ConcreteType extends Type {

    private final TypeName typeName;
    private final Type declaringType;
    private final List<Type> typeParams;

    ConcreteType(Builder builder) {
        super(builder);
        this.typeName = builder.typeName;
        if (typeName.enclosingNames().isEmpty()) {
            this.declaringType = null;
        } else {
            String parents = String.join(".", typeName.enclosingNames());
            TypeName parent;
            if (typeName.packageName().isEmpty()) {
                parent = TypeName.create(parents);
            } else {
                parent = TypeName.create(typeName.packageName() + "." + parents);
            }

            this.declaringType = Type.fromTypeName(parent);
        }
        this.typeParams = List.copyOf(builder.typeParams);
    }

    static Builder builder() {
        return new Builder();
    }

    @Override
    void writeComponent(ModelWriter writer, Set<String> declaredTokens, ImportOrganizer imports, ClassType classType) throws
            IOException {
        String typeName = imports.typeName(this, includeImport());
        writer.write(typeName);
        if (!typeParams.isEmpty()) {
            writer.write("<");
            boolean first = true;
            for (Type parameter : typeParams) {
                if (first) {
                    first = false;
                } else {
                    writer.write(", ");
                }
                parameter.writeComponent(writer, declaredTokens, imports, classType);
            }
            writer.write(">");
        }
        if (isArray()) {
            writer.write("[]");
        }
    }

    @Override
    void addImports(ImportOrganizer.Builder imports) {
        if (includeImport()) {
            imports.addImport(this);
        }
        typeParams.forEach(type -> type.addImports(imports));
    }

    @Override
    String fqTypeName() {
        if (innerClass()) {
            return typeName.classNameWithEnclosingNames();
        } else {
            return typeName.name();
        }
    }

    @Override
    String resolvedTypeName() {
        return typeName.resolvedName();
    }

    @Override
    String simpleTypeName() {
        return typeName.className();
    }

    @Override
    boolean isArray() {
        return typeName.array();
    }

    @Override
    boolean innerClass() {
        return !typeName.enclosingNames().isEmpty();
    }

    @Override
    Optional<Type> declaringClass() {
        return Optional.ofNullable(declaringType);
    }

    @Override
    TypeName genericTypeName() {
        return typeName;
    }

    String packageName() {
        return typeName.packageName();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ConcreteType that = (ConcreteType) o;
        return isArray() == that.isArray()
                && Objects.equals(typeName.resolvedName(), that.typeName.resolvedName());
    }

    @Override
    public String toString() {
        return typeName.toString();
    }

    @Override
    public int hashCode() {
        return Objects.hash(isArray(), typeName.resolvedName());
    }

    static final class Builder extends ModelComponent.Builder<Builder, ConcreteType> {
        private final List<Type> typeParams = new ArrayList<>();
        private TypeName typeName;

        private Builder() {
        }

        @Override
        public ConcreteType build() {
            if (typeName == null) {
                throw new ClassModelException("Type value needs to be set");
            }
            return new ConcreteType(this);
        }

        Builder type(String typeName) {
            return type(TypeName.create(typeName));
        }

        Builder type(Class<?> typeName) {
            return type(TypeName.create(typeName));
        }

        Builder type(TypeName typeName) {
            this.typeName = typeName;
            return this;
        }

        Builder addParam(TypeName typeName) {
            this.typeParams.add(Type.fromTypeName(typeName));
            return this;
        }
    }

}
