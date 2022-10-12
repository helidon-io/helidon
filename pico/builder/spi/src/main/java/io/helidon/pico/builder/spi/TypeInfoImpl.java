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

package io.helidon.pico.builder.spi;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.helidon.pico.spi.TypedElementName;
import io.helidon.pico.types.AnnotationAndValue;
import io.helidon.pico.types.TypeName;

/**
 * Concrete implementation w/ builder for {@link TypeInfo}.
 */
@SuppressWarnings("unchecked")
public class TypeInfoImpl implements TypeInfo {
    private final TypeName typeName;
    private final String typeKind;
    private final List<AnnotationAndValue> annotations;
    private final List<TypedElementName> elementInfo;
    private final TypeInfo superTypeInfo;

    protected TypeInfoImpl(Builder builder) {
        this.typeName = builder.typeName;
        this.typeKind = builder.typeKind;
        this.annotations = Objects.isNull(builder.annotations)
                ? Collections.emptyList() : Collections.unmodifiableList(new LinkedList<>(builder.annotations));
        this.elementInfo = Objects.isNull(builder.elementInfo)
                ? Collections.emptyList() : Collections.unmodifiableList(new LinkedList<>(builder.elementInfo));
        this.superTypeInfo = builder.superTypeInfo;
    }

    @Override
    public TypeName getTypeName() {
        return typeName;
    }

    @Override
    public String getTypeKind() {
        return typeKind;
    }

    @Override
    public List<AnnotationAndValue> getAnnotations() {
        return annotations;
    }

    @Override
    public List<TypedElementName> getElementInfo() {
        return elementInfo;
    }

    @Override
    public TypeInfo getSuperTypeInfo() {
        return superTypeInfo;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" + toStringInner() + ")";
    }

    protected String toStringInner() {
        return "typeName=" + getTypeName()
                + ", elementInfo=" + getElementInfo()
                + ", superTypeInfo=" + getSuperTypeInfo();
    }

    public static Builder<? extends Builder> builder() {
        return new Builder();
    }


    public static class Builder<B extends Builder<B>> {
        private TypeName typeName;
        private String typeKind;
        private List<AnnotationAndValue> annotations;
        private List<TypedElementName> elementInfo;
        private Map<TypedElementName, String> defaultValueMap;
        private TypeInfo superTypeInfo;

        public B typeName(TypeName val) {
            this.typeName = val;
            return (B) this;
        }

        public B typeKind(String val) {
            this.typeKind = val;
            return (B) this;
        }

        public B annotations(Collection<AnnotationAndValue> val) {
            this.annotations = Objects.isNull(val) ? null : new LinkedList<>(val);
            return (B) this;
        }

        public B addAnnotation(AnnotationAndValue val) {
            if (Objects.isNull(annotations)) {
                annotations = new LinkedList<>();
            }
            annotations.add(val);
            return (B) this;
        }

        public B elementInfo(Collection<TypedElementName> val) {
            this.elementInfo = Objects.isNull(val) ? null : new LinkedList<>(val);
            return (B) this;
        }

        public B addElementInfo(TypedElementName val) {
            if (Objects.isNull(elementInfo)) {
                elementInfo = new LinkedList<>();
            }
            elementInfo.add(val);
            return (B) this;
        }

        public B defaultValueMap(Map<TypedElementName, String> val) {
            this.defaultValueMap = Objects.isNull(val) ? null : new LinkedHashMap<>(val);
            return (B) this;
        }

        public B defaultValue(TypedElementName key, String val) {
            if (Objects.isNull(defaultValueMap)) {
                defaultValueMap = new LinkedHashMap<>();
            }
            defaultValueMap.put(key, val);
            return (B) this;
        }

        public B superTypeInfo(TypeInfo val) {
            this.superTypeInfo = val;
            return (B) this;
        }

        public TypeInfoImpl build() {
            return new TypeInfoImpl(this);
        }
    }

}
