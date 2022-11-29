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

package io.helidon.builder.test;

import java.util.Optional;

import io.helidon.builder.test.testsubjects.DefaultPickle;
import io.helidon.builder.test.testsubjects.DefaultPickleBarrel;
import io.helidon.builder.test.testsubjects.Pickle;
import io.helidon.builder.test.testsubjects.PickleBarrel;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PickleBarrelTest {

    @Test
    void testIt() {
        DefaultPickle.Builder pickleBuilder = DefaultPickle.builder().size(Optional.of(Pickle.Size.MEDIUM));
        Exception e = assertThrows(IllegalStateException.class, pickleBuilder::build);
        assertThat(e.getMessage(),
               equalTo("'type' is a required attribute and should not be null"));

        // now it will build since we provided the required 'type' attribute...
        Pickle pickle = pickleBuilder.type(Pickle.Type.DILL).build();
        assertThat(pickle.toString(),
               equalTo("Pickle(type=DILL, size=Optional[MEDIUM])"));

        DefaultPickleBarrel.Builder pickleBarrelBuilder = DefaultPickleBarrel.builder();
        e = assertThrows(IllegalStateException.class, pickleBarrelBuilder::build);
        assertThat(e.getMessage(),
               equalTo("'id' is a required attribute and should not be null"));

        PickleBarrel pickleBarrel = pickleBarrelBuilder.addPickle(pickle).id("123").build();
        assertThat(pickleBarrel.toString(),
               equalTo("PickleBarrel(id=123, type=Optional.empty, pickles=[Pickle(type=DILL, size=Optional[MEDIUM])])"));
    }
}
