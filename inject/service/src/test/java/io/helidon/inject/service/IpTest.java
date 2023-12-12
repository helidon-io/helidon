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

package io.helidon.inject.service;

import io.helidon.common.Errors;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeNames;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IpTest {
    @Test
    void testDefaults() {
        // only specify required
        Ip ip = Ip.builder()
                .service(TypeNames.STRING)
                .name("fieldName")
                .descriptor(TypeNames.STRING)
                .contract(TypeNames.STRING)
                .field("FIELD_0")
                .typeName(TypeNames.STRING)
                .build();

        assertThat(ip.elementKind(), is(ElementKind.CONSTRUCTOR));
        assertThat(ip.access(), is(AccessModifier.PACKAGE_PRIVATE));
    }

    @Test
    void testRequired() {
        Errors.ErrorMessagesException exception = assertThrows(Errors.ErrorMessagesException.class,
                                                               () -> Ip.builder()
                                                                       .name("fieldName")
                                                                       .descriptor(TypeNames.STRING)
                                                                       .contract(TypeNames.STRING)
                                                                       .field("FIELD_0")
                                                                       .typeName(TypeNames.STRING)
                                                                       .build(),
                                                               "Service type should be required");

        assertThat(exception.getMessage(), containsString("Property \"service\" must not be null"));

        exception = assertThrows(Errors.ErrorMessagesException.class,
                                 () -> Ip.builder()
                                         .service(TypeNames.STRING)
                                         .descriptor(TypeNames.STRING)
                                         .contract(TypeNames.STRING)
                                         .field("FIELD_0")
                                         .typeName(TypeNames.STRING)
                                         .build(),
                                 "IP name should be required");

        assertThat(exception.getMessage(), containsString("Property \"name\" must not be null"));

        exception = assertThrows(Errors.ErrorMessagesException.class,
                                 () -> Ip.builder()
                                         .service(TypeNames.STRING)
                                         .name("fieldName")
                                         .contract(TypeNames.STRING)
                                         .field("FIELD_0")
                                         .typeName(TypeNames.STRING)
                                         .build(),
                                 "Descriptor type should be required");

        assertThat(exception.getMessage(), containsString("Property \"descriptor\" must not be null"));

        exception = assertThrows(Errors.ErrorMessagesException.class,
                                 () -> Ip.builder()
                                         .service(TypeNames.STRING)
                                         .name("fieldName")
                                         .descriptor(TypeNames.STRING)
                                         .field("FIELD_0")
                                         .typeName(TypeNames.STRING)
                                         .build(),
                                 "Contract type should be required");

        assertThat(exception.getMessage(), containsString("Property \"contract\" must not be null"));

        exception = assertThrows(Errors.ErrorMessagesException.class,
                                 () -> Ip.builder()
                                         .service(TypeNames.STRING)
                                         .name("fieldName")
                                         .descriptor(TypeNames.STRING)
                                         .contract(TypeNames.STRING)
                                         .typeName(TypeNames.STRING)
                                         .build(),
                                 "Field constant name should be required");

        assertThat(exception.getMessage(), containsString("Property \"field\" must not be null"));

        exception = assertThrows(Errors.ErrorMessagesException.class,
                                 () -> Ip.builder()
                                         .service(TypeNames.STRING)
                                         .name("fieldName")
                                         .descriptor(TypeNames.STRING)
                                         .contract(TypeNames.STRING)
                                         .field("FIELD_0")
                                         .build(),
                                 "IP type should be required");

        assertThat(exception.getMessage(), containsString("Property \"typeName\" must not be null"));
    }
}
