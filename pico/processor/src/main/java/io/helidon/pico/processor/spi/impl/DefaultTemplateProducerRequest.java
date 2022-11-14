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

package io.helidon.pico.processor.spi.impl;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import javax.lang.model.element.ElementKind;

import io.helidon.pico.processor.spi.CustomAnnotationTemplateProducerRequest;
import io.helidon.pico.types.AnnotationAndValue;
import io.helidon.pico.InjectionPointInfo;
import io.helidon.pico.ServiceInfoBasics;
import io.helidon.pico.types.TypeName;
import io.helidon.pico.types.TypedElementName;

/**
 * Default implementation for {@link io.helidon.pico.processor.spi.CustomAnnotationTemplateProducerRequest}.
 */
public class DefaultTemplateProducerRequest implements CustomAnnotationTemplateProducerRequest {
    private final TypeName annoType;
    private final boolean isFilerEnabled;
    private final TypeName enclosingClassType;
    private final List<AnnotationAndValue> enclosingClassAnnotations;
    private final ServiceInfoBasics basicServiceInfo;
    private final ElementKind elementKind;
    private final InjectionPointInfo.Access elementAccess;
    private final boolean isElementStatic;
    private final String elementName;
    private final TypeName elementType;
    private final List<AnnotationAndValue> elementAnnotations;
    private final List<TypedElementName> elementArgs;
    private final TypeName returnType;

    private DefaultTemplateProducerRequest(Builder builder) {
        this.annoType = builder.annoType;
        this.isFilerEnabled = builder.isFilerEnabled;
        this.enclosingClassType = builder.enclosingClassType;
        this.enclosingClassAnnotations = builder.enclosingClassAnnotations;
        this.basicServiceInfo = builder.basicServiceInfo;
        this.elementKind = builder.elementKind;
        this.elementAccess = builder.elementAccess;
        this.isElementStatic = builder.elementIsStatic;
        this.elementName = builder.elementName;
        this.elementType = builder.elementType;
        this.elementAnnotations = builder.elementAnnotations;
        this.elementArgs = builder.elementArgs;
        this.returnType = builder.returnType;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " ("
                + "annoType=\"" + annoType + "\", "
                + "enclosingClassType=\"" + enclosingClassType + "\", "
                + "enclosingClassAnnotations=\"" + enclosingClassAnnotations + "\", "
                + "basicServiceInfo=\"" + basicServiceInfo + "\", "
                + "elementKind=\"" + elementKind + "\", "
                + "elementAccess=\"" + elementAccess + "\", "
                + "isElementStatic=" + isElementStatic + ", "
                + "elementName=\"" + elementName + "\", "
                + "elementType=\"" + elementType + "\", "
                + "elementAnnotations=\"" + elementAnnotations + "\", "
                + "elementArgs=\"" + elementArgs + "\", "
                + "returnType=\"" + returnType + "\")";
    }

    @Override
    public TypeName getAnnoType() {
        return annoType;
    }

    @Override
    public TypeName getEnclosingClassType() {
        return enclosingClassType;
    }

    @Override
    public List<AnnotationAndValue> getEnclosingClassAnnotations() {
        return enclosingClassAnnotations;
    }

    @Override
    public ServiceInfoBasics getBasicServiceInfo() {
        return basicServiceInfo;
    }

    @Override
    public ElementKind getElementKind() {
        return elementKind;
    }

    @Override
    public InjectionPointInfo.Access getElementAccess() {
        return elementAccess;
    }

    @Override
    public boolean isElementStatic() {
        return isElementStatic;
    }

    @Override
    public String getElementName() {
        return elementName;
    }

    @Override
    public List<AnnotationAndValue> getElementAnnotations() {
        return elementAnnotations;
    }

    @Override
    public List<TypedElementName> getElementArgs() {
        return Collections.unmodifiableList(elementArgs);
    }

    @Override
    public TypeName getElementType() {
        return elementType;
    }

    @Override
    public boolean isFilerEnabled() {
        return isFilerEnabled;
    }

    @Override
    public TypeName getReturnType() {
        return returnType;
    }

    /**
     * Builder for the request.
     *
     * @param annoType the annotation to be processed.
     * @return the fluent builder
     */
    public static Builder builder(TypeName annoType) {
        return new Builder(Objects.requireNonNull(annoType));
    }

