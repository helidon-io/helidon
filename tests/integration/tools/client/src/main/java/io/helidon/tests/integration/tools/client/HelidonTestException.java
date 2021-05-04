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
package io.helidon.tests.integration.tools.client;

import java.io.PrintStream;
import java.util.List;

import javax.json.JsonArray;
import javax.json.JsonObject;

/**
 * Thrown to report error in integration test execution.
 */
public class HelidonTestException extends RuntimeException {

    private final JsonArray remoteCause;

    /**
     * Creates an instance of {@code HelidonTestException} with remote cause.
     *
     * @param message the detail message. The detail message is saved for
     *                later retrieval by the {@link #getMessage()} method.
     * @param remoteCause JSON array with remote cause data
     */
    public HelidonTestException(final String message, final JsonArray remoteCause) {
        super(message);
        this.remoteCause = remoteCause;
    }

    /**
     * Creates an instance of {@code HelidonTestException} with no cause.
     *
     * @param message the detail message. The detail message is saved for
     *                later retrieval by the {@link #getMessage()} method.
     * @param localCause the cause (which is saved for later retrieval by the
     *                   {@link #getCause()} method).
     */
    public HelidonTestException(final String message, final Throwable localCause) {
        super(message, localCause);
        this.remoteCause = null;
    }

    /**
     * Creates an instance of {@code HelidonTestException} with no cause.
     *
     * @param message the detail message. The detail message is saved for
     *                later retrieval by the {@link #getMessage()} method.
     */
    public HelidonTestException(final String message) {
        super(message);
        this.remoteCause = null;
    }

    /**
     * Prints this exception and its local and remote stack traces
     * to the specified print stream.
     *
     * @param s {@code PrintStream} to use for output
     */
    @Override
    public void printStackTrace(PrintStream s) {
        s.println("Local stack trace");
        super.printStackTrace(s);
        if (remoteCause != null && remoteCause.size() > 0) {
            s.println("Remote stack trace");
            List<JsonObject> tracesList = remoteCause.getValuesAs(JsonObject.class);
            for (JsonObject trace : tracesList) {
                s.println(String.format(
                        "%s: %s",
                        trace.getString("class"),
                        trace.getString("message")));
                JsonArray lines = trace.getJsonArray("trace");
                List<JsonObject> linesList = lines.getValuesAs(JsonObject.class);
                for (JsonObject line : linesList) {
                    s.println(String.format(
                            "    at %s$%s (%s:%d)",
                            line.getString("class"),
                            line.getString("method"),
                            line.getString("file"),
                            line.getInt("line")));
                }
            }
        }
    }

}
