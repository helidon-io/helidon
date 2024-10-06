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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.TypeName;

/**
 * Executable base, used by method and constructor.
 */
public abstract class Executable extends AnnotatedComponent {

    private final Content content;
    private final List<Parameter> parameters;
    private final List<Type> exceptions;

    Executable(Builder<?, ?> builder) {
        super(builder);
        this.content = builder.contentBuilder.build();
        this.parameters = List.copyOf(builder.parameters.values());
        this.exceptions = List.copyOf(builder.exceptions);
    }

    @Override
    void addImports(ImportOrganizer.Builder imports) {
        super.addImports(imports);
        parameters.forEach(parameter -> parameter.addImports(imports));
        content.addImports(imports);
        exceptions.forEach(exc -> exc.addImports(imports));
    }

    void writeThrows(ModelWriter writer, Set<String> declaredTokens, ImportOrganizer imports, ClassType classType)
            throws IOException {
        if (!exceptionTypes().isEmpty()) {
            writer.write(" throws ");
            boolean first = true;
            for (Type exception : exceptionTypes()) {
                if (first) {
                    first = false;
                } else {
                    writer.write(", ");
                }
                exception.writeComponent(writer, declaredTokens, imports, classType);
            }
        }
    }

    void writeBody(ModelWriter writer, ImportOrganizer imports) throws IOException {
        writer.increasePaddingLevel();
        writer.write("\n");
        content.writeBody(writer, imports);
        writer.decreasePaddingLevel();
        writer.write("\n");
    }

    /**
     * List of method parameters.
     *
     * @return parameters
     */
    public List<Parameter> parameters() {
        return List.copyOf(parameters);
    }

    /**
     * List of thrown exceptions.
     *
     * @return exceptions
     */
    public List<TypeName> exceptions() {
        return exceptions.stream()
                .map(Type::genericTypeName)
                .collect(Collectors.toUnmodifiableList());
    }

    List<Type> exceptionTypes() {
        return exceptions;
    }

    boolean hasBody() {
        return content.hasBody();
    }

    /**
     * Base builder from executable components (method an constructor).
     *
     * @param <B> type of the builder
     * @param <T> type of the built instance
     */
    public abstract static class Builder<B extends Builder<B, T>, T extends Executable>
            extends AnnotatedComponent.Builder<B, T>
            implements ContentBuilder<B> {

        private final Map<String, Parameter> parameters = new LinkedHashMap<>();
        private final Set<Type> exceptions = new LinkedHashSet<>();
        private final Content.Builder contentBuilder = Content.builder();

        Builder() {
        }

        @Override
        public B javadoc(Javadoc javadoc) {
            return super.javadoc(javadoc);
        }

        @Override
        public B addJavadocTag(String tag, String description) {
            return super.addJavadocTag(tag, description);
        }

        @Override
        public B accessModifier(AccessModifier accessModifier) {
            return super.accessModifier(accessModifier);
        }

        @Override
        public B content(List<String> content) {
            contentBuilder.content(content);
            return identity();
        }

        @Override
        public B addContent(String line) {
            contentBuilder.addContent(line);
            return identity();
        }

        @Override
        public B addContent(TypeName typeName) {
            contentBuilder.addContent(typeName);
            return identity();
        }

        @Override
        public B addTypeToContent(String typeName) {
            contentBuilder.addTypeToContent(typeName);
            return identity();
        }

        @Override
        public B padContent() {
            contentBuilder.padContent();
            return identity();
        }

        @Override
        public B padContent(int repetition) {
            contentBuilder.padContent(repetition);
            return identity();
        }

        @Override
        public B increaseContentPadding() {
            contentBuilder.increaseContentPadding();
            return identity();
        }

        @Override
        public B decreaseContentPadding() {
            contentBuilder.decreaseContentPadding();
            return identity();
        }

        @Override
        public B clearContent() {
            contentBuilder.clearContent();
            return identity();
        }

        /**
         * Add new method parameter.
         *
         * @param consumer method builder consumer
         * @return updated builder instance
         */
        public B addParameter(Consumer<Parameter.Builder> consumer) {
            Parameter.Builder builder = Parameter.builder();
            consumer.accept(builder);
            return addParameter(builder.build());
        }

        /**
         * Add new method parameter.
         *
         * @param parameter method parameter
         * @return updated builder instance
         */
        public B addParameter(Parameter parameter) {
            this.parameters.put(parameter.name(), parameter);
            return this.addJavadocParameter(parameter.name(), parameter.description());
        }

        /**
         * Add new method parameter.
         *
         * @param supplier method parameter supplier
         * @return updated builder instance
         */
        public B addParameter(Supplier<Parameter> supplier) {
            Parameter parameter = supplier.get();
            this.parameters.put(parameter.name(), parameter);
            return this.addJavadocParameter(parameter.name(), parameter.description());
        }

        /**
         * Add a declared throws definition.
         *
         * @param exception exception declaration
         * @param description description to add to javadoc
         * @return updated builder instance
         */
        public B addThrows(TypeName exception, String description) {
            Objects.requireNonNull(exception);
            Objects.requireNonNull(description);
            return addThrows(ex -> ex.type(exception)
                    .description(description));
        }

        /**
         * Add a declared throws definition.
         *
         * @param consumer exception declaration builder consumer
         * @return updated builder instance
         */
        public B addThrows(Consumer<Throws.Builder> consumer) {
            Objects.requireNonNull(consumer);
            Throws.Builder builder = Throws.builder();
            consumer.accept(builder);
            return addThrows(builder);
        }

        /**
         * Add a declared throws definition.
         *
         * @param supplier exception declaration supplier
         * @return updated builder instance
         */
        public B addThrows(Supplier<Throws> supplier) {
            Objects.requireNonNull(supplier);
            return addThrows(supplier.get());
        }

        /**
         * Add a declared throws definition.
         *
         * @param exception exception declaration
         * @return updated builder instance
         */
        public B addThrows(Throws exception) {
            Objects.requireNonNull(exception);
            this.exceptions.add(exception.type());
            return addJavadocThrows(exception.type().fqTypeName(), exception.description());
        }

        @Override
        public B generateJavadoc(boolean generateJavadoc) {
            return super.generateJavadoc(generateJavadoc);
        }

        Map<String, Parameter> parameters() {
            return parameters;
        }
    }

}

