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

package io.helidon.integrations.micronaut.cdi;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Priority;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.BeforeDestroyed;
import javax.enterprise.context.Dependent;
import javax.enterprise.context.Initialized;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.BeforeBeanDiscovery;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.ProcessAnnotatedType;
import javax.enterprise.inject.spi.configurator.AnnotatedMethodConfigurator;
import javax.enterprise.inject.spi.configurator.BeanConfigurator;
import javax.inject.Qualifier;

import io.micronaut.aop.Around;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Type;
import io.micronaut.context.env.PropertySource;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.io.service.ServiceDefinition;
import io.micronaut.core.io.service.SoftServiceLoader;
import io.micronaut.inject.AdvisedBeanType;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanDefinitionReference;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.eclipse.microprofile.config.Config;

import static javax.interceptor.Interceptor.Priority.PLATFORM_AFTER;
import static javax.interceptor.Interceptor.Priority.PLATFORM_BEFORE;

public class MicronautCdiExtension implements Extension {
    private final AtomicReference<ApplicationContext> micronautContext = new AtomicReference<>();
    private final Map<Method, MethodInterceptorMetadata> methods = new HashMap<>();
    // all bean definitions as seen by Micronaut
    private final List<MicronautBean> beanDefinitions = new LinkedList<>();
    // map of an actual class (user's source code) mapping to Micronaut bean definition
    private final Map<Class<?>, List<MicronautBean>> mBeanToDefRef = new HashMap<>();
    // Micronaut beans not yet processed by CDI
    private final Map<Class<?>, List<MicronautBean>> unprocessedBeans = new HashMap<>();

    public List<MicronautBean> beanDefinitions() {
        return beanDefinitions;
    }

    public ApplicationContext context() {
        ApplicationContext ctx = micronautContext.get();
        if (ctx == null) {
            throw new IllegalStateException(
                    "Micronaut application context can only be obtained after the ApplicationScoped is initialized");
        }
        return ctx;
    }

    public void addInterceptor(AnnotatedMethodConfigurator<?> method,
                               Set<Class<? extends MethodInterceptor<?, ?>>> interceptors,
                               Map<Class<? extends Annotation>, Map<CharSequence, Object>> annotationsToAdd) {
        method.add(MicronautIntercepted.Literal.INSTANCE);
        Method javaMethod = method.getAnnotated().getJavaMember();
        methods.computeIfAbsent(javaMethod, it -> MethodInterceptorMetadata.create(javaMethod, annotationsToAdd))
                .addInterceptors(interceptors);
    }

