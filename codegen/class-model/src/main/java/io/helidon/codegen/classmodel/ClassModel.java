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
package io.helidon.codegen.classmodel;

import java.io.IOException;
import java.io.Writer;
import java.util.Set;

import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.TypeName;

/**
 * Entry point to create class model.
 * This model contain all needed information for each generated type and handles resulting generation.
 */
public final class ClassModel extends ClassBase {

    /**
     * Padding token used for identifying extra padding requirement for content formatting.
     */
    public static final String PADDING_TOKEN = "<<padding>>";
    /**
     * Type token is used to prepend and append to the fully qualified type names to support import handling.
     */
    public static final String TYPE_TOKEN = "@";
    /**
     * Pattern in which are type names saved in the content templates.
     */
    public static final String TYPE_TOKEN_PATTERN = TYPE_TOKEN + "name" + TYPE_TOKEN;
    /**
     * Default padding used in the generated type.
     */
    public static final String DEFAULT_PADDING = "    ";
    private final String packageName;
    private final String copyright;
    private ImportOrganizer imports;

    private ClassModel(Builder builder) {
        super(builder);
        this.copyright = builder.copyright;
        this.packageName = builder.packageName;
    }

    /**
     * Create new {@link Builder} instance.
     *
     * @return new builder instance
     */
    public static ClassModel.Builder builder() {
        return new Builder();
    }

    /**
     * Write created type model.
     * Default padding {@link #DEFAULT_PADDING} is used.
     *
     * @param writer writer to be used
     * @throws IOException write operation failure
     */
    public void write(Writer writer) throws IOException {
        write(writer, DEFAULT_PADDING);
    }

    /**
     * Write created type model.
     *
     * @param writer writer to be used
     * @param padding padding to be used
     * @throws IOException write operation failure
     */
    public void write(Writer writer, String padding) throws IOException {
        ModelWriter innerWriter = new ModelWriter(writer, padding);
        writeComponent(innerWriter, Set.of(), imports, classType());
    }

    @Override
    void writeComponent(ModelWriter writer,
                        Set<String> declaredTokens,
                        ImportOrganizer imports,
                        ClassType classType) throws IOException {
        if (copyright != null) {
            String[] lines = copyright.split("\n");
            if (lines.length > 1) {
                boolean applyFormatting = !lines[0].startsWith("/*");
                if (applyFormatting) {
                    writer.write("/*\n");
                }
                for (String line : lines) {
                    if (applyFormatting) {
                        writer.write(" * " + line + "\n");
                    } else {
                        writer.write(line + "\n");
                    }
                }
                if (applyFormatting) {
                    writer.write(" */\n\n");
                }
            } else {
                if (!lines[0].startsWith("//")) {
                    writer.write("// ");
                }
                writer.write(lines[0] + "\n");
            }
            writer.writeSeparatorLine();
        }
        if (packageName != null && !packageName.isEmpty()) {
            writer.write("package " + packageName + ";\n\n");
        }
        imports.writeImports(writer);
        imports.writeStaticImports(writer);
        super.writeComponent(writer, declaredTokens, imports, classType);
        writer.writeSeparatorLine();
    }

    /**
     * Type name of this class.
     *
     * @return type name
     */
    public TypeName typeName() {
        return TypeName.create(packageName + "." + name());
    }

    @Override
    public String toString() {
        return "ClassModel{"
                + "packageName='" + packageName + '\''
                + "name='" + name() + '\''
                + '}';
    }

    /**
     * Fluent API builder for {@link ClassModel}.
     */
    public static final class Builder extends ClassBase.Builder<Builder, ClassModel> {

        private String packageName = "";
        private String copyright;

        private Builder() {
        }

        @Override
        public ClassModel build() {
            if (name() == null) {
                throw new ClassModelException("Class need to have name specified");
            }
            ClassModel classModel = new ClassModel(this);
            ImportOrganizer.Builder importOrganizer = importOrganizer();
            classModel.addImports(importOrganizer);
            classModel.imports = importOrganizer.build();
            return classModel;
        }

        @Override
        public Builder accessModifier(AccessModifier accessModifier) {
            if (accessModifier == AccessModifier.PRIVATE) {
                throw new IllegalArgumentException("Outer class cannot be private!");
            }
            return super.accessModifier(accessModifier);
        }

        /**
         * Package name of this type.
         *
         * @param packageName type package name
         * @return updated builder instance
         */
        public Builder packageName(String packageName) {
            this.packageName = packageName;
            importOrganizer().packageName(packageName);
            return this;
        }

        /**
         * Copyright header to be used.
         *
         * @param copyright copyright header
         * @return updated builder instance
         */
        public Builder copyright(String copyright) {
            this.copyright = copyright;
            return this;
        }

        @Override
        public Builder name(String name) {
            importOrganizer().typeName(name);
            return super.name(name);
        }

        @Override
        public Builder type(TypeName type) {
            packageName(type.packageName());
            name(type.className());
            return this;
        }

    }

}
