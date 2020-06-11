/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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
import java.util.Map;
import java.util.SortedMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Dependent;
import javax.enterprise.context.RequestScoped;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.literal.SingletonLiteral;
import javax.enterprise.inject.se.SeContainer;
import javax.enterprise.inject.se.SeContainerInitializer;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.BeforeBeanDiscovery;
import javax.enterprise.inject.spi.Extension;

import io.helidon.config.mp.MpConfigSources;
import io.helidon.microprofile.server.JaxRsCdiExtension;
import io.helidon.microprofile.server.ServerCdiExtension;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.annotation.Gauge;
import org.jboss.weld.exceptions.DeploymentException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

public class BadGaugeTest {
    private static final String GAUGE_NAME = "BadGaugeTest.gauge";
    private static Config originalConfig;
    private static ConfigProviderResolver resolver;
    private static ClassLoader classLoader;

    @BeforeAll
    static void initClass() {
        resolver = ConfigProviderResolver.instance();
        classLoader = Thread.currentThread().getContextClassLoader();
        originalConfig = resolver.getConfig(classLoader);

        Config config = resolver.getBuilder()
                .withSources(MpConfigSources.create(Map.of("server.port", "0",
                                                           "mp.initializer.allow", "true")))
                .build();
        resolver.registerConfig(config, classLoader);
    }

    @AfterAll
    static void destroyClass() {
        if (originalConfig != null) {
            resolver.registerConfig(originalConfig, classLoader);
        }
    }

    // TODO - remove following Disabled line once MP metrics enforces restriction
    @Disabled
    @Test
    void testBadBean() {
        SeContainerInitializer initializer = SeContainerInitializer.newInstance();
        assertThat(initializer, is(notNullValue()));
        initializer.addBeanClasses(BadGaugedBean.class);
        DeploymentException ex = assertThrows(DeploymentException.class, () -> {
            SeContainer cdiContainer = initializer.initialize();
            cdiContainer.close();
        });

        Throwable cause = ex.getCause();
        assertThat(cause, notNullValue());
        assertThat(cause, is(instanceOf(IllegalArgumentException.class)));

        assertThat(cause.getMessage(), containsString("Error processing @Gauge"));
        assertThat(cause.getMessage(), containsString(BadGaugedBean.class.getName() + ":notAllowed"));

        Throwable subCause = cause.getCause();
        assertThat(subCause, notNullValue());
        assertThat(subCause, is(instanceOf(IllegalArgumentException.class)));
        assertThat(subCause.getMessage(), containsString("assignment-compatible with Number"));
    }

    @Test
    void testAppScoped() {
        goodTest(ApplicationScoped.Literal.INSTANCE);
    }

    @Test
    void testSingleton() {
        goodTest(SingletonLiteral.INSTANCE);
    }

    @Test
    void testDependentScope() {
        // this is questionable, but it may be reporting a static field
        goodTest(Dependent.Literal.INSTANCE);
    }

    @Test
    void testRequestScoped() {
        try (SeContainer ignored = startContainer(RequestScoped.Literal.INSTANCE)) {
            // using try with resources to make sure the container is closed if it does start
            fail("Creating the container should have thrown an exception, as request scoped beans cannot be gauge sources");
        } catch (DeploymentException ignored) {
            // expected
        }
    }

    private SeContainer startContainer(Annotation scope) {
        return SeContainerInitializer.newInstance()
                .disableDiscovery()
                .addExtensions(MetricsCdiExtension.class, ServerCdiExtension.class, JaxRsCdiExtension.class)
                .addExtensions(new ScopeExtension(scope, GaugeBean.class))
                .initialize();
    }

    private void goodTest(Annotation scope) {
        try (SeContainer container = startContainer(scope)) {
            MetricRegistry metricRegistry = MetricsCdiExtension.getMetricRegistry();
            SortedMap<MetricID, org.eclipse.microprofile.metrics.Gauge> gauges = metricRegistry.getGauges();

            org.eclipse.microprofile.metrics.Gauge gauge = null;

            for (Map.Entry<MetricID, org.eclipse.microprofile.metrics.Gauge> entry : gauges.entrySet()) {
                MetricID id = entry.getKey();
                if (GAUGE_NAME.equals(id.getName())) {
                    gauge = entry.getValue();
                }
            }

            assertThat("Gauge should have been found", gauge, notNullValue());

            assertThat(gauge.getValue(), is(7));

            GaugeBean gaugeBean = container.select(GaugeBean.class).get();
            gaugeBean.setValue(42);

            if (scope == Dependent.Literal.INSTANCE) {
                // for dependent scope, the value is reset, as we get a new instance each time
                assertThat(gauge.getValue(), is(7));
            } else {
                assertThat(gauge.getValue(), is(42));
            }
        }
    }

    public static class GaugeBean {
        private final AtomicInteger value = new AtomicInteger(7);

        @Gauge(unit = MetricUnits.SECONDS, name = GAUGE_NAME, absolute = true)
        public int aGauge() {
            return value.get();
        }

        void setValue(int newValue) {
            this.value.set(newValue);
        }
    }

    private static class ScopeExtension implements Extension {
        private Annotation scope;
        private Class<?> beanClass;

        private ScopeExtension(Annotation scope,
                               Class<?> beanClass) {
            this.scope = scope;
            this.beanClass = beanClass;
        }

        void registerBean(@Observes BeforeBeanDiscovery bbd, BeanManager bm) {

            bbd.addAnnotatedType(beanClass, beanClass.getName())
                    .add(scope);
        }
    }
}
