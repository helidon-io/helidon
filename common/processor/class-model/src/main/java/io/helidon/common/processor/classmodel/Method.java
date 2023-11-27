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
package io.helidon.common.processor.classmodel;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.TypeName;

/**
 * Model of the method which should be created in the specific type.
 *
 * @deprecated use {@code helidon-codegen-class-model} instead.
 */
@Deprecated(forRemoval = true, since = "4.1.0")
public final class Method extends Executable {

    private final Map<String, TypeArgument> declaredTokens;
    private final boolean isDefault;
    private final boolean isFinal;
    private final boolean isStatic;
    private final boolean isAbstract;

    private Method(Builder builder) {
        super(builder);
        this.isDefault = builder.isDefault;
        this.isFinal = builder.isFinal;
        this.isStatic = builder.isStatic;
        this.isAbstract = builder.isAbstract;
        this.declaredTokens = Collections.unmodifiableMap(new LinkedHashMap<>(builder.declaredTokens));
    }

    /**
     * Create new {@link Builder} instance.
     *
     * @return new builder instance
     */
    public static Builder builder() {
        return new Builder()
                .returnType(builder -> builder.type(void.class));
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
        if (classType == ClassType.INTERFACE) {
            if (isDefault) {
                writer.write("default ");
            } else if (isStatic) {
                writer.write("static ");
            }
        } else {
            if (AccessModifier.PACKAGE_PRIVATE != accessModifier()) {
                writer.write(accessModifier().modifierName() + " ");
            }
            if (isStatic) {
                writer.write("static ");
            }
            if (isFinal) {
                writer.write("final ");
            }
            if (isAbstract) {
                writer.write("abstract ");
            }
        }
        appendTokenDeclaration(writer, declaredTokens, imports, classType);
        type().writeComponent(writer, declaredTokens, imports, classType); //write return type
        writer.write(" " + name() + "(");
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
        if (classType == ClassType.INTERFACE) {
            if (!isDefault && !isStatic) {
                writer.write(";");
                return;
            }
        } else {
            if (isAbstract) {
                writer.write(";");
                return;
            }
        }
        writer.write(" {");
        if (hasBody()) {
            writeBody(writer, imports);
        } else {
            writer.write("\n");
        }
        writer.write("}");
    }

    private void appendTokenDeclaration(ModelWriter writer,
                                        Set<String> declaredTokens,
                                        ImportOrganizer imports,
                                        ClassType classType)
            throws IOException {
        Set<String> tokensToDeclare = new LinkedHashSet<>();
        if (isStatic) {
            for (Parameter parameter : parameters()) {
                if (parameter.type() instanceof TypeArgument typeArgument) {
                    String tokenName = typeArgument.token();
                    if (!tokenName.equals("?")) {
                        tokensToDeclare.add(tokenName);
                    }
                }
            }
        } else {
            for (Parameter parameter : parameters()) {
                if (parameter.type() instanceof TypeArgument typeArgument) {
                    String tokenName = typeArgument.token();
                    if (!declaredTokens.contains(tokenName) && !tokenName.equals("?")) {
                        tokensToDeclare.add(tokenName);
                    }
                }
            }
        }
        if (!tokensToDeclare.isEmpty()) {
            writer.write("<");
            boolean first = true;
            for (String token : tokensToDeclare) {
                if (first) {
                    first = false;
                } else {
                    writer.write(", ");
                }
                if (this.declaredTokens.containsKey(token)) {
                    this.declaredTokens.get(token).writeComponent(writer, declaredTokens, imports, classType);
                } else {
                    writer.write(token);
                }
            }
            for (Map.Entry<String, TypeArgument> entry : this.declaredTokens.entrySet()) {
                if (!tokensToDeclare.contains(entry.getKey())) {
                    entry.getValue().writeComponent(writer, declaredTokens, imports, classType);
                }
            }
            writer.write("> ");
        } else if (!this.declaredTokens.isEmpty()) {
            writer.write("<");
            boolean first = true;
            for (Map.Entry<String, TypeArgument> entry : this.declaredTokens.entrySet()) {
                if (first) {
                    first = false;
                } else {
                    writer.write(", ");
                }
                entry.getValue().writeComponent(writer, declaredTokens, imports, classType);
            }
            writer.write("> ");
        }
    }

