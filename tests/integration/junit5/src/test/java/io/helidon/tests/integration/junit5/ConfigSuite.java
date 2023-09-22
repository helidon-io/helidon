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
import java.util.Map;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.tests.integration.junit5.spi.SuiteProvider;

@TestConfig
public class ConfigSuite implements SuiteProvider {

    private static final Logger LOGGER = System.getLogger(ConfigSuite.class.getName());

    final static String BEFORE_KEY = "ConfigSuite.before";
    final static String AFTER_KEY = "ConfigSuite.after";
    final static String SETUP_CONFIG_KEY = "ConfigSuite.config";

    private SuiteContext suiteContext;

    private int counter;

    public ConfigSuite() {
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
                   String.format("Running beforeSuite of ConfigSuite test class, order %d", counter));
        suiteContext.storage().put(BEFORE_KEY, counter++);
    }

    // Validate that @AfterSuite is executed
    @AfterSuite
    public void afterSuite() {
        LOGGER.log(Level.TRACE,
                   String.format("Running afterSuite of ConfigSuite test class, order %d", counter));
        suiteContext.storage().put(AFTER_KEY, counter);
    }

    @SetUpConfig
    public void setupConfig(Config.Builder builder) {
        LOGGER.log(Level.TRACE,
                   String.format("Running setupConfig of ConfigSuite test class, order %d", counter));
        // Modify target Config content with additional node
        builder.addSource(ConfigSources.create(Map.of("id", "TEST")));
        suiteContext.storage().put(SETUP_CONFIG_KEY, counter++);
    }

}
