/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;

import javax.enterprise.inject.se.SeContainer;
import javax.enterprise.inject.se.SeContainerInitializer;
import javax.enterprise.inject.spi.CDI;

import io.helidon.config.mp.MpConfigSources;
import io.helidon.microprofile.server.ServerCdiExtension;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

public abstract class AbstractCDITest {
    protected SeContainer cdiContainer;

    protected Map<String, String> cdiConfig() {
        return Collections.emptyMap();
    }

    protected void cdiBeanClasses(Set<Class<?>> classes) {
        //noop
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
        try {
            cdiContainer.close();
        } catch (Throwable t) {
            //emergency cleanup see #1446
            stopCdiContainer();
        }
    }


    protected <T> void forEachBean(Class<T> beanType, Annotation annotation, Consumer<T> consumer) {
        cdiContainer.select(beanType, annotation).stream().forEach(consumer);
    }

    protected void assertAllReceived(CountableTestBean bean) {
        try {
            assertThat("All messages not delivered in time, number of unreceived messages: "
                            + bean.getTestLatch().getCount(),
                    bean.getTestLatch().await(2, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            fail(e);
        }
    }

    protected static SeContainer startCdiContainer(Map<String, String> p, Class<?>... beanClasses) {
        return startCdiContainer(p, new HashSet<>(Arrays.asList(beanClasses)));
    }

    private static SeContainer startCdiContainer(Map<String, String> p, Set<Class<?>> beanClasses) {
        Config config = ConfigProviderResolver.instance().getBuilder()
                .withSources(MpConfigSources.create(p),
                        MpConfigSources.create(Map.of("mp.initializer.allow", "true")))
                .build();

        ConfigProviderResolver.instance()
                .registerConfig(config, Thread.currentThread().getContextClassLoader());

        final SeContainerInitializer initializer = SeContainerInitializer.newInstance();
        initializer.addBeanClasses(beanClasses.toArray(new Class<?>[0]));
        return initializer.initialize();
    }

    protected static void stopCdiContainer() {
        try {
            ServerCdiExtension server = CDI.current()
                    .getBeanManager()
                    .getExtension(ServerCdiExtension.class);

            if (server.started()) {
                SeContainer container = (SeContainer) CDI.current();
                container.close();
            }
        } catch (IllegalStateException e) {
            //noop container is not running
        }
    }

    protected static final class CdiTestCase {
        private final String name;
        private final Class<?>[] clazzes;

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
        public Stream<Class<? extends AsyncTestBean>> getAsyncBeanClasses() {
            return Arrays.stream(clazzes)
                    .filter(AsyncTestBean.class::isAssignableFrom)
                    .map(c -> (Class<? extends AsyncTestBean>) c);
        }

        @SuppressWarnings("unchecked")
        public Stream<Class<? extends CountableTestBean>> getCountableBeanClasses() {
            return Arrays.stream(clazzes)
                    .filter(CountableTestBean.class::isAssignableFrom)
                    .map(c -> (Class<? extends CountableTestBean>) c);
        }

        @SuppressWarnings("unchecked")
        public Stream<Class<? extends AssertableTestBean>> getCompletableBeanClasses() {
            return Arrays.stream(clazzes)
                    .filter(AssertableTestBean.class::isAssignableFrom)
                    .map(c -> (Class<? extends AssertableTestBean>) c);
        }
    }
}
