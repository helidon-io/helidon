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
package io.helidon.config.metadata.model;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import io.helidon.config.metadata.model.CmModel.CmModule;
import io.helidon.config.metadata.model.CmModel.CmOption;
import io.helidon.config.metadata.model.CmModel.CmType;

import org.hamcrest.FeatureMatcher;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

/**
 * Tests {@link CmResolver}.
 */
class CmResolverTest {

    @Test
    void testMergeValueType() {
        var model = CmModel.of(List.of(
                CmModule.of("com.acme", List.of(
                        CmType.builder()
                                .type("com.acme.AcmeConfig")
                                .standalone(true)
                                .prefix("acme")
                                .options(List.of(
                                        CmOption.builder()
                                                .key("option1")
                                                .type("java.lang.Integer")
                                                .description("Option1")
                                                .merge(true)
                                                .build()))
                                .build()))));
        var resolver = CmResolver.create(model);
        var resolvedType = resolver.type("com.acme.AcmeConfig").orElse(null);
        assertThat(resolvedType, is(not(nullValue())));
        assertThat(resolvedType, hasProperty("options", CmType::options, is(hasItem(allOf(List.of(
                hasProperty("key", CmOption::key, is(Optional.of("option1"))),
                hasProperty("description", CmOption::description, is(Optional.of("Option1"))),
                hasProperty("type", CmOption::type, is("java.lang.Integer"))
        ))))));
    }

    static <T, U> Matcher<T> hasProperty(String name, Function<T, U> extractor, Matcher<U> subMatcher) {
        return new FeatureMatcher<>(subMatcher, "has property " + name, name) {
            @Override
            protected U featureValueOf(T target) {
                return extractor.apply(target);
            }
        };
    }
}
