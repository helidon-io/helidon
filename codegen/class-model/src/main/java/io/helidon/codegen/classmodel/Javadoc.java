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
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Javadoc model representation.
 * <br>
 * Javadoc tags are printed out in the ordering of:
 * <ul>
 *     <li>parameters</li>
 *     <li>generic arguments</li>
 *     <li>return</li>
 *     <li>throws</li>
 *     <li>deprecated</li>
 *     <li>everything else</li>
 * </ul>
 */
public final class Javadoc extends ModelComponent {

    private final List<String> content;
    private final Map<String, List<String>> parameters;
    private final Map<String, List<String>> genericsTokens;
    private final Map<String, List<String>> throwsDesc;
    private final Map<String, List<List<String>>> otherTags;
    private final List<String> returnDescription;
    private final List<String> deprecation;
    private final Boolean generate;

    private Javadoc(Builder builder) {
        super(builder);
        this.content = List.of(builder.contentBuilder.toString().split("\n"));
        this.parameters = Collections.unmodifiableMap(new LinkedHashMap<>(builder.filteredParameters));
        this.genericsTokens = Collections.unmodifiableMap(new LinkedHashMap<>(builder.genericArguments));
        this.throwsDesc = Collections.unmodifiableMap(new LinkedHashMap<>(builder.throwsDesc));
        this.otherTags = createCopyOfTagMap(builder.otherTags);
        this.returnDescription = List.copyOf(builder.finalReturnDescription);
        this.deprecation = List.copyOf(builder.deprecation);
        this.generate = builder.generate;
    }

    /**
     * Parse Javadoc model object from the String.
     *
     * @param fullJavadocString javadoc string
     * @return new javadoc instance
     */
    public static Javadoc parse(String fullJavadocString) {
        return builder().parse(fullJavadocString).build();
    }

    /**
     * Parse Javadoc model object from the list of strings.
     *
     * @param fullJavadocLines javadoc string lines
     * @return new javadoc instance
     */
    public static Javadoc parse(List<String> fullJavadocLines) {
        return builder().parse(fullJavadocLines).build();
    }

    /**
     * Create new {@link Builder} instance.
     *
     * @return new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }


    /**
     * Create new {@link Builder} instance.
     *
     * @param javadoc existing javadoc to copy
     * @return new builder instance
     */
    public static Builder builder(Javadoc javadoc) {
        return new Builder()
                .from(javadoc);
    }

