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
package io.helidon.tests.integration.dbclient.appl.it;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.json.JsonArray;
import javax.json.JsonObject;

/**
 * JSON Data Log Helper.
 */
public class LogData {

    private static final Logger LOGGER = Logger.getLogger(LogData.class.getName());

    /**
     * Log JSON object in human readable form.
     *
     * @param level logging level
     * @param data  data to log
     */
    public static void logJsonObject(final Level level, final JsonObject data) {
        LOGGER.log(level, () -> "JSON object:");
        if (data == null) {
            LOGGER.log(level, "   is null");
            return;
        }
        if (data.isEmpty()) {
            LOGGER.log(level, "   is empty");
            return;
        }
        data.forEach((key, value)
                -> LOGGER.log(level, () -> String.format(" - %s: %s", key, value.toString())));
    }

    /**
     * Log JSON array in human readable form.
     *
     * @param level logging level
     * @param data  data to log
     */
    public static void logJsonArray(final Level level, final JsonArray data) {
        LOGGER.log(level, () -> String.format("JSON array: %s", data.toString()));
        data.forEach(row -> {
            switch (row.getValueType()) {
                case OBJECT:
                    LOGGER.log(level, () -> " - Row:");
                    row.asJsonObject().forEach((key, value)
                            -> LOGGER.log(level, () -> String.format("   - %s: %s", key, value.toString())));
                default:
                    LOGGER.log(level, () -> String.format(" - %s", row.toString()));
            }
        });
    }

}
