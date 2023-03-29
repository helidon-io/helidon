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

import java.util.List;
import java.util.Map;
import java.util.Set;

import io.helidon.builder.Builder;
import io.helidon.builder.Singular;

/**
 * Used for demonstrating and testing the Builder.
 * In this case we are enforcing bean style is used, and overriding the generated class to have a suffix of Impl on the class name.
 */
@Builder(requireLibraryDependencies = false, requireBeanStyle = true, implPrefix = "", implSuffix = "Impl")
public interface ComplexCase extends MyConfigBean {

    /**
     * Used for testing, and demonstrating the {@link io.helidon.builder.Singular} annotation.
     *
     * @return ignored, here for testing purposes only
     */
    @Singular("keyToConfigBean")
    Map<String, List<? extends MyConfigBean>> getMapOfKeyToConfigBeans();

    /**
     * Used for testing, and demonstrating the {@link io.helidon.builder.Singular} annotation.
     *
     * @return ignored, here for testing purposes only
     */
    @Singular("configBean")
    List<? extends MyConfigBean> getListOfConfigBeans();

    /**
     * Used for testing.
     *
     * @return ignored, here for testing purposes only
     */
    Set<List<Object>> getSetOfLists();

    /**
     * Used for testing.
     *
     * @return ignored, here for testing purposes only
     */
    Class<?> getClassType();

    /**
     * The Builder will ignore {@code default} and {@code static} functions.
     *
     * @return ignored, here for testing purposes only
     */
    default String getCompositeName() {
        return getName() + ":" + getPort() + ":" + isEnabled();
    }

    /**
     * The Builder will ignore {@code default} and {@code static} functions.
     *
     * @return ignored, here for testing purposes only
     */
    default boolean hasBeenEnabled() {
        return isEnabled();
    }

    /**
     * The Builder will ignore {@code default} and {@code static} functions.
     *
     * @return ignored, here for testing purposes only
     */
    static boolean hasBeenEnabledStatic() {
        return false;
    }

}