    @Override
    void addImports(ImportOrganizer.Builder imports) {
        super.addImports(imports);
        type().addImports(imports);
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
        Method method = (Method) o;
        return Objects.equals(type(), method.type())
                && Objects.equals(name(), method.name())
                && parameters().size() == method.parameters().size()
                && parameters().equals(method.parameters());
    }

    @Override
    public int hashCode() {
        return Objects.hash(type(), name(), parameters());
    }

    @Override
    public String toString() {
        return "Method{"
                + "name=" + name()
                + ", isFinal=" + isFinal
                + ", isStatic=" + isStatic
                + ", isAbstract=" + isAbstract
                + ", returnType=" + type().fqTypeName()
                + '}';
    }

    /**
     * Fluent API builder for {@link Method}.
     */
    public static final class Builder extends Executable.Builder<Builder, Method> {

        private final Map<String, TypeArgument> declaredTokens = new LinkedHashMap<>();
        private boolean isDefault = false;
        private boolean isFinal = false;
        private boolean isStatic = false;
        private boolean isAbstract = false;

        Builder() {
        }

        @Override
        public Method build() {
            if (name() == null) {
                throw new ClassModelException("Method needs to have name specified");
            }
            if (isStatic && isAbstract) {
                throw new IllegalStateException("Method cannot be static and abstract at the same time");
            }
            if (isFinal && isAbstract) {
                throw new IllegalStateException("Method cannot be final and abstract at the same time");
            }
            return new Method(this);
        }

        @Override
        public Builder content(List<String> content) {
            declaredTokens.clear();
            return super.content(content);
        }

        /**
         * Whether this method is final.
         *
         * @param isFinal method is final
         * @return updated builder instance
         */
        public Builder isFinal(boolean isFinal) {
            this.isFinal = isFinal;
            return this;
        }

        /**
         * Whether this method is static.
         *
         * @param isStatic method is static
         * @return updated builder instance
         */
        public Builder isStatic(boolean isStatic) {
            this.isStatic = isStatic;
            return this;
        }

        /**
         * Whether this method is abstract.
         *
         * @param isAbstract method is abstract
         * @return updated builder instance
         */
        public Builder isAbstract(boolean isAbstract) {
            this.isAbstract = isAbstract;
            return this;
        }

        /**
         * Whether this method is default.
         *
         * @param isDefault method is default
         * @return updated builder instance
         */
        public Builder isDefault(boolean isDefault) {
            this.isDefault = isDefault;
            return this;
        }

        /**
         * Set return type of the method.
         * Default is {@code void}.
         *
         * @param type return type
         * @return updated builder instance
         */
        public Builder returnType(TypeName type) {
            return type(type);
        }

        /**
         * Set return type of the method.
         * Default is {@code void}.
         *
         * @param type return type
         * @param description return type description
         * @return updated builder instance
         */
        public Builder returnType(TypeName type, String description) {
            return type(type).returnJavadoc(description);
        }

        /**
         * Set return type of the method.
         * Default is {@code void}.
         *
         * @param consumer return type builder consumer
         * @return updated builder instance
         */
        public Builder returnType(Consumer<Returns.Builder> consumer) {
            Objects.requireNonNull(consumer);
            Returns.Builder builder = Returns.builder();
            consumer.accept(builder);
            return returnType(builder);
        }

        /**
         * Set return type of the method.
         * Default is {@code void}.
         *
         * @param supplier return type supplier
         * @return updated builder instance
         */
        public Builder returnType(Supplier<Returns> supplier) {
            Objects.requireNonNull(supplier);
            return returnType(supplier.get());
        }

        /**
         * Set return type of the method.
         * Default is {@code void}.
         *
         * @param returnType return type
         * @return updated builder instance
         */
        public Builder returnType(Returns returnType) {
            return type(returnType.type())
                    .returnJavadoc(returnType.description());
        }

        /**
         * Add generic argument to be declared by this method.
         *
         * @param typeArgument argument to be declared
         * @return updated builder instance
         */
        public Builder addGenericArgument(TypeArgument typeArgument) {
            declaredTokens.put(typeArgument.token(), typeArgument);
            addGenericToken(typeArgument.token(), typeArgument.description());
            return this;
        }

        Type returnType() {
            return type();
        }

    }

}
