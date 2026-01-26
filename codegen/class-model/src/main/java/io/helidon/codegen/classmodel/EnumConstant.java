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

import java.util.List;
import java.util.Objects;
import java.util.Set;

import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;

/**
 * Field model representation.
 */
public final class EnumConstant extends AnnotatedComponent {
    private final Content content;

    private EnumConstant(Builder builder) {
        super(builder);

        this.content = builder.contentBuilder.build();
    }

    /**
     * Create new {@link io.helidon.codegen.classmodel.EnumConstant.Builder} instance.
     *
     * @return new builder instance
     */
    public static Builder builder() {
        return new Builder().accessModifier(AccessModifier.PRIVATE);
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
        writer.write(name());
        if (content.hasBody()) {
            writer.write("(");
            content.writeBody(writer, imports);
            writer.write(")");
        }
    }

    @Override
    void addImports(ImportOrganizer.Builder imports) {
        super.addImports(imports);
        type().addImports(imports);
        content.addImports(imports);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        EnumConstant enumConstant = (EnumConstant) o;
        return name().equals(enumConstant.name())
                && type().equals(enumConstant.type());
    }

    @Override
    public int hashCode() {
        return Objects.hash(name(), type());
    }

    @Override
    public String toString() {
        return name();
    }

    /**
     * Fluent API builder for {@link io.helidon.codegen.classmodel.EnumConstant}.
     */
    public static final class Builder extends AnnotatedComponent.Builder<Builder, EnumConstant> implements ContentBuilder<Builder> {

        private final Content.Builder contentBuilder = Content.builder();

        private Builder() {
        }

        @Override
        public EnumConstant build() {
            super.accessModifier(AccessModifier.PACKAGE_PRIVATE)
                    .type(TypeNames.OBJECT);

            return new EnumConstant(this);
        }

        @Override
        public Builder content(List<String> content) {
            contentBuilder.content(content);
            return this;
        }

        @Override
        public Builder addContent(String line) {
            contentBuilder.addContent(line);
            return this;
        }

        @Override
        public Builder addContent(TypeName typeName) {
            contentBuilder.addContent(typeName);
            return this;
        }

        @Override
        public Builder padContent() {
            contentBuilder.padContent();
            return this;
        }

        @Override
        public Builder padContent(int repetition) {
            contentBuilder.padContent(repetition);
            return this;
        }

        @Override
        public Builder increaseContentPadding() {
            contentBuilder.increaseContentPadding();
            return this;
        }

        @Override
        public Builder decreaseContentPadding() {
            contentBuilder.decreaseContentPadding();
            return this;
        }

        @Override
        public Builder clearContent() {
            contentBuilder.clearContent();
            return this;
        }

        @Override
        public Builder addTypeToContent(String typeName) {
            contentBuilder.addTypeToContent(typeName);
            return this;
        }

        @Override
        public Builder javadoc(Javadoc javadoc) {
            return super.javadoc(javadoc);
        }
    }
}
