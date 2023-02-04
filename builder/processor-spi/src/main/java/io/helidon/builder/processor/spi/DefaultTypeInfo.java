/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.builder.processor.spi;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import io.helidon.builder.types.AnnotationAndValue;
import io.helidon.builder.types.TypeName;
import io.helidon.builder.types.TypedElementName;

/**
 * Default implementation for {@link TypeInfo}.
 */
public class DefaultTypeInfo implements TypeInfo {
    private final TypeName typeName;
    private final String typeKind;
    private final List<AnnotationAndValue> annotations;
    private final List<TypedElementName> elementInfo;
    private final List<TypedElementName> otherElementInfo;
    private final TypeInfo superTypeInfo;
    private final Set<String> modifierNames;

    /**
     * Default constructor taking the builder as an argument.
     *
     * @param b the builder
     * @see #builder()
     */
    protected DefaultTypeInfo(Builder b) {
        this.typeName = b.typeName;
        this.typeKind = b.typeKind;
        this.annotations = List.copyOf(b.annotations);
        this.elementInfo = List.copyOf(b.elementInfo);
        this.otherElementInfo = List.copyOf(b.otherElementInfo);
        this.superTypeInfo = b.superTypeInfo;
        this.modifierNames = Set.copyOf(b.modifierNames);
    }

    /**
     * Creates a new builder for this type.
     *
     * @return the fluent builder
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public TypeName typeName() {
        return typeName;
    }

    @Override
    public String typeKind() {
        return typeKind;
    }

    @Override
    public List<AnnotationAndValue> annotations() {
        return annotations;
    }

    @Override
    public List<TypedElementName> elementInfo() {
        return elementInfo;
    }

    @Override
    public List<TypedElementName> otherElementInfo() {
        return otherElementInfo;
    }

    @Override
    public Optional<TypeInfo> superTypeInfo() {
        return Optional.ofNullable(superTypeInfo);
    }

    @Override
    public Set<String> modifierNames() {
        return modifierNames;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" + toStringInner() + ")";
    }

    /**
     * Calculates the inner portion of the {@link #toString()} result.
     *
     * @return the inner portion of the toString result
     */
    protected String toStringInner() {
        return "typeName=" + typeName()
                + ", elementInfo=" + elementInfo()
                + ", annotations=" + annotations()
                + ", superTypeInfo=" + superTypeInfo()
                + ", modifierNames=" + modifierNames();
    }

    /**
     * Builder for this type.
     */
    public static class Builder implements io.helidon.common.Builder<Builder, DefaultTypeInfo> {
        private final List<AnnotationAndValue> annotations = new ArrayList<>();
        private final List<TypedElementName> elementInfo = new ArrayList<>();
        private final List<TypedElementName> otherElementInfo = new ArrayList<>();
        private final Set<String> modifierNames = new LinkedHashSet<>();
        private TypeName typeName;
        private String typeKind;

        private TypeInfo superTypeInfo;

        /**
         * Default constructor.
         */
        protected Builder() {
        }

        /**
         * Builds the instance.
         *
         * @return the built instance
         */
        @Override
        public DefaultTypeInfo build() {
            return new DefaultTypeInfo(this);
        }

        /**
         * Sets the typeName to val.
         *
         * @param val the value
         * @return this fluent builder
         */
        public Builder typeName(TypeName val) {
            this.typeName = val;
            return this;
        }

        /**
         * Sets the typeKind to val.
         *
         * @param val the value
         * @return this fluent builder
         */
        public Builder typeKind(String val) {
            this.typeKind = val;
            return this;
        }

        /**
         * Sets the annotations to val.
         *
         * @param val the value
         * @return this fluent builder
         */
        public Builder annotations(Collection<AnnotationAndValue> val) {
            Objects.requireNonNull(val);
            this.annotations.clear();
            this.annotations.addAll(val);
            return this;
        }

        /**
         * Adds a single annotation val.
         *
         * @param val the value
         * @return this fluent builder
         */
        public Builder addAnnotation(AnnotationAndValue val) {
            Objects.requireNonNull(val);
            annotations.add(Objects.requireNonNull(val));
            return this;
        }

        /**
         * Sets the elementInfo to val.
         *
         * @param val the value
         * @return this fluent builder
         */
        public Builder elementInfo(Collection<TypedElementName> val) {
            Objects.requireNonNull(val);
            this.elementInfo.clear();
            this.elementInfo.addAll(val);
            return this;
        }

        /**
         * Adds a single elementInfo val.
         *
         * @param val the value
         * @return this fluent builder
         */
        public Builder addElementInfo(TypedElementName val) {
            Objects.requireNonNull(val);
            elementInfo.add(val);
            return this;
        }

        /**
         * Sets the otherElementInfo to val.
         *
         * @param val the value
         * @return this fluent builder
         */
        public Builder otherElementInfo(Collection<TypedElementName> val) {
            Objects.requireNonNull(val);
            this.otherElementInfo.clear();
            this.otherElementInfo.addAll(val);
            return this;
        }

        /**
         * Adds a single otherElementInfo val.
         *
         * @param val the value
         * @return this fluent builder
         */
        public Builder addOtherElementInfo(TypedElementName val) {
            Objects.requireNonNull(val);
            otherElementInfo.add(val);
            return this;
        }

        /**
         * Sets the modifiers to val.
         *
         * @param val the value
         * @return this fluent builder
         */
        public Builder modifierNames(Collection<String> val) {
            Objects.requireNonNull(val);
            this.modifierNames.clear();
            this.modifierNames.addAll(val);
            return this;
        }

        /**
         * Adds a single modifier val.
         *
         * @param val the value
         * @return this fluent builder
         */
        public Builder addModifierName(String val) {
            Objects.requireNonNull(val);
            modifierNames.add(val);
            return this;
        }

        /**
         * Sets the superTypeInfo to val.
         *
         * @param val the value
         * @return this fluent builder
         */
        public Builder superTypeInfo(TypeInfo val) {
            Objects.requireNonNull(val);
            this.superTypeInfo = val;
            return this;
        }
    }

}
