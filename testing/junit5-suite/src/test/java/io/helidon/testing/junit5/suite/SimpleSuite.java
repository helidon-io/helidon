/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.testing.junit5.suite;

import java.lang.System.Logger.Level;

import io.helidon.testing.junit5.suite.spi.SuiteProvider;

/**
 * Simple test suite to validate basic life-cycle.
 */
public class SimpleSuite implements SuiteProvider, SuiteStorage {

    final static String BEFORE_KEY = "SimpleSuite.before";
    final static String AFTER_KEY = "SimpleSuite.after";
    private static final System.Logger LOGGER = System.getLogger(SimpleSuite.class.getName());
    private int counter;

    public SimpleSuite() {
        counter = 1;
    }

    // Validate that @BeforeSuite is executed
    @BeforeSuite
    public void beforeSuite(Storage storage) {
        LOGGER.log(Level.TRACE,
                   () -> String.format("Running beforeSuite of SimpleSuite test class, order %d", counter));
        storage.put(BEFORE_KEY, counter++);
    }

    // Validate that @AfterSuite is executed
    @AfterSuite
    public void afterSuite(Storage storage) {
        LOGGER.log(Level.TRACE,
                   () -> String.format("Running afterSuite of SimpleSuite test class, order %d", counter));
        storage.put(AFTER_KEY, counter);
    }

}
