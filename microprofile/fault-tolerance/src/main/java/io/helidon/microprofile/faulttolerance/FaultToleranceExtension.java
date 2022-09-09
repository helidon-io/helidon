/*
 * Copyright (c) 2018, 2023 Oracle and/or its affiliates.
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

package io.helidon.microprofile.faulttolerance;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;

import io.helidon.common.configurable.ScheduledThreadPoolSupplier;
import io.helidon.common.configurable.ThreadPoolSupplier;
import io.helidon.config.mp.MpConfig;
import io.helidon.faulttolerance.FaultTolerance;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.AfterDeploymentValidation;
import jakarta.enterprise.inject.spi.AnnotatedConstructor;
import jakarta.enterprise.inject.spi.AnnotatedField;
import jakarta.enterprise.inject.spi.AnnotatedMethod;
import jakarta.enterprise.inject.spi.AnnotatedType;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.BeforeBeanDiscovery;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.ProcessAnnotatedType;
import jakarta.enterprise.inject.spi.ProcessManagedBean;
import jakarta.enterprise.inject.spi.ProcessSyntheticBean;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.faulttolerance.Asynchronous;
import org.eclipse.microprofile.faulttolerance.Bulkhead;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.glassfish.jersey.process.internal.RequestScope;

import static jakarta.interceptor.Interceptor.Priority.LIBRARY_BEFORE;

/**
 * CDI extension for Helidon's Fault Tolerance implementation.
 */
public class FaultToleranceExtension implements Extension {
    static final String MP_FT_NON_FALLBACK_ENABLED = "MP_Fault_Tolerance_NonFallback_Enabled";
    static final String MP_FT_METRICS_ENABLED = "MP_Fault_Tolerance_Metrics_Enabled";
    static final String MP_FT_INTERCEPTOR_PRIORITY = "mp.fault.tolerance.interceptor.priority";

    private static boolean isFaultToleranceEnabled = true;

    private static boolean isFaultToleranceMetricsEnabled = true;

    private Set<AnnotatedMethod<?>> registeredMethods;

    private ThreadPoolSupplier threadPoolSupplier;

    private ScheduledThreadPoolSupplier scheduledThreadPoolSupplier;

    /**
     * Class to mimic a {@link Priority} annotation for the purpose of changing
     * its value dynamically.
     */
    private static class LiteralPriority extends AnnotationLiteral<Priority> implements Priority {

        private final int value;

        LiteralPriority(int value) {
            this.value = value;
        }

        @Override
        public int value() {
            return value;
        }
    }

    /**
     * Returns a boolean indicating if FT is enabled.
     *
     * @return Fault tolerance enabled or disabled.
     */
    static boolean isFaultToleranceEnabled() {
        return isFaultToleranceEnabled;
    }

    /**
     * Returns a boolean indicating if FT metrics are enabled.
     *
     * @return Fault tolerance metrics enabled or disabled.
     */
    static boolean isFaultToleranceMetricsEnabled() {
        return isFaultToleranceMetricsEnabled;
    }

    /**
     * Adds interceptor bindings and annotated types.
     *
     * @param discovery Event information.
     * @param bm Bean manager instance.
     */
    void registerInterceptorBindings(@Observes BeforeBeanDiscovery discovery, BeanManager bm) {
        // Check if fault tolerance and its metrics are enabled
        final Config config = ConfigProvider.getConfig();
        isFaultToleranceEnabled = config.getOptionalValue(MP_FT_NON_FALLBACK_ENABLED, Boolean.class)
                .orElse(true);      // default is enabled
        isFaultToleranceMetricsEnabled = config.getOptionalValue(MP_FT_METRICS_ENABLED, Boolean.class)
                .orElse(true);      // default is enabled

        discovery.addInterceptorBinding(
                new AnnotatedTypeWrapper<>(bm.createAnnotatedType(Retry.class),
                        LiteralCommandBinding.getInstance()));
        discovery.addInterceptorBinding(
                new AnnotatedTypeWrapper<>(bm.createAnnotatedType(CircuitBreaker.class),
                        LiteralCommandBinding.getInstance()));
        discovery.addInterceptorBinding(
                new AnnotatedTypeWrapper<>(bm.createAnnotatedType(Timeout.class),
                        LiteralCommandBinding.getInstance()));
        discovery.addInterceptorBinding(
                new AnnotatedTypeWrapper<>(bm.createAnnotatedType(Asynchronous.class),
                        LiteralCommandBinding.getInstance()));
        discovery.addInterceptorBinding(
                new AnnotatedTypeWrapper<>(bm.createAnnotatedType(Bulkhead.class),
                        LiteralCommandBinding.getInstance()));
        discovery.addInterceptorBinding(
                new AnnotatedTypeWrapper<>(bm.createAnnotatedType(Fallback.class),
                        LiteralCommandBinding.getInstance()));

        discovery.addAnnotatedType(bm.createAnnotatedType(CommandInterceptor.class),
                CommandInterceptor.class.getName());
        discovery.addAnnotatedType(bm.createAnnotatedType(JerseyRequestScopeAsCdiBean.class),
                JerseyRequestScopeAsCdiBean.class.getName());
    }

