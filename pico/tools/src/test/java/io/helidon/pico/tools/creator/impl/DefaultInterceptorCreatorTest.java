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

package io.helidon.pico.tools.creator.impl;

import java.util.Collections;
import java.util.Set;

import io.helidon.pico.DefaultServiceInfo;
import io.helidon.pico.InterceptedTrigger;
import io.helidon.pico.ServiceInfoBasics;
import io.helidon.pico.test.utils.JsonUtils;
import io.helidon.pico.testsubjects.interceptor.InterceptorBasedAnno;
import io.helidon.pico.testsubjects.interceptor.X;
import io.helidon.pico.tools.creator.InterceptionPlan;
import io.helidon.pico.tools.creator.InterceptorCreator;
import io.helidon.pico.tools.processor.TypeTools;
import io.helidon.pico.tools.utils.CommonUtils;

import jakarta.inject.Named;
import jakarta.inject.Qualifier;
import jakarta.inject.Scope;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import static io.helidon.pico.tools.creator.impl.DefaultInterceptorCreator.AnnotationTypeNameResolver;
import static io.helidon.pico.tools.creator.impl.DefaultInterceptorCreator.InterceptorProcessor;
import static io.helidon.pico.tools.creator.impl.DefaultInterceptorCreator.TriggerFilter;
import static io.helidon.pico.tools.creator.impl.DefaultInterceptorCreator.createTriggerFilter;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Also see ComplexInterceptorTest
 */
public class DefaultInterceptorCreatorTest extends AbstractBaseCreator {

    private final InterceptorCreator interceptorCreator = loadAndCreate(InterceptorCreator.class);

    @Test
    public void sanity() {
        assertNotNull(interceptorCreator);
        assertEquals(DefaultInterceptorCreator.class, interceptorCreator.getClass());
        assertEquals(InterceptorCreator.Strategy.BLENDED, interceptorCreator.getStrategy());
        assertTrue(interceptorCreator.getWhiteListedAnnotationTypes().isEmpty());
        assertFalse(interceptorCreator.isWhiteListed(Named.class.getName()));
        assertFalse(interceptorCreator.isWhiteListed(InterceptorBasedAnno.class.getName()));
    }

    @Test
    public void resolverByReflection() {
        AnnotationTypeNameResolver resolver = DefaultInterceptorCreator.createResolverFromReflection();
        assertNotNull(resolver);
        assertEquals("[DefaultAnnotationAndValue(typeName=io.helidon.pico.spi.InterceptedTrigger, values={}), "
                        + "DefaultAnnotationAndValue(typeName=java.lang.annotation.Retention, values={value=java.lang"
                        + ".annotation.RetentionPolicy.RUNTIME})]",
                     resolver.resolve(InterceptorBasedAnno.class.getName()).toString());
        assertEquals("[DefaultAnnotationAndValue(typeName=java.lang.annotation.Documented, values={}), "
                        + "DefaultAnnotationAndValue(typeName=java.lang.annotation.Retention, "
                        + "values={value=java.lang.annotation.RetentionPolicy.RUNTIME}), "
                        + "DefaultAnnotationAndValue(typeName=java.lang.annotation.Target, values={value={java"
                        + ".lang.annotation.ElementType.ANNOTATION_TYPE}})]",
                     resolver.resolve(InterceptedTrigger.class.getName()).toString());
        assertEquals("[DefaultAnnotationAndValue(typeName=jakarta.inject.Qualifier, values={}), DefaultAnnotationAndValue"
                        + "(typeName=java.lang.annotation.Documented, values={}), DefaultAnnotationAndValue"
                        + "(typeName=java.lang.annotation.Retention, values={value=java.lang.annotation"
                        + ".RetentionPolicy.RUNTIME})]",
                     resolver.resolve(Named.class.getName()).toString());
        assertEquals("[DefaultAnnotationAndValue(typeName=java.lang.annotation.Documented, values={}), "
                        + "DefaultAnnotationAndValue(typeName=java.lang.annotation.Retention, "
                        + "values={value=java.lang.annotation.RetentionPolicy.RUNTIME}), "
                        + "DefaultAnnotationAndValue(typeName=java.lang.annotation.Target, values={value={java"
                        + ".lang.annotation.ElementType.ANNOTATION_TYPE}})]",
                     resolver.resolve(Qualifier.class.getName()).toString());
        assertEquals("[DefaultAnnotationAndValue(typeName=jakarta.inject.Scope, values={}), DefaultAnnotationAndValue"
                        + "(typeName=java.lang.annotation.Documented, values={}), DefaultAnnotationAndValue"
                        + "(typeName=java.lang.annotation.Retention, values={value=java.lang.annotation"
                        + ".RetentionPolicy.RUNTIME})]",
                     resolver.resolve(Singleton.class.getName()).toString());
        assertEquals("[DefaultAnnotationAndValue(typeName=java.lang.annotation.Documented, values={}), "
                        + "DefaultAnnotationAndValue(typeName=java.lang.annotation.Retention, "
                        + "values={value=java.lang.annotation.RetentionPolicy.RUNTIME}), "
                        + "DefaultAnnotationAndValue(typeName=java.lang.annotation.Target, values={value={java"
                        + ".lang.annotation.ElementType.ANNOTATION_TYPE}})]",
                     resolver.resolve(Scope.class.getName()).toString());
        assertEquals("[DefaultAnnotationAndValue(typeName=java.lang.annotation.Retention, values={value=java.lang"
                        + ".annotation.RetentionPolicy.SOURCE}), DefaultAnnotationAndValue(typeName=java.lang"
                        + ".annotation.Target, values={value={java.lang.annotation.ElementType.METHOD}})]",
                resolver.resolve(Override.class.getName()).toString());
    }

