/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.docs.includes.security;

import io.helidon.common.SerializationConfig;

@SuppressWarnings("ALL")
class Jep290Snippets {

    // stub
    class MyType {
    }

    void snippet_1() {
        // tag::snippet_1[]
        SerializationConfig.builder()
                .traceSerialization(SerializationConfig.TraceOption.BASIC) // <1>
                .filterPattern(MyType.class.getName()) // <2>
                .ignoreFiles(true) // <3>
                .onWrongConfig(SerializationConfig.Action.IGNORE) // <4>
                .build()
                .configure(); // <5>
        // end::snippet_1[]
    }

}
