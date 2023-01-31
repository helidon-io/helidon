/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.builder.test;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.helidon.builder.test.testsubjects.MapCase;
import io.helidon.builder.test.testsubjects.TestMapCase;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasKey;

class MapCaseTest {

    @Test
    void testIt() {
        TestMapCase.Builder builder = TestMapCase.builder();

        assertThat(builder.stringToString(),
                   sameInstance(builder.stringToString()));
        assertThat(builder.stringToDependencies(),
                   sameInstance(builder.stringToDependencies()));
        assertThat(builder.stringToDependencyMap(),
                   sameInstance(builder.stringToDependencyMap()));

        // allow direct mutation
        builder.stringToString().put("a", "1");
        assertThat(builder.stringToString().get("a"),
                   equalTo("1"));

        // allow replacement of the map
        LinkedHashMap<String, Set<MapCase.Dependency>> map = new LinkedHashMap<>();
        LinkedHashSet<MapCase.Dependency> mSet = new LinkedHashSet<>();
        mSet.add(Mockito.mock(MapCase.Dependency.class));
        map.put("m", mSet);
        builder.stringToDependencies(map);
        assertThat(builder.stringToDependencies(),
                   hasKey("m"));
        assertThat(builder.stringToDependencies().size(),
                   equalTo(1));
        builder.stringToDependencies(Map.of("n", Set.of(Mockito.mock(MapCase.Dependency.class))));
        assertThat(builder.stringToDependencies(),
                   hasKey("n"));
        assertThat(builder.stringToDependencies().size(),
                   equalTo(1));
        builder.stringToDependencies(map);

        // allow augmentation of the collection within the maps - see how we understand the values are collections that we add to
        // instead of replace
        builder.addDependency("m", Set.of(Mockito.mock(MapCase.Dependency.class)));
        assertThat(builder.stringToDependencies().get("m").size(),
                   equalTo(2));
        // true singular version on the value parameter as well
        builder.addDependency("n", Mockito.mock(MapCase.Dependency.class));
        assertThat(builder.stringToDependencies().get("n").size(),
                   equalTo(1));
        assertThat(builder.stringToDependencies().size(),
                   equalTo(2));

        // map of maps
        builder.addStringToDependencyMap("p", Map.of("1", Mockito.mock(MapCase.Dependency.class)));
        assertThat(builder.stringToDependencyMap().get("p").size(),
                   equalTo(1));
        builder.addStringToDependencyMap("p", Map.of("2", Mockito.mock(MapCase.Dependency.class)));
        assertThat(builder.stringToDependencyMap().get("p").size(),
                   equalTo(2));
        assertThat(builder.stringToDependencyMap().size(),
                   equalTo(1));

        // map of lists
        builder.addStringToStringList("1", List.of("a", "b"));
        builder.addStringToStringList("1", List.of("c", "d"));
        builder.addStringToStringList("1", "e");
        builder.addStringToStringList("2", "x");

        assertThat(builder.stringToStringList().size(),
                   equalTo(2));
        assertThat(builder.stringToStringList().get("1"),
                   contains("a", "b", "c", "d", "e"));
        assertThat(builder.stringToStringList().get("2"),
                   contains("x"));
    }

}
