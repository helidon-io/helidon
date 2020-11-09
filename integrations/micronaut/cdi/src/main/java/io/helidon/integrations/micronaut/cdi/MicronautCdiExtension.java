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
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanDefinitionReference;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.eclipse.microprofile.config.Config;

import static javax.interceptor.Interceptor.Priority.PLATFORM_AFTER;
import static javax.interceptor.Interceptor.Priority.PLATFORM_BEFORE;

/**
 * Extension integrating CDI with Micronaut.
 * This extensions adds Micronaut beans to be injectable into CDI beans (limited to {@link javax.inject.Singleton}
 * scope), adn adds support for invoking Micronaut interceptors.
 */
public class MicronautCdiExtension implements Extension {
    private final AtomicReference<ApplicationContext> micronautContext = new AtomicReference<>();
    private final Map<Method, ExecutableMethod<?, ?>> executableMethodCache = new HashMap<>();
    private final Map<Method, MethodInterceptorMetadata> methods = new HashMap<>();
    // all bean definitions as seen by Micronaut
    private List<MicronautBean> beanDefinitions = new LinkedList<>();
    // map of an actual class (user's source code) mapping to Micronaut bean definition
    private final Map<Class<?>, List<MicronautBean>> mBeanToDefRef = new HashMap<>();
    // Micronaut beans not yet processed by CDI
    private final Map<Class<?>, List<MicronautBean>> unprocessedBeans = new HashMap<>();

    /**
     * Get the application context of Micronaut.
     * This method can only be invoked once the server is started.
     *
     * @return Micronaut application context
     * @throws java.lang.IllegalStateException when invoked when server (and hence the context) is not started
     */
    public ApplicationContext context() {
        ApplicationContext ctx = micronautContext.get();
        if (ctx == null) {
            throw new IllegalStateException(
                    "Micronaut application context can only be obtained after the ApplicationScoped is initialized");
        }
        return ctx;
    }

    MethodInterceptorMetadata getInterceptionMetadata(Method javaMethod) {
        return methods.get(javaMethod);
    }

    /**
     * Load all Micronaut bean definitions and register our interceptor binding, so we can
     * execute Micronaut interceptors using a CDI interceptor.
     *
     * @param event CDI event
     */
    void beforeBeanDiscovery(@Priority(PLATFORM_BEFORE) @Observes BeforeBeanDiscovery event) {
        loadMicronautBeanDefinitions();

        event.addAnnotatedType(MicronautInterceptor.class, "mcdi-MicronautInterceptor");
        event.addInterceptorBinding(MicronautIntercepted.class);
    }

    /**
     * Construct a list of Micronaut interceptors to execute on each CDI method.
     * In case a Micronaut bean definition is available for the CDI bean (which should be for application, as
     * the CDI annotation processor should be used, and it adds CDI beans as Micronaut beans), the information
     * is combined from Micronaut and CDI bean definitions.
     *
     * @param event CDI event
     */
    @SuppressWarnings("unchecked")
    void processTypes(@Priority(PLATFORM_AFTER) @Observes ProcessAnnotatedType<?> event) {
        Set<Class<?>> classInterceptors = new HashSet<>();
        Map<Method, Set<Class<?>>> allMethodInterceptors = new HashMap<>();

        List<MicronautBean> miBeans = unprocessedBeans.remove(event.getAnnotatedType().getJavaClass());

        if (miBeans != null && miBeans.size() > 0) {
            BeanDefinitionReference<?> miBean = findMicronautBeanDefinition(miBeans);
            // add all interceptors that are seen based on Micronaut
            findMicronautInterceptors(classInterceptors, allMethodInterceptors, miBean);
        }

        // find all annotations that have meta annotation Around and collect their Type list to add as interceptors
        addMicronautInterceptors(classInterceptors, event.getAnnotatedType().getAnnotations());

        // for each method, find the same (Around, collect Type), and add the interceptor binding for Micronaut interceptors
        // CDI interceptors will be automatic
        event.configureAnnotatedType()
                .methods()
                .forEach(method -> {
                    Method javaMethod = method.getAnnotated().getJavaMember();

                    Set<Class<?>> methodInterceptors = allMethodInterceptors.computeIfAbsent(javaMethod, it -> new HashSet<>());
                    methodInterceptors.addAll(classInterceptors);

                    addMicronautInterceptors(methodInterceptors, method.getAnnotated().getAnnotations());

                    if (!methodInterceptors.isEmpty()) {
                        // now I have a set of micronaut interceptors that are needed for this method
                        method.add(MicronautIntercepted.Literal.INSTANCE);

                        Set<Class<? extends MethodInterceptor<?, ?>>> interceptors = new HashSet<>();
                        methodInterceptors.forEach(it -> interceptors.add((Class<? extends MethodInterceptor<?, ?>>) it));

                        methods.computeIfAbsent(javaMethod,
                                                theMethod -> MethodInterceptorMetadata.create(
                                                        method.getAnnotated(),
                                                        executableMethodCache.get(theMethod)))
                                .addInterceptors(interceptors);
                    }
                });
    }

