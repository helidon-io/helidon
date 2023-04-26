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

import io.helidon.builder.test.testsubjects.Pickle;
import io.helidon.builder.test.testsubjects.PickleBarrel;
import io.helidon.builder.test.testsubjects.PickleBarrelDefault;
import io.helidon.builder.test.testsubjects.PickleDefault;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PickleBarrelTest {

    @Test
    void testIt() {
        PickleDefault.Builder pickleBuilder = PickleDefault.builder().size(Pickle.Size.MEDIUM);
        Exception e = assertThrows(IllegalStateException.class, pickleBuilder::build);
        assertThat(e.getMessage(),
               equalTo("'type' is a required attribute and should not be null"));

        // now it will build since we provided the required 'type' attribute...
        Pickle pickle = pickleBuilder.type(Pickle.Type.DILL).build();
        assertThat(pickle.toString(),
               equalTo("Pickle(type=DILL, size=Optional[MEDIUM])"));

        PickleBarrelDefault.Builder pickleBarrelBuilder = PickleBarrelDefault.builder();
        e = assertThrows(IllegalStateException.class, pickleBarrelBuilder::build);
        assertThat(e.getMessage(),
               equalTo("'id' is a required attribute and should not be null"));

        PickleBarrel pickleBarrel = pickleBarrelBuilder.addPickle(pickle).id("123").build();
        assertThat(pickleBarrel.toString(),
               equalTo("PickleBarrel(id=123, type=Optional[PLASTIC], pickles=[Pickle(type=DILL, size=Optional[MEDIUM])])"));
    }
}
