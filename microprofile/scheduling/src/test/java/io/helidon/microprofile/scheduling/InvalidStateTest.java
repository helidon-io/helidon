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

package io.helidon.microprofile.scheduling;

import java.util.Map;

import javax.enterprise.inject.se.SeContainer;
import javax.enterprise.inject.se.SeContainerInitializer;
import javax.enterprise.inject.spi.DeploymentException;

import io.helidon.config.mp.MpConfigSources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

public class InvalidStateTest {

    static class InvalidCronBean {
        @Scheduled("invalid cron")
        void invalidCron() {
        }
    }

    static class DoubleAnnotationBean {
        @Scheduled("0/2 * * * * ? *")
        @FixedRate(2)
        void invalidAnnotations() {
        }
    }

    static class UnresolvedPlaceholderBean {
        @Scheduled("${unresolved}")
        void invalidAnnotations() {
        }
    }

    static class NegativeDelay {
        @FixedRate(-1)
        void negativeDelay() {
        }
    }

    static class ZeroRateBean {
        @FixedRate(0)
        void zeroRate() {
        }
    }

    static class InvalidTimeUnit {
        @FixedRate(2)
        void invalidTimeUnitMethod() {
        }
    }

    @Test
    void zeroRate() {
        assertDeploymentException(IllegalArgumentException.class, ZeroRateBean.class);
    }

    @Test
    void invalidCron() {
        assertDeploymentException(IllegalArgumentException.class, InvalidCronBean.class);
    }

    @Test
    void invalidAnnotations() {
        assertDeploymentException(DeploymentException.class, DoubleAnnotationBean.class);
    }

    @Test
    void unresolvedCronPlaceholder() {
        assertDeploymentException(IllegalArgumentException.class, UnresolvedPlaceholderBean.class);
    }

    @Test
    void negativeDelay() {
        assertDeploymentException(IllegalArgumentException.class, NegativeDelay.class);
    }

    @Test
    void invalidTimeUnit() {
        assertDeploymentException(IllegalArgumentException.class,
                Map.of(InvalidTimeUnit.class.getName() + ".invalidTimeUnitMethod.schedule.time-unit", "LIGHT_YEAR"),
                InvalidTimeUnit.class);
    }

    void assertDeploymentException(Class<? extends Throwable> expected, Class<?>... beans) {
        assertDeploymentException(expected, Map.of(), beans);
    }

    @SuppressWarnings("unchecked")
    void assertDeploymentException(Class<? extends Throwable> expected, Map<String, String> configMap, Class<?>... beans) {
        Config config = ConfigProviderResolver.instance().getBuilder()
                .withSources(MpConfigSources.create(configMap),
                        MpConfigSources.create(Map.of("mp.initializer.allow", "true")))
                .build();

        ConfigProviderResolver.instance()
                .registerConfig(config, Thread.currentThread().getContextClassLoader());

        SeContainerInitializer initializer = SeContainerInitializer.newInstance();
        initializer.addExtensions(SchedulingCdiExtension.class);
        initializer.addBeanClasses(beans);
        try (SeContainer c = initializer.initialize()) {
            fail("Expected " + expected.getName());
        } catch (AssertionFailedError e) {
            throw e;
        } catch (Throwable e) {
            assertEquals(expected, e.getClass());
        }
    }
}
