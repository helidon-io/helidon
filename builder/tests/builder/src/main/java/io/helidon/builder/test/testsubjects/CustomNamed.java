/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.builder.test.testsubjects;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import io.helidon.builder.Annotated;
import io.helidon.builder.Builder;
import io.helidon.builder.Singular;

/**
 * Used for demonstrating and testing the Builder.
 * <p>
 * In this case we are overriding the Map, Set, and List types, usages of annotations placed on the generated class, as well as
 * changing the package name targeted for the generated class.
 */
@Builder(packageName = ".impl",
         implPrefix = "Default",
         implSuffix = "",
         listImplType = LinkedList.class,
         mapImplType = TreeMap.class,
         setImplType = TreeSet.class,
         requireBeanStyle = true)
@Annotated("com.fasterxml.jackson.annotation.JsonAutoDetect(fieldVisibility = "
        + "com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY)")
public interface CustomNamed {

    /**
     * Used for testing.
     *
     * @return ignored, here for testing purposes only
     */
    @Singular
    Set<String> getStringSet();

    /**
     * Used for testing.
     *
     * @return ignored, here for testing purposes only
     */
    @Singular
    @Annotated("com.fasterxml.jackson.annotation.JsonIgnore")
    List<String> getStringList();

    /**
     * Used for testing.
     *
     * @return ignored, here for testing purposes only
     */
    @Singular
    @Annotated("com.fasterxml.jackson.annotation.JsonIgnore")
    Map<String, Integer> getStringToIntegerMap();

}