    /**
     * Add all (not yet added) Micronaut beans for injection as long as they are singletons.
     *
     * @param event CDI event
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
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

            List<? extends BeanDefinitionReference<?>> refs = List.of();
            if (beans.size() > 1) {
                // first make sure these are singletons; if not, ignore
                refs = beans.stream()
                        .map(MicronautBean::definitionRef)
                        .filter(it -> !it.getBeanType().getName().endsWith("$Intercepted"))
                        .filter(BeanDefinitionReference::isSingleton)
                        .collect(Collectors.toList());
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
                    io.micronaut.context.Qualifier[] mq = new io.micronaut.context.Qualifier[qualifiers.size()];

                    for (int i = 0; i < qualifiers.size(); i++) {
                        Annotation annotation = annotationMetadata.synthesize(qualifiers.get(i));
                        synthesized[i] = annotation;
                        if (annotation != null) {
                            mq[i] = Qualifiers.byAnnotation(annotation);
                        }
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

    /**
     * Start Micronaut application context and make it available for other extensions.
     *
     * @param event CDI event
     */
    void startContext(@Observes @Priority(PLATFORM_BEFORE) @Initialized(ApplicationScoped.class) Object event) {
        ApplicationContext context = ApplicationContext.builder()
                .propertySources(createMicronautPropertySource())
                .build();

        context.start();

        micronautContext.set(context);
    }

    /**
     * Stop Micronaut application context.
     * @param event CDI event
     */
    void stopContext(@Observes @Priority(PLATFORM_AFTER) @BeforeDestroyed(ApplicationScoped.class) Object event) {
        ApplicationContext context = micronautContext.get();
        // if startup failed, context is null
        if (context != null) {
            context.close();
        }
    }

    /**
     * Use MicroProfile Config as a config source for Micronaut.
     *
     * @return Micronaut property source from MP Config
     */
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

    /**
     * Load Micronaut bean definition using service loader, to have full knowledge of the landscape.
     */
    @SuppressWarnings("rawtypes")
    private void loadMicronautBeanDefinitions() {
        // we now need to load all Micronaut beans so other extensions can inject them
        List<ServiceDefinition<BeanDefinitionReference>> list = new ArrayList<>(200);

        SoftServiceLoader.load(BeanDefinitionReference.class)
                .forEach(list::add);

        List<MicronautBean> beans = list.parallelStream()
                .filter(ServiceDefinition::isPresent)
                .map(ServiceDefinition::load)
                .filter(BeanDefinitionReference::isPresent)
                .map(ref -> {
                    Class<?> beanType = ref.getBeanType();

                    String className = ref.getBeanType().getName();
                    if (className.endsWith("$Intercepted")) {
                        // either superclass is the one we want, or first implemented interface
                        if (Object.class.equals(beanType.getSuperclass())) {
                            Class<?>[] interfaces = beanType.getInterfaces();
                            if (interfaces.length > 0) {
                                beanType = interfaces[0];
                            }
                        } else {
                            beanType = beanType.getSuperclass();
                        }
                    }

                    return new MicronautBean(beanType, ref);
                })
                .collect(Collectors.toList());

        // using my own collection, so the field is final
        beanDefinitions.addAll(beans);

        for (MicronautBean defRef : beanDefinitions) {
            mBeanToDefRef.computeIfAbsent(defRef.beanType(), it -> new LinkedList<>())
                    .add(defRef);
        }

        unprocessedBeans.putAll(mBeanToDefRef);
    }

    /**
     * Find Micronaut interceptor annotations and locate interceptor classes to be used.
     *
     * @param interceptors set of interceptors to add new interceptors to
     * @param annotations set of annotations on the processed element
     */
    private void addMicronautInterceptors(Set<Class<?>> interceptors, Set<Annotation> annotations) {
        annotations.stream()
                .map(Annotation::annotationType)
                .filter(type -> type.getAnnotation(Around.class) != null)
                .map(type -> type.getAnnotation(Type.class))
                .map(Type::value)
                .map(Set::of)
                .flatMap(Set::stream)
                .forEach(interceptors::add);
    }

    private void findMicronautInterceptors(Set<Class<?>> classInterceptors,
                                           Map<Method, Set<Class<?>>> allMethodInterceptors,
                                           BeanDefinitionReference<?> miBean) {
        // find all annotations with Around stereotype and find its Type annotation to add interceptors
        miBean.getAnnotationMetadata()
                .getAnnotationTypesByStereotype(Around.class)
                .stream()
                .map(it -> it.getAnnotation(Type.class))
                .filter(Objects::nonNull)
                .map(Type::value)
                .flatMap(Stream::of)
                .forEach(classInterceptors::add);

        BeanDefinition<?> beanDef = miBean.load();

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

            this.executableMethodCache.putIfAbsent(executableMethod.getTargetMethod(), executableMethod);

            allMethodInterceptors.computeIfAbsent(executableMethod.getTargetMethod(), it -> new HashSet<>())
                    .addAll(methodInterceptors);
        }
    }

    private BeanDefinitionReference<?> findMicronautBeanDefinition(List<MicronautBean> mBeans) {
        for (MicronautBean mBean : mBeans) {
            BeanDefinitionReference<?> ref = mBean.definitionRef();
            //            if (ref instanceof AdvisedBeanType) {
            //                continue;
            //            }
            if (ref.getBeanType().getName().endsWith("$Intercepted")) {
                continue;
            }
            return ref;
        }
        // just use the first one
        return mBeans.get(0).definitionRef();
    }
}
