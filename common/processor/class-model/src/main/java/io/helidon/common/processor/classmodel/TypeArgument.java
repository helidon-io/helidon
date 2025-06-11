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
package io.helidon.common.processor.classmodel;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import io.helidon.common.types.TypeName;

/**
 * Generic type argument model.
 *
 * @deprecated use {@code helidon-codegen-class-model} instead.
 */
@Deprecated(forRemoval = true, since = "4.1.0")
public final class TypeArgument extends Type implements TypeName {

    private final TypeName token;
    private final Type bound;
    private final List<String> description;

    private TypeArgument(Builder builder) {
        super(builder);
        this.token = builder.tokenBuilder.build();
        this.bound = builder.bound;
        this.description = builder.description;
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
        if (bound == null) {
            return null;
        }
        return bound.genericTypeName();
    }

    @Override
    void writeComponent(ModelWriter writer, Set<String> declaredTokens, ImportOrganizer imports, ClassType classType)
            throws IOException {
        writer.write(token.className());
        if (bound != null) {
            writer.write(" extends ");
            bound.writeComponent(writer, declaredTokens, imports, classType);
        }
    }

    @Override
    void addImports(ImportOrganizer.Builder imports) {
        if (bound != null) {
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
        return List.of(bound.genericTypeName());
    }

    @Override
    public String toString() {
        if (bound == null) {
            return "Token: " + token.className();
        }
        return "Token: " + token.className() + " Bound: " + bound;
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
                && Objects.equals(bound, typeArgument1.bound);
    }

    @Override
    public int hashCode() {
        return Objects.hash(token, bound);
    }

    @Override
    public int compareTo(TypeName o) {
        return token.compareTo(o);
    }

    @Override
    public boolean vararg() {
        return false;
    }

    /**
     * Fluent API builder for {@link TypeArgument}.
     */
    public static final class Builder extends Type.Builder<Builder, TypeArgument> {

        private final TypeName.Builder tokenBuilder = TypeName.builder()
                .generic(true);
        private Type bound;
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
         * Type this argument is bound to.
         *
         * @param bound argument bound
         * @return updated builder instance
         */
        public Builder bound(TypeName bound) {
            this.bound = Type.fromTypeName(bound);
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