    @Test
    public void triggerFilterExplicit() {
        AnnotationTypeNameResolver resolver = mockResolver();
        InterceptorCreator creator = mock(InterceptorCreator.class);
        when(creator.getStrategy())
                .thenReturn(InterceptorCreator.Strategy.EXPLICIT);
        when(creator.getWhiteListedAnnotationTypes())
                .thenReturn(Collections.singleton(Named.class.getName()));
        when(creator.isWhiteListed(Named.class.getName()))
                .thenReturn(true);

        TriggerFilter filter = createTriggerFilter(creator, resolver);
        assertThat(filter.isQualifyingTrigger(InterceptorBasedAnno.class.getName()),
                   is(true));
        assertThat(filter.isQualifyingTrigger(InterceptedTrigger.class.getName()),
                   is(true));
        assertThat(filter.isQualifyingTrigger(Named.class.getName()),
                   is(false));
        assertThat(filter.isQualifyingTrigger(Qualifier.class.getName()),
                   is(false));
        assertThat(filter.isQualifyingTrigger(Singleton.class.getName()),
                   is(false));
        assertThat(filter.isQualifyingTrigger(Scope.class.getName()),
                   is(false));
        assertThat(filter.isQualifyingTrigger(Override.class.getName()),
                   is(false));
    }

    @Test
    public void triggerFilterAllRuntime() {
        AnnotationTypeNameResolver resolver = mockResolver();
        InterceptorCreator creator = mock(InterceptorCreator.class);
        when(creator.getStrategy())
                .thenReturn(InterceptorCreator.Strategy.ALL_RUNTIME);
        when(creator.getWhiteListedAnnotationTypes())
                .thenReturn(Collections.emptySet());

        TriggerFilter filter = createTriggerFilter(creator, resolver);
        assertThat(filter.isQualifyingTrigger(InterceptorBasedAnno.class.getName()),
                   is(true));
        assertThat(filter.isQualifyingTrigger(InterceptedTrigger.class.getName()),
                   is(true));
        assertThat(filter.isQualifyingTrigger(Named.class.getName()),
                   is(true));
        assertThat(filter.isQualifyingTrigger(Qualifier.class.getName()),
                   is(true));
        assertThat(filter.isQualifyingTrigger(Singleton.class.getName()),
                   is(true));
        assertThat(filter.isQualifyingTrigger(Scope.class.getName()),
                   is(true));
        assertThat(filter.isQualifyingTrigger(Override.class.getName()),
                   is(false));
    }

