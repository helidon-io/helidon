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

package io.helidon.pico.processor.spi;

import java.util.List;

import javax.lang.model.element.ElementKind;

import io.helidon.pico.types.AnnotationAndValue;
import io.helidon.pico.InjectionPointInfo;
import io.helidon.pico.ServiceInfoBasics;
import io.helidon.pico.types.TypeName;
import io.helidon.pico.types.TypedElementName;

/**
 * The request will be generated internally and then passed to the appropriate
 * {@link io.helidon.pico.processor.spi.CustomAnnotationTemplateProducer#produce(CustomAnnotationTemplateProducerRequest, TemplateHelperTools)}.
 * to handle the request.
 */
public interface CustomAnnotationTemplateProducerRequest {

    /**
     * @return the type of the annotation being processed
     */
    TypeName getAnnoType();

    /**
     * @return the enclosing class type for the element.
     */
    TypeName getEnclosingClassType();

    /**
     * @return the enclosing class type's annotations.
     */
    List<AnnotationAndValue> getEnclosingClassAnnotations();

    /**
     * @return Attempts to portray the {@link #getEnclosingClassType()} as a {@link io.helidon.pico.ServiceInfoBasics} data structure.
     */
    ServiceInfoBasics getBasicServiceInfo();

    /**
     * @return the kind of the element.
     */
    ElementKind getElementKind();

    /**
     * @return the access modifier of the element.
     */
    InjectionPointInfo.Access getElementAccess();

    /**
     * @return returns true if the element is declared to be private.
     */
    boolean isElementStatic();

    /**
     * @return the element name.
     */
    String getElementName();

    /**
     * @return the element's annotations.
     */
    List<AnnotationAndValue> getElementAnnotations();

    /**
     * Only applicable for {@link javax.lang.model.element.ElementKind#METHOD} or {@link javax.lang.model.element.ElementKind#CONSTRUCTOR}.
     * @return the list of typed arguments for this method or constructor
     */
    List<TypedElementName> getElementArgs();

    /**
     * Return type if this is an executable element.
     *
     * @return return type
     */
    TypeName getReturnType();

    /**
     * @return the element type name.
     */
    TypeName getElementType();

    /**
     * @return true if the code should be literally generated with the {@link javax.annotation.processing.Filer}.
     */
    boolean isFilerEnabled();

}
