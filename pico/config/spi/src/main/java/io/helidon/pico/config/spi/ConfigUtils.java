/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.pico.config.spi;

/**
 * Config utils.
 */
public class ConfigUtils {

    private ConfigUtils() {
    }

    /**
     * TODO: This was taken from ConfigMetadataHandler. We should relocate it to common so that we can use it.
     *
     * Method name is camel case (such as maxInitialLineLength)
     * result is dash separated and lower cased (such as max-initial-line-length)
     */
    public static String toConfigKey(String methodName) {
        StringBuilder result = new StringBuilder(methodName.length() + 5);

        char[] chars = methodName.toCharArray();
        for (char aChar : chars) {
            if (Character.isUpperCase(aChar)) {
                if (result.length() == 0) {
                    result.append(Character.toLowerCase(aChar));
                } else {
                    result.append('-')
                            .append(Character.toLowerCase(aChar));
                }
            } else {
                result.append(aChar);
            }
        }

        return result.toString();
    }


}
