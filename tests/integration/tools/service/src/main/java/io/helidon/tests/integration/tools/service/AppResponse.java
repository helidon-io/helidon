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
package io.helidon.tests.integration.tools.service;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;

/**
 * Response helper methods.
 */
public class AppResponse {

    /**
     * Build JSON response with {@code OK} status.
     * <p>Returned JSON object has following structure:<br>
     * <pre> {
     *      "status": "OK",
     *      "data": &lt;data&gt;
     * }</pre>
     *
     * @param data attached data JSON value.
     * @return JSON response with OK status and attached data.
     */
    public static JsonObject okStatus(final JsonValue data) {
        final JsonObjectBuilder job = Json.createObjectBuilder();
        job.add("status", "OK");
        job.add("data", data != null ? data : JsonValue.NULL);
        return job.build();
    }

    /**
     * Build JSON response with {@code exception} status.
     * <p>Returned JSON object has following structure:<br>
     * <pre> {
     *      "status": "exception",
     *      "stacktrace": [
     *          {
     *              "class": "class.of.Exception",
     *              "message": "Exception message",
     *              "trace": [
     *                  {
     *                      "file": "Java class file",
     *                      line": &lt;exception line number in Java class file&gt;,
     *                      "module": "module name",
     *                      "modVersion": "modukle version",
     *                      "loader": "classloader name",
     *                      "class": "class name",
     *                      "method": "method name"
     *                  },
     *                  {...},...
     *              ]
     *          },
     *          {...},...
     *      ]
     * }</pre>
     *
     * @param t {@Link Throwable} to be stored in JSON response
     * @return JSON response with exception status and attached stack trace.
     */
    public static JsonObject exceptionStatus(final Throwable t) {
        final JsonObjectBuilder job = Json.createObjectBuilder();
        job.add("status", "exception");
        final JsonArrayBuilder jabSt = Json.createArrayBuilder();
        Throwable current = t;
        while (current != null) {
            jabSt.add(buildStackTrace(current));
            current = current.getCause();
        }
        job.add("stacktrace", jabSt.build());
        return job.build();
    }

    private static JsonObject buildStackTrace(final Throwable t) {
        JsonObjectBuilder jobSt = Json.createObjectBuilder();
        jobSt.add("class", t.getClass().getName());
        jobSt.add("message", t.getMessage());
        final JsonArrayBuilder jab = Json.createArrayBuilder();
        final StackTraceElement[] elements = t.getStackTrace();
        if (elements != null) {
            for (final StackTraceElement element : elements) {
                JsonObjectBuilder jobElement = Json.createObjectBuilder();
                jobElement.add("file", element.getFileName() != null ? Json.createValue(element.getFileName()) : JsonValue.NULL);
                jobElement.add("line", element.getLineNumber());
                jobElement.add("module", element.getModuleName() != null ? Json.createValue(element.getModuleName()) : JsonValue.NULL);
                jobElement.add("modVersion", element.getModuleVersion() != null ? Json.createValue(element.getModuleVersion()) : JsonValue.NULL);
                jobElement.add("loader", element.getClassLoaderName() != null ? Json.createValue(element.getClassLoaderName()) : JsonValue.NULL);
                jobElement.add("class", element.getClassName() != null ? Json.createValue(element.getClassName()) : JsonValue.NULL);
                jobElement.add("method", element.getMethodName() != null ? Json.createValue(element.getMethodName()) : JsonValue.NULL);
                jab.add(jobElement.build());
            }
        }
        jobSt.add("trace", jab.build());
        return jobSt.build();
    }

}
