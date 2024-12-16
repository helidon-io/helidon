/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import io.helidon.common.types.TypeName;

/**
 * Generic type argument model.
 */
public final class TypeArgument extends Type implements TypeName {

    private final TypeName token;
    private final List<Type> bounds;
    private final List<String> description;
    private final boolean isLowerBound;

    private TypeArgument(Builder builder) {
        super(builder);
        this.token = builder.tokenBuilder.build();
        this.bounds = List.copyOf(builder.bounds);
        this.description = builder.description;
        this.isLowerBound = builder.isLowerBound;
    }

    /**
     * Creates new {@link TypeArgument} instance based on the provided token.
     *
     * @param token argument token
     * @return new argument instance
     */
    public static TypeArgument create(String token) {
        return builder().token(token).build();
    }

    /**
     * Return new {@link Builder} instance.
     *
     * @return new builder instance
     */
    public static TypeArgument.Builder builder() {
        return new TypeArgument.Builder();
    }

    @Override
    public TypeName boxed() {
        return this;
    }

    @Override
    public TypeName genericTypeName() {
        if (bounds.isEmpty()) {
            return this;
        }
        return TypeName.builder()
                .from(this)
                .typeArguments(List.of())
                .typeParameters(List.of())
                .build();
    }

    @Override
    void writeComponent(ModelWriter writer, Set<String> declaredTokens, ImportOrganizer imports, ClassType classType)
            throws IOException {
        writer.write(token.className());
        if (bounds.isEmpty()) {
            return;
        }

        if (isLowerBound) {
            writer.write(" super ");
        } else {
            writer.write(" extends ");
        }

        if (bounds.size() == 1) {
            bounds.getFirst().writeComponent(writer, declaredTokens, imports, classType);
            return;
        }
        for (int i = 0; i < bounds.size(); i++) {
            if (i != 0) {
                writer.write(" & ");
            }
            bounds.get(i).writeComponent(writer, declaredTokens, imports, classType);
        }
    }

    @Override
    void addImports(ImportOrganizer.Builder imports) {
        for (Type bound : bounds) {
            bound.addImports(imports);
        }
    }

    /**
     * Type argument token.
     *
     * @return token value
     */
    public String token() {
        return token.className();
    }

    @Override
    public String packageName() {
        return "";
    }

    List<String> description() {
        return description;
    }

    @Override
    String fqTypeName() {
        return token.className();
    }

    @Override
    String resolvedTypeName() {
        return token.resolvedName();
    }

    @Override
    String simpleTypeName() {
        return token.className();
    }

    @Override
    boolean isArray() {
        return false;
    }

    @Override
    boolean innerClass() {
        return false;
    }

    @Override
    Optional<Type> declaringClass() {
        return Optional.empty();
    }

    @Override
    public String className() {
        return token.className();
    }

    @Override
    public List<String> enclosingNames() {
        return List.of();
    }

    @Override
    public boolean primitive() {
        return false;
    }

    @Override
    public boolean array() {
        return token.array();
    }

    @Override
    public boolean generic() {
        return token.generic();
    }

    @Override
    public boolean wildcard() {
        return token.wildcard();
    }

    @Override
    public List<TypeName> typeArguments() {
        return List.of();
    }

    @Override
    public List<String> typeParameters() {
        return List.of();
    }

    @Override
    public List<TypeName> lowerBounds() {
        // not yet supported
        return List.of();
    }

    @Override
    public List<TypeName> upperBounds() {
        return bounds.stream()
                .map(Type::typeName)
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public String toString() {
        if (bounds.isEmpty()) {
            return "Token: " + token.className();
        }
        return "Token: " + token.className() + " Bound: " + bounds;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TypeArgument typeArgument1 = (TypeArgument) o;
        return Objects.equals(token, typeArgument1.token)
                && Objects.equals(bounds, typeArgument1.bounds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(token, bounds);
    }

    @Override
    public int compareTo(TypeName o) {
        return token.compareTo(o);
    }

    @Override
    TypeName typeName() {
        return this;
    }

    /**
     * Fluent API builder for {@link TypeArgument}.
     */
    public static final class Builder extends Type.Builder<Builder, TypeArgument> {

        private final TypeName.Builder tokenBuilder = TypeName.builder()
                .generic(true);
        private final List<Type> bounds = new ArrayList<>();

        private boolean isLowerBound;
        private List<String> description = List.of();

        private Builder() {
        }

        /**
         * Token name of this argument.
         *
         * @param token token name
         * @return updated builder instance
         */
        public Builder token(String token) {
            tokenBuilder.className(Objects.requireNonNull(token))
                    .wildcard(token.startsWith("?"));
            return this;
        }

        /**
         * Type this argument is bound to.
         *
         * @param bound argument bound
         * @return updated builder instance
         */
        public Builder bound(String bound) {
            return bound(TypeName.create(bound));
        }

        /**
         * Type this argument is bound to.
         *
         * @param bound argument bound
         * @return updated builder instance
         */
        public Builder bound(Class<?> bound) {
            return bound(TypeName.create(bound));
        }

        /**
         * Bound is by default an upper bounds (presented as {@code extends} in code).
         * By specifying that we use a {@code lowerBound}, the keyword will be {@code super}.
         *
         * @param lowerBound whether the specified bound is a lower bound (defaults to upper bound); ignore if no bound
         * @return updated builder instance
         */
        public Builder lowerBound(boolean lowerBound) {
            this.isLowerBound = lowerBound;
            return this;
        }

        /**
         * Type this argument is bound to.
         *
         * @param bound argument bound
         * @return updated builder instance
         */
        public Builder bound(TypeName bound) {
            this.bounds.add(Type.fromTypeName(bound));
            return this;
        }

        /**
         * Type this argument is bound to (may have more than one for intersection types).
         *
         * @param bound argument bound
         * @return updated builder instance
         */
        public Builder addBound(TypeName bound) {
            this.bounds.add(Type.fromTypeName(bound));
            return this;
        }

        /**
         * Set description of the component.
         * It overwrites previously set description.
         *
         * @param description component description
         * @return updated builder instance
         */
        public Builder description(String description) {
            this.description = List.of(description.split("\n"));
            return this;
        }

        /**
         * Set description of the component.
         * It overwrites previously set description.
         *
         * @param description component description
         * @return updated builder instance
         */
        public Builder description(List<String> description) {
            this.description = List.copyOf(description);
            return this;
        }

        @Override
        public TypeArgument build() {
            if (tokenBuilder.className().isEmpty()) {
                throw new ClassModelException("Token name needs to be specified.");
            }
            return new TypeArgument(this);
        }

    }
}
