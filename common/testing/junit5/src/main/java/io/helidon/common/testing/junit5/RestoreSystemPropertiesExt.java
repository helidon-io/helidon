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

package io.helidon.common.testing.junit5;

import java.util.Properties;

import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ExtensionContext.Store;

/**
 * JUnit 5 extension for preserving and restoring system properties around
 * test executions.
 * <p>
 * Annotate each test method that modifies system properties using
 * <code>@ExtendWith(RestoreSystemPropertiesExt.class)</code>
 *
 */
public class RestoreSystemPropertiesExt implements BeforeTestExecutionCallback, AfterTestExecutionCallback {

    private static final String SYSPROPS_KEY = "systemProps";

    @Override
    public void beforeTestExecution(ExtensionContext ec) throws Exception {
        getStore(ec).put(SYSPROPS_KEY, System.getProperties());
        Properties copy = new Properties();
        copy.putAll(System.getProperties());
        System.setProperties(copy);
    }

    @Override
    public void afterTestExecution(ExtensionContext ec) throws Exception {
        System.setProperties(getSavedProps(ec));
    }

    private Store getStore(ExtensionContext context) {
        return context.getStore(Namespace.create(getClass(), context.getRequiredTestMethod()));
    }

    private Properties getSavedProps(ExtensionContext ec) throws Exception {
        Object o = getStore(ec).get(SYSPROPS_KEY);
        return Properties.class.cast(o);
    }
}
