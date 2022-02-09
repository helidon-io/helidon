/*
 * Copyright (c) 2021, 2022 Oracle and/or its affiliates.
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
package io.helidon.integrations.micrometer.cdi;

import java.lang.annotation.Annotation;
import java.lang.reflect.Executable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import io.helidon.integrations.micrometer.MicrometerSupport;
import io.helidon.microprofile.server.ServerCdiExtension;
import io.helidon.servicecommon.restcdi.HelidonRestCdiExtension;
import io.helidon.webserver.Routing;

import io.micrometer.core.annotation.Counted;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.AnnotatedCallable;
import jakarta.enterprise.inject.spi.AnnotatedType;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.BeforeBeanDiscovery;
import jakarta.enterprise.inject.spi.ProcessAnnotatedType;
import jakarta.enterprise.inject.spi.ProcessManagedBean;
import jakarta.enterprise.inject.spi.ProcessProducerField;
import jakarta.enterprise.inject.spi.ProcessProducerMethod;
import jakarta.enterprise.inject.spi.WithAnnotations;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.enterprise.util.Nonbinding;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InterceptorBinding;

import static jakarta.interceptor.Interceptor.Priority.LIBRARY_BEFORE;

/**
 * CDI extension for handling Micrometer artifacts.
 */
public class MicrometerCdiExtension extends HelidonRestCdiExtension<MicrometerSupport> {

    private static final Logger LOGGER = Logger.getLogger(MicrometerCdiExtension.class.getName());

    private static final List<Class<? extends Annotation>> METRIC_ANNOTATIONS
            = Arrays.asList(Counted.class, Timed.class);

    private final List<DeferredMeterCreation> annotationsForMeters = new ArrayList<>();

    private final WorkItemsManager<MeterWorkItem> workItemsManager = WorkItemsManager.create();

    /**
     * Creates new extension instance.
     */
    public MicrometerCdiExtension() {
        super(LOGGER, MicrometerSupport::create, "micrometer");
    }

    MeterRegistry meterRegistry() {
        return serviceSupport().registry();
    }

    @Override
    protected void processManagedBean(ProcessManagedBean<?> pmb) {

        AnnotatedType<?> type = pmb.getAnnotatedBeanClass();
        Class<?> clazz = pmb.getAnnotatedBeanClass().getJavaClass();

        // Check for Interceptor. We have already checked developer-provided beans, but other extensions might have supplied
        // additional beans that we have not checked yet.
        if (type.isAnnotationPresent(Interceptor.class)) {
            LOGGER.log(Level.FINE, "Ignoring objects defined on type " + clazz.getName()
                    + " because a CDI portable extension added @Interceptor to it dynamically");
            return;
        }

        // Record the annotation information so we can create the meters later, after we can get the runtime configuration
        // which could affect the creation of the MeterRegistry.
        Stream.of(pmb.getAnnotatedBeanClass().getMethods(),
                  pmb.getAnnotatedBeanClass().getConstructors())
                .flatMap(Set::stream)
                // For executables, register the object only on the declaring
                // class, not subclasses in alignment with the MP Metrics 2.0 TCK
                // VisibilityTimedMethodBeanTest.
                .filter(annotatedCallable -> clazz.equals(annotatedCallable.getDeclaringType().getJavaClass()))
                .forEach(annotatedCallable ->
                        Stream.of(Counted.class, Timed.class)
                                .forEach(annotationType -> {
                                    annotatedCallable.getAnnotations(annotationType).stream()
                                            .forEach(annotation -> recordMeterToCreate(annotation, annotatedCallable));
                                }));
    }

    /**
     * Registers the service-related endpoint, after security and as CDI initializes the app scope, returning the default routing
     * for optional use by the caller.
     *
     * @param adv    app-scoped initialization event
     * @param bm     BeanManager
     * @return default routing
     */
    @Override
    public Routing.Builder registerService(
            @Observes @Priority(LIBRARY_BEFORE + 10) @Initialized(ApplicationScoped.class) Object adv,
            BeanManager bm, ServerCdiExtension serverCdiExtension) {
        Routing.Builder result = super.registerService(adv, bm, serverCdiExtension);

        MeterRegistry meterRegistry = serviceSupport().registry();

        annotationsForMeters.forEach(deferredAnnotation -> {

            Annotation annotation = deferredAnnotation.annotation;
            AnnotatedCallable<?> annotatedCallable = deferredAnnotation.annotatedCallable;
            Meter newMeter;
            boolean isOnlyOnException = false;

            if (annotation instanceof Counted) {
                Counter counter = MeterProducer.produceCounter(meterRegistry, (Counted) annotation);
                LOGGER.log(Level.FINE, () -> "Registered counter " + counter.getId()
                        .toString());
                newMeter = counter;
                isOnlyOnException = ((Counted) annotation).recordFailuresOnly();
            } else {
                Timed timed = (Timed) annotation;
                if (timed.longTask()) {
                    LongTaskTimer longTaskTimer =
                            MeterProducer.produceLongTaskTimer(meterRegistry, timed);
                    LOGGER.log(Level.FINE, () -> "Registered long task timer " + longTaskTimer.getId()
                            .toString());
                    newMeter = longTaskTimer;
                } else {
                    Timer timer = MeterProducer.produceTimer(meterRegistry, timed);
                    LOGGER.log(Level.FINE, () -> "Registered timer " + timer.getId()
                            .toString());
                    newMeter = timer;
                }
            }
            workItemsManager.put(Executable.class.cast(annotatedCallable.getJavaMember()),
                    annotation.annotationType(),
                    MeterWorkItem.create(newMeter, isOnlyOnException));
        });
        return result;
    }


