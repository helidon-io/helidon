/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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

package io.helidon.config.tests.config.metadata.meta.api;

import java.util.List;

import io.helidon.metadata.hson.Hson;

import org.hamcrest.FeatureMatcher;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

class GeneratedMetadataTest {
    @Test
    void testMetadata() {
        var is = getClass().getResourceAsStream("/META-INF/helidon/config-metadata.json");
        assertThat(is, is(not(nullValue())));

        var actual = Hson.parse(is).asArray();

        assertThat(actual.value(), is(hasItem(allOf(
                hasString("module", is("io.helidon.config.tests.config.metadata.meta.api")),
                hasArray("types", allOf(
                        hasItem(allOf(
                                hasString("type", is(AbstractBuilder.class.getName())),
                                hasString("description", is("abstract builder")),
                                hasArray("options", allOf(List.of(
                                        hasItem(allOf(
                                                hasString("key", is("abstract-message")),
                                                hasString("description", is("abstract description"))
                                        )))
                                )))),
                        hasItem(allOf(
                                hasString("type", is("io.helidon.config.tests.config.metadata.meta.api.MyTarget")),
                                hasString("description", is("builder")),
                                hasStrings("inherits", hasItems(
                                        AbstractBuilder.class.getName())),
                                hasArray("options", allOf(List.of(
                                        hasItem(allOf(
                                                hasString("key", is("message")),
                                                hasString("description", is("message description")),
                                                hasString("defaultValue", is("message"))
                                        )),
                                        hasItem(allOf(
                                                hasString("key", is("type")),
                                                hasString("type", is("java.lang.Integer")),
                                                hasString("description", is("type description")),
                                                hasString("defaultValue", is("42")),
                                                hasArray("allowedValues", allOf(
                                                        hasItem(allOf(
                                                                hasString("value", is("42")),
                                                                hasString("description", is("answer"))
                                                        )),
                                                        hasItem(allOf(
                                                                hasString("value", is("0")),
                                                                hasString("description", is("no answer"))
                                                        ))
                                                ))
                                        ))
                                )))))
                ))))
        ));
    }

    static Matcher<Hson.Value<?>> hasArray(String name, Matcher<List<Hson.Struct>> matcher) {
        return new FeatureMatcher<>(matcher, "has property " + name, name) {
            @Override
            protected List<Hson.Struct> featureValueOf(Hson.Value target) {
                return target.asStruct().arrayValue(name)
                        .map(Hson.Array::getStructs)
                        .orElseGet(List::of);
            }
        };
    }

    @SuppressWarnings("SameParameterValue")
    static Matcher<Hson.Value<?>> hasStrings(String name, Matcher<Iterable<String>> matcher) {
        return new FeatureMatcher<>(matcher, "has property " + name, name) {
            @Override
            protected List<String> featureValueOf(Hson.Value target) {
                return target.asStruct()
                        .stringArray(name)
                        .orElseGet(List::of);
            }
        };
    }

    static Matcher<Hson.Value<?>> hasString(String name, Matcher<String> matcher) {
        return new FeatureMatcher<>(matcher, "has property " + name, name) {
            @Override
            protected String featureValueOf(Hson.Value<?> target) {
                return target.asStruct().stringValue(name, null);
            }
        };
    }
}