    @Test
    public void triggerFilterWhiteListed() {
        AnnotationTypeNameResolver resolver = mockResolver();
        InterceptorCreator creator = mock(InterceptorCreator.class);
        when(creator.getStrategy())
                .thenReturn(InterceptorCreator.Strategy.WHITE_LISTED);
        when(creator.getWhiteListedAnnotationTypes())
                .thenReturn(Collections.singleton(Named.class.getName()));

        TriggerFilter filter = createTriggerFilter(creator, resolver);
        assertThat(filter.isQualifyingTrigger(InterceptorBasedAnno.class.getName()),
                   is(false));
        assertThat(filter.isQualifyingTrigger(InterceptedTrigger.class.getName()),
                   is(false));
        assertThat(filter.isQualifyingTrigger(Named.class.getName()),
                   is(true));
        assertThat(filter.isQualifyingTrigger(Qualifier.class.getName()),
                   is(false));
        assertThat(filter.isQualifyingTrigger(Singleton.class.getName()),
                   is(false));
        assertThat(filter.isQualifyingTrigger(Scope.class.getName()),
                   is(false));
        assertThat(filter.isQualifyingTrigger(Override.class.getName()),
                   is(false));
    }

    @Test
    public void triggerFilterCustom() {
        AnnotationTypeNameResolver resolver = mockResolver();
        InterceptorCreator creator = mock(InterceptorCreator.class);
        when(creator.getStrategy())
                .thenReturn(InterceptorCreator.Strategy.CUSTOM);
        when(creator.getWhiteListedAnnotationTypes())
                .thenReturn(Collections.singleton(Named.class.getName()));
        when(creator.isWhiteListed(Named.class.getName()))
                .thenReturn(true);

        TriggerFilter filter = createTriggerFilter(creator, resolver);
        assertThat(filter.isQualifyingTrigger(InterceptorBasedAnno.class.getName()),
                   is(false));
        assertThat(filter.isQualifyingTrigger(InterceptedTrigger.class.getName()),
                   is(false));
        assertThat(filter.isQualifyingTrigger(Named.class.getName()),
                   is(true));
        assertThat(filter.isQualifyingTrigger(Qualifier.class.getName()),
                   is(false));
        assertThat(filter.isQualifyingTrigger(Singleton.class.getName()),
                   is(false));
        assertThat(filter.isQualifyingTrigger(Scope.class.getName()),
                   is(false));
        assertThat(filter.isQualifyingTrigger(Override.class.getName()),
                   is(false));
    }

    @Test
    public void triggerFilterNone() {
        AnnotationTypeNameResolver resolver = mockResolver();
        InterceptorCreator creator = mock(InterceptorCreator.class);
        when(creator.getStrategy())
                .thenReturn(InterceptorCreator.Strategy.NONE);
        when(creator.getWhiteListedAnnotationTypes())
                .thenReturn(Collections.singleton(Named.class.getName()));
        when(creator.isWhiteListed(Named.class.getName()))
                .thenReturn(true);

        TriggerFilter filter = createTriggerFilter(creator, resolver);
        assertThat(filter.isQualifyingTrigger(InterceptorBasedAnno.class.getName()),
                   is(false));
        assertThat(filter.isQualifyingTrigger(InterceptedTrigger.class.getName()),
                   is(false));
        assertThat(filter.isQualifyingTrigger(Named.class.getName()),
                   is(false));
        assertThat(filter.isQualifyingTrigger(Qualifier.class.getName()),
                   is(false));
        assertThat(filter.isQualifyingTrigger(Singleton.class.getName()),
                   is(false));
        assertThat(filter.isQualifyingTrigger(Scope.class.getName()),
                   is(false));
        assertThat(filter.isQualifyingTrigger(Override.class.getName()),
                   is(false));
    }

