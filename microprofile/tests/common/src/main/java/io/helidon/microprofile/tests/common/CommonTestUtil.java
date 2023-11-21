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

package io.helidon.microprofile.tests.common;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.enterprise.inject.spi.Extension;

/**
 * Common Utility class for tests.
 */
public class CommonTestUtil {

    private CommonTestUtil() {
    }

    /**
     * Extract all Extensions from the given testClass.
     *
     * @param testClass Class
     * @return List with Extension classes
     */
    public static List<Class<? extends Extension>> getFeatureExtensions(Class<?> testClass) {

        List<Class<? extends Extension>> result = Arrays.stream(testClass.getDeclaredAnnotations())
                .flatMap(a -> Arrays.stream(a.annotationType().getDeclaredAnnotations()))
                .filter(a -> a instanceof CommonCdiExtension)
                .map(CommonCdiExtension.class::cast)
                .map(CommonCdiExtension::value)
                .collect(Collectors.toList());

        result.addAll(Arrays.stream(testClass.getDeclaredAnnotations())
                .flatMap(a -> Arrays.stream(a.annotationType().getDeclaredAnnotations()))
                .filter(a -> a instanceof CommonCdiExtensions)
                .map(CommonCdiExtensions.class::cast)
                .flatMap(e -> Arrays.stream(e.value()))
                .map(CommonCdiExtension::value)
                .collect(Collectors.toList()));

        return result;
    }

    /**
     * Extract all Beans from the given testClass.
     *
     * @param testClass Class
     * @return List with Beans classes
     */
    public static List<Class<?>> getFeatureBeans(Class<?> testClass) {

        ArrayList<Class<?>> result = Arrays.stream(testClass.getDeclaredAnnotations())
                .flatMap(a -> Arrays.stream(a.annotationType().getDeclaredAnnotations()))
                .filter(a -> a instanceof CommonAddBean)
                .map(CommonAddBean.class::cast)
                .map(CommonAddBean::value).collect(Collectors.toCollection(ArrayList::new));

        result.addAll(Arrays.stream(testClass.getDeclaredAnnotations())
                .flatMap(a -> Arrays.stream(a.annotationType().getDeclaredAnnotations()))
                .filter(a -> a instanceof CommonAddBeans)
                .map(CommonAddBeans.class::cast)
                .flatMap(e -> Arrays.stream(e.value()))
                .map(CommonAddBean::value)
                .collect(Collectors.toList()));
        return result;
    }
}
