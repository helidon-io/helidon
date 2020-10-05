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
import java.util.EventListener;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Priority;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.BeforeDestroyed;
import javax.enterprise.context.Initialized;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.BeforeBeanDiscovery;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.ProcessAnnotatedType;
import javax.enterprise.inject.spi.configurator.AnnotatedMethodConfigurator;

import io.micronaut.aop.Around;
import io.micronaut.aop.Introduced;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Type;
import io.micronaut.context.env.PropertySource;
import io.micronaut.core.io.service.ServiceDefinition;
import io.micronaut.core.io.service.SoftServiceLoader;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanDefinitionReference;
import io.micronaut.inject.ExecutableMethod;
import org.eclipse.microprofile.config.Config;

import static javax.interceptor.Interceptor.Priority.PLATFORM_AFTER;
import static javax.interceptor.Interceptor.Priority.PLATFORM_BEFORE;

public class MicronautCdiExtension implements Extension {
    private final AtomicReference<ApplicationContext> micronautContext = new AtomicReference<>();
    private final Map<Method, MethodInterceptorMetadata> methods = new HashMap<>();
    @SuppressWarnings("rawtypes")
    private final List<BeanDefinitionReference<?>> beanDefinitions = new LinkedList<>();
    private final Map<Class<?>, List<BeanDefinitionReference<?>>> mBeanToDefRef = new HashMap<>();
    private final Map<BeanDefinitionReference<?>, Class<?>> defRefToBean = new HashMap<>();
    private final Set<Class<?>> micronautBeans = new HashSet<>();

    public List<BeanDefinitionReference<?>> beanDefinitions() {
        return beanDefinitions;
    }

    public ApplicationContext context() {
        return micronautContext.get();
    }

    public Class<?> beanClass(BeanDefinitionReference<?> defRef) {
        return defRefToBean.get(defRef);
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
        // TODO verify that this type does not have micronaut bean. if it does, combine annotations from both
        Set<Class<?>> classInterceptors = new HashSet<>();
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
                    Set<Class<?>> methodInterceptors = new HashSet<>(classInterceptors);
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
                        Method javaMethod = method.getAnnotated().getJavaMember();

                        Set<Class<? extends MethodInterceptor<?, ?>>> interceptors = new HashSet<>();
                        methodInterceptors.forEach(it -> interceptors.add((Class<? extends MethodInterceptor<?, ?>>) it));

                        methods.computeIfAbsent(javaMethod, MethodInterceptorMetadata::create)
                                .addInterceptors(interceptors);
                    }
                });
    }

    void beforeBeanDiscovery(@Priority(PLATFORM_BEFORE) @Observes BeforeBeanDiscovery event) {
        event.addAnnotatedType(MicronautInterceptor.class, "mcdi-MicronautInterceptor");
        event.addInterceptorBinding(MicronautIntercepted.class);
    }

    void afterBeanDiscovery(@Priority(PLATFORM_BEFORE) @Observes AfterBeanDiscovery event) {
        event.addBean()
                .addType(ApplicationContext.class)
                .id("micronaut-context")
                .scope(ApplicationScoped.class)
                .produceWith(instance -> micronautContext.get());

        loadMicronautBeanDefinitions();
        for (BeanDefinitionReference<?> defRef : beanDefinitions) {
            Class<?> beanType = defRef.getBeanType();
            if (!event.getAnnotatedTypes(beanType).iterator().hasNext()) {
                micronautBeans.add(beanType);
                event.addBean()
                        .addType(beanType)
                        .id("micronaut-" + beanType.getName())
                        .scope(ApplicationScoped.class)
                        .produceWith(instance -> micronautContext.get().getBean(beanType));
            }
        }
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
                // we only care for singletons
                .filter(BeanDefinitionReference::isSingleton)
                .forEach(beanDefinitions::add);

        for (BeanDefinitionReference<?> defRef : beanDefinitions) {
            Class<?> beanType = defRef.getBeanType();

            // if the bean definition is an $Intercepted, we need to find the actual bean class
            if (defRef.getName().endsWith("$Intercepted")) {
                // bean is either an interface or an abstract class, we need to find the original class (not a generated one)
                if (Object.class.equals(beanType.getSuperclass())) {
                    // we have an interface
                    Class<?>[] interfaces = beanType.getInterfaces();
                    // by design, the first interface should be our repo, but let's be nice
                    for (Class<?> anInterface : interfaces) {
                        if (Introduced.class.equals(anInterface)) {
                            continue;
                        }
                        if (EventListener.class.isAssignableFrom(anInterface)) {
                            break;
                        }
                        beanType = anInterface;
                        break;
                    }
                } else {
                    // we have an abstract class
                    beanType = beanType.getSuperclass();
                }
            }

            mBeanToDefRef.computeIfAbsent(beanType, it -> new LinkedList<>())
                    .add(defRef);
            defRefToBean.put(defRef, beanType);
        }
    }

    public MethodInterceptorMetadata getInterceptionMetadata(Method javaMethod) {
        for (BeanDefinitionReference beanDefinition : beanDefinitions) {
            BeanDefinition beanDef = beanDefinition.load();
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
