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

import javax.inject.Singleton;

import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

public class MicronautBeanDefining implements TypeElementVisitor<Object, Object> {
    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        if (element.hasAnnotation("javax.enterprise.context.ApplicationScoped")) {
            element.annotate(Singleton.class);
        } else if (element.hasAnnotation("javax.enterprise.context.RequestScoped")) {
            element.annotate("io.micronaut.runtime.http.scope.RequestScope");
        } else if (element.hasAnnotation("javax.enterprise.context.Dependent")) {
            // intentional - we ignore non-Singleton beans from Micronaut,
            // yet we want Micronaut to create executable methods for any bean
            element.annotate("io.micronaut.runtime.http.scope.RequestScope");
        }
    }
}