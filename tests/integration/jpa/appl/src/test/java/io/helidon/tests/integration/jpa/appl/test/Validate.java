/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.tests.integration.jpa.appl.test;

import java.io.StringReader;
import java.util.logging.Logger;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Validate test response.
 * Utility class.
 */
public class Validate {

    private static final Logger LOGGER = Logger.getLogger(Validate.class.getName());

    private Validate() {
        throw new UnsupportedOperationException("Instances of Validate class are not allowed");
    }

    /**
     * Check test result String.
     *
     * @param resultString test result string with JSON data
     */
    public static void check(String resultString) {
        JsonObject testResult = Json.createReader(new StringReader(resultString)).readObject();
        String name = testResult.getString("name");
        String status = testResult.getString("status");
        printTestHeader(name, status);
        if (testResult.containsKey("messages")) {
            printMessages(testResult.getJsonArray("messages"));
        }
        if (testResult.containsKey("exception")) {
            printException(testResult.getJsonObject("exception"));
        }
        if (!"SUCCESS".equals(status.toUpperCase())) {
            StringBuilder sb = new StringBuilder();
            sb.append("Test ");
            sb.append(name != null ? name : "UNKNOWN TEST");
            sb.append(" failed");
            if (testResult.containsKey("error")) {
                sb.append(": ");
                sb.append(testResult.getString("error"));
            }
            fail(sb.toString());
        }
    }

    private static String dots(int count) {
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            sb.append('.');
        }
        return sb.toString();
    }

    private static void printTestHeader(final String name, final String status) {
        final String printName = name != null ? name : "UNKNOWN TEST";
        final String printStatus = status != null ? status : "N?A";
        final int dotsCount = 60 - printName.length();
        LOGGER.fine(() -> String.format("*** %s %s", printName, dots(dotsCount)));
        LOGGER.fine(printStatus);
    }

    private static void printMessages(final JsonArray messages) {
        if (messages != null && messages.size() > 0) {
            LOGGER.fine("Messages:");
            for (int i = 0; i < messages.size(); i++) {
                final int idx = i;
                LOGGER.fine(() -> String.format(" - %s", messages.getString(idx)));
            }
        }
    }

    private static void printException(final JsonObject exception) {
        final String message = exception.getString("message");
        LOGGER.fine(() -> String.format("Exception: %s", message != null ? message : ""));
        if (exception.containsKey("trace")) {
            final JsonArray trace = exception.getJsonArray("trace");
            for (int i = 0; i < trace.size(); i++) {
                final int idx = i;
                LOGGER.fine(() -> String.format(" - %s", trace.getString(idx)));
            }
        }
    }

}
