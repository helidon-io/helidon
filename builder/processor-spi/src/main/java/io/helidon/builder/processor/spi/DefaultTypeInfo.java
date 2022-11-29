/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import io.helidon.pico.types.AnnotationAndValue;
import io.helidon.pico.types.TypeName;
import io.helidon.pico.types.TypedElementName;

/**
 * Default implementation for {@link TypeInfo}.
 */
public class DefaultTypeInfo implements TypeInfo {
    private final TypeName typeName;
    private final String typeKind;
    private final List<AnnotationAndValue> annotations;
    private final List<TypedElementName> elementInfo;
    private final TypeInfo superTypeInfo;

    /**
     * Default constructor taking the builder as an argument.
     *
     * @param b the builder
     * @see #builder()
     */
    protected DefaultTypeInfo(Builder b) {
        this.typeName = b.typeName;
        this.typeKind = b.typeKind;
        this.annotations = Collections.unmodifiableList(new LinkedList<>(b.annotations));
        this.elementInfo = Collections.unmodifiableList(new LinkedList<>(b.elementInfo));
        this.superTypeInfo = b.superTypeInfo;
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
    public Optional<TypeInfo> superTypeInfo() {
        return Optional.ofNullable(superTypeInfo);
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
                + ", superTypeInfo=" + superTypeInfo();
    }

    /**
     * Builder for this type.
     */
    public static class Builder implements io.helidon.common.Builder<Builder, DefaultTypeInfo> {
        private final List<AnnotationAndValue> annotations = new ArrayList<>();
        private final List<TypedElementName> elementInfo = new ArrayList<>();

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
            elementInfo.add(Objects.requireNonNull(val));
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