    @Test
    public void triggerFilterBlended() {
        AnnotationTypeNameResolver resolver = mockResolver();
        InterceptorCreator creator = mock(InterceptorCreator.class);
        when(creator.getStrategy())
                .thenReturn(InterceptorCreator.Strategy.BLENDED);
        when(creator.getWhiteListedAnnotationTypes())
                .thenReturn(Collections.singleton(Named.class.getName()));
        when(creator.isWhiteListed(Named.class.getName()))
                .thenReturn(true);

        TriggerFilter filter = createTriggerFilter(creator, resolver);
        assertThat(filter.isQualifyingTrigger(InterceptorBasedAnno.class.getName()),
                   is(true));
        assertThat(filter.isQualifyingTrigger(InterceptedTrigger.class.getName()),
                   is(true));
        assertThat(filter.isQualifyingTrigger(Named.class.getName()),
                   is(true));
        assertThat(filter.isQualifyingTrigger(Qualifier.class.getName()),
                   is(false));
        assertThat(filter.isQualifyingTrigger(Singleton.class.getName()),
                   is(false));
        assertThat(filter.isQualifyingTrigger(Scope.class.getName()),
                   is(false));
        assertThat(filter.isQualifyingTrigger(Override.class.getName()),
                   is(false));
    }

    @Test
    public void allAnnotationsByReflection() {
        DefaultInterceptorCreator defaultInterceptorCreator = (DefaultInterceptorCreator) interceptorCreator;
        ServiceInfoBasics interceptedService = interceptedServiceInfo(X.class, null);
        InterceptorProcessor processor = DefaultInterceptorCreator
                .createInterceptorProcessor(interceptedService, defaultInterceptorCreator, null);
        Set<String> allAnnotations = processor.getAllAnnotations();
        assertEquals("[jakarta.inject.Named, jakarta.inject.Qualifier, jakarta.inject.Scope, jakarta.inject.Singleton, "
                        + "jakarta.inject.Inject, io.helidon.pico.spi.InterceptedTrigger, io.helidon.pico"
                        + ".testsubjects.interceptor.InterceptorBasedAnno]",
                     allAnnotations.toString());
    }

    @Test
    public void allAnnotationTypeTriggers() {
        DefaultInterceptorCreator defaultInterceptorCreator = (DefaultInterceptorCreator) interceptorCreator;
        defaultInterceptorCreator.setWhiteListed(Collections.singleton(Named.class.getName()));
        ServiceInfoBasics interceptedService = interceptedServiceInfo(X.class, null);
        InterceptorProcessor processor = DefaultInterceptorCreator
                .createInterceptorProcessor(interceptedService, defaultInterceptorCreator, null);
        Set<String> allAnnotationTypeTriggers = processor.getAllAnnotationTypeTriggers();
        assertEquals("[io.helidon.pico.testsubjects.interceptor.InterceptorBasedAnno, jakarta.inject.Named]",
                     allAnnotationTypeTriggers.toString());
    }

    @Test
    public void planCreation_withoutNamed() {
        ServiceInfoBasics interceptedService = interceptedServiceInfo(X.class, null);
        InterceptorProcessor processor = DefaultInterceptorCreator
                .createInterceptorProcessor(interceptedService, interceptorCreator, null);
        Set<String> allAnnotationTypeTriggers = processor.getAllAnnotationTypeTriggers();
        InterceptionPlan plan = interceptorCreator
                .createInterceptorPlan(interceptedService, null, allAnnotationTypeTriggers);
        assertEquals(CommonUtils.loadStringFromResource("expected/ximpl-interceptor-plan-without-named.json")
                             .trim(), JsonUtils.prettyPrintJson(plan));
    }

