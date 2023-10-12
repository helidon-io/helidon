/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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
package io.helidon.microprofile.openapi;

import java.util.List;
import java.util.Set;

import io.helidon.microprofile.openapi.other.TestApp2;
import io.helidon.microprofile.server.JaxRsApplication;

import io.smallrye.openapi.runtime.scanner.FilteredIndexView;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.junit.jupiter.api.Test;

import static io.helidon.microprofile.openapi.TestUtil.config;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests {@link FilteredIndexViewsBuilder}.
 */
class FilteredIndexViewsBuilderTest {

    @Test
    void testMultipleIndexFiles() {

        // The pom builds two differently-named test Jandex files, as an approximation
        // to handling multiple same-named index files in the class path.

        List<String> indexPaths = List.of("META-INF/jandex.idx", "META-INF/other.idx");

        List<JaxRsApplication> apps = List.of(
                JaxRsApplication.create(new TestApp()),
                JaxRsApplication.create(new TestApp2()));

        List<FilteredIndexView> indexViews = new FilteredIndexViewsBuilder(
                config(), apps, Set.of(), indexPaths, false).buildViews();

        List<ClassInfo> filteredIndexViews = indexViews.stream()
                .flatMap(view -> view.getKnownClasses().stream())
                .toList();

        DotName testAppName = DotName.createSimple(TestApp.class.getName());
        DotName testApp2Name = DotName.createSimple(TestApp2.class.getName());

        ClassInfo testAppInfo = filteredIndexViews.stream()
                .filter(classInfo -> classInfo.name().equals(testAppName))
                .findFirst()
                .orElse(null);
        assertThat(testAppInfo, notNullValue());

        ClassInfo testApp2Info = filteredIndexViews.stream()
                .filter(classInfo -> classInfo.name().equals(testApp2Name))
                .findFirst()
                .orElse(null);
        assertThat(testApp2Info, notNullValue());
    }
}
