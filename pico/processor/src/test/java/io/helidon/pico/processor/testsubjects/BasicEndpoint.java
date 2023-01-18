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

package io.helidon.pico.processor.testsubjects;

import jakarta.inject.Singleton;

/**
 * For Testing.
 */
@Singleton
@BasicPath("/*")
public class BasicEndpoint {

    /**
     * For testing.
     *
     * @param header for testing
     * @return for testing.
     */
    @ExtensibleGET
    public String itWorks(
            String header) {
        return "Pico Works!";
    }

    @ExtensibleGET
    @BasicPath("/whatever/*")
    public String itWorks2() {
        return "Pico Works 2!";
    }

}
