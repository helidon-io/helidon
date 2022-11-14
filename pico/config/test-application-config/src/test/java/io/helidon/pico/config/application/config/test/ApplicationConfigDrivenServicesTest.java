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

package io.helidon.pico.config.application.config.test;

import java.util.Objects;

import io.helidon.pico.config.test.ConfigDrivenServicesTest;
import io.helidon.pico.testsupport.TestableServices;

import org.junit.jupiter.api.AfterEach;

/**
 * The main point of this is to ensure application-binding usages of pico has no affect on behavior.
 * We are running all the tests from super here.
 */
public class ApplicationConfigDrivenServicesTest extends ConfigDrivenServicesTest {

    Integer startingLookups;
    Integer finishingLookups;

    @Override
    public TestableServices getServices() {
        TestableServices services = super.getServices();
        if (Objects.isNull(startingLookups)) {
            startingLookups = Objects.isNull(services) ? null : services.lookupCount();
        }
        return services;
    }

    @AfterEach
    @Override
    public void tearDown() {
        TestableServices services = getServices();
        finishingLookups = Objects.isNull(services) ? null : services.lookupCount();
        super.tearDown();
    }

}
