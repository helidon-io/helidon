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

package io.helidon.integrations.micronaut.cdi.data;

import java.lang.annotation.Annotation;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Priority;
import javax.enterprise.context.Dependent;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.ProcessAnnotatedType;
import javax.enterprise.inject.spi.WithAnnotations;
import javax.transaction.Transactional;

import io.helidon.integrations.micronaut.cdi.MicronautCdiExtension;

import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.inject.BeanDefinitionReference;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.annotation.TransactionalAdvice;
import io.micronaut.transaction.interceptor.TransactionalInterceptor;

import static javax.interceptor.Interceptor.Priority.PLATFORM_BEFORE;

public class MicronautDataCdiExtension implements Extension {
    private static final Map<CharSequence, Object> DEFAULT_TX_ADVICE = Map.of(
            "propagation", TransactionDefinition.Propagation.REQUIRED,
            "isolation", TransactionDefinition.Isolation.DEFAULT,
            "readOnly", false
    );

    void processBeans(@Observes @WithAnnotations(Transactional.class) ProcessAnnotatedType<?> event, BeanManager bm) {
        MicronautCdiExtension mCdi = bm.getExtension(MicronautCdiExtension.class);

        TransactionalAdvice annotation = event.getAnnotatedType()
                .getAnnotation(TransactionalAdvice.class);
        if (annotation != null) {
            // already handled by MicronautCdiExtension, as this is an interceptor binding
            return;
        }
        Transactional onClass = event.getAnnotatedType()
                .getAnnotation(Transactional.class);

        event.configureAnnotatedType().methods()
                .forEach(method -> {
                    if (method.getAnnotated().getAnnotation(TransactionalAdvice.class) != null) {
                        // this is an interceptor binding
                        return;
                    }
                    Transactional onMethod = method.getAnnotated().getAnnotation(Transactional.class);
                    onMethod = (onMethod == null) ? onClass : onMethod;
                    if (onMethod == null) {
                        // not transactional
                        return;
                    }

                    Map<Class<? extends Annotation>, Map<CharSequence, Object>> annotationToAdd = Map.of(
                            TransactionalAdvice.class, DEFAULT_TX_ADVICE);

                    mCdi.addInterceptor(method, Set.of(TransactionalInterceptor.class), annotationToAdd);
        });
    }

    void afterBeanDiscovery(@Priority(PLATFORM_BEFORE + 10) @Observes AfterBeanDiscovery event,
                            BeanManager bm) {
        MicronautCdiExtension mcdi = bm.getExtension(MicronautCdiExtension.class);
        List<BeanDefinitionReference> allBeans = mcdi.beanDefinitions();

        allBeans.stream()
                .filter(this::isRepository)
                .forEach(it -> addBean(event, mcdi, it));

        event.addBean()
                .addType(Connection.class)
                .id("micronaut-sql-connection")
                .scope(Dependent.class)
                .produceWith(instance -> mcdi.context().createBean(Connection.class));
    }

    private boolean isRepository(BeanDefinitionReference<?> ref) {
        return ref.getAnnotationMetadata().hasStereotype(Repository.class);
    }

    @SuppressWarnings("unchecked")
    private void addBean(AfterBeanDiscovery event,
                         MicronautCdiExtension mcdi,
                         BeanDefinitionReference<?> it) {

        Class<?> beanType = it.getBeanType();
        // repository is either an interface or an abstract class, we need to find the original class (not a generated one)
        if (Object.class.equals(beanType.getSuperclass())) {
            // we have an interface
            Class<?>[] interfaces = beanType.getInterfaces();
            // by design, the first interface should be our repo, but let's be nice

            for (Class<?> anInterface : interfaces) {
                if (isRepo(anInterface)) {
                    beanType = anInterface;
                    break;
                }
            }
        } else {
            // we have an abstract class
            Class<?> superclass = beanType.getSuperclass();
            if (isRepo(superclass)) {
                beanType = superclass;
            }
        }

        event.addBean()
                .addType(beanType)
                .id("micronaut-data-" + it.getBeanType().getName())
                .scope(Dependent.class)
                .produceWith(that -> mcdi.context().getBean(it.getBeanType()));
    }

    private boolean isRepo(Class<?> type) {
        if (type == null || type.equals(Object.class)) {
            return false;
        }

        Class<?>[] interfaces = type.getInterfaces();

        for (Class<?> anInterface : interfaces) {
            if (anInterface.equals(GenericRepository.class)) {
                return true;
            }
            if (isRepo(anInterface)) {
                return true;
            }
        }

        return isRepo(type.getSuperclass());
    }
}