    /**
     * Initializes the extension prior to bean discovery.
     *
     * @param discovery bean discovery event
     */
    protected void before(@Observes BeforeBeanDiscovery discovery) {
        LOGGER.log(Level.FINE, () -> "Before bean discovery " + discovery);

        // Register types manually
        discovery.addAnnotatedType(MeterRegistryProducer.class, MeterRegistryProducer.class.getName());
        discovery.addAnnotatedType(MeterProducer.class, MeterProducer.class.getName());

        prepareInterceptor(discovery, Counted.class, InterceptorCounted.class, CountedLiteral.INSTANCE);
        prepareInterceptor(discovery, Timed.class, InterceptorTimed.class, TimedLiteral.INSTANCE);
    }

    protected void recordProducerFields(@Observes ProcessProducerField<? extends Meter, ?> ppf) {
        if (!isOwnProducerOrNonDefaultQualified(ppf.getBean(), MeterProducer.class)) {
            recordProducerField(ppf);
        }
    }

    protected void recordProducerMethods(@Observes ProcessProducerMethod<? extends Meter, ?> ppm) {
        if (!isOwnProducerOrNonDefaultQualified(ppm.getBean(), MeterProducer.class)) {
            recordProducerMethod(ppm);
        }
    }

    Iterable<MeterWorkItem> workItems(Executable executable, Class<? extends Annotation> annotationType) {
        return workItemsManager.workItems(executable, annotationType);
    }

    static class MeterWorkItem {
        private final Meter meter;
        private final boolean isOnlyOnException;

        static <M extends Meter> MeterWorkItem create(M meter, boolean isOnlyOnException) {
            return new MeterWorkItem(meter, isOnlyOnException);
        }

        private MeterWorkItem(Meter meter, boolean isOnlyOnException) {
            this.meter = meter;
            this.isOnlyOnException = isOnlyOnException;
        }

        Meter meter() {
            return meter;
        }

        boolean isOnlyOnException() {
            return isOnlyOnException;
        }
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

    private static <A extends Annotation, M extends Meter, I extends MicrometerInterceptorBase<M>>
    void prepareInterceptor(BeforeBeanDiscovery bbd,
            Class<A> annotationType,
            Class<I> interceptorClass,
            AnnotationLiteral<A> literal) {

        /*
         * The Micrometer annotations do not have @InterceptorBinding. So:
         * 1. Add @InterceptorBinding to each.
         * 2. Mark all methods on each annotation as nonbinding; we want the same interceptor type for each usage of the same
         * meter.
         * 3. Add the interceptor class to CDI, augmenting it with a literal for the corresponding meter annotation.
         */
        bbd.configureInterceptorBinding(annotationType)
                .add(InterceptorBindingLiteral.INSTANCE)
                .methods()
                .forEach(m -> m.add(Nonbinding.Literal.INSTANCE));
        bbd.addAnnotatedType(interceptorClass, interceptorClass.getName())
                .add(literal);
    }

    /**
     * Records Java classes with a metrics annotation somewhere.
     *
     * By recording the classes here, we let CDI optimize its invocations of this observer method. Later, when we
     * observe managed beans (which CDI invokes for all managed beans) where we also have to examine each method and
     * constructor, we can quickly eliminate from consideration any classes we have not recorded here.
     *
     * @param pat ProcessAnnotatedType event
     */
    private void recordMetricAnnotatedClass(@Observes
    @WithAnnotations({Counted.class, Timed.class}) ProcessAnnotatedType<?> pat) {
        if (isConcreteNonInterceptor(pat)) {
            recordAnnotatedType(pat);
        }
    }

    private void recordMeterToCreate(Annotation annotation, AnnotatedCallable<?> annotatedCallable) {
        LOGGER.log(Level.FINE, () -> "Recording meter for deferred creation per annotation " + annotation);
        annotationsForMeters.add(new DeferredMeterCreation(annotation, annotatedCallable));
    }

    static final class InterceptorBindingLiteral extends AnnotationLiteral<InterceptorBinding> implements InterceptorBinding {

        static final InterceptorBindingLiteral INSTANCE = new InterceptorBindingLiteral();

        private static final long serialVersionUID = 1L;

    }

    static final class CountedLiteral extends AnnotationLiteral<Counted> implements Counted {

        static final CountedLiteral INSTANCE = new CountedLiteral();

        private static final long serialVersionUID = 1L;

        @Override
        public String value() {
            return "";
        }

        @Override
        public boolean recordFailuresOnly() {
            return false;
        }

        @Override
        public String[] extraTags() {
            return new String[0];
        }

        @Override
        public String description() {
            return "";
        }
    }

    static final class TimedLiteral extends AnnotationLiteral<Timed> implements Timed {

        static final TimedLiteral INSTANCE = new TimedLiteral();

        private static final long serialVersionUID = 1L;

        @Override
        public String value() {
            return "";
        }

        @Override
        public String[] extraTags() {
            return new String[0];
        }

        @Override
        public boolean longTask() {
            return false;
        }

        @Override
        public double[] percentiles() {
            return new double[0];
        }

        @Override
        public boolean histogram() {
            return false;
        }

        @Override
        public String description() {
            return "";
        }
    }

    private static class DeferredMeterCreation {
        private final Annotation annotation;
        private final AnnotatedCallable<?> annotatedCallable;

        private DeferredMeterCreation(Annotation annotation, AnnotatedCallable<?> annotatedCallable) {
            this.annotation = annotation;
            this.annotatedCallable = annotatedCallable;
        }
    }
}
