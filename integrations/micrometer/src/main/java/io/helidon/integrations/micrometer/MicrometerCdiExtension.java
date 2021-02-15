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
 *
 */
package io.helidon.integrations.micrometer;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.BeforeBeanDiscovery;
import javax.enterprise.inject.spi.ProcessAnnotatedType;
import javax.enterprise.inject.spi.ProcessProducerField;
import javax.enterprise.inject.spi.ProcessProducerMethod;
import javax.enterprise.inject.spi.WithAnnotations;
import javax.enterprise.util.AnnotationLiteral;
import javax.enterprise.util.Nonbinding;
import javax.interceptor.InterceptorBinding;

import io.helidon.common.servicesupport.cdi.AnnotationLookupResult;
import io.helidon.common.servicesupport.cdi.CdiExtensionBase;

import io.micrometer.core.annotation.Counted;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * CDI extension for handling Micrometer artifacts.
 */
public class MicrometerCdiExtension extends CdiExtensionBase<
        MicrometerCdiExtension.MicrometerAsyncResponseInfo,
        MicrometerCdiExtension.MicrometerRestEndpointInfo,
        MicrometerSupport,
        MicrometerSupport.Builder> {

    private static final Logger LOGGER = Logger.getLogger(MicrometerCdiExtension.class.getName());

    private static final List<Class<? extends Annotation>> METRIC_ANNOTATIONS
            = Arrays.asList(Counted.class, Timed.class);

    private final MeterRegistry meterRegistry;

    /**
     * Creates new extension instance.
     */
    public MicrometerCdiExtension() {
        super(LOGGER, Set.of(Counted.class, Timed.class), MeterProducer.class, config ->
                        MeterRegistryProducer.getMicrometerSupport(),
                "micrometer");
        meterRegistry = MeterRegistryProducer.getMeterRegistry();
    }

    @Override
    protected <E extends Member & AnnotatedElement>
    void register(E element, Class<?> clazz, AnnotationLookupResult<? extends Annotation> lookupResult) {
        Annotation annotation = lookupResult.getAnnotation();

        if (annotation instanceof Counted) {
            Counter counter = MeterProducer.produceCounter(meterRegistry, (Counted) annotation);
            LOGGER.log(Level.FINE, () -> "Registered counter " + counter.getId().toString());
        } else if (annotation instanceof Timed) {
            Timed timed = (Timed) annotation;
            if (timed.longTask()) {
                LongTaskTimer longTaskTimer = MeterProducer.produceLongTaskTimer(meterRegistry, timed);
                LOGGER.log(Level.FINE, () -> "Registered long task timer " + longTaskTimer.getId()
                        .toString());
            } else {
                Timer timer = MeterProducer.produceTimer(meterRegistry, timed);
                LOGGER.log(Level.FINE, () -> "Registered timer " + timer.getId()
                        .toString());
            }
        }
    }

    /**
     * Initializes the extension prior to bean discovery.
     *
     * @param discovery bean discovery event
     */
    @Override
    protected void before(@Observes BeforeBeanDiscovery discovery) {
        LOGGER.log(Level.FINE, () -> "Before bean discovery " + discovery);
        super.before(discovery);

        // Initialize our implementation
        MeterRegistryProducer.clear();

        // Register types manually
        discovery.addAnnotatedType(MeterRegistryProducer.class, "MeterRegistryProducer");
        discovery.addAnnotatedType(MeterProducer.class, "MeterProducer");

        prepareInterceptor(discovery, Counted.class, InterceptorCounted.class, CountedLiteral.INSTANCE);
        prepareInterceptor(discovery, Timed.class, InterceptorTimed.class, TimedLiteral.INSTANCE);
    }

    @Override
    protected MicrometerRestEndpointInfo newRestEndpointInfo() {
        return new MicrometerRestEndpointInfo();
    }

    @Override
    protected MicrometerAsyncResponseInfo newAsyncResponseInfo(Method method) {
        int slot = asyncParameterSlot(method);
        return slot >= 0 ? new MicrometerAsyncResponseInfo(slot) : null;
    }

    protected void recordProducerFields(@Observes ProcessProducerField<? extends Meter, ?> ppf) {
        recordProducerField(ppf);
    }

    protected void recordProducerMethods(@Observes ProcessProducerMethod<? extends Meter, ?> ppm) {
        recordProducerMethod(ppm);
    }

    private static <A extends Annotation, M extends Meter, I extends InterceptorBase<M, A>>
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
        bbd.addAnnotatedType(interceptorClass, interceptorClass.getSimpleName())
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
        recordConcreteNonInterceptor(pat);
    }

    static class MicrometerRestEndpointInfo extends RestEndpointInfo {
    }

    static class MicrometerAsyncResponseInfo extends AsyncResponseInfo {

        MicrometerAsyncResponseInfo(int parameterSlot) {
            super(parameterSlot);
        }
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
}
