/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.webserver.observe.telemetry.metrics;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

import io.helidon.json.JsonArray;
import io.helidon.json.JsonObject;
import io.helidon.json.JsonValue;

import org.hamcrest.Description;
import org.hamcrest.FeatureMatcher;
import org.hamcrest.Matcher;

class JsonTestUtil {

    private JsonTestUtil() {
    }

    static Matcher<JsonValue> hasArray(String featureName, Matcher<Iterable<? super JsonValue>> subMatcher) {
        return new JsonValueMatcher<>((obj, fName) -> obj.arrayValue(fName).map(JsonArray::values),
                                      subMatcher,
                                      "has array property" + featureName,
                                      featureName);
    }

    static Matcher<JsonValue> hasString(String featureName, Matcher<String> subMatcher) {
        return new JsonValueMatcher<>(JsonObject::stringValue,
                                      subMatcher,
                                      "has string property " + featureName,
                                      featureName);
    }

    static Matcher<JsonValue> hasDouble(String featureName, Matcher<Double> subMatcher) {
        return new JsonValueMatcher<>(JsonObject::doubleValue,
                                      subMatcher,
                                      "has string property " + featureName,
                                      featureName);
    }

    static Matcher<JsonValue> hasAttributes(Matcher<Map<?,?>> subMatcher) {
        return new JsonValueMatcher<>((obj, featureName) -> obj.arrayValue(featureName)
                .map(JsonTestUtil::asAttributesMap),
                     subMatcher,
                     "has attributes",
                     "attributes");

    }

    static Matcher<JsonValue> hasObject(String featureName, Matcher<JsonObject> subMatcher) {
        return new JsonValueMatcher<>(JsonObject::objectValue,
                                      subMatcher,
                                      "has object property " + featureName,
                                      featureName);
    }

    static Map<String, Object> asAttributesMap(JsonArray attributesArray) {
        Map<String, Object> result = new HashMap<>();

        attributesArray.values().stream()
                .map(JsonValue::asObject)
                .forEach(attrEntry -> attrEntry.stringValue("key")
                        .ifPresent(key -> attrEntry.objectValue("value")
                                .ifPresent(valueObj -> {
                                    valueObj.stringValue("stringValue")
                                            .ifPresent(sv -> result.put(key, sv));

                                    valueObj.stringValue("intValue")
                                            .ifPresent(intValue -> result.put(key, Integer.valueOf(intValue)));

                                    valueObj.stringValue("doubleValue")
                                            .ifPresent(dv -> result.put(key, Double.valueOf(dv)));

                                    valueObj.stringValue("booleanValue")
                                            .ifPresent(bool -> result.put(key, Boolean.valueOf(bool)));
                                })));

        return result;
    }

    static class JsonValueMatcher<U> extends FeatureMatcher<JsonValue, U> {

        private final BiFunction<JsonObject, String, Optional<U>> valueGetter;
        private final String featureName;
        private final Matcher<? super U> subMatcher;

        JsonValueMatcher(BiFunction<JsonObject, String, Optional<U>> valueGetter,
                         Matcher<? super U> subMatcher,
                         String featureDescriptorSuffix,
                         String featureName) {
            super(subMatcher, "JsonValue get of " + featureDescriptorSuffix, featureName);
            this.valueGetter = valueGetter;
            this.featureName = featureName;
            this.subMatcher = subMatcher;
        }

        @Override
        protected U featureValueOf(JsonValue actual) {
            return valueGetter.apply(actual.asObject(), featureName).get();
        }

        protected boolean matchesSafely(JsonValue actual, Description mismatch) {
            var optFeatureValue = valueGetter.apply(actual.asObject(), featureName);
            if (optFeatureValue.isEmpty()) {
                mismatch.appendText("missing property " + featureName);
            } else {
                if (!subMatcher.matches(optFeatureValue.get())) {
                    mismatch.appendText(featureName).appendText(" ");
                    subMatcher.describeMismatch(optFeatureValue.get(), mismatch);
                    return false;
                }
            }
            return true;
        }
    }
}
