/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.metrics;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.enterprise.event.Observes;
import javax.enterprise.inject.Default;
import javax.enterprise.inject.spi.AfterDeploymentValidation;
import javax.enterprise.inject.spi.AnnotatedMember;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.BeforeBeanDiscovery;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.ProcessAnnotatedType;
import javax.enterprise.inject.spi.ProcessInjectionPoint;
import javax.enterprise.inject.spi.ProcessProducerField;
import javax.enterprise.inject.spi.ProcessProducerMethod;
import javax.enterprise.inject.spi.WithAnnotations;
import javax.enterprise.inject.spi.configurator.AnnotatedMethodConfigurator;
import javax.enterprise.inject.spi.configurator.AnnotatedTypeConfigurator;
import javax.inject.Qualifier;
import javax.interceptor.Interceptor;

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Meter;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.Timer;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Gauge;
import org.eclipse.microprofile.metrics.annotation.Metered;
import org.eclipse.microprofile.metrics.annotation.Metric;
import org.eclipse.microprofile.metrics.annotation.Timed;

import static io.helidon.microprofile.metrics.MetricUtil.LookupResult;
import static io.helidon.microprofile.metrics.MetricUtil.getMetricName;
import static io.helidon.microprofile.metrics.MetricUtil.lookupAnnotation;

/**
 * MetricsCdiExtension class.
 */
public class MetricsCdiExtension implements Extension {
    private static final Logger LOGGER = Logger.getLogger(MetricsCdiExtension.class.getName());

    private static final List<Class<? extends Annotation>> METRIC_ANNOTATIONS
            = Arrays.asList(Counted.class, Metered.class, Timed.class, Gauge.class);

    private final Map<Bean<?>, AnnotatedMember<?>> producers = new HashMap<>();

    private final Map<String, AnnotatedMethodConfigurator<?>> annotatedGaugeSites = new HashMap<>();

    @SuppressWarnings("unchecked")
    private static <T> T getReference(BeanManager bm, Type type, Bean<?> bean) {
        return (T) bm.getReference(bean, type, bm.createCreationalContext(bean));
    }

    static <E extends Member & AnnotatedElement>
    void registerMetric(E element, Class<?> clazz, LookupResult<? extends Annotation> lookupResult) {
        MetricRegistry registry = getMetricRegistry();
        Annotation annotation = lookupResult.getAnnotation();

        if (annotation instanceof Counted) {
            Counted counted = (Counted) annotation;
            String metricName = getMetricName(element, clazz, lookupResult.getType(), counted.name(), counted.absolute());
            Metadata meta = new Metadata(metricName,
                                         counted.displayName(),
                                         counted.description(),
                                         MetricType.COUNTER,
                                         counted.unit(),
                                         toTags(counted.tags()));
            meta.setReusable(counted.reusable());
            registry.counter(meta);
            LOGGER.log(Level.FINE, () -> "### Registered counter " + metricName);
        } else if (annotation instanceof Metered) {
            Metered metered = (Metered) annotation;
            String metricName = getMetricName(element, clazz, lookupResult.getType(), metered.name(), metered.absolute());
            Metadata meta = new Metadata(metricName,
                                         metered.displayName(),
                                         metered.description(),
                                         MetricType.METERED,
                                         metered.unit(),
                                         toTags(metered.tags()));
            meta.setReusable(metered.reusable());
            registry.meter(meta);
            LOGGER.log(Level.FINE, () -> "### Registered meter " + metricName);
        } else if (annotation instanceof Timed) {
            Timed timed = (Timed) annotation;
            String metricName = getMetricName(element, clazz, lookupResult.getType(), timed.name(), timed.absolute());
            Metadata meta = new Metadata(metricName,
                                         timed.displayName(),
                                         timed.description(),
                                         MetricType.TIMER,
                                         timed.unit(),
                                         toTags(timed.tags()));
            meta.setReusable(timed.reusable());
            registry.timer(meta);
            LOGGER.log(Level.FINE, () -> "### Registered timer " + metricName);
        }
    }

