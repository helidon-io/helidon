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

package io.helidon.integrations.vault;

import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import io.helidon.integrations.common.rest.ApiJsonRequest;

import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObjectBuilder;

/**
 * Common helper methods for Vault Requests.
 * @param <T> type of the subclass
 */
public abstract class VaultRequest<T extends VaultRequest<T>> extends ApiJsonRequest<T> {
    private final Map<String, List<String>> commaDelimitedArrays = new HashMap<>();

    /**
     * Add aa list of values as a comma delimited string instead of a JSON Array.
     *
     * @param json the builder
     * @param name name of the property
     * @param values list of values
     */
    protected static void addCommaDelimitedArray(JsonObjectBuilder json, String name, List<String> values) {
        if (!values.isEmpty()) {
            json.add(name, String.join(",", values));
        }
    }

    /**
     * Add a duration formatted in Vault manner, as a string with duration.
     *
     * @param name name of the property
     * @param duration duration to add
     * @return updated request
     */
    public T add(String name, Duration duration) {
        return add(name, durationToTtl(duration));
    }

    /**
     * Add a string to an array represented as a comma delimited string in the request JSON.
     *
     * @param name key in the json payload
     * @param element element of the array
     * @return updated request
     */
    public T addToCommaDelimitedArray(String name, String element) {
        commaDelimitedArrays.computeIfAbsent(name, it -> new LinkedList<>())
                .add(element);
        return me();
    }

    @Override
    protected void preBuild(JsonBuilderFactory factory, JsonObjectBuilder payload) {
        commaDelimitedArrays.forEach((key, value) -> addCommaDelimitedArray(payload, key, value));
        super.preBuild(factory, payload);
    }

    /**
     * Duration to time to live in HCP Vault format.
     * The format is "5h" for exact hour values, "5m" for exact minute values,
     * or "5s" when seconds are part of the value.
     *
     * @param duration duration
     * @return String of that duration
     */
    public static String durationToTtl(Duration duration) {
        long hours = duration.toHours();
        int minutes = duration.toMinutesPart();
        int seconds = duration.toSecondsPart();

        if (seconds == 0) {
            if (minutes == 0) {
                return hours + "h";
            } else {
                return duration.toMinutes() + "m";
            }
        } else {
            // in seconds
            return duration.toSeconds() + "s";
        }
    }
}
