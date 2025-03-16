/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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
package io.helidon.microprofile.testing.mocking;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.literal.InjectLiteral;
import jakarta.enterprise.inject.literal.NamedLiteral;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.enterprise.inject.spi.AfterDeploymentValidation;
import jakarta.enterprise.inject.spi.AnnotatedParameter;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.ProcessAnnotatedType;
import jakarta.enterprise.inject.spi.WithAnnotations;
import jakarta.enterprise.inject.spi.configurator.AnnotatedConstructorConfigurator;
import jakarta.enterprise.inject.spi.configurator.AnnotatedFieldConfigurator;
import jakarta.enterprise.inject.spi.configurator.AnnotatedTypeConfigurator;
import org.mockito.MockSettings;
import org.mockito.Mockito;

/**
 * CDI extension that supports {@link MockBean}.
 */
public class MockBeansCdiExtension implements Extension {

    private final Map<Class<?>, MockBean> mocks = new HashMap<>();

    void processMockBean(@Observes @WithAnnotations(MockBean.class) ProcessAnnotatedType<?> obj) {
        AnnotatedTypeConfigurator<?> configurator = obj.configureAnnotatedType();
        for (AnnotatedFieldConfigurator<?> field : configurator.fields()) {
            MockBean mockBean = field.getAnnotated().getAnnotation(MockBean.class);
            if (mockBean != null) {
                Field f = field.getAnnotated().getJavaMember();
                // make @Inject optional
                field.add(InjectLiteral.INSTANCE);
                mocks.put(f.getType(), mockBean);
            }
        }
        for (AnnotatedConstructorConfigurator<?> ctor : configurator.constructors()) {
            for (AnnotatedParameter<?> parameter : ctor.getAnnotated().getParameters()) {
                MockBean mockBean = parameter.getAnnotation(MockBean.class);
                if (mockBean != null) {
                    Class<?> parameterType = parameter.getJavaParameter().getType();
                    mocks.put(parameterType, mockBean);
                }
            };
        }
    }

    void registerOtherBeans(@Observes AfterBeanDiscovery event) {
        // register mocks
        mocks.forEach((key, value) -> event.addBean()
                .addTransitiveTypeClosure(key)
                .scope(ApplicationScoped.class)
                .alternative(true)
                .produceWith(i -> {
                    Instance<MockSettings> msi = i.select(MockSettings.class);
                    MockSettings settings = msi.isUnsatisfied()
                            ? Mockito.withSettings().defaultAnswer(value.answer())
                            : msi.get();
                    return Mockito.mock(key, settings);
                })
                .priority(0));
    }

    void initializeBeans(@Observes AfterDeploymentValidation event, BeanManager manager) {
        for (Class<?> key : mocks.keySet()) {
            for (Bean<?> bean : manager.getBeans(key)) {
                // call toString() to force the beans to be initialized
                // noinspection ResultOfMethodCallIgnored
                manager.getReference(bean, key, manager.createCreationalContext(bean)).toString();
            }
        }
    }
}
