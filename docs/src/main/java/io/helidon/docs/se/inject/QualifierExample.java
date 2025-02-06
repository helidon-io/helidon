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

class QualifierExample {


    // tag::snippet_1[]
    interface Color {
        String hexCode();
    }

    @Service.Named("blue")
    @Service.Singleton
    public class Blue implements Color {

        @Override
        public String hexCode() {
            return "0000FF";
        }
    }

    @Service.Named("green")
    @Service.Singleton
    public class Green implements Color {

        @Override
        public String hexCode() {
            return "008000";
        }
    }
    // end::snippet_1[]

    // tag::snippet_2[]
    @Service.Singleton
    record BlueCircle(@Service.Named("blue") Color color) {
    }
    // end::snippet_2[]

    // tag::snippet_3[]
    @Service.Singleton
    record GreenCircle(@Service.Named("green") Color color) {
    }
    // end::snippet_3[]

    // tag::snippet_4[]
    @Service.NamedByType(GreenNamedByType.class)
    @Service.Singleton
    public class GreenNamedByType implements Color {

        @Override
        public String hexCode() {
            return "008000";
        }
    }
    // end::snippet_4[]

}
