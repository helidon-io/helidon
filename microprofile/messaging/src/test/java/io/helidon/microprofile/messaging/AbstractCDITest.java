/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.messaging;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.messaging.kafka.connector.KafkaConnectorFactory;
import io.helidon.microprofile.config.MpConfig;
import io.helidon.microprofile.config.MpConfigProviderResolver;
import io.helidon.microprofile.messaging.kafka.KafkaCdiExtensionTest;
import io.helidon.microprofile.server.Server;
import org.eclipse.microprofile.reactive.messaging.spi.Connector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import javax.enterprise.inject.se.SeContainer;
import javax.enterprise.inject.se.SeContainerInitializer;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.LogManager;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public abstract class AbstractCDITest {

    static {
        try (InputStream is = KafkaCdiExtensionTest.class.getResourceAsStream("/logging.properties")) {
            LogManager.getLogManager().readConfiguration(is);
        } catch (IOException e) {
            fail(e);
        }
    }

    protected static final Connector KAFKA_CONNECTOR_LITERAL = new Connector() {

        @Override
        public Class<? extends Annotation> annotationType() {
            return Connector.class;
        }

        @Override
        public String value() {
            return KafkaConnectorFactory.CONNECTOR_NAME;
        }
    };

    protected SeContainer cdiContainer;

    protected Map<String, String> cdiConfig() {
        return Collections.emptyMap();
    }

    protected void cdiBeanClasses(Set<Class<?>> classes) {

    }

    @BeforeEach
    public void setUp() {
        Set<Class<?>> classes = new HashSet<>();
        cdiBeanClasses(classes);
        Map<String, String> p = new HashMap<>(cdiConfig());
        cdiContainer = startCdiContainer(p, classes);
    }

    @AfterEach
    public void tearDown() {
        if (cdiContainer != null) {
            cdiContainer.close();
        }
    }


    protected <T> void forEachBean(Class<T> beanType, Annotation annotation, Consumer<T> consumer) {
        cdiContainer.select(beanType, annotation).stream().forEach(consumer);
    }

    protected void assertAllReceived(CountableTestBean bean) {
        try {
            assertTrue(bean.getTestLatch().await(2, TimeUnit.SECONDS)
                    , "All messages not delivered in time, number of unreceived messages: "
                            + bean.getTestLatch().getCount());
        } catch (InterruptedException e) {
            fail(e);
        }
    }

    protected static SeContainer startCdiContainer(Map<String, String> p, Class<?>... beanClasses) {
        return startCdiContainer(p, new HashSet<>(Arrays.asList(beanClasses)));
    }

    private static SeContainer startCdiContainer(Map<String, String> p, Set<Class<?>> beanClasses) {
        Config config = Config.builder()
                .sources(ConfigSources.create(p))
                .build();

        final Server.Builder builder = Server.builder();
        assertNotNull(builder);
        builder.config(config);
        MpConfigProviderResolver.instance()
                .registerConfig(MpConfig.builder()
                                .config(config).build(),
                        Thread.currentThread().getContextClassLoader());
        final SeContainerInitializer initializer = SeContainerInitializer.newInstance();
        assertThat(initializer, is(notNullValue()));
        initializer.addBeanClasses(beanClasses.toArray(new Class<?>[0]));
        return initializer.initialize();
    }

    protected static final class CdiTestCase {
        private String name;
        private Class<?>[] clazzes;

        private CdiTestCase(String name, Class<?>... clazzes) {
            this.name = name;
            this.clazzes = clazzes;
        }

        public static CdiTestCase from(Class<?> clazz) {
            return new CdiTestCase(clazz.getSimpleName(), clazz);
        }

        public static CdiTestCase from(String name, Class<?>... clazzes) {
            return new CdiTestCase(name, clazzes);
        }

        @Override
        public String toString() {
            return name;
        }

        public Class<?>[] getClazzes() {
            return clazzes;
        }

        public Optional<? extends Class<? extends Throwable>> getExpectedThrowable() {
            return Arrays.stream(clazzes)
                    .filter(c -> c.getAnnotation(AssertThrowException.class) != null)
                    .map(c -> c.getAnnotation(AssertThrowException.class).value())
                    .findFirst();
        }

        @SuppressWarnings("unchecked")
        public List<Class<? extends CountableTestBean>> getCountableBeanClasses() {
            return Arrays.stream(clazzes)
                    .filter(CountableTestBean.class::isAssignableFrom)
                    .map(c -> (Class<? extends CountableTestBean>) c)
                    .collect(Collectors.toList());
        }

        @SuppressWarnings("unchecked")
        public List<Class<? extends CompletableTestBean>> getCompletableBeanClasses() {
            return Arrays.stream(clazzes)
                    .filter(CompletableTestBean.class::isAssignableFrom)
                    .map(c -> (Class<? extends CompletableTestBean>) c)
                    .collect(Collectors.toList());
        }
    }
}
