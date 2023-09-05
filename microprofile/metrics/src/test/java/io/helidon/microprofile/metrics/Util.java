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
package io.helidon.microprofile.metrics;

class Util {

    static final String OBS_PREFIX = "/observe/metrics";
    static final String PREFIX = "/metrics";

    static String obsPath(String path) {
        return OBS_PREFIX + slashless(path);
    }

    static String path(String path) {
        return PREFIX + slashless(path);
    }

    private static String slashless(String path) {
        return path.startsWith("/") ? path.substring(1) : path;
    }

}
