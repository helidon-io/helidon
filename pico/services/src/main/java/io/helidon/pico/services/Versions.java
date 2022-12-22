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

package io.helidon.pico.services;

/**
 * Keeps track of the Pico Interop Versions.
 * <p>
 * Since Pico performs code-generation, each previously generated artifact version may need to be discoverable in order to
 * determine interoperability with previous release versions. This class will only track version changes for anything that might
 * affect interoperability - it will not be rev'ed for general code enhancements and fixes.
 * <p>
 * Please note that this version is completely independent of the Helidon version.
 */
public class Versions {

    /**
     * Version 1 - the initial release of Pico.
     */
    public static final String VERSION_1 = "1";

    /**
     * The current release is {@link #VERSION_1}.
     */
    public static final String CURRENT_VERSION = VERSION_1;

    private Versions() {
    }

}