    /**
     * Builder for {@link io.helidon.pico.processor.spi.impl.DefaultTemplateProducerRequest}.
     */
    public static class Builder {
        private final TypeName annoType;
        private boolean isFilerEnabled = true;
        private TypeName enclosingClassType;
        private List<AnnotationAndValue> enclosingClassAnnotations = Collections.emptyList();
        private ServiceInfoBasics basicServiceInfo;
        private ElementKind elementKind;
        private InjectionPointInfo.Access elementAccess;
        private boolean elementIsStatic;
        private String elementName;
        private TypeName elementType;
        private List<AnnotationAndValue> elementAnnotations = Collections.emptyList();
        private List<TypedElementName> elementArgs = Collections.emptyList();
        private TypeName returnType;

        private Builder(TypeName annoType) {
            this.annoType = annoType;
        }

        /**
         * @return the built request instance
         */
        public DefaultTemplateProducerRequest build() {
            return new DefaultTemplateProducerRequest(this);
        }

        /**
         * Sets whether the filer is enabled; the filer controls whether code and resources actually get generated.
         *
         * @param isFilerEnabled true if the filer is enabled
         * @return the fluent builder
         */
        public Builder isFilerEnabled(boolean isFilerEnabled) {
            this.isFilerEnabled = isFilerEnabled;
            return this;
        }

        /**
         * The enclosing class type for the element being processed.
         *
         * @param enclosingClassType the enclosing class type
         * @return the fluent builder
         */
        public Builder enclosingClassType(TypeName enclosingClassType) {
            this.enclosingClassType = enclosingClassType;
            return this;
        }

        /**
         * The enclosing class type's annotations.
         *
         * @param enclosingClassAnnotations the enclosing class type's annotions
         * @return the fluent builder
         */
        public Builder enclosingClassAnnotations(List<AnnotationAndValue> enclosingClassAnnotations) {
            this.enclosingClassAnnotations = Objects.isNull(enclosingClassAnnotations)
                    ? Collections.emptyList() : new LinkedList<>(enclosingClassAnnotations);
            return this;
        }

        /**
         * The basic service info.
         *
         * @param basicServiceInfo the basie service info
         * @return the fluent builder
         */
        public Builder basicServiceInfo(ServiceInfoBasics basicServiceInfo) {
            this.basicServiceInfo = basicServiceInfo;
            return this;
        }

        /**
         * The element kind.
         *
         * @param elementKind the element kind
         * @return the fluent builder
         */
        public Builder elementKind(ElementKind elementKind) {
            this.elementKind = elementKind;
            return this;
        }

        /**
         * The element access.
         *
         * @param elementAccess the element access
         * @return the fluent builder
         */
        public Builder elementAccess(InjectionPointInfo.Access elementAccess) {
            this.elementAccess = elementAccess;
            return this;
        }

        /**
         * Is the element declared to be static.
         *
         * @param elementIsStatic true if the element is declared to be static
         * @return the fluent builder
         */
        public Builder isElementStatic(boolean elementIsStatic) {
            this.elementIsStatic = elementIsStatic;
            return this;
        }

        /**
         * The element name.
         *
         * @param elementName the element name
         * @return the fluent builder
         */
        public Builder elementName(String elementName) {
            this.elementName = elementName;
            return this;
        }

        /**
         * The element args.
         *
         * @param elementArgs the element args
         * @return the fluent builder
         */
        public Builder elementArgs(List<TypedElementName> elementArgs) {
            this.elementArgs = Objects.isNull(elementArgs) ? Collections.emptyList() : new LinkedList<>(elementArgs);
            return this;
        }

        /**
         * The element type.
         *
         * @param elementType the element type
         * @return the fluent builder
         */
        public Builder elementType(TypeName elementType) {
            this.elementType = elementType;
            return this;
        }

        /**
         * The element annotations.
         *
         * @param elementAnnotations the element annotations
         * @return the fluent builder
         */
        public Builder elementAnnotations(List<AnnotationAndValue> elementAnnotations) {
            this.elementAnnotations = Objects.isNull(elementAnnotations)
                    ? Collections.emptyList() : new LinkedList<>(elementAnnotations);
            return this;
        }

        /**
         * The element's return type.
         *
         * @param returnType the element's return type
         * @return the fluent builder
         */
        public Builder returnType(TypeName returnType) {
            this.returnType = returnType;
            return this;
        }
    }

}