    static String toTags(String[] tags) {
        if (null == tags || tags.length == 0) {
            return "";
        }
        return String.join(",", tags);
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

    private static MetricRegistry getMetricRegistry() {
        return MetricRegistry.class.cast(RegistryProducer.getDefaultRegistry());
    }

    /**
     * Initializes the extension prior to bean discovery.
     *
     * @param discovery bean discovery event
     */
    public void before(@Observes BeforeBeanDiscovery discovery) {
        LOGGER.log(Level.FINE, () -> "### Before bean discovery " + discovery);

        // Initialize our implementation
        RegistryProducer.clearApplicationRegistry();

        // Register beans manually
        discovery.addAnnotatedType(RegistryProducer.class, "RegistryProducer");
        discovery.addAnnotatedType(MetricProducer.class, "MetricProducer");
        discovery.addAnnotatedType(InterceptorCounted.class, "InterceptorCounted");
        discovery.addAnnotatedType(InterceptorMetered.class, "InterceptorMetered");
        discovery.addAnnotatedType(InterceptorTimed.class, "InterceptorTimed");
    }

    private void registerMetrics(@Observes @WithAnnotations({Counted.class, Metered.class, Timed.class})
                                         ProcessAnnotatedType<?> pat) {
        // Filter out interceptors
        AnnotatedType<?> type = pat.getAnnotatedType();
        Interceptor annot = type.getAnnotation(Interceptor.class);
        if (annot != null) {
            return;
        }

        LOGGER.log(Level.FINE, () -> "### Processing annotations for " + pat.getAnnotatedType().getJavaClass().getName());

        // Register metrics based on annotations
        AnnotatedTypeConfigurator<?> configurator = pat.configureAnnotatedType();
        Class<?> clazz = configurator.getAnnotated().getJavaClass();

        // If abstract class, then handled by concrete subclasses
        if (Modifier.isAbstract(clazz.getModifiers())) {
            return;
        }

        // Process methods keeping non-private declared on this class
        configurator.filterMethods(method -> !Modifier.isPrivate(method.getJavaMember().getModifiers()))
                .forEach(method -> {
                    METRIC_ANNOTATIONS.forEach(annotation -> {
                        Method m = method.getAnnotated().getJavaMember();
                        LookupResult<? extends Annotation> lookupResult = lookupAnnotation(m, annotation, clazz);
                            // For methods, register the metric only on the declaring
                            // class, not subclasses per the MP Metrics TCK
                            // VisibilityTimedMethodBeanTest.
                            if (lookupResult != null
                                    && (lookupResult.getType() != MetricUtil.MatchingType.METHOD
                                    || clazz.equals(m.getDeclaringClass()))) {

                                registerMetric(method.getAnnotated().getJavaMember(), clazz, lookupResult);
                           }
                       });
                    });

        // Process constructors
        configurator.filterConstructors(constructor -> !Modifier.isPrivate(constructor.getJavaMember().getModifiers()))
                .forEach(constructor -> {
                    METRIC_ANNOTATIONS.forEach(annotation -> {
                        LookupResult<? extends Annotation> lookupResult
                                = lookupAnnotation(constructor.getAnnotated().getJavaMember(), annotation, clazz);
                        if (lookupResult != null) {
                            registerMetric(constructor.getAnnotated().getJavaMember(), clazz, lookupResult);
                        }
                    });
                });
    }

    private void processInjectionPoints(@Observes ProcessInjectionPoint<?, ?> pip) {
        Type type = pip.getInjectionPoint().getType();
        if (type.equals(Counter.class) || type.equals(Histogram.class)
                || type.equals(Meter.class) || type.equals(Timer.class)) {
            pip.configureInjectionPoint().addQualifier(VendorDefined.Literal.INSTANCE);
        }
    }

    /**
     * Records metric producer fields defined by the application. Ignores producers
     * with non-default qualifiers and library producers.
     *
     * @param ppf Producer field.
     */
    private void recordProducerFields(@Observes ProcessProducerField<? extends org.eclipse.microprofile.metrics.Metric, ?> ppf) {
        LOGGER.log(Level.FINE, () -> "### recordProducerFields " + ppf.getBean().getBeanClass()
                                   + ", field: " + ppf.getAnnotatedProducerField());
        if (!MetricProducer.class.equals(ppf.getBean().getBeanClass())) {
            Metric metric = ppf.getAnnotatedProducerField().getAnnotation(Metric.class);
            if (metric != null) {
                Optional<? extends Annotation> hasQualifier
                        = ppf.getAnnotatedProducerField()
                        .getAnnotations()
                        .stream()
                        .filter(annotation -> annotation.annotationType().isAnnotationPresent(Qualifier.class))
                        .findFirst();
                // Ignore producers with non-default qualifiers
                if (!hasQualifier.isPresent() || hasQualifier.get() instanceof Default) {
                    producers.put(ppf.getBean(), ppf.getAnnotatedProducerField());
                }
            }
        }
    }

    // -- Utility classes and methods -----------------------------------------

    /**
     * Records metric producer methods defined by the application. Ignores producers
     * with non-default qualifiers and library producers.
     *
     * @param ppm Producer method.
     */
    private void recordProducerMethods(@Observes
                                               ProcessProducerMethod<? extends org.eclipse.microprofile.metrics.Metric, ?> ppm) {
        LOGGER.log(Level.FINE, () -> "### recordProducerMethods " + ppm.getBean().getBeanClass()
                                   + ", method: " + ppm.getAnnotatedProducerMethod());
        if (!MetricProducer.class.equals(ppm.getBean().getBeanClass())) {
            Metric metric = ppm.getAnnotatedProducerMethod().getAnnotation(Metric.class);
            if (metric != null) {
                Optional<? extends Annotation> hasQualifier
                        = ppm.getAnnotatedProducerMethod()
                        .getAnnotations()
                        .stream()
                        .filter(annotation -> annotation.annotationType().isAnnotationPresent(Qualifier.class))
                        .findFirst();
                // Ignore producers with non-default qualifiers
                if (!hasQualifier.isPresent() || hasQualifier.get() instanceof Default) {
                    producers.put(ppm.getBean(), ppm.getAnnotatedProducerMethod());
                }
            }
        }
    }

    /**
     * Registers metrics for all field and method producers defined by the application.
     *
     * @param adv After deployment validation event.
     * @param bm  Bean manager.
     */
    private void registerProducers(@Observes AfterDeploymentValidation adv, BeanManager bm) {
        LOGGER.log(Level.FINE, () -> "### registerProducers");

        MetricRegistry registry = getMetricRegistry();
        producers.entrySet().forEach(entry -> {
            Metric metric = entry.getValue().getAnnotation(Metric.class);
            if (metric != null) {
                String metricName = getMetricName(new AnnotatedElementWrapper(entry.getValue()),
                                                  entry.getValue().getDeclaringType().getJavaClass(),
                                                  MetricUtil.MatchingType.METHOD,
                                                  metric.name(), metric.absolute());
                registry.register(metricName, getReference(bm, entry.getValue().getBaseType(), entry.getKey()));
            }
        });
        producers.clear();
    }

    private void recordAnnotatedGaugeSite(@Observes @WithAnnotations(Gauge.class) ProcessAnnotatedType<?> pat) {
        LOGGER.log(Level.FINE, () -> "### recordAnnoatedGaugeSite for class " + pat.getAnnotatedType().getJavaClass());
        AnnotatedType<?> type = pat.getAnnotatedType();

        LOGGER.log(Level.FINE, () -> "### Processing annotations for " + type.getJavaClass().getName());

        // Register metrics based on annotations
        AnnotatedTypeConfigurator<?> configurator = pat.configureAnnotatedType();
        Class<?> clazz = configurator.getAnnotated().getJavaClass();

        // If abstract class, then handled by concrete subclasses
        if (Modifier.isAbstract(clazz.getModifiers())) {
            return;
        }

        // Process @Gauge methods keeping non-private declared on this class
        configurator.filterMethods(method -> method.getJavaMember().getDeclaringClass().equals(clazz)
                && !Modifier.isPrivate(method.getJavaMember().getModifiers())
                && method.isAnnotationPresent(Gauge.class))
                .forEach(method -> {
                    Method javaMethod = method.getAnnotated().getJavaMember();
                    Gauge gaugeAnnotation = method.getAnnotated().getAnnotation(Gauge.class);
                    String explicitGaugeName = gaugeAnnotation.name();
                    String gaugeNameSuffix = (explicitGaugeName.length() > 0 ? explicitGaugeName
                            : javaMethod.getName());
                    String gaugeName = (gaugeAnnotation.absolute() ? gaugeNameSuffix
                            : String.format("%s.%s", clazz.getName(), gaugeNameSuffix));
                    annotatedGaugeSites.put(gaugeName, method);
                    LOGGER.log(Level.FINE, () -> String.format("### Recorded annotated gauge with name %s", gaugeName));
                });
    }

    private void registerAnnotatedGauges(@Observes AfterDeploymentValidation adv, BeanManager bm) {
        LOGGER.log(Level.FINE, () -> "### registerGauges");
        MetricRegistry registry = getMetricRegistry();

        annotatedGaugeSites.entrySet().forEach(gaugeSite -> {
            LOGGER.log(Level.FINE, () -> "### gaugeSite " + gaugeSite.toString());
            String gaugeName = gaugeSite.getKey();

            AnnotatedMethodConfigurator<?> site = gaugeSite.getValue();
            DelegatingGauge<?> dg = buildDelegatingGauge(gaugeName, site, bm);
            Gauge gaugeAnnotation = site.getAnnotated().getAnnotation(Gauge.class);
            Metadata md = new Metadata(gaugeName,
                    gaugeAnnotation.displayName(),
                    gaugeAnnotation.description(),
                    MetricType.GAUGE,
                    gaugeAnnotation.unit(),
                    toTags(gaugeAnnotation.tags()));
            LOGGER.log(Level.FINE, () -> String.format("### Registering gauge with metadata %s", md.toString()));
            registry.register(md, dg);
        });

        annotatedGaugeSites.clear();
    }

    private DelegatingGauge<?> buildDelegatingGauge(String gaugeName, AnnotatedMethodConfigurator<?> site, BeanManager bm) {
        Bean<?> bean = bm.getBeans(site.getAnnotated().getJavaMember().getDeclaringClass())
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Cannot find bean for annotated gauge " + gaugeName));
        return DelegatingGauge.newInstance(
                site.getAnnotated().getJavaMember(),
                getReference(bm, bean.getBeanClass(), bean),
                site.getAnnotated().getJavaMember().getReturnType());
    }

    static class AnnotatedElementWrapper implements AnnotatedElement, Member {

        private final AnnotatedMember<?> annotatedMember;

        AnnotatedElementWrapper(AnnotatedMember<?> annotatedMember) {
            this.annotatedMember = annotatedMember;
        }

        @Override
        public boolean isAnnotationPresent(Class<? extends Annotation> annotationClass) {
            return annotatedMember.isAnnotationPresent(annotationClass);
        }

        @Override
        public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
            return annotatedMember.getAnnotation(annotationClass);
        }

        @Override
        public Annotation[] getAnnotations() {
            return annotatedMember.getAnnotations().toArray(new Annotation[] {});
        }

        @Override
        public Annotation[] getDeclaredAnnotations() {
            return getAnnotations();
        }

        @Override
        public Class<?> getDeclaringClass() {
            return annotatedMember.getDeclaringType().getJavaClass();
        }

        @Override
        public String getName() {
            return annotatedMember.getJavaMember().getName();
        }

        @Override
        public int getModifiers() {
            return annotatedMember.getJavaMember().getModifiers();
        }

        @Override
        public boolean isSynthetic() {
            return annotatedMember.getJavaMember().isSynthetic();
        }
    }
}
