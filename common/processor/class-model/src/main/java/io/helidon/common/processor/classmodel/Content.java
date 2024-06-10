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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.helidon.common.types.TypeName;

class Content {

    private final StringBuilder content;
    private final Set<String> toImport;
    private final List<Position> tokenPositions;

    private Content(Builder builder) {
        this.content = new StringBuilder(builder.content);
        this.toImport = Set.copyOf(builder.toImport);
        this.tokenPositions = List.copyOf(builder.tokenPositions);
    }

    static Builder builder() {
        return new Builder();
    }

    @Override
    public String toString() {
        return content.toString();
    }

    boolean hasBody() {
        return !content.isEmpty();
    }

    void writeBody(ModelWriter writer, ImportOrganizer imports) throws IOException {
        int offset = 0;
        Map<String, String> replacements = new HashMap<>();
        for (Position position : tokenPositions) {
            String replacement = replacements.computeIfAbsent(position.type, key -> {
                TypeName typeName = TypeName.create(key);
                return imports.typeName(Type.fromTypeName(typeName), true);
            });
            content.replace(position.start - offset, position.end - offset, replacement);
            //Since we are replacing values in the StringBuilder, previously obtained position indexes for class name tokens
            //will differ and because fo that, these changes need to be reflected via calculating overall offset
            offset += (position.end - position.start) - replacement.length();
        }
        String[] lines = content.toString().split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            writer.write(line);
            if (i + 1 != lines.length) {
                writer.write("\n");
            }
        }
    }

    void addImports(ImportOrganizer.Builder builder) {
        toImport.forEach(builder::addImport);
    }

    /**
     * Fluent API builder for {@link Content}.
     */
    @SuppressWarnings("removal")
    static final class Builder implements io.helidon.common.Builder<Builder, Content> {

        private static final Pattern TYPE_NAME_PATTERN =
                Pattern.compile(io.helidon.common.processor.classmodel.ClassModel.TYPE_TOKEN + "(.*?)"
                                        + io.helidon.common.processor.classmodel.ClassModel.TYPE_TOKEN);
        private static final Pattern TYPE_IDENTIFICATION_PATTERN = Pattern.compile("[.a-zA-Z0-9_]+");

        private final StringBuilder content = new StringBuilder();
        private final Set<String> toImport = new HashSet<>();
        private final List<Position> tokenPositions = new ArrayList<>();
        private String extraPadding = "";
        private int extraPaddingLevel = 0;
        private boolean newLine = false;

        private Builder() {
        }

        @Override
        public Content build() {
            toImport.clear();
            tokenPositions.clear();
            identifyClassTokens();
            return new Content(this);
        }

        /**
         * Set new content.
         * This method replaces previously created content in this builder.
         *
         * @param content content to be set
         * @return updated builder instance
         */
        Builder content(String content) {
            return content(List.of(content));
        }

        /**
         * Set new content.
         * This method replaces previously created content in this builder.
         *
         * @param content content to be set
         * @return updated builder instance
         */
        Builder content(List<String> content) {
            this.content.setLength(0);
            content.forEach(this::addLine);
            return identity();
        }

        /**
         * Add text line to the content.
         * New line character is added after this line.
         *
         * @param line line to add
         * @return updated builder instance
         */
        Builder addLine(String line) {
            return add(line).add("\n");
        }

        /**
         * Add text line to the content.
         * New line character is not added after this line, so all newly added text will be appended to the same line.
         *
         * @param line line to add
         * @return updated builder instance
         */
        Builder add(String line) {
            String trimmed = line.trim();
            if (trimmed.equals("}")) {
                decreasePadding();
            }
            if (newLine) {
                this.content.append(extraPadding);
            }
            this.newLine = line.endsWith("\n");
            String replacedLine;
            //we need to ensure proper extra padding if multiline String is received
            if (newLine) {
                //newly added line ends with \n. This \n must not be replaced
                replacedLine = line.substring(0, line.lastIndexOf("\n"))
                        .replaceAll("\n", "\n" + extraPadding) + "\n";
            } else {
                replacedLine = line.replaceAll("\n", "\n" + extraPadding);
            }
            this.content.append(replacedLine);
            if (trimmed.endsWith("{")) {
                increasePadding();
            }
            return this;
        }

        /**
         * Obtained fully qualified type name is enclosed between {@link ClassModel#TYPE_TOKEN} tokens.
         * Class names in such a format are later recognized as class names for import handling.
         *
         * @param fqClassName fully qualified class name
         * @return updated builder instance
         */
        Builder typeName(String fqClassName) {
            String processedFqName = TYPE_IDENTIFICATION_PATTERN.matcher(fqClassName)
                    .replaceAll(className -> ClassModel.TYPE_TOKEN_PATTERN.replace("name", className.group()));
            return add(processedFqName);
        }

        /**
         * Adds single padding.
         * This extra padding is added only once. If more permanent padding increment is needed use {{@link #increasePadding()}}.
         *
         * @return updated builder instance
         */
        Builder padding() {
            this.content.append(io.helidon.common.processor.classmodel.ClassModel.PADDING_TOKEN);
            return this;
        }

        /**
         * Adds padding with number of repetitions.
         * This extra padding is added only once. If more permanent padding increment is needed use {{@link #increasePadding()}}.
         *
         * @param repetition number of padding repetitions
         * @return updated builder instance
         */
        Builder padding(int repetition) {
            this.content.append(io.helidon.common.processor.classmodel.ClassModel.PADDING_TOKEN.repeat(repetition));
            return this;
        }

        /**
         * Method for manual padding increment.
         * This method will affect padding of the later added content.
         *
         * @return updated builder instance
         */
        Builder increasePadding() {
            this.extraPaddingLevel++;
            this.extraPadding = io.helidon.common.processor.classmodel.ClassModel.PADDING_TOKEN.repeat(this.extraPaddingLevel);
            return this;
        }

        /**
         * Method for manual padding decrement.
         * This method will affect padding of the later added content.
         *
         * @return updated builder instance
         */
        Builder decreasePadding() {
            this.extraPaddingLevel--;
            if (this.extraPaddingLevel < 0) {
                throw new ClassModelException("Content padding cannot be negative");
            }
            this.extraPadding = io.helidon.common.processor.classmodel.ClassModel.PADDING_TOKEN.repeat(this.extraPaddingLevel);
            return this;
        }

        /**
         * Clears created content.
         *
         * @return updated builder instance
         */
        Builder clearContent() {
            this.content.setLength(0);
            return this;
        }

        /**
         * Method which identifies all the type names in the generated content.
         * These names are later replaced with fully qualified names or just simple class names.
         */
        private void identifyClassTokens() {
            Matcher matcher = TYPE_NAME_PATTERN.matcher(content);
            while (matcher.find()) {
                String className = matcher.group(1);
                toImport.add(className);
                tokenPositions.add(new Position(matcher.start(), matcher.end(), className));
            }
        }
    }

    /**
     * Position of the type name token, which should later be replaced.
     *
     * @param start starting index of the token
     * @param end   end index of the token
     * @param type  name of the type
     */
    private record Position(int start, int end, String type) {
    }

}
