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
 */
package io.helidon.servicecommon.restcdi;

import java.util.logging.Logger;

import javax.enterprise.inject.spi.ProcessManagedBean;

/**
 * Test MP extension that relies on the test SE service which itself reads a value from config, to make sure the config used
 * is runtime not build-time config.
 */
public class ConfiguredTestCdiExtension extends HelidonRestCdiExtension<ConfiguredTestSupport> {

    /**
     * Common initialization for concrete implementations.
     */
    protected ConfiguredTestCdiExtension() {
        super(Logger.getLogger(ConfiguredTestCdiExtension.class.getName()),
                config -> ConfiguredTestSupport.builder().config(config).build(), "test");
    }

    @Override
    protected void processManagedBean(ProcessManagedBean<?> processManagedBean) {
    }
}