    /**
     * We require access to {@link org.glassfish.jersey.process.internal.RequestScope}
     * via CDI to propagate request contexts to newly created threads, but Jersey
     * only registers this type as a bean if it can find an injection point (see
     * org.glassfish.jersey.ext.cdi1x.internal.CdiComponentProvider#afterDiscoveryObserver).
     * Here we define a dummy bean with such an injection point for Jersey to
     * create and register a CDI bean for RequestScope.
     */
    private static class JerseyRequestScopeAsCdiBean {
        @Inject
        private RequestScope requestScope;
    }

    /**
     * Update priority of FT interceptor if set in config.
     *
     * @param event Process annotated event.
     */
    void updatePriorityMaybe(@Observes final ProcessAnnotatedType<CommandInterceptor> event) {
        final Config config = ConfigProvider.getConfig();
        Optional<Integer> priority = config.getOptionalValue(MP_FT_INTERCEPTOR_PRIORITY, Integer.class);
        priority.ifPresent(v -> event.configureAnnotatedType()
                .remove(Priority.class::isInstance)
                .add(new LiteralPriority(v)));
    }

    /**
     * Collects all FT methods in a set for later processing.
     *
     * @param event Event information.
     */
    void registerFaultToleranceMethods(BeanManager bm, @Observes ProcessSyntheticBean<?> event) {
        registerFaultToleranceMethods(bm, bm.createAnnotatedType(event.getBean().getBeanClass()));
    }

    /**
     * Collects all FT methods in a set for later processing.
     *
     * @param event Event information.
     */
    void registerFaultToleranceMethods(BeanManager bm, @Observes ProcessManagedBean<?> event) {
        registerFaultToleranceMethods(bm, event.getAnnotatedBeanClass());
    }

    /**
     * Register FT methods for later processing.
     *
     * @param type Bean type.
     */
    private void registerFaultToleranceMethods(BeanManager bm, AnnotatedType<?> type) {
        for (AnnotatedMethod<?> method : type.getMethods()) {
            if (isFaultToleranceMethod(method, bm)) {
                getRegisteredMethods().add(method);
            }
        }
    }

    /**
     * Validates annotations.
     *
     * @param event Event information.
     */
    void validateAnnotations(BeanManager bm,
                             @Observes @Priority(LIBRARY_BEFORE + 10 + 5)
                             @Initialized(ApplicationScoped.class) Object event) {
        if (FaultToleranceMetrics.enabled()) {
            getRegisteredMethods().forEach(annotatedMethod -> {
                final AnnotatedType<?> annotatedType = annotatedMethod.getDeclaringType();

                // Metrics depending on the annotationSet present
                if (MethodAntn.isAnnotationPresent(annotatedMethod, Retry.class, bm)) {
                    new RetryAntn(annotatedMethod).validate();
                }
                if (MethodAntn.isAnnotationPresent(annotatedMethod, CircuitBreaker.class, bm)) {
                    new CircuitBreakerAntn(annotatedMethod).validate();
                }
                if (MethodAntn.isAnnotationPresent(annotatedMethod, Timeout.class, bm)) {
                    new TimeoutAntn(annotatedMethod).validate();
                }
                if (MethodAntn.isAnnotationPresent(annotatedMethod, Bulkhead.class, bm)) {
                    new BulkheadAntn(annotatedMethod).validate();
                }
                if (MethodAntn.isAnnotationPresent(annotatedMethod, Fallback.class, bm)) {
                    new FallbackAntn(annotatedMethod).validate();
                }
                if (MethodAntn.isAnnotationPresent(annotatedMethod, Asynchronous.class, bm)) {
                    new AsynchronousAntn(annotatedMethod).validate();
                }
            });
        }
    }

    /**
     * Creates the executors used by FT using config. Must be created during the
     * {@code AfterDeploymentValidation} event.
     *
     * @param event the AfterDeploymentValidation event
     */
    void createFaultToleranceExecutors(@Observes AfterDeploymentValidation event) {
        // Initialize executors for MP FT - default size of 20
        io.helidon.config.Config config = MpConfig.toHelidonConfig(ConfigProvider.getConfig());
        scheduledThreadPoolSupplier = ScheduledThreadPoolSupplier.builder()
                .threadNamePrefix("ft-mp-schedule-")
                .corePoolSize(20)
                .config(config.get("scheduled-executor"))
                .build();
        FaultTolerance.scheduledExecutor(scheduledThreadPoolSupplier);
        threadPoolSupplier = ThreadPoolSupplier.builder()
                .threadNamePrefix("ft-mp-")
                .corePoolSize(20)
                .config(config.get("executor"))
                .build();
        FaultTolerance.executor(threadPoolSupplier);
    }

