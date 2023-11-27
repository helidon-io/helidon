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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.helidon.common.types.TypeName;

import static io.helidon.codegen.classmodel.ClassModel.PADDING_TOKEN;
import static io.helidon.codegen.classmodel.ClassModel.TYPE_TOKEN;

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
    static final class Builder implements ContentBuilder<Builder>, io.helidon.common.Builder<Builder, Content> {

        private static final Pattern TYPE_NAME_PATTERN = Pattern.compile(TYPE_TOKEN + "(.*?)" + TYPE_TOKEN);
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

        @Override
        public Builder content(List<String> content) {
            this.content.setLength(0);
            content.forEach(this::addContentLine);
            return this;
        }

        @Override
        public Builder addContent(String line) {
            String trimmed = line.trim();
            if (trimmed.equals("}")) {
                decreaseContentPadding();
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
                increaseContentPadding();
            }
            return this;
        }

        @Override
        public Builder padContent() {
            this.content.append(PADDING_TOKEN);
            return identity();
        }

        @Override
        public Builder padContent(int repetition) {
            this.content.append(PADDING_TOKEN.repeat(repetition));
            return identity();
        }

        @Override
        public Builder addTypeToContent(String typeName) {
            String processedFqName = TYPE_IDENTIFICATION_PATTERN.matcher(typeName)
                    .replaceAll(className -> ClassModel.TYPE_TOKEN_PATTERN.replace("name", className.group()));
            return addContent(processedFqName);
        }

        @Override
        public Builder increaseContentPadding() {
            this.extraPaddingLevel++;
            this.extraPadding = PADDING_TOKEN.repeat(this.extraPaddingLevel);
            return identity();
        }

        @Override
        public Builder decreaseContentPadding() {
            this.extraPaddingLevel--;
            if (this.extraPaddingLevel < 0) {
                throw new ClassModelException("Content padding cannot be negative");
            }
            this.extraPadding = PADDING_TOKEN.repeat(this.extraPaddingLevel);
            return identity();
        }

        @Override
        public Builder clearContent() {
            this.content.setLength(0);
            return identity();
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
