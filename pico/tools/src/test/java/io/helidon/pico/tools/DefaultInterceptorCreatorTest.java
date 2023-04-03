/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.pico.tools;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.Optional;
import java.util.Set;

import io.helidon.common.types.DefaultAnnotationAndValue;
import io.helidon.common.types.DefaultTypeName;
import io.helidon.pico.DefaultServiceInfoBasics;
import io.helidon.pico.InterceptedTrigger;
import io.helidon.pico.tools.spi.InterceptorCreator;
import io.helidon.pico.tools.testsubjects.HelloPicoWorld;
import io.helidon.pico.tools.testsubjects.HelloPicoWorldImpl;

import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import static io.helidon.pico.tools.DefaultInterceptorCreator.AnnotationTypeNameResolver;
import static io.helidon.pico.tools.DefaultInterceptorCreator.createResolverFromReflection;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;

class DefaultInterceptorCreatorTest extends AbstractBaseCreator {

    final InterceptorCreator interceptorCreator = loadAndCreate(InterceptorCreator.class);

    @Test
    void sanity() {
        assertThat(interceptorCreator.getClass(), equalTo(DefaultInterceptorCreator.class));
        assertThat(interceptorCreator.strategy(), is(InterceptorCreator.Strategy.BLENDED));
        assertThat(interceptorCreator.allowListedAnnotationTypes().size(), is(0));
        assertThat(interceptorCreator.isAllowListed(Named.class.getName()), is(false));
    }

    @Test
    void resolverByReflection() {
        AnnotationTypeNameResolver resolver = createResolverFromReflection();
        assertThat(resolver.resolve(InterceptedTrigger.class.getName()),
                   containsInAnyOrder(
                           DefaultAnnotationAndValue.create(Documented.class),
                           DefaultAnnotationAndValue.create(Retention.class, "java.lang.annotation.RetentionPolicy.CLASS"),
                           DefaultAnnotationAndValue.create(Target.class, "{java.lang.annotation.ElementType.ANNOTATION_TYPE}")
                   ));
    }

    @Test
    void interceptorPlanByReflection() {
        DefaultServiceInfoBasics serviceInfoBasics = DefaultServiceInfoBasics.builder()
                .serviceTypeName(HelloPicoWorldImpl.class.getName())
                .build();
        DefaultInterceptorCreator.AbstractInterceptorProcessor processor =
                ((DefaultInterceptorCreator) interceptorCreator).createInterceptorProcessor(serviceInfoBasics,
                                                                                            interceptorCreator,
                                                                                            Optional.empty());
        InterceptionPlan plan = processor.createInterceptorPlan(Set.of(Singleton.class.getName())).orElseThrow();
        assertThat(plan.hasNoArgConstructor(), is(false));
        assertThat(plan.interfaces(), contains(DefaultTypeName.create(HelloPicoWorld.class)));
    }

}
