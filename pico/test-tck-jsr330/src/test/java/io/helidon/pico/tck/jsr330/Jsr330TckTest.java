/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.pico.tck.jsr330;

import java.util.Enumeration;

import io.helidon.pico.PicoServices;
import io.helidon.pico.PicoServicesConfig;
import io.helidon.pico.spi.impl.DefaultPicoServices;

import jakarta.inject.Provider;
import junit.framework.TestFailure;
import junit.framework.TestResult;
import org.atinject.tck.Tck;
import org.atinject.tck.auto.Car;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Jsr-330 TCK Testing.
 *
 * This test requires the annotation processing and the maven-plugin to run - see pom.xml.
 */
public class Jsr330TckTest {

    /**
     * Run's the TCK tests.
     */
    @Test
    public void runIt() {
        PicoServices picoServices = DefaultPicoServices.getInstance();
        assertNotNull(picoServices);
        Provider<Car> carProvider = picoServices.services().lookupFirst(Car.class);
        assertNotNull(carProvider);
        assertNotNull(carProvider.get());
        assertNotSame(carProvider.get(), carProvider.get());
        junit.framework.Test jsrTest = Tck.testsFor(carProvider.get(),
                                                    picoServices.config().get().value(PicoServicesConfig.KEY_SUPPORTS_JSR330_STATIC,
                                                                                      PicoServicesConfig.defaultValue(PicoServicesConfig.DEFAULT_SUPPORTS_STATIC)),
                                                    picoServices.config().get().value(PicoServicesConfig.KEY_SUPPORTS_JSR330_PRIVATE,
                                                                                      PicoServicesConfig.defaultValue(PicoServicesConfig.DEFAULT_SUPPORTS_PRIVATE)));
        TestResult result = new TestResult();
        jsrTest.run(result);
        assertTrue(result.runCount() > 0);
        assertTrue(result.wasSuccessful(), toFailureReport(result));
    }

    static String toFailureReport(TestResult result) {
        StringBuilder builder = new StringBuilder();
        int count = 0;
        Enumeration<TestFailure> failures = result.failures();
        while (failures.hasMoreElements()) {
            TestFailure failure = failures.nextElement();
            builder.append("\nFAILURE #").append(++count).append(" : ")
                    .append(failure.trace())
                    .append("\n");
        }
        return builder.toString();
    }

}
