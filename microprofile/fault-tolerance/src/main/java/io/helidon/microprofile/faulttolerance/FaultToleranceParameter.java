/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.faulttolerance;

import java.util.NoSuchElementException;
import java.util.logging.Logger;

import org.eclipse.microprofile.config.ConfigProvider;

/**
 * Class FaultToleranceParameter.
 */
class FaultToleranceParameter {
    private static final Logger LOGGER = Logger.getLogger(FaultToleranceParameter.class.getName());

    private FaultToleranceParameter() {
    }

    static String getParameter(String className, String methodName, String annotationType, String parameter) {
        final String param = String.format("%s/%s/%s/%s", className, methodName, annotationType, parameter);
        return getProperty(param);
    }

    static String getParameter(String className, String annotationType, String parameter) {
        final String param = String.format("%s/%s/%s", className, annotationType, parameter);
        return getProperty(param);
    }

    static String getParameter(String annotationType, String parameter) {
        final String param = String.format("%s/%s", annotationType, parameter);
        return getProperty(param);
    }

    /**
     * Returns the value of a property using the MP config API.
     *
     * @param name Property name.
     * @return Property value or {@code null} if it does not exist.
     */
    private static String getProperty(String name) {
        try {
            ClassLoader ccl = Thread.currentThread().getContextClassLoader();
            String value = ConfigProvider.getConfig(ccl).getValue(name, String.class);
            LOGGER.fine(() -> "Found config property '" + name + "' value '" + value + "'");
            return value;
        } catch (NoSuchElementException e) {
            return null;
        }
    }
}
