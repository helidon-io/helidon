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

import java.util.List;
import java.util.Objects;

import io.helidon.common.types.AccessModifier;

abstract class CommonComponent extends DescribableComponent {

    private final String name;
    private final Javadoc javadoc;
    private final AccessModifier accessModifier;

    CommonComponent(Builder<?, ?> builder) {
        super(builder);
        this.name = builder.name;
        this.accessModifier = builder.accessModifier;
        this.javadoc = builder.javadocBuilder.build(builder);
    }

    String name() {
        return name;
    }

    Javadoc javadoc() {
        return javadoc;
    }

    AccessModifier accessModifier() {
        return accessModifier;
    }

    abstract static class Builder<B extends Builder<B, T>, T extends CommonComponent> extends DescribableComponent.Builder<B, T> {
        private final Javadoc.Builder javadocBuilder = Javadoc.builder();
        private AccessModifier accessModifier = AccessModifier.PUBLIC;
        private String name;

        Builder() {
        }

        @Override
        B description(String description) {
            return description(List.of(description));
        }

        @Override
        B description(List<String> description) {
            this.javadocBuilder.content(description);
            this.javadocBuilder.generate(true);
            return identity();
        }

        /**
         * Add another line to already existing javadoc.
         *
         * @param line line to add
         * @return updated builder instance
         */
        B addDescriptionLine(String line) {
            this.javadocBuilder.addLine(line);
            return identity();
        }

        /**
         * Javadoc of the component.
         * Overwrites all previously created content.
         *
         * @param javadoc component javadoc
         * @return updated builder instance
         */
        B javadoc(Javadoc javadoc) {
            this.javadocBuilder.clear();
            this.javadocBuilder.from(javadoc);
            return identity();
        }

        /**
         * Set whether to generate javadoc or not.
         * Javadoc is automatically generated when description is set.
         *
         * @param generateJavadoc true if javadoc should be generated
         * @return updated builder instance
         */
        B generateJavadoc(boolean generateJavadoc) {
            this.javadocBuilder.generate(generateJavadoc);
            return identity();
        }

        /**
         * Add method parameter javadoc.
         *
         * @param parameter parameter name
         * @param description param description
         * @return updated builder instance
         */
        B addJavadocParameter(String parameter, List<String> description) {
            this.javadocBuilder.addParameter(parameter, description);
            return identity();
        }

        /**
         * Add generic parameter javadoc.
         *
         * @param parameter parameter name
         * @param description param description
         * @return updated builder instance
         */
        B addGenericToken(String parameter, String description) {
            this.javadocBuilder.addGenericArgument(parameter, description);
            return identity();
        }

        /**
         * Add generic parameter javadoc.
         *
         * @param parameter parameter name
         * @param description param description
         * @return updated builder instance
         */
        B addGenericToken(String parameter, List<String> description) {
            this.javadocBuilder.addGenericArgument(parameter, description);
            return identity();
        }

        /**
         * Add throws javadoc description.
         *
         * @param exception exception name
         * @param description exception description
         * @return updated builder instance
         */
        B addJavadocThrows(String exception, List<String> description) {
            this.javadocBuilder.addThrows(exception, description);
            return identity();
        }

        /**
         * Add any javadoc tag.
         *
         * @param tag tag name
         * @param description tag description
         * @return updated builder instance
         */
        B addJavadocTag(String tag, String description) {
            this.javadocBuilder.addTag(tag, description);
            return identity();
        }

        /**
         * Set javadoc deprecation description.
         *
         * @param description deprecation description
         * @return updated builder instance
         */
        B deprecationJavadoc(String description) {
            this.javadocBuilder.deprecation(description);
            return identity();
        }

        /**
         * Method return type javadoc.
         *
         * @param description return type description
         * @return updated builder instance
         */
        B returnJavadoc(String description) {
            this.javadocBuilder.returnDescription(description);
            return identity();
        }

        /**
         * Method return type javadoc.
         *
         * @param description return type description
         * @return updated builder instance
         */
        B returnJavadoc(List<String> description) {
            this.javadocBuilder.returnDescription(description);
            return identity();
        }

        /**
         * Set new name of this component.
         *
         * @param name name of this component
         * @return updated builder instance
         */
        B name(String name) {
            this.name = Objects.requireNonNull(name);
            return identity();
        }

        /**
         * Set new access modifier of this component.
         *
         * @param accessModifier access modifier
         * @return updated builder instance
         */
        B accessModifier(AccessModifier accessModifier) {
            this.accessModifier = Objects.requireNonNull(accessModifier);
            return identity();
        }

        String name() {
            return name;
        }
    }

}
