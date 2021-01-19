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

import javax.enterprise.inject.se.SeContainer;
import javax.enterprise.inject.se.SeContainerInitializer;
import javax.enterprise.inject.spi.DeploymentException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

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

    static class UnresolvedFixedDelay {
        @FixedRate
        void unresolvedDelay() {
        }
    }

    static class ZeroRateBean {
        @FixedRate(0)
        void zeroRate() {
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
    void unresolvedFixedDelay() {
        assertDeploymentException(IllegalArgumentException.class, UnresolvedFixedDelay.class);
    }

    @SuppressWarnings("unchecked")
    void assertDeploymentException(Class<? extends Throwable> expected, Class<?>... beans) {
        System.setProperty("mp.initializer.allow", "true");
        SeContainerInitializer initializer = SeContainerInitializer.newInstance();
        initializer.addExtensions(SchedulingCdiExtension.class);
        initializer.addBeanClasses(beans);
        try (SeContainer container = initializer.initialize()) {
            fail("Expected " + expected.getName());
        } catch (AssertionFailedError e) {
            throw e;
        } catch (Throwable e) {
            assertEquals(expected, e.getClass());
        }
    }
}
