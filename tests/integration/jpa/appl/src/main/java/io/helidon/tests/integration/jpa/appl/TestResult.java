/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.jpa.appl;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

/**
 * Test execution result.
 */
public class TestResult {

    private boolean failed;

    private final JsonObjectBuilder ob;

    private final JsonArrayBuilder msg;

    private JsonObjectBuilder ex;

    /**
     * Creates an instance of test execution result.
     */
    public TestResult() {
        failed = false;
        ob = Json.createObjectBuilder();
        msg = Json.createArrayBuilder();
        ex = null;
    }

    /**
     * Add test name.
     *
     * @param name name of the test
     */
    public void name(final String name) {
        ob.add("name", name);
    }

    /**
     * Get execution status (failed or not failed).
     *
     * @return execution status
     */
    public boolean failed() {
        return failed;
    }

    /**
     * Build test execution result JSON object.
     *
     * @return test execution result JSON object
     */
    public JsonObject build() {
        ob.add("status", failed ? "FAILURE" : "SUCCESS");
        ob.add("messages", msg.build());
        if (ex != null) {
            ob.add("exception", ex.build());
        }
        return ob.build();
    }
    /**
     * Add test execution activity message.
     *
     * @param message test execution activity message
     * @return this instance
     */
    public TestResult message(final String message) {
        msg.add(message);
        return this;
    }

    /**
     * Mark executed test as failed and store message related to failure.
     *
     * @param message message related to failure
     * @return this instance
     */
    public TestResult fail(final String message) {
        // Log 1st failure as an error.
        if (!failed) {
            ob.add("error", message);
        }
        failed = true;
        msg.add(message);
        return this;
    }

    /**
     * Store an exception thrown during test execution.
     *
     * @param t exception thrown during test execution
     * @return this instance
     */
    public TestResult throwed(final Throwable t) {
        // Log 1st failure as an error.
        if (!failed) {
            ob.add("error", t.getMessage());
        }
        failed = true;
        if (ex == null) {
            JsonArrayBuilder trace = Json.createArrayBuilder();
            ex = Json.createObjectBuilder();
            ex.add("message", t.getMessage());
            StackTraceElement[] st = t.getStackTrace();
            for (StackTraceElement ste : st) {
                trace.add(ste.toString());
            }
            ex.add("trace", trace.build());
        }
        return this;
    }

    /**
     * Test result check: equals
     *
     * @param expected expected value
     * @param actual actual value to be checked
     */
    public void assertEquals(Object expected, Object actual) {
        boolean addError = false;
        StringBuilder sb = new StringBuilder();
        sb.append("Expected: ");
        sb.append(expected != null ? expected.toString() : "<null>");
        sb.append(" Actual: ");
        sb.append(actual != null ? actual.toString() : "<null>");
        sb.append(" :: ");
        if (expected == actual || (expected != null && expected.equals(actual))) {
            sb.append("EQUAL");
        } else {
            if (!failed) {
                addError = true;
            }
            failed = true;
            sb.append("NOT EQUAL");            
        }
        String message = sb.toString();
        msg.add(message);
        if (addError) {
            ob.add("error", message);
        }
    }
    
    /**
     * Test result check: boolean true
     *
     * @param value actual value to be checked
     */
    public void assertTrue(Boolean value) {
        StringBuilder sb = new StringBuilder();
        sb.append("Expected: true");
        sb.append(" Actual: ");
        sb.append(Boolean.toString(value));
        sb.append(" :: ");
        sb.append(value ? "EQUAL" : "NOT EQUAL");
        String message = sb.toString();
        msg.add(message);
        if (!value) {
            ob.add("error", message);
            failed = true;
        }
    }

    /**
     * Test result check: boolean true
     *
     * @param value actual value to be checked
     */
    public void assertTrue(Boolean value, String header) {
        StringBuilder sb = new StringBuilder();
        sb.append(header);
        sb.append(" Expected: true");
        sb.append(" Actual: ");
        sb.append(Boolean.toString(value));
        sb.append(" :: ");
        sb.append(value ? "EQUAL" : "NOT EQUAL");
        String message = sb.toString();
        msg.add(message);
        if (!value) {
            ob.add("error", message);
            failed = true;
        }
    }

    /**
     * Test result check: boolean false
     *
     * @param value actual value to be checked
     */
    public void assertFalse(Boolean value) {
        StringBuilder sb = new StringBuilder();
        sb.append("Expected: false");
        sb.append(" Actual: ");
        sb.append(Boolean.toString(value));
        sb.append(" :: ");
        sb.append(value ? "NOT EQUAL" : "EQUAL");
        String message = sb.toString();
        msg.add(message);
        if (value) {
            ob.add("error", message);
            failed = true;
        }
    }

    /**
     * Test result check: null value
     *
     * @param value actual value to be checked
     */
    public void assertNull(Object value) {
        StringBuilder sb = new StringBuilder();
        sb.append("Expected: null");
        sb.append(" Actual: ");
        sb.append(value == null ? "null" : "not null");
        sb.append(" :: ");
        sb.append(value == null ? "EQUAL" : "NOT EQUAL");
        String message = sb.toString();
        msg.add(message);
        if (value != null ) {
            ob.add("error", message);
            failed = true;
        }
    }

    /**
     * Test result check: non null value
     *
     * @param value actual value to be checked
     */
    public void assertNotNull(Object value) {
        StringBuilder sb = new StringBuilder();
        sb.append("Expected: not null");
        sb.append(" Actual: ");
        sb.append(value == null ? "null" : "not null");
        sb.append(" :: ");
        sb.append(value == null ? "NOT EQUAL" : "EQUAL");
        String message = sb.toString();
        msg.add(message);
        if (value == null ) {
            ob.add("error", message);
            failed = true;
        }
    }

}
