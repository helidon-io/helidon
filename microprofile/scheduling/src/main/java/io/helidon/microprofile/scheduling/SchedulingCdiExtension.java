/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Priority;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.BeforeDestroyed;
import javax.enterprise.context.Initialized;
import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.AnnotatedMethod;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.DeploymentException;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.ProcessAnnotatedType;
import javax.enterprise.inject.spi.ProcessManagedBean;
import javax.enterprise.inject.spi.WithAnnotations;

import io.helidon.common.configurable.ScheduledThreadPoolSupplier;
import io.helidon.config.Config;
import io.helidon.microprofile.cdi.RuntimeStart;

import static javax.interceptor.Interceptor.Priority.PLATFORM_AFTER;

import org.eclipse.microprofile.config.ConfigProvider;

/**
 * Scheduling CDI Extension.
 */
public class SchedulingCdiExtension implements Extension {
    private static final Logger LOGGER = Logger.getLogger(SchedulingCdiExtension.class.getName());
    private static final Pattern CRON_PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{(?<key>[^\\}]+)\\}");
    private final Queue<AnnotatedMethod<?>> methods = new LinkedList<>();
    private final Map<AnnotatedMethod<?>, Bean<?>> beans = new HashMap<>();
    private final Queue<ScheduledExecutorService> executors = new LinkedList<>();
    private Config config;
    private Config schedulingConfig;

    void registerMethods(
            @Observes
            @WithAnnotations({Scheduled.class, FixedRate.class}) ProcessAnnotatedType<?> pat) {
        // Lookup scheduled methods
        pat.getAnnotatedType().getMethods()
                .stream()
                .filter(am -> am.isAnnotationPresent(Scheduled.class) || am.isAnnotationPresent(FixedRate.class))
                .forEach(methods::add);
    }

    void onProcessBean(@Observes ProcessManagedBean<?> event) {
        // Gather bean references
        Bean<?> bean = event.getBean();
        Class<?> beanClass = bean.getBeanClass();
        for (AnnotatedMethod<?> am : methods) {
            if (beanClass == am.getDeclaringType().getJavaClass()) {
                beans.put(am, bean);
            }
        }
    }

    private void prepareRuntime(@Observes @RuntimeStart Config config) {
        this.config = config;
        this.schedulingConfig = config.get("schedule");
    }

    void invoke(@Observes @Priority(PLATFORM_AFTER + 4000) @Initialized(ApplicationScoped.class) Object event,
                BeanManager beanManager) {

        ScheduledThreadPoolSupplier scheduledThreadPoolSupplier = ScheduledThreadPoolSupplier.builder()
                .threadNamePrefix(schedulingConfig.get("thread-name-prefix").asString().orElse("scheduled-"))
                .config(schedulingConfig)
                .build();

        for (AnnotatedMethod<?> am : methods) {
            Class<?> aClass = am.getDeclaringType().getJavaClass();
            Bean<?> bean = beans.get(am);
            Object beanInstance = lookup(bean, beanManager);
            ScheduledExecutorService executorService = scheduledThreadPoolSupplier.get();
            executors.add(executorService);
            Method method = am.getJavaMember();

            if (!method.trySetAccessible()) {
                throw new DeploymentException(String.format("Scheduled method %s#%s is not accessible!",
                        method.getDeclaringClass().getName(),
                        method.getName()));
            }

            if (am.isAnnotationPresent(FixedRate.class)
                    && am.isAnnotationPresent(Scheduled.class)) {
                throw new DeploymentException(String.format("Scheduled method %s#%s can have only one scheduling annotation.",
                        method.getDeclaringClass().getName(),
                        method.getName()));
            }

            Config methodConfig = config.get(aClass.getName() + "." + method.getName() + ".schedule");

            if (am.isAnnotationPresent(FixedRate.class)) {
                FixedRate annotation = am.getAnnotation(FixedRate.class);

                long initialDelay = methodConfig.get("initial-delay").asLong()
                        .orElseGet(annotation::initialDelay);

                long delay = methodConfig.get("delay").asLong()
                        .orElseGet(annotation::value);

                TimeUnit timeUnit = methodConfig.get("time-unit").asString()
                        .map(TimeUnit::valueOf)
                        .orElseGet(annotation::timeUnit);

                Task task = new FixedRateTask(executorService, initialDelay, delay, timeUnit,
                        inv -> invokeWithOptionalParam(beanInstance, method, inv));

                LOGGER.log(Level.FINE, () -> String.format("Method %s#%s scheduled to be executed %s",
                        aClass.getSimpleName(), method.getName(), task.description()));

            } else if (am.isAnnotationPresent(Scheduled.class)) {
                Scheduled annotation = am.getAnnotation(Scheduled.class);

                String cron = methodConfig.get("cron").asString()
                        .orElseGet(() -> resolvePlaceholders(annotation.value(), config));

                boolean concurrent = methodConfig.get("concurrent").asBoolean()
                        .orElseGet(annotation::concurrentExecution);

                Task task = new CronTask(executorService, cron, concurrent,
                        inv -> invokeWithOptionalParam(beanInstance, method, inv));

                LOGGER.log(Level.FINE, () -> String.format("Method %s#%s scheduled to be executed %s",
                        aClass.getSimpleName(), method.getName(), task.description()));
            }
        }
    }

    void terminate(@Observes @BeforeDestroyed(ApplicationScoped.class) Object event) {
        executors.forEach(ExecutorService::shutdownNow);
    }

    @SuppressWarnings("unchecked")
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

    static void invokeWithOptionalParam(Object instance, Method method, Invocation invocation)
            throws InvocationTargetException, IllegalAccessException {

        Class<?>[] parameterTypes = method.getParameterTypes();
        Class<? extends Invocation> invClazz = invocation.getClass();

        if (parameterTypes.length > 1 ||
                parameterTypes.length > 0 && !parameterTypes[0].isAssignableFrom(invClazz)) {

            throw new DeploymentException(
                    String.format("Unsupported param types for scheduled method %s, none or %s is supported.",
                            method.getName(), invClazz.getName())
            );
        }

        if (parameterTypes.length == 0) {
            method.invoke(instance);
        } else {
            method.invoke(instance, invocation);
        }
    }

    static String resolvePlaceholders(String src, Config config) {
        Matcher m = CRON_PLACEHOLDER_PATTERN.matcher(src);
        StringBuilder result = new StringBuilder();
        int index = 0;

        while (m.find()) {
            String key = m.group("key");
            String value = config.get(key)
                    .asString()
                    .orElseThrow(() ->
                            new IllegalArgumentException(String.format("Scheduling placeholder %s could not be resolved.", key))
                    );

            result.append(src, index, m.start()).append(value);
            index = m.end();
        }

        if (index < src.length()) {
            result.append(src, index, src.length());
        }

        return result.toString();
    }
}
