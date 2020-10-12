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

import javax.annotation.Priority;
import javax.enterprise.context.Dependent;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.ProcessAnnotatedType;
import javax.enterprise.inject.spi.WithAnnotations;
import javax.transaction.Transactional;

import io.micronaut.context.ApplicationContext;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.annotation.TransactionalAdvice;

import static javax.interceptor.Interceptor.Priority.PLATFORM_BEFORE;

public class MicronautDataCdiExtension implements Extension {

    void processBeans(@Observes @WithAnnotations(Transactional.class) @Priority(PLATFORM_BEFORE + 10) ProcessAnnotatedType<?> event,
                      BeanManager bm) {
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

                    // this will be processed by MicronautCdiExtension
                    method.add(new TransactionalAdvice() {
                        @Override
                        public String value() {
                            return "";
                        }

                        @Override
                        public String transactionManager() {
                            return "";
                        }

                        @Override
                        public TransactionDefinition.Propagation propagation() {
                            return TransactionDefinition.Propagation.REQUIRED;
                        }

                        @Override
                        public TransactionDefinition.Isolation isolation() {
                            return TransactionDefinition.Isolation.DEFAULT;
                        }

                        @Override
                        public int timeout() {
                            return -1;
                        }

                        @Override
                        public boolean readOnly() {
                            return false;
                        }

                        @Override
                        public Class<? extends Throwable>[] noRollbackFor() {
                            return new Class[0];
                        }

                        @Override
                        public Class<? extends Annotation> annotationType() {
                            return TransactionalAdvice.class;
                        }
                    });
                });
    }

    void afterBeanDiscovery(@Priority(PLATFORM_BEFORE + 10) @Observes AfterBeanDiscovery event,
                            BeanManager bm) {
        event.addBean()
                .addType(Connection.class)
                .id("micronaut-sql-connection")
                .scope(Dependent.class)
                .produceWith(instance -> instance.select(ApplicationContext.class)
                        .get()
                        .getBean(Connection.class));
    }

}
