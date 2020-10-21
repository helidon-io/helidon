/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.integrations.micronaut.cdi.processor;

import java.lang.annotation.Annotation;
import java.util.List;

import javax.inject.Scope;

import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.annotation.NamedAnnotationTransformer;
import io.micronaut.inject.visitor.VisitorContext;

/**
 * Transforms CDI Dependent annotation into Micronaut RequestScope (as the integration module does not
 * use this bean from Micronaut, the scope is not relevant, we only use it to get ExecutableMethod metadata).
 */
public class DependentTransformer implements NamedAnnotationTransformer {
    @Override
    public String getName() {
        return "javax.enterprise.context.Dependent";
    }

    @Override
    public List<AnnotationValue<?>> transform(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
        return List.of(
                AnnotationValue.builder(Scope.class).build(),
                AnnotationValue.builder(Prototype.class).build()
        );
    }
}