    @SuppressWarnings("unchecked")
    void processTypes(@Priority(PLATFORM_AFTER) @Observes ProcessAnnotatedType<?> event) {
        Set<Class<?>> classInterceptors = new HashSet<>();
        Map<Method, Set<Class<?>>> allMethodInterceptors = new HashMap<>();

        List<MicronautBean> mBeans = unprocessedBeans.remove(event.getAnnotatedType().getJavaClass());

        if (mBeans != null && mBeans.size() > 0) {
            BeanDefinitionReference<?> found = null;

            for (MicronautBean mBean : mBeans) {
                BeanDefinitionReference<?> ref = mBean.definitionRef();
                if (ref instanceof AdvisedBeanType) {
                    continue;
                }
                found = ref;
                break;
            }
            if (found == null) {
                // just use the first one
                found = mBeans.get(0).definitionRef();
            }

            // find all annotations with Around stereotype and find its Type annotation to add interceptors
            found.getAnnotationMetadata()
                    .getAnnotationTypesByStereotype(Around.class)
                    .stream()
                    .map(it -> it.getAnnotation(Type.class))
                    .filter(Objects::nonNull)
                    .map(Type::value)
                    .flatMap(Stream::of)
                    .forEach(classInterceptors::add);

            BeanDefinition<?> beanDef = found.load();

            Collection<? extends ExecutableMethod<?, ?>> executableMethods = beanDef.getExecutableMethods();
            for (ExecutableMethod<?, ?> executableMethod : executableMethods) {

                Set<Class<?>> methodInterceptors = new HashSet<>();

                executableMethod
                        .getAnnotationTypesByStereotype(Around.class)
                        .stream()
                        .map(it -> it.getAnnotation(Type.class))
                        .filter(Objects::nonNull)
                        .map(Type::value)
                        .flatMap(Stream::of)
                        .forEach(methodInterceptors::add);

                allMethodInterceptors.computeIfAbsent(executableMethod.getTargetMethod(), it -> new HashSet<>())
                        .addAll(methodInterceptors);
            }
        }

        event.getAnnotatedType()
                .getAnnotations()
                .forEach(annot -> {
                    if (annot.annotationType().getAnnotation(Around.class) != null) {
                        Set.of(annot.annotationType().getAnnotation(Type.class)
                                       .value())
                                .forEach(classInterceptors::add);
                    }
                });

        event.configureAnnotatedType().methods()
                .forEach(method -> {
                    Method javaMethod = method.getAnnotated().getJavaMember();

                    Set<Class<?>> methodInterceptors = allMethodInterceptors.computeIfAbsent(javaMethod, it -> new HashSet<>());
                    methodInterceptors.addAll(classInterceptors);

                    method.getAnnotated()
                            .getAnnotations()
                            .forEach(annot -> {
                                if (annot.annotationType().getAnnotation(Around.class) != null) {
                                    Set.of(annot.annotationType().getAnnotation(Type.class)
                                                   .value())
                                            .forEach(methodInterceptors::add);
                                }
                            });

                    if (!methodInterceptors.isEmpty()) {
                        // now I have a set of micronaut interceptors that are needed for this method
                        method.add(MicronautIntercepted.Literal.INSTANCE);

                        Set<Class<? extends MethodInterceptor<?, ?>>> interceptors = new HashSet<>();
                        methodInterceptors.forEach(it -> interceptors.add((Class<? extends MethodInterceptor<?, ?>>) it));

                        methods.computeIfAbsent(javaMethod, MethodInterceptorMetadata::create)
                                .addInterceptors(interceptors);
                    }
                });
    }

    void beforeBeanDiscovery(@Priority(PLATFORM_BEFORE) @Observes BeforeBeanDiscovery event) {
        loadMicronautBeanDefinitions();

        event.addAnnotatedType(MicronautInterceptor.class, "mcdi-MicronautInterceptor");
        event.addInterceptorBinding(MicronautIntercepted.class);
    }

    void afterBeanDiscovery(@Priority(PLATFORM_BEFORE) @Observes AfterBeanDiscovery event) {
        event.addBean()
                .addType(ApplicationContext.class)
                .id("micronaut-context")
                .scope(ApplicationScoped.class)
                .produceWith(instance -> micronautContext.get());

        // add the remaining Micronaut beans
        for (var entry : unprocessedBeans.entrySet()) {
            Class<?> beanType = entry.getKey();
            List<MicronautBean> beans = entry.getValue();

            // first make sure these are singletons; if not, ignore
            List<? extends BeanDefinitionReference<?>> refs = beans.stream()
                    .map(MicronautBean::definitionRef)
                    .collect(Collectors.toList());

            if (refs.isEmpty()) {
                // no beans to add
                return;
            }

            // primary
            event.addBean()
                    .addType(beanType)
                    .id("micronaut-" + beanType.getName())
                    // inject using dependent - manage scope by micronaut context
                    .scope(Dependent.class)
                    .produceWith(instance -> micronautContext.get().getBean(beanType));

            if (refs.size() > 1) {
                // we must care about qualifiers
                for (var ref : refs) {
                    AnnotationMetadata annotationMetadata = ref.getAnnotationMetadata();
                    List<Class<? extends Annotation>> qualifiers = annotationMetadata
                            .getAnnotationTypesByStereotype(Qualifier.class);

                    Annotation[] synthesized = new Annotation[qualifiers.size()];
                    for (int i = 0; i < qualifiers.size(); i++) {
                        synthesized[i] = annotationMetadata.synthesize(qualifiers.get(i));
                    }

                    io.micronaut.context.Qualifier[] mq = new io.micronaut.context.Qualifier[qualifiers.size()];

                    for (int i = 0; i < qualifiers.size(); i++) {
                        mq[i] = Qualifiers.byAnnotation(synthesized[i]);
                    }

                    io.micronaut.context.Qualifier composite = Qualifiers.byQualifiers(mq);

                    BeanConfigurator<Object> newBean = event.addBean()
                            .addType(beanType)
                            .id("micronaut-" + ref.getBeanDefinitionName())
                            .scope(Dependent.class)
                            .produceWith(instance -> micronautContext.get().getBean(beanType, composite));

                    for (Annotation annotation : synthesized) {
                        newBean.addQualifier(annotation);
                    }

                }
            }
        }
        unprocessedBeans.clear();
    }

