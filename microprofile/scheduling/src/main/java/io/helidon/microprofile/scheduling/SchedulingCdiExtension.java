/*
 * Copyright (c)  2020 Oracle and/or its affiliates.
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

package io.helidon.microprofile.scheduling;

import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Priority;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Initialized;
import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.AfterDeploymentValidation;
import javax.enterprise.inject.spi.AnnotatedMethod;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.DeploymentException;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.ProcessAnnotatedType;
import javax.enterprise.inject.spi.ProcessManagedBean;
import javax.enterprise.inject.spi.WithAnnotations;

import io.helidon.common.configurable.ScheduledThreadPoolSupplier;

import com.cronutils.descriptor.CronDescriptor;
import com.cronutils.model.Cron;
import com.cronutils.model.definition.CronDefinition;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;

import static com.cronutils.model.CronType.QUARTZ;
import static javax.interceptor.Interceptor.Priority.PLATFORM_AFTER;

/**
 * Scheduling CDI Extension.
 */
public class SchedulingCdiExtension implements Extension {
    private static final Logger LOGGER = Logger.getLogger(SchedulingCdiExtension.class.getName());
    private final Map<Class<?>, AnnotatedMethod<?>> methods = new HashMap<>();
    private final Map<AnnotatedMethod<?>, Bean<?>> beans = new HashMap<>();

    private void registerMethods(
            @Observes
            @WithAnnotations({Scheduled.class}) ProcessAnnotatedType<?> pat) {
        // Lookup channel methods
        pat.getAnnotatedType().getMethods()
                .stream()
                .filter(am -> am.isAnnotationPresent(Scheduled.class))
                .forEach(annotatedMethod -> {
                    methods.put(annotatedMethod.getDeclaringType().getJavaClass(), annotatedMethod);
                });
    }

    private void onProcessBean(@Observes ProcessManagedBean<?> event) {
        // Gather bean references
        Bean<?> bean = event.getBean();
        Optional.ofNullable(methods.get(bean.getBeanClass()))
                .ifPresent(m -> beans.put(m, bean));
    }

    private void deploymentValidation(@Observes AfterDeploymentValidation event) {
    }

    private void invoke(@Observes @Priority(PLATFORM_AFTER + 102) @Initialized(ApplicationScoped.class) Object event,
                        BeanManager beanManager) {
        // kickoff
        ScheduledThreadPoolSupplier scheduledThreadPoolSupplier = ScheduledThreadPoolSupplier.builder()
                .threadNamePrefix("scheduled-")
                .build();
        for (Map.Entry<Class<?>, AnnotatedMethod<?>> entry : methods.entrySet()) {
            Class<?> aClass = entry.getKey();
            AnnotatedMethod<?> am = entry.getValue();
            Bean<?> bean = beans.get(am);
            Object beanInstance = lookup(bean, beanManager);
            Scheduled annotation = am.getAnnotation(Scheduled.class);
            ScheduledExecutorService executorService = scheduledThreadPoolSupplier.get();
            if (annotation.fixedRate() > 0) {
                executorService.scheduleAtFixedRate(() -> {
                    try {
                        am.getJavaMember().invoke(beanInstance);
                    } catch (Throwable e) {
                        LOGGER.log(Level.SEVERE, e, () -> "Error when invoking scheduled method.");
                    }
                }, 0L, annotation.fixedRate(), TimeUnit.MILLISECONDS);
            } else if (annotation.cron().length() > 0) {
                CronDefinition cronDefinition =
                        CronDefinitionBuilder.instanceDefinitionFor(QUARTZ);

                //create a parser based on provided definition
                CronParser parser = new CronParser(cronDefinition);
                Cron cron = parser.parse(annotation.cron());
                LOGGER.info(() ->
                        "Method "
                                + aClass.getSimpleName()
                                + "#"
                                + am.getJavaMember().getName()
                                + " scheduled to be executed "
                                + CronDescriptor.instance(Locale.ENGLISH).describe(cron));
                ExecutionTime executionTime = ExecutionTime.forCron(cron);
                executorService.scheduleAtFixedRate(() -> {
                    Optional<ZonedDateTime> time = executionTime.nextExecution(ZonedDateTime.now());
                    if (time.isPresent()
                            && time.get()
                            .minus(100, ChronoUnit.MILLIS)
                            .toLocalTime().isBefore(LocalTime.now())) {
                        try {
                            am.getJavaMember().invoke(beanInstance);
                        } catch (Throwable e) {
                            LOGGER.log(Level.SEVERE, e, () -> "Error when invoking scheduled method.");
                        }
                    }
                }, 0L, 100, TimeUnit.MILLISECONDS);
            }
        }
    }

    static <T> T lookup(Bean<?> bean, BeanManager beanManager) {
        javax.enterprise.context.spi.Context context = beanManager.getContext(bean.getScope());
        Object instance = context.get(bean);
        if (instance == null) {
            CreationalContext<?> creationalContext = beanManager.createCreationalContext(bean);
            instance = beanManager.getReference(bean, bean.getBeanClass(), creationalContext);
        }
        if (instance == null) {
            throw new DeploymentException("Instance of bean " + bean.getName() + " not found");
        }
        return (T) instance;
    }
}
