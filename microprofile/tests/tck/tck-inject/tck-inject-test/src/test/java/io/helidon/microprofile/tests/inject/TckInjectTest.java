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

package io.helidon.microprofile.tests.inject;

import static org.junit.Assert.fail;

import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.se.SeContainerInitializer;
import junit.framework.TestResult;
import org.atinject.tck.Tck;
import org.atinject.tck.auto.Car;
import org.atinject.tck.auto.Convertible;
import org.atinject.tck.auto.DriversSeat;
import org.atinject.tck.auto.FuelTank;
import org.atinject.tck.auto.Seat;
import org.atinject.tck.auto.Seatbelt;
import org.atinject.tck.auto.Tire;
import org.atinject.tck.auto.V8Engine;
import org.atinject.tck.auto.accessories.Cupholder;
import org.atinject.tck.auto.accessories.RoundThing;
import org.atinject.tck.auto.accessories.SpareTire;
import org.junit.jupiter.api.Test;

class TckInjectTest {
    
    @Test
    void test() {
        SeContainer container = SeContainerInitializer
                .newInstance()
                .addExtensions(weld.AtInjectTCKExtension.class)
                .addBeanClasses(Car.class, Convertible.class, DriversSeat.class, FuelTank.class, Seat.class,
                        Seatbelt.class, Tire.class, V8Engine.class, Cupholder.class, RoundThing.class,
                        SpareTire.class)
                .initialize();
        Car tckCar = container.select(Car.class).get();
        junit.framework.Test suite = Tck.testsFor(tckCar, false, true);
        TestResult result = new TestResult();
        suite.run(result);
        String results = printResults(result);
        if (!result.wasSuccessful()) {
            fail(results);
        } else {
            System.out.println(results);
        }
    }

    // Tests run: 1, Failures: 0, Errors: 1
    private String printResults(TestResult result) {
        StringBuilder builder = new StringBuilder();
        builder.append("Tests run: ").append(result.runCount());
        builder.append(", Failures: ").append(result.failureCount());
        builder.append(", Errors: ").append(result.errorCount());
        return builder.toString();
    }
}
