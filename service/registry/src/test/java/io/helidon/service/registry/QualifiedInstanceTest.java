/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.service.registry;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QualifiedInstanceTest {
    @Test
    void rejectsNullInstances() {
        assertThrows(NullPointerException.class, () -> Service.QualifiedInstance.create(null));
        assertThrows(NullPointerException.class, () -> Service.QualifiedInstance.create(null, Set.of()));
    }

    @Test
    void rejectsNullQualifiers() {
        assertThrows(NullPointerException.class,
                     () -> Service.QualifiedInstance.create("service", (Qualifier[]) null));
        assertThrows(NullPointerException.class,
                     () -> Service.QualifiedInstance.create("service", (Set<Qualifier>) null));
        assertThrows(NullPointerException.class,
                     () -> Service.QualifiedInstance.create("service", new Qualifier[] {null}));

        Set<Qualifier> qualifiers = new HashSet<>();
        qualifiers.add(null);
        assertThrows(NullPointerException.class, () -> Service.QualifiedInstance.create("service", qualifiers));
    }

    @Test
    void copiesQualifierSet() {
        var qualifier = Qualifier.createNamed("original");
        Set<Qualifier> qualifiers = new HashSet<>(Set.of(qualifier));
        var instance = Service.QualifiedInstance.create("service", qualifiers);

        qualifiers.clear();

        assertThat(instance.get(), is("service"));
        assertThat(instance.qualifiers(), is(Set.of(qualifier)));
        assertThrows(UnsupportedOperationException.class, () -> instance.qualifiers().clear());
    }

    @Test
    void copiesQualifierArray() {
        var qualifier = Qualifier.createNamed("original");
        Qualifier[] qualifiers = {qualifier};
        var instance = Service.QualifiedInstance.create("service", qualifiers);

        qualifiers[0] = null;

        assertThat(instance.qualifiers(), is(Set.of(qualifier)));
    }
}
