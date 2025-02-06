/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.docs.se.inject;

import io.helidon.service.registry.Service;

class Qualifier2Example {

    // tag::snippet_1[]
    /**
     * Custom Helidon Inject qualifier.
     */
    @Service.Qualifier
    public @interface HexCode {
        String value();
    }
    // end::snippet_1[]

    // tag::snippet_2[]
    interface Color {
        String name();
    }

    @HexCode("0000FF")
    @Service.Singleton
    static class BlueColor implements Color {

        @Override
        public String name() {
            return "blue";
        }
    }

    @HexCode("008000")
    @Service.Singleton
    static class GreenColor implements Color {

        @Override
        public String name() {
            return "green";
        }
    }
    // end::snippet_2[]

    // tag::snippet_3[]
    @Service.Singleton
    record BlueCircle(@HexCode("0000FF") Color color) {
    }

    @Service.Singleton
    record GreenCircle(@HexCode("008000") Color color) {
    }
    // end::snippet_3[]


}
