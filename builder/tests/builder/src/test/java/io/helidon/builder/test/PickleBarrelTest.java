/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.builder.test;

import java.util.List;

import io.helidon.builder.test.testsubjects.ContainerType;
import io.helidon.builder.test.testsubjects.Pickle;
import io.helidon.builder.test.testsubjects.PickleBarrel;
import io.helidon.builder.test.testsubjects.PickleSize;
import io.helidon.builder.test.testsubjects.PickleType;
import io.helidon.common.Errors;

import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.junit5.OptionalMatcher.optionalValue;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PickleBarrelTest {

    @Test
    void testWithErrorAndSuccess() {
        Pickle.Builder pickleBuilder = Pickle.builder().size(PickleSize.MEDIUM);
        Errors.ErrorMessagesException e = assertThrows(Errors.ErrorMessagesException.class, pickleBuilder::build);
        assertThat(e.getMessage(),
                   startsWith("FATAL: Property \"type\" is required, but not set at"));

        // now it will build since we provided the required 'type' attribute...
        Pickle pickle = pickleBuilder.type(PickleType.GHERKIN).build();
        assertAll(
                () -> assertThat(pickle.type(), is(PickleType.GHERKIN)),
                () -> assertThat(pickle.size(), optionalValue(is(PickleSize.MEDIUM)))
        );

        PickleBarrel.Builder pickleBarrelBuilder = PickleBarrel.builder();
        e = assertThrows(Errors.ErrorMessagesException.class, pickleBarrelBuilder::build);
        assertThat(e.getMessage(),
                   startsWith("FATAL: Property \"id\" is required, but not set at"));

        PickleBarrel pickleBarrel = pickleBarrelBuilder.addPickle(pickle).id("243").build();

        assertAll(
                () -> assertThat(pickleBarrel.id(), is("243")),
                () -> assertThat(pickleBarrel.type(), optionalValue(is(ContainerType.PLASTIC))),
                () -> assertThat(pickleBarrel.pickles(), hasSize(1))
        );
    }

    @Test
    void expand() {
        Pickle pickle = Pickle.builder().size(PickleSize.MEDIUM).type(PickleType.DILL).build();
        PickleBarrel pickleBarrel = PickleBarrel.builder().addPickle(pickle).id("123").build();

        // test values, toString is already tested in other tests
        assertAll(
                () -> assertThat(pickleBarrel.id(), is("123")),
                () -> assertThat(pickleBarrel.type(), optionalValue(is(ContainerType.PLASTIC)))
        );
        List<Pickle> pickles = pickleBarrel.pickles();
        assertThat(pickles, hasSize(1));
        Pickle actualPickle = pickles.get(0);
        assertAll(
                () -> assertThat(actualPickle.type(), is(PickleType.DILL)),
                () -> assertThat(actualPickle.size(), optionalValue(is(PickleSize.MEDIUM)))
        );
    }

}