    /**
     * Lazy initialization of set.
     *
     * @return The set.
     */
    private Set<AnnotatedMethod<?>> getRegisteredMethods() {
        if (registeredMethods == null) {
            registeredMethods = new CopyOnWriteArraySet<>();
        }
        return registeredMethods;
    }

    /**
     * Returns the real class of this object, skipping proxies.
     *
     * @param object The object.
     * @return Its class.
     */
    static Class<?> getRealClass(Object object) {
        Class<?> result = object.getClass();
        while (result.isSynthetic()) {
            result = result.getSuperclass();
        }
        return result;
    }

    /**
     * Determines if a method has any fault tolerance annotationSet. Only {@code @Fallback}
     * is considered if fault tolerance is disabled.
     *
     * @param annotatedMethod The method to check.
     * @return Outcome of test.
     */
    static boolean isFaultToleranceMethod(AnnotatedMethod<?> annotatedMethod,
                                          BeanManager bm) {
        return MethodAntn.isAnnotationPresent(annotatedMethod, Retry.class, bm)
                || MethodAntn.isAnnotationPresent(annotatedMethod, CircuitBreaker.class, bm)
                || MethodAntn.isAnnotationPresent(annotatedMethod, Bulkhead.class, bm)
                || MethodAntn.isAnnotationPresent(annotatedMethod, Timeout.class, bm)
                || MethodAntn.isAnnotationPresent(annotatedMethod, Asynchronous.class, bm)
                || MethodAntn.isAnnotationPresent(annotatedMethod, Fallback.class, bm);
    }

    /**
     * Access {@code ThreadPoolSupplier} configured by this extension.
     *
     * @return a thread pool supplier.
     */
    public ThreadPoolSupplier threadPoolSupplier() {
        return threadPoolSupplier;
    }

    /**
     * Access {@code ScheduledThreadPoolSupplier} configured by this extension.
     *
     * @return a scheduled thread pool supplier.
     */
    public ScheduledThreadPoolSupplier scheduledThreadPoolSupplier() {
        return scheduledThreadPoolSupplier;
    }

    /**
     * Wraps an annotated type for the purpose of adding and/or overriding
     * some annotations.
     *
     * @param <T> Underlying type.
     */
    public static class AnnotatedTypeWrapper<T> implements AnnotatedType<T> {

        private final AnnotatedType<T> delegate;

        private final Set<Annotation> annotationSet;

        /**
         * Constructor.
         *
         * @param delegate Wrapped annotated type.
         * @param annotations New set of annotations possibly overriding existing ones.
         */
        public AnnotatedTypeWrapper(AnnotatedType<T> delegate, Annotation... annotations) {
            this.delegate = delegate;
            this.annotationSet = new HashSet<>(Arrays.asList(annotations));

            // Include only those annotations not overridden
            for (Annotation da : delegate.getAnnotations()) {
                boolean overridden = false;
                for (Annotation na : annotationSet) {
                    if (da.annotationType().isAssignableFrom(na.annotationType())) {
                        overridden = true;
                        break;
                    }
                }
                if (!overridden) {
                    this.annotationSet.add(da);
                }
            }
        }

        public Class<T> getJavaClass() {
            return delegate.getJavaClass();
        }

        public Type getBaseType() {
            return delegate.getBaseType();
        }

        public Set<Type> getTypeClosure() {
            return delegate.getTypeClosure();
        }

        public Set<AnnotatedConstructor<T>> getConstructors() {
            return delegate.getConstructors();
        }

        public Set<AnnotatedMethod<? super T>> getMethods() {
            return delegate.getMethods();
        }

        public Set<AnnotatedField<? super T>> getFields() {
            return delegate.getFields();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <R extends Annotation> R getAnnotation(Class<R> annotationType) {
            Optional<Annotation> optional = annotationSet.stream()
                    .filter(a -> annotationType.isAssignableFrom(a.annotationType()))
                    .findFirst();
            return (R) optional.orElse(null);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T extends Annotation> Set<T> getAnnotations(Class<T> annotationType) {
            return (Set<T>) annotationSet.stream()
                    .filter(a -> annotationType.isAssignableFrom(a.annotationType()))
                    .collect(Collectors.toSet());
        }

        @Override
        public Set<Annotation> getAnnotations() {
            return annotationSet;
        }

        @Override
        public boolean isAnnotationPresent(Class<? extends Annotation> annotationType) {
            Annotation annotation = getAnnotation(annotationType);
            return annotation != null;
        }
    }
}
