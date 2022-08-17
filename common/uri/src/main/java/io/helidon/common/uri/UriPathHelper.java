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

package io.helidon.common.uri;

final class UriPathHelper {
    private UriPathHelper() {
    }

    /**
     * This method operates on raw path string (encoded). This is to make sure we only string path parameters
     * that are valid (using unencoded semicolons). The path may also contain an encoded semicolon, but that must be
     * treated as part of the path.
     *
     * @param path raw path (may include path parameters)
     * @return raw path without path parameters
     */
    static String stripMatrixParams(String path) {
        int i = path.indexOf(';');

        if (i == -1) {
            return path;
        }

        StringBuilder result = new StringBuilder();
        String remainingPath = path;
        while (true) {
            result.append(remainingPath.substring(0, i));
            // now we need to find the next slash
            remainingPath = remainingPath.substring(i + 1);
            i = remainingPath.indexOf('/');
            if (i == -1) {
                break;
            }
            remainingPath = remainingPath.substring(i);
            i = remainingPath.indexOf(';');
            if (i == -1) {
                result.append(remainingPath);
                break;
            }
        }

        return result.toString();
    }
}
