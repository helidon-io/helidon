/*
 * Copyright (c) 2017, 2022 Oracle and/or its affiliates.
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

package io.helidon.pico.types;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Default implementation for {@link io.helidon.pico.types.TypedElementName}.
 */
public class DefaultTypedElementName implements TypedElementName {
    private final TypeName typeName;
    private final List<TypeName> componentTypeNames;
    private final String elementName;
    private final String defaultValue;
    private final List<AnnotationAndValue> annotations;
    private final List<AnnotationAndValue> elementTypeAnnotations;

    protected DefaultTypedElementName(Builder b) {
        this.typeName = b.typeName;
        this.componentTypeNames = Objects.isNull(b.componentTypeNames)
                ? Collections.emptyList() : Collections.unmodifiableList(b.componentTypeNames);
        this.elementName = b.elementName;
        this.defaultValue = b.defaultValue;
        this.annotations = Objects.isNull(b.annotations)
                ? Collections.emptyList() : Collections.unmodifiableList(b.annotations);
        this.elementTypeAnnotations = Objects.isNull(b.elementTypeAnnotations)
                ? Collections.emptyList() : Collections.unmodifiableList(b.elementTypeAnnotations);
    }

    @Override
    public TypeName typeName() {
        return typeName;
    }

    @Override
    public String elementName() {
        return elementName;
    }

    @Override
    public Optional<String> defaultValue() {
        return Optional.ofNullable(defaultValue);
    }

    @Override
    public List<AnnotationAndValue> annotations() {
        return annotations;
    }

    @Override
    public List<AnnotationAndValue> elementTypeAnnotations() {
        return elementTypeAnnotations;
    }

    /**
     * Returns the component type names describing the element.
     *
     * @return the component type names of the element
     */
    public List<TypeName> getComponentTypeNames() {
        return componentTypeNames;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(typeName());
    }

    @Override
    public boolean equals(Object another) {
        if (!(another instanceof TypedElementName)) {
            return false;
        }

        TypedElementName other = (TypedElementName) another;
        return Objects.equals(typeName(), other.typeName())
                && Objects.equals(elementName(), other.elementName())
                && Objects.equals(annotations(), other.annotations());
    }

    @Override
    public String toString() {
        return toDeclaration();
    }

    /**
     * Provides a description for this instance.
     *
     * @return provides the {typeName}{space}{elementName}
     */
    public String toDeclaration() {
//        if (Objects.nonNull(typeName()) && Objects.nonNull(elementName())) {
            return typeName() + " " + elementName();
//        }
//
//        if (Objects.nonNull(elementName())) {
//            return elementName();
//        }
//
//        if (Objects.nonNull(typeName())) {
//            return typeName().name();
//        }
//
//        return "";
    }

//    /**
//     * Creates an instance of a {@link io.helidon.pico.types.TypedElementName given its type and element name parameters.
//     *
//     * @param typeName the type name
//     * @param elementName the element name
//     * @return the created instance
//     */
//    public static TypedElementName create(TypeName typeName, String elementName) {
//        return create(typeName, null, elementName, Collections.emptyList());
//    }

//    /**
//     * Creates an instance of a {@link TypedElementName} given its type and element name parameters.
//     *
//     * @param typeName the type name
//     * @param componentTypeNames the component type names (i.e., generics on the type)
//     * @param elementName the element name
//     * @param annotations the list of annotations
//     * @return the created instance
//     */
//    public static TypedElementName create(TypeName typeName,
//                                          List<TypeName> componentTypeNames,
//                                          String elementName,
//                                          Collection<AnnotationAndValue> annotations) {
//        return DefaultTypedElementName.builder()
//                .typeName(typeName)
//                .componentTypeNames(Objects.isNull(componentTypeNames) ? Collections.emptyList() : componentTypeNames)
//                .elementName(elementName)
//                .annotations(new LinkedList<>(annotations))
//                .build();
//    }


    /**
     * Creates a builder for {@link io.helidon.pico.types.TypedElementName}.
     *
     * @return a fluent builder
     */
    public static Builder builder() {
        return new Builder();
    }


    /**
     * The fluent builder.
     */
    public static class Builder {
        private TypeName typeName;
        private List<TypeName> componentTypeNames;
        private String elementName;
        private String defaultValue;
        private List<AnnotationAndValue> annotations;
        private List<AnnotationAndValue> elementTypeAnnotations;

        protected Builder() {
        }

        /**
         * Set the type name.
         *
         * @param val   the type name value
         * @return this fluent builder
         */
        public Builder typeName(TypeName val) {
            this.typeName = val;
            return this;
        }

        /**
         * Set the type of the element.
         *
         * @param type  the type
         * @return the fluent builder
         */
        public Builder typeName(Class<?> type) {
            return typeName(DefaultTypeName.create(type));
        }

        /**
         * Set the component type names.
         *
         * @param val   the component type values
         * @return this fluent builder
         */
        public Builder componentTypeNames(List<TypeName> val) {
            this.componentTypeNames = Objects.isNull(val) ? Collections.emptyList() : new LinkedList<>(val);
            return this;
        }

        /**
         * Set the element name.
         *
         * @param val   the element name value
         * @return this fluent builder
         */
        public Builder elementName(String val) {
            this.elementName = val;
            return this;
        }

        /**
         * Set the default value.
         *
         * @param val   the default value
         * @return this fluent builder
         */
        public Builder defaultValue(String val) {
            this.defaultValue = val;
            return this;
        }

        /**
         * Set the annotations for this element.
         *
         * @param val   the annotation values
         * @return this fluent builder
         */
        public Builder annotations(List<AnnotationAndValue> val) {
            this.annotations = new LinkedList<>(val);
            return this;
        }

        /**
         * Adds a singular annotation.
         *
         * @param annotation the annotation to add
         * @return the fluent builder
         */
        public Builder annotation(AnnotationAndValue annotation) {
            if (Objects.isNull(annotations)) {
                this.annotations = new LinkedList<>();
            }
            this.annotations.add(Objects.requireNonNull(annotation));
            return this;
        }

        /**
         * Set the annotations for this element type.
         *
         * @param val   the element type annotation values
         * @return this fluent builder
         */
        public Builder elementTypeAnnotations(List<AnnotationAndValue> val) {
            this.elementTypeAnnotations = new LinkedList<>(val);
            return this;
        }

        /**
         * Build the instance.
         *
         * @return the built instance
         */
        public DefaultTypedElementName build() {
            Objects.requireNonNull(typeName);
            return new DefaultTypedElementName(this);
        }
    }

}
