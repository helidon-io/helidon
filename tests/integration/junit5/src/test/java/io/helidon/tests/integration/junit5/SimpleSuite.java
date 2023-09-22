/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.junit5;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;

import io.helidon.tests.integration.junit5.spi.SuiteProvider;

/**
 * Simple test suite to validate basic life-cycle.
 */
public class SimpleSuite implements SuiteProvider {

    private static final Logger LOGGER = System.getLogger(SimpleSuite.class.getName());

    final static String BEFORE_KEY = "SimpleSuite.before";
    final static String AFTER_KEY = "SimpleSuite.after";
    final static String SETUP_CONFIG_KEY = "SimpleSuite.config";
    final static String SETUP_CONTAINER_KEY = "SimpleSuite.container";

    private SuiteContext suiteContext;
    private int counter;

    public SimpleSuite() {
        suiteContext = null;
        counter = 1;
    }

    // Store shared suite context when passed from suite initialization
    @Override
    public void suiteContext(SuiteContext suiteContext) {
        this.suiteContext = suiteContext;
    }

    // Validate that @BeforeSuite is executed
    @BeforeSuite
    public void beforeSuite() {
        LOGGER.log(Level.TRACE,
                   () -> String.format("Running beforeSuite of SimpleSuite test class, order %d", counter));
        suiteContext.storage().put(BEFORE_KEY, counter++);
    }

    // Validate that @AfterSuite is executed
    @AfterSuite
    public void afterSuite() {
        LOGGER.log(Level.TRACE,
                   () -> String.format("Running afterSuite of SimpleSuite test class, order %d", counter));
        suiteContext.storage().put(AFTER_KEY, counter);
    }

    @SetUpConfig
    public void setupConfig() {
        LOGGER.log(Level.TRACE,
                   () -> String.format("Running setupConfig of SimpleSuite test class, order %d", counter));
        suiteContext.storage().put(SETUP_CONFIG_KEY, counter++);
    }

    @SetUpContainer
    public void setupContainer() {
        LOGGER.log(Level.TRACE,
                   () -> String.format("Running setupContainer of SimpleSuite test class, order %d", counter));
        suiteContext.storage().put(SETUP_CONTAINER_KEY, counter++);
    }

}
