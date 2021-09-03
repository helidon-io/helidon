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
package io.helidon.tests.integration.dbclient.appl.it.tools;

import javax.json.JsonNumber;
import javax.json.JsonString;
import javax.json.JsonValue;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * JSON values tools.
 */
public class JsonTools {

    public static long getLong(JsonValue value) {
        switch (value.getValueType()) {
            case NUMBER:
                return ((JsonNumber)value).longValue();
            case STRING:
                try {
                    return Long.parseLong(((JsonString)value).getString());
                } catch (NumberFormatException nfe) {
                    fail(String.format("Could not parse %s value as Long number", ((JsonString)value).getString()));
                }
            case TRUE:
                return 1;
            case FALSE:
                return 0;
            default:
                fail(String.format("Could not parse %s value of type %s as Long number", value.toString(), value.getValueType().name()));
        }
        throw new IllegalStateException("Unknown JSON value type.");
    }

}
