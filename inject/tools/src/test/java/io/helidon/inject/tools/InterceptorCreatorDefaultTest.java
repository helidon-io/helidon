/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.inject.tools;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.Set;

import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;
import io.helidon.inject.api.InterceptedTrigger;
import io.helidon.inject.api.ServiceInfoBasics;
import io.helidon.inject.tools.spi.InterceptorCreator;
import io.helidon.inject.tools.testsubjects.HelloInjectionWorld;
import io.helidon.inject.tools.testsubjects.HelloInjectionWorldImpl;

import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;

class InterceptorCreatorDefaultTest extends AbstractBaseCreator {

    final InterceptorCreator interceptorCreator = loadAndCreate(InterceptorCreator.class);

    @Test
    void sanity() {
        assertThat(interceptorCreator.getClass(), equalTo(InterceptorCreatorDefault.class));
        assertThat(interceptorCreator.strategy(), is(InterceptorCreator.Strategy.BLENDED));
        assertThat(interceptorCreator.allowListedAnnotationTypes().size(), is(0));
        assertThat(interceptorCreator.isAllowListed(TypeName.create(Named.class)), is(false));
    }

    @Test
    void resolverByReflection() {
        InterceptorCreatorDefault.AnnotationTypeNameResolver resolver = InterceptorCreatorDefault.createResolverFromReflection();
        assertThat(resolver.resolve(TypeName.create(InterceptedTrigger.class)),
                   containsInAnyOrder(
                           Annotation.create(Documented.class),
                           Annotation.create(Retention.class, "java.lang.annotation.RetentionPolicy.CLASS"),
                           Annotation.create(Target.class, "{java.lang.annotation.ElementType.ANNOTATION_TYPE}"),
                           Annotation.builder()
                                   .type(Deprecated.class)
                                   .putValue("forRemoval", "true")
                                   .putValue("since", "4.0.8")
                                   .build()
                   ));
    }

    @Test
    void interceptorPlanByReflection() {
        ServiceInfoBasics serviceInfoBasics = ServiceInfoBasics.builder()
                .serviceTypeName(HelloInjectionWorldImpl.class)
                .build();
        InterceptorCreatorDefault.AbstractInterceptorProcessor processor =
                (InterceptorCreatorDefault.AbstractInterceptorProcessor)
                interceptorCreator.createInterceptorProcessor(serviceInfoBasics, interceptorCreator);
        InterceptionPlan plan = processor.createInterceptorPlan(Set.of(TypeName.create(Singleton.class.getName())))
                .orElseThrow();
        assertThat(plan.hasNoArgConstructor(),
                   is(false));
        assertThat(plan.interfaces(),
                   contains(TypeName.create(HelloInjectionWorld.class)));
    }

}