    @Test
    public void planCreation_withNamed() {
        DefaultInterceptorCreator defaultInterceptorCreator = (DefaultInterceptorCreator) interceptorCreator;
        defaultInterceptorCreator.setWhiteListed(Collections.singleton(Named.class.getName()));
        ServiceInfoBasics interceptedService = interceptedServiceInfo(X.class, null);
        InterceptorProcessor processor = DefaultInterceptorCreator
                .createInterceptorProcessorFromReflection(interceptedService, defaultInterceptorCreator);
        Set<String> allAnnotationTypeTriggers = processor.getAllAnnotationTypeTriggers();
        InterceptionPlan plan = interceptorCreator
                .createInterceptorPlan(interceptedService, null, allAnnotationTypeTriggers);
        assertEquals(CommonUtils.loadStringFromResource("expected/ximpl-interceptor-plan-with-named.json")
                             .trim(), JsonUtils.prettyPrintJson(plan));
    }

    @Test
    public void createInterceptorSource() {
        DefaultInterceptorCreator defaultInterceptorCreator = (DefaultInterceptorCreator) interceptorCreator;
        defaultInterceptorCreator.setWhiteListed(Collections.singleton(Named.class.getName()));
        ServiceInfoBasics interceptedService = interceptedServiceInfo(X.class, null);
        InterceptorProcessor processor = DefaultInterceptorCreator
                .createInterceptorProcessorFromReflection(interceptedService, defaultInterceptorCreator);
        Set<String> allAnnotationTypeTriggers = processor.getAllAnnotationTypeTriggers();
        InterceptionPlan plan = interceptorCreator
                .createInterceptorPlan(interceptedService, null, allAnnotationTypeTriggers);
        assertEquals("io.helidon.pico.testsubjects.interceptor.X$$picoInterceptor",
                     DefaultInterceptorCreator.createInterceptorSourceTypeName(plan).name());
        assertNotNull(plan.getInterceptedService());
        assertNotNull(plan.getInterceptedService().serviceTypeName());
        assertNotNull(plan.getAnnotationTriggerTypeNames());
        assertEquals("[io.helidon.pico.testsubjects.interceptor.InterceptorBasedAnno, jakarta.inject.Named]",
                     plan.getAnnotationTriggerTypeNames().toString());
        String java = DefaultInterceptorCreator.createInterceptorSourceBody(plan);
        assertEquals(CommonUtils.loadStringFromResource("expected/ximpl-interceptor-with-names.java_")
                             .trim(), java);
    }

    @Test
    public void defaultStrategy() {
        DefaultInterceptorCreator defaultInterceptorCreator = (DefaultInterceptorCreator) interceptorCreator;
        assertEquals(InterceptorCreator.Strategy.BLENDED, defaultInterceptorCreator.getStrategy());
    }

    static AnnotationTypeNameResolver mockResolver() {
        AnnotationTypeNameResolver resolver = mock(AnnotationTypeNameResolver.class);
        when(resolver.resolve(InterceptorBasedAnno.class.getName()))
                .thenReturn(TypeTools.createAnnotationAndValueListFromAnnotations(InterceptorBasedAnno.class.getAnnotations()));
        when(resolver.resolve(InterceptedTrigger.class.getName()))
                .thenReturn(TypeTools.createAnnotationAndValueListFromAnnotations(InterceptedTrigger.class.getAnnotations()));
        when(resolver.resolve(Named.class.getName()))
                .thenReturn(TypeTools.createAnnotationAndValueListFromAnnotations(Named.class.getAnnotations()));
        when(resolver.resolve(Qualifier.class.getName()))
                .thenReturn(TypeTools.createAnnotationAndValueListFromAnnotations(Qualifier.class.getAnnotations()));
        when(resolver.resolve(Scope.class.getName()))
                .thenReturn(TypeTools.createAnnotationAndValueListFromAnnotations(Scope.class.getAnnotations()));
        when(resolver.resolve(Singleton.class.getName()))
                .thenReturn(TypeTools.createAnnotationAndValueListFromAnnotations(Scope.class.getAnnotations()));
        when(resolver.resolve(Override.class.getName()))
                .thenReturn(TypeTools.createAnnotationAndValueListFromAnnotations(Override.class.getAnnotations()));
        return resolver;
    }

    static ServiceInfoBasics interceptedServiceInfo(Class<?> type, Double weight) {
        return DefaultServiceInfo.builder()
                .serviceTypeName(type.getName())
                .weight(weight)
                .build();
    }

}