    private static Map<String, List<List<String>>> createCopyOfTagMap(Map<String, List<List<String>>> otherTags) {
        Map<String, List<List<String>>> newTags = new HashMap<>();
        for (Map.Entry<String, List<List<String>>> entry : otherTags.entrySet()) {
            newTags.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return Map.copyOf(newTags);
    }

    void writeComponent(ModelWriter writer, Set<String> declaredTokens, ImportOrganizer imports, ClassType classType)
            throws IOException {
        writer.write("/**\n");
        for (String line : content) {
            if (!line.isEmpty() && Character.isWhitespace(line.charAt(0))) {
                writer.writeLine(" *" + line);
            } else if (line.isBlank()) {
                writer.writeLine(" *");
            } else {
                writer.writeLine(" * " + line);
            }
        }
        if (hasAnyOtherParts()) {
            writer.write(" *\n");
        }
        for (Map.Entry<String, List<String>> entry : parameters.entrySet()) {
            writeTagInformation(writer, "param", entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, List<String>> entry : genericsTokens.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("<") && key.endsWith(">")) {
                writeTagInformation(writer, "param", key, entry.getValue());
            } else {
                writeTagInformation(writer, "param", "<" + key + ">", entry.getValue());
            }
        }
        if (!returnDescription.isEmpty()) {
            writeTagInformation(writer, "return", null, returnDescription);
        }
        for (Map.Entry<String, List<String>> entry : throwsDesc.entrySet()) {
            writeTagInformation(writer, "throws", entry.getKey(), entry.getValue());
        }
        if (!deprecation.isEmpty()) {
            writeTagInformation(writer, "deprecated", null, deprecation);
        }
        for (Map.Entry<String, List<List<String>>> entry : otherTags.entrySet()) {
            for (List<String> description : entry.getValue()) {
                writeTagInformation(writer, entry.getKey(), null, description);
            }
        }
        writer.write(" */");
    }

    private void writeTagInformation(ModelWriter writer, String paramName, String name, List<String> description)
            throws IOException {
        if (description.isEmpty()) {
            if (name != null) {
                writer.writeLine(" * @" + paramName + " " + name);
            } else {
                writer.writeLine(" * @" + paramName);
            }
        } else {
            boolean first = true;
            String padding;
            if (name != null) {
                //If there is specific name, we want this to be included into smart padding
                //Example: @param myParam first line
                //                        second line
                padding = " ".repeat(1 + paramName.length() + 1 + name.length() + 1);
            } else {
                //There is no specific for this tag
                //Example: @return first line
                //                 second line
                padding = " ".repeat(1 + paramName.length() + 1);
            }
            for (String line : description) {
                if (first) {
                    if (name != null) {
                        writer.write(" * @" + paramName + " " + name);
                    } else {
                        writer.write(" * @" + paramName);
                    }
                    if (line.isBlank()) {
                        writer.writeLine("");
                    } else {
                        writer.writeLine(" " + line);
                    }
                    first = false;
                } else {
                    writer.writeLine(" * " + padding + line);
                }
            }
        }
    }

    /**
     * Content of this javadoc.
     *
     * @return content
     */
    public List<String> content() {
        return content;
    }

    /**
     * Parameter tags names and descriptions.
     *
     * @return parameter tags
     */
    public Map<String, List<String>> parameters() {
        return parameters;
    }

    /**
     * Generic parameter tags names and descriptions.
     *
     * @return generic parameter tags
     */
    public Map<String, List<String>> genericsTokens() {
        return genericsTokens;
    }

    /**
     * Return type description.
     *
     * @return return type description
     */
    public List<String> returnDescription() {
        return returnDescription;
    }

    /**
     * Throws tags names and descriptions.
     *
     * @return throws tags
     */
    public Map<String, List<String>> throwsDesc() {
        return throwsDesc;
    }

    /**
     * Deprecation description.
     *
     * @return deprecation description
     */
    public List<String> deprecation() {
        return deprecation;
    }

    /**
     * Other created tags with descriptions.
     *
     * @return other tags
     */
    public Map<String, List<List<String>>> otherTags() {
        return otherTags;
    }

    boolean generate() {
        return generate;
    }

    private boolean hasAnyOtherParts() {
        return !parameters.isEmpty()
                || !throwsDesc.isEmpty()
                || !genericsTokens.isEmpty()
                || !returnDescription.isEmpty()
                || !deprecation.isEmpty()
                || !otherTags.isEmpty();
    }

    /**
     * Fluent API builder for {@link Javadoc}.
     */
    public static final class Builder extends ModelComponent.Builder<Builder, Javadoc> {

        private final StringBuilder contentBuilder = new StringBuilder();
        private final Map<String, List<String>> parameters = new LinkedHashMap<>();
        private final Map<String, List<String>> genericArguments = new LinkedHashMap<>();
        private final Map<String, List<String>> throwsDesc = new LinkedHashMap<>();
        private final Map<String, List<List<String>>> otherTags = new LinkedHashMap<>();
        private final List<String> returnDescription = new ArrayList<>();
        private final List<String> deprecation = new ArrayList<>();
        private Map<String, List<String>> filteredParameters = parameters;
        private List<String> finalReturnDescription = returnDescription;
        private boolean generate = false;

        private Builder() {
        }

        @Override
        public Javadoc build() {
            return new Javadoc(this);
        }

        /**
         * Add text line to the content.
         * New line character is added after this line.
         *
         * @param line line to add
         * @return updated builder instance
         */
        public Builder addLine(String line) {
            this.contentBuilder.append(line).append("\n");
            return this;
        }

        /**
         * Add text line to the content.
         * New line character is not added after this line, so all newly added text will be appended to the same line.
         *
         * @param line line to add
         * @return updated builder instance
         */
        public Builder add(String line) {
            this.contentBuilder.append(line);
            return this;
        }

        /**
         * Set new content.
         * This method replaces previously created content in this builder.
         *
         * @param content content to be set
         * @return updated builder instance
         */
        public Builder content(List<String> content) {
            this.contentBuilder.setLength(0);
            content.forEach(this::addLine);
            return this;
        }

        /**
         * Add parameter tag name and description.
         *
         * @param paramName parameter name
         * @param description parameter description
         * @return updated builder instance
         */
        public Builder addParameter(String paramName, String description) {
            return addParameter(paramName, List.of(description.split("\n")));
        }

        /**
         * Add parameter tag name and description.
         *
         * @param paramName parameter name
         * @param description parameter description
         * @return updated builder instance
         */
        public Builder addParameter(String paramName, List<String> description) {
            if (parameters.containsKey(paramName) && description.isEmpty()) {
                //Do nothing, since there is already some description of this parameter,
                // and we are rewriting it with empty list
                return this;
            }
            this.parameters.put(paramName, List.copyOf(description));
            return this;
        }

        /**
         * Add throws tag name and description.
         *
         * @param exception exception name
         * @param description exception description
         * @return updated builder instance
         */
        public Builder addThrows(String exception, List<String> description) {
            this.throwsDesc.put(exception, description);
            return this;
        }

        /**
         * Add throws tag name and description.
         *
         * @param tag tag name
         * @param description tag description
         * @return updated builder instance
         */
        public Builder addTag(String tag, String description) {
            this.otherTags.computeIfAbsent(tag, k -> new ArrayList<>())
                    .add(List.of(description.split("\n")));
            return this;
        }

        /**
         * Add throws tag name and description.
         *
         * @param tag tag name
         * @param description tag description
         * @return updated builder instance
         */
        public Builder addTag(String tag, List<String> description) {
            this.otherTags.computeIfAbsent(tag, k -> new ArrayList<>())
                    .add(List.copyOf(description));
            return this;
        }

        /**
         * Add return type description.
         *
         * @param returnDescription return type description
         * @return updated builder instance
         */
        public Builder returnDescription(String returnDescription) {
            return returnDescription(List.of(returnDescription));
        }

        /**
         * Add return type description.
         *
         * @param returnDescription return type description
         * @return updated builder instance
         */
        public Builder returnDescription(List<String> returnDescription) {
            Objects.requireNonNull(returnDescription);
            if (returnDescription.isEmpty()) {
                //This is here to prevent overwriting of the previously set value with empty description
                return this;
            }
            this.returnDescription.clear();
            this.returnDescription.addAll(returnDescription);
            return this;
        }

        /**
         * Add generic argument tag name and description.
         *
         * @param argument parameter name
         * @param description parameter description
         * @return updated builder instance
         */
        public Builder addGenericArgument(String argument, List<String> description) {
            this.genericArguments.put(argument, List.copyOf(description));
            return this;
        }

        /**
         * Add generic argument tag name and description.
         *
         * @param argument parameter name
         * @param description parameter description
         * @return updated builder instance
         */
        public Builder addGenericArgument(String argument, String description) {
            this.genericArguments.put(argument, List.of(description.split("\n")));
            return this;
        }

        /**
         * Deprecation description.
         *
         * @param deprecation deprecation description
         * @return updated builder instance
         */
        public Builder deprecation(String deprecation) {
            this.deprecation.clear();
            this.deprecation.add(deprecation);
            return this;
        }

        /**
         * Deprecation description, multiple lines.
         *
         * @param deprecation deprecation description
         * @return updated builder instance
         */
        public Builder deprecation(List<String> deprecation) {
            this.deprecation.clear();
            this.deprecation.addAll(deprecation);
            return this;
        }

        /**
         * Whether to generate this javadoc.
         *
         * @param generate generate javadoc
         * @return updated builder instance
         */
        public Builder generate(boolean generate) {
            this.generate = generate;
            return this;
        }

        /**
         * Populate this builder with content of the already created Javadoc instance.
         *
         * @param javadoc already created javadoc instance
         * @return updated builder instance
         */
        public Builder from(Javadoc javadoc) {
            this.generate = true;
            this.deprecation.addAll(javadoc.deprecation());
            this.returnDescription.addAll(javadoc.returnDescription());
            this.contentBuilder.append(String.join("\n", javadoc.content()));
            this.parameters.putAll(javadoc.parameters());
            this.genericArguments.putAll(javadoc.genericsTokens());
            this.throwsDesc.putAll(javadoc.throwsDesc());
            this.otherTags.putAll(javadoc.otherTags());
            return this;
        }

        /**
         * Remove everything from this builder.
         *
         * @return updated builder instance
         */
        public Builder clear() {
            this.generate = false;
            this.deprecation.clear();
            this.returnDescription.clear();
            this.contentBuilder.delete(0, contentBuilder.length());
            this.parameters.clear();
            this.genericArguments.clear();
            this.throwsDesc.clear();
            this.otherTags.clear();
            return this;
        }

        /**
         * Populates this builder with the parsed javadoc data.
         *
         * @param fullJavadocString string format javadoc
         * @return updated builder instance
         */
        public Builder parse(String fullJavadocString) {
            return JavadocParser.parse(this, fullJavadocString);
        }

        /**
         * Populates this builder with the parsed javadoc data.
         *
         * @param fullJavadocLines string list format javadoc
         * @return updated builder instance
         */
        public Builder parse(List<String> fullJavadocLines) {
            return JavadocParser.parse(this, fullJavadocLines);
        }

        Javadoc build(CommonComponent.Builder<?, ?> componentBuilder) {
            //This build method serves as configuration method based on the component this javadoc is generated for
            if (componentBuilder instanceof Method.Builder methodBuilder) {
                return build(methodBuilder);
            }
            return build();
        }

        Javadoc build(Method.Builder methodBuilder) {
            this.filteredParameters = new LinkedHashMap<>();
            for (String paramName : methodBuilder.parameters().keySet()) {
                //generate only really present parameters
                if (parameters.containsKey(paramName)) {
                    this.filteredParameters.put(paramName, parameters.get(paramName));
                }
            }
            if (methodBuilder.returnType().fqTypeName().equals(void.class.getName())) {
                //Do not add return tag if method does not return anything
                finalReturnDescription = new ArrayList<>();
            }
            return build();
        }
    }
}
