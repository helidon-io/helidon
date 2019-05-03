/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.security;

import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import io.helidon.common.CollectionsHelper;

/**
 * Security level stores annotations bound to the specific class and method.
 *
 * The first level represents {@link EndpointConfig.AnnotationScope#APPLICATION} level annotations.
 * Other levels are representations of each resource and method used on path to get to the target method.
 *
 * @author David Kral
 */
public class SecurityLevel {

    private final Map<Class<? extends Annotation>, List<Annotation>> classLevelAnnotations;
    private final Map<Class<? extends Annotation>, List<Annotation>> methodLevelAnnotations;

    public SecurityLevel() {
        classLevelAnnotations = new HashMap<>();
        methodLevelAnnotations = new HashMap<>();
    }

    /**
     * Filters out all annotations of the specific type in the specific scope
     *
     * @param annotationType type of the annotation
     * @param scope desired scope
     * @return list of annotations
     */
    @SuppressWarnings("unchecked")
    public <T extends Annotation> List<T> filterAnnotations(Class<T> annotationType, EndpointConfig.AnnotationScope scope) {
        switch (scope) {
        case CLASS:
            return (List<T>) classLevelAnnotations.getOrDefault(annotationType, CollectionsHelper.listOf());
        case METHOD:
            return (List<T>) methodLevelAnnotations.getOrDefault(annotationType, CollectionsHelper.listOf());
        default:
            return CollectionsHelper.listOf();
        }
    }

    /**
     * Combines all the annotations of the specific type across all the requested scopes.
     *
     * @param annotationType type of the annotation
     * @param scopes desired scopes
     * @return list of annotations
     */
    public <T extends Annotation> List<T> combineAnnotations(Class<T> annotationType, EndpointConfig.AnnotationScope... scopes) {
        List<T> result = new LinkedList<>();
        for (EndpointConfig.AnnotationScope scope : scopes) {
            result.addAll(filterAnnotations(annotationType, scope));
        }
        return result;
    }

    /**
     * Returns class level and method level annotations together in one {@link Map}
     *
     * @return map with class and method level annotations
     */
    public Map<Class<? extends Annotation>, List<Annotation>> allAnnotations() {
        Map<Class<? extends Annotation>, List<Annotation>> result = new HashMap<>(classLevelAnnotations);
        methodLevelAnnotations.forEach((key, value) -> {
            if (result.containsKey(key)) {
                result.get(key).addAll(value);
            } else {
                result.put(key, value);
            }
        });
        return result;
    }

    /**
     * Returns class level annotations.
     *
     * @return map of annotations
     */
    public Map<Class<? extends Annotation>, List<Annotation>> getClassLevelAnnotations() {
        return classLevelAnnotations;
    }

    /**
     * Returns method level annotations.
     *
     * @return map of annotations
     */
    public Map<Class<? extends Annotation>, List<Annotation>> getMethodLevelAnnotations() {
        return methodLevelAnnotations;
    }
}