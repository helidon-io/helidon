/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
import java.lang.reflect.Member;
import java.lang.reflect.Type;
import java.time.Duration;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;

import io.helidon.metrics.Registry;
import io.helidon.metrics.api.RegistrySettings;
import jakarta.enterprise.inject.spi.Annotated;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.enterprise.util.AnnotationLiteral;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.Tag;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.lessThan;

public class MetricLookupTest {

    private static final int TOTAL_COUNTERS = 5000;
    private static final Logger LOGGER = Logger.getLogger(MetricLookupTest.class.getName());

    @Test
    void testCounterLookup() {
        Registry registry = Registry.create(MetricRegistry.Type.APPLICATION, RegistrySettings.create());
        MetricProducer metricProducer = new MetricProducer();

        // register TOTAL_COUNTERS counters in registry
        IntStream.range(0, TOTAL_COUNTERS).forEach(i -> {
            MetricID metricID = new MetricID("myMetric", new Tag("tag", Integer.toString(i)));
            registry.counter(metricID);
        });

        // lookup TOTAL_COUNTERS counters using MetricProducer
        long start = System.nanoTime();
        IntStream.range(0, TOTAL_COUNTERS).forEach(i -> {
            MetricID metricID = new MetricID("myMetric", new Tag("tag", Integer.toString(i)));
            Counter counter = metricProducer.produceMetric(registry,
                    new TestInjectionPoint(metricID),
                    null,
                    null,
                    Counter.class);
            assertThat(counter, is(notNullValue()));
        });

        // verify lookup time is bounded
        int elapsed = (int) Duration.ofNanos(System.nanoTime() - start).toMillis();
        LOGGER.log(Level.INFO, "Elapsed is " + elapsed + " milliseconds");
        assertThat(elapsed, lessThan(1000));        // very loose upper bound of 1 second
    }

    // -- Auxiliary classes ----------------------------------------------------

    static class TestInjectionPoint implements InjectionPoint {

        private final MetricID metricID;

        TestInjectionPoint(MetricID metricID) {
            this.metricID = metricID;
        }

        @Override
        public Type getType() {
            return null;
        }

        @Override
        public Set<Annotation> getQualifiers() {
            return null;
        }

        @Override
        public Bean<?> getBean() {
            return null;
        }

        @Override
        public Member getMember() {
            return null;
        }

        @Override
        public Annotated getAnnotated() {
            return new Annotated() {
                @Override
                public Type getBaseType() {
                    return null;
                }

                @Override
                public Set<Type> getTypeClosure() {
                    return null;
                }

                @Override
                @SuppressWarnings("unchecked")
                public <T extends Annotation> T getAnnotation(Class<T> annotationType) {
                    return (T) new MetricLiteral(metricID);
                }

                @Override
                public <T extends Annotation> Set<T> getAnnotations(Class<T> annotationType) {
                    return null;
                }

                @Override
                public Set<Annotation> getAnnotations() {
                    return null;
                }

                @Override
                public boolean isAnnotationPresent(Class<? extends Annotation> annotationType) {
                    return false;
                }
            };
        }

        @Override
        public boolean isDelegate() {
            return false;
        }

        @Override
        public boolean isTransient() {
            return false;
        }
    }

    static class MetricLiteral extends AnnotationLiteral<org.eclipse.microprofile.metrics.annotation.Metric>
            implements org.eclipse.microprofile.metrics.annotation.Metric {

        private final MetricID metricID;

        MetricLiteral(MetricID metricID) {
            this.metricID = metricID;
        }

        @Override
        public String name() {
            return metricID.getName();
        }

        @Override
        public String[] tags() {
            return metricID.getTagsAsList()
                    .stream()
                    .map(t -> t.getTagName() + "=" + t.getTagValue())
                    .toArray(String[]::new);
        }

        @Override
        public boolean absolute() {
            return true;
        }

        @Override
        public String displayName() {
            return metricID.getName();
        }

        @Override
        public String description() {
            return null;
        }

        @Override
        public String unit() {
            return null;
        }
    }
}
