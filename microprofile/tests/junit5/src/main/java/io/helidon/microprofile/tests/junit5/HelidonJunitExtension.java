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

package io.helidon.microprofile.tests.junit5;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.RequestScoped;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.se.SeContainer;
import javax.enterprise.inject.se.SeContainerInitializer;
import javax.enterprise.inject.spi.BeforeBeanDiscovery;
import javax.enterprise.inject.spi.Extension;
import javax.inject.Singleton;

import io.helidon.config.mp.MpConfigSources;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;

/**
 * Junit5 extension to support Helidon CDI container in tests.
 */
class HelidonJunitExtension implements BeforeAllCallback, AfterAllCallback, InvocationInterceptor {
    private Config config;
    private ConfigProviderResolver instance;
    private SeContainer container;

    @SuppressWarnings("unchecked")
    @Override
    public void beforeAll(ExtensionContext context) {
        Class<?> testClass = context.getRequiredTestClass();

        // prepare configuration
        Map<String, String> additionalConfig = new HashMap<>();
        additionalConfig.put("mp.initializer.allow", "true");
        additionalConfig.put("mp.initializer.no-warn", "true");

        AddConfig[] configAnnotations = testClass.getAnnotationsByType(AddConfig.class);

        for (AddConfig configAnnotation : configAnnotations) {
            additionalConfig.put(configAnnotation.key(), configAnnotation.value());
        }

        instance = ConfigProviderResolver.instance();
        config = instance.getBuilder()
                .withSources(MpConfigSources.create(additionalConfig))
                .addDefaultSources()
                .addDiscoveredSources()
                .addDiscoveredConverters()
                .build();
        instance.registerConfig(config, Thread.currentThread().getContextClassLoader());

        // now let's prepare the CDI bootstrapping
        SeContainerInitializer initializer = SeContainerInitializer.newInstance();

        HelidonTest testAnnot = testClass.getAnnotation(HelidonTest.class);
        if (testAnnot != null) {
            if (!testAnnot.discovery()) {
                initializer.disableDiscovery();
            }
        }

        AddBean[] addBeans = testClass.getAnnotationsByType(AddBean.class);
        initializer.addExtensions(new AddBeansExtension(testClass, addBeans));

        AddExtension[] addExtensions = testClass.getAnnotationsByType(AddExtension.class);
        for (AddExtension addExtension : addExtensions) {
            Class<? extends Extension> extensionClass = addExtension.value();
            if (Modifier.isPublic(extensionClass.getModifiers())) {
                initializer.addExtensions(addExtension.value());
            } else {
                throw new IllegalArgumentException("Extension classes must be public, but " + extensionClass
                        .getName() + " is not");
            }
        }

        container = initializer.initialize();
    }

    @Override
    public void afterAll(ExtensionContext context) {
        if (container != null) {
            container.close();
        }
        if (instance != null && config != null) {
            instance.releaseConfig(config);
        }
    }

    @Override
    public <T> T interceptTestClassConstructor(Invocation<T> invocation,
                                               ReflectiveInvocationContext<Constructor<T>> invocationContext,
                                               ExtensionContext extensionContext) {

        // we need to replace instantiation with CDI lookup, to properly injection into fields (and constructors)
        invocation.skip();

        return container.select(invocationContext.getExecutable().getDeclaringClass())
                .get();
    }

    // this is not registered as a bean - we manually register an instance
    @SuppressWarnings("CdiManagedBeanInconsistencyInspection")
    private static class AddBeansExtension implements Extension {
        private final Class<?> testClass;
        private final AddBean[] addBeans;

        private AddBeansExtension(Class<?> testClass, AddBean[] addBeans) {
            this.testClass = testClass;
            this.addBeans = addBeans;
        }

        void registerAddedBeans(@Observes BeforeBeanDiscovery event) {
            event.addAnnotatedType(testClass, "junit-" + testClass.getName())
                    .add(ApplicationScoped.Literal.INSTANCE);

            for (AddBean addBean : addBeans) {
                Annotation scope;
                Class<? extends Annotation> definedScope = addBean.scope();

                if (definedScope.equals(ApplicationScoped.class) || definedScope.equals(Singleton.class)) {
                    scope = ApplicationScoped.Literal.INSTANCE;
                } else if (definedScope.equals(RequestScoped.class)) {
                    scope = RequestScoped.Literal.INSTANCE;
                } else {
                    throw new IllegalStateException(
                            "Only Singleton, ApplicationScoped and RequestScoped are allowed in tests. Scope " + definedScope
                                    .getName() + " is not allowed for bean " + addBean.value().getName());
                }

                event.addAnnotatedType(addBean.value(), "junit-" + addBean.value().getName())
                        .add(scope);
            }
        }
    }
}
