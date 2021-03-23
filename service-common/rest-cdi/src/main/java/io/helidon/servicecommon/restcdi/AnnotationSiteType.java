/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
 *
 */
package io.helidon.servicecommon.restcdi;

import java.lang.reflect.Method;

import javax.enterprise.inject.spi.Annotated;
import javax.enterprise.inject.spi.AnnotatedMember;

/**
 * DO NOT USE THIS CLASS please.
 * <p>
 * Types of possible matching.
 * </p>
 */
public enum AnnotationSiteType {
    /**
     * Method.
     */
    METHOD,
    /**
     * Class.
     */
    CLASS;

    /**
     * Returns the {@code AnnotationSiteType} appropriate for the supplied {@code Annotated} annotation site.
     *
     * @param annotated the annotation site
     * @return the appropriate matching type
     */
    public static AnnotationSiteType matchingType(Annotated annotated) {
        return annotated instanceof AnnotatedMember
                ? (((AnnotatedMember<?>) annotated).getJavaMember() instanceof Method
                    ? METHOD : CLASS)
                : CLASS;
    }
}