    void startContext(@Observes @Priority(PLATFORM_BEFORE) @Initialized(ApplicationScoped.class) Object adv) {
        ApplicationContext context = ApplicationContext.builder()
                .propertySources(createMicronautPropertySource())
                .build();

        context.start();

        micronautContext.set(context);
    }

    void stopContext(@Observes @Priority(PLATFORM_AFTER) @BeforeDestroyed(ApplicationScoped.class) Object adv) {
        ApplicationContext context = micronautContext.get();
        // if startup failed, context is null
        if (context != null) {
            context.close();
        }
    }

    private PropertySource createMicronautPropertySource() {
        Config config = org.eclipse.microprofile.config.ConfigProvider.getConfig();

        return new PropertySource() {
            @Override
            public String getName() {
                return "MicroProfile-Config";
            }

            @Override
            public Object get(String key) {
                return config.getOptionalValue(key, String.class).orElse(null);
            }

            @Override
            public Iterator<String> iterator() {
                List<String> names = new LinkedList<>();
                config.getPropertyNames()
                        .forEach(names::add);

                return names.iterator();
            }
        };
    }

    @SuppressWarnings("rawtypes")
    private void loadMicronautBeanDefinitions() {
        // we now need to load all Micronaut beans so other extensions can inject them
        List<ServiceDefinition<BeanDefinitionReference>> list = new ArrayList<>(200);

        SoftServiceLoader.load(BeanDefinitionReference.class)
                .forEach(list::add);

        list.stream()
                .filter(ServiceDefinition::isPresent)
                .map(ServiceDefinition::load)
                .filter(BeanDefinitionReference::isPresent)
                .map(ref -> {
                    Class<?> beanType = ref.getBeanType();

                    if (ref instanceof AdvisedBeanType) {
                        beanType = ((AdvisedBeanType) ref).getInterceptedType();
                    }
                    return new MicronautBean(beanType, ref);
                })
                .forEach(beanDefinitions::add);

        for (MicronautBean defRef : beanDefinitions) {
            mBeanToDefRef.computeIfAbsent(defRef.beanType(), it -> new LinkedList<>())
                    .add(defRef);
        }

        unprocessedBeans.putAll(mBeanToDefRef);
    }

    public MethodInterceptorMetadata getInterceptionMetadata(Method javaMethod) {
        for (MicronautBean mBean : beanDefinitions) {
            BeanDefinition beanDef = mBean.definitionRef().load();
            if (beanDef.getBeanType().equals(javaMethod.getDeclaringClass())) {
                Collection<ExecutableMethod<?, ?>> executableMethods = beanDef.getExecutableMethods();
                for (ExecutableMethod<?, ?> method : executableMethods) {
                    if (method.getMethodName().equals(javaMethod.getName())) {
                        if (method.getTargetMethod().equals(javaMethod)) {
                            MethodInterceptorMetadata methodMeta = methods.get(javaMethod);
                            methodMeta.executableMethod(method);
                            return methodMeta;
                        }
                    }
                }
            }
        }
        return methods.get(javaMethod);
    }
}
