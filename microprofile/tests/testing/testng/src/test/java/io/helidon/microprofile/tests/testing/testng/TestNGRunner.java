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
package io.helidon.microprofile.tests.testing.testng;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.testng.IAlterSuiteListener;
import org.testng.ITestResult;
import org.testng.TestListenerAdapter;
import org.testng.TestNG;
import org.testng.xml.XmlSuite;

/**
 * {@link TestNG} helper.
 */
class TestNGRunner {

    private final TestNG testng;
    private final TestListenerAdapter tla;
    private final Map<String, String> parameters = new HashMap<>();
    private boolean printErrors = true;

    /**
     * Create a new instance.
     */
    TestNGRunner() {
        tla = new TestListenerAdapter();
        testng = new TestNG(false);
        testng.addListener(new SuiteParameterAdapter(parameters));
        testng.addListener(tla);
        testng.setListenersToSkipFromBeingWiredInViaServiceLoaders(ParallelizerListenerImpl.class.getName());
        testng.setVerbose(0);
    }

    /**
     * Set the default test name.
     *
     * @param name name
     * @return this instance
     */
    TestNGRunner name(String name) {
        testng.setDefaultTestName(name);
        return this;
    }

    /**
     * Print errors.
     *
     * @param printErrors {@code true} to print errors
     * @return this instance
     */
    TestNGRunner printErrors(boolean printErrors) {
        this.printErrors = printErrors;
        return this;
    }

    /**
     * Set the test classes.
     *
     * @param classes test classes
     * @return this instance
     */
    TestNGRunner testClasses(Class<?>... classes) {
        testng.setTestClasses(classes);
        return this;
    }

    /**
     * Set a parameter.
     *
     * @param name  parameter name
     * @param value parameter value
     * @return this instance
     */
    TestNGRunner parameter(String name, String value) {
        parameters.put(name, value);
        return this;
    }

    /**
     * Set the parallel mode.
     *
     * @param parallelMode parallel mode
     * @return this instance
     */
    TestNGRunner parallel(XmlSuite.ParallelMode parallelMode) {
        testng.setParallel(parallelMode);
        return this;
    }

    /**
     * Run the tests.
     *
     * @return TestListenerAdapter
     */
    TestListenerAdapter run() {
        testng.run();
        if (printErrors) {
            for (ITestResult failedTest : tla.getFailedTests()) {
                failedTest.getThrowable().printStackTrace(System.out);
            }
        }
        return tla;
    }

    private record SuiteParameterAdapter(Map<String, String> parameters) implements IAlterSuiteListener {
        @Override
        public void alter(List<XmlSuite> suites) {
            for (XmlSuite suite : suites) {
                suite.getParameters().putAll(parameters);
            }
        }
    }
}
