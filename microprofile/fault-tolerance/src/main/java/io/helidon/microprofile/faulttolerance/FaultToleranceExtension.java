/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates.
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
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;

import javax.annotation.Priority;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.AfterDeploymentValidation;
import javax.enterprise.inject.spi.AnnotatedConstructor;
import javax.enterprise.inject.spi.AnnotatedField;
import javax.enterprise.inject.spi.AnnotatedMethod;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.BeforeBeanDiscovery;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.ProcessAnnotatedType;
import javax.enterprise.inject.spi.ProcessManagedBean;
import javax.enterprise.inject.spi.ProcessSyntheticBean;
import javax.enterprise.util.AnnotationLiteral;
import javax.inject.Inject;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.faulttolerance.Asynchronous;
import org.eclipse.microprofile.faulttolerance.Bulkhead;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.glassfish.jersey.process.internal.RequestScope;

/**
 * Class FaultToleranceExtension.
 */
public class FaultToleranceExtension implements Extension {
    static final String MP_FT_NON_FALLBACK_ENABLED = "MP_Fault_Tolerance_NonFallback_Enabled";
    static final String MP_FT_METRICS_ENABLED = "MP_Fault_Tolerance_Metrics_Enabled";
    static final String MP_FT_INTERCEPTOR_PRIORITY = "mp.fault.tolerance.interceptor.priority";

    private static boolean isFaultToleranceEnabled = true;

    private static boolean isFaultToleranceMetricsEnabled = true;

    private Set<BeanMethod> registeredMethods;

    /**
     * A bean method class that pairs a class and a method.
     */
    private static class BeanMethod {

        private final Class<?> beanClass;
        private final Method method;

        BeanMethod(Class<?> beanClass, Method method) {
            this.beanClass = beanClass;
            this.method = method;
        }

        Class<?> beanClass() {
            return beanClass;
        }

        Method method() {
            return method;
        }
    }

    /**
     * Class to mimic a {@link Priority} annotation for the purpuse of changing
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
                .remove(a -> a instanceof Priority)
                .add(new LiteralPriority(v)));
    }

    /**
     * Collects all FT methods in a set for later processing.
     *
     * @param event Event information.
     */
    void registerFaultToleranceMethods(BeanManager bm, @Observes ProcessSyntheticBean<?> event) {
        registerFaultToleranceMethods(bm.createAnnotatedType(event.getBean().getBeanClass()));
    }

    /**
     * Collects all FT methods in a set for later processing.
     *
     * @param event Event information.
     */
    void registerFaultToleranceMethods(@Observes ProcessManagedBean<?> event) {
        registerFaultToleranceMethods(event.getAnnotatedBeanClass());
    }

    /**
     * Register FT methods for later processing.
     *
     * @param type Bean type.
     */
    private void registerFaultToleranceMethods(AnnotatedType<?> type) {
        for (AnnotatedMethod<?> method : type.getMethods()) {
            if (isFaultToleranceMethod(type.getJavaClass(), method.getJavaMember())) {
                getRegisteredMethods().add(new BeanMethod(type.getJavaClass(), method.getJavaMember()));
            }
        }
    }

    /**
     * Registers metrics for all FT methods.
     *
     * @param validation Event information.
     */
    void registerFaultToleranceMetrics(@Observes AfterDeploymentValidation validation) {
        if (FaultToleranceMetrics.enabled()) {
            getRegisteredMethods().stream().forEach(beanMethod -> {
                final Method method = beanMethod.method();
                final Class<?> beanClass = beanMethod.beanClass();

                // Counters for all methods
                FaultToleranceMetrics.registerMetrics(method);

                // Metrics depending on the annotationSet present
                if (MethodAntn.isAnnotationPresent(beanClass, method, Retry.class)) {
                    FaultToleranceMetrics.registerRetryMetrics(method);
                    new RetryAntn(beanClass, method).validate();
                }
                if (MethodAntn.isAnnotationPresent(beanClass, method, CircuitBreaker.class)) {
                    FaultToleranceMetrics.registerCircuitBreakerMetrics(method);
                    new CircuitBreakerAntn(beanClass, method).validate();
                }
                if (MethodAntn.isAnnotationPresent(beanClass, method, Timeout.class)) {
                    FaultToleranceMetrics.registerTimeoutMetrics(method);
                    new TimeoutAntn(beanClass, method).validate();
                }
                if (MethodAntn.isAnnotationPresent(beanClass, method, Bulkhead.class)) {
                    FaultToleranceMetrics.registerBulkheadMetrics(method);
                    new BulkheadAntn(beanClass, method).validate();
                }
                if (MethodAntn.isAnnotationPresent(beanClass, method, Fallback.class)) {
                    FaultToleranceMetrics.registerFallbackMetrics(method);
                    new FallbackAntn(beanClass, method).validate();
                }
                if (MethodAntn.isAnnotationPresent(beanClass, method, Asynchronous.class)) {
                    new AsynchronousAntn(beanClass, method).validate();
                }
            });
        }
    }

    /**
     * Lazy initialization of set.
     *
     * @return The set.
     */
    private Set<BeanMethod> getRegisteredMethods() {
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
     * @param beanClass The bean.
     * @param method The method to check.
     * @return Outcome of test.
     */
    static boolean isFaultToleranceMethod(Class<?> beanClass, Method method) {
        return MethodAntn.isAnnotationPresent(beanClass, method, Retry.class)
                || MethodAntn.isAnnotationPresent(beanClass, method, CircuitBreaker.class)
                || MethodAntn.isAnnotationPresent(beanClass, method, Bulkhead.class)
                || MethodAntn.isAnnotationPresent(beanClass, method, Timeout.class)
                || MethodAntn.isAnnotationPresent(beanClass, method, Asynchronous.class)
                || MethodAntn.isAnnotationPresent(beanClass, method, Fallback.class);
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
            return optional.isPresent() ? (R) optional.get() : null;
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
