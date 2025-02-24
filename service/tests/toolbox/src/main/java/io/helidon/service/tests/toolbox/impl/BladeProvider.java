/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.service.tests.toolbox.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.helidon.common.types.TypeName;
import io.helidon.service.registry.Lookup;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.Service.InjectionPointFactory;
import io.helidon.service.registry.Service.QualifiedInstance;
import io.helidon.service.tests.toolbox.AbstractBlade;

/**
 * Provides contextual injection for blades.
 */
@Service.Singleton
@Service.Named("*")
public class BladeProvider implements InjectionPointFactory<AbstractBlade> {

    static final Qualifier QUALIFIER_ALL = Qualifier.WILDCARD_NAMED;
    static final Qualifier QUALIFIER_COARSE = Qualifier.createNamed("coarse");
    static final Qualifier QUALIFIER_FINE = Qualifier.createNamed("fine");
    static final Qualifier QUALIFIER_DULL = Qualifier.createNamed("dull");

    @Override
    public Optional<QualifiedInstance<AbstractBlade>> first(Lookup lookup) {
        assert (lookup.contracts().size() == 1) : lookup;
        assert (lookup.contracts().contains(TypeName.create(AbstractBlade.class))) : lookup;

        AbstractBlade blade;
        Qualifier qualifier;
        if (lookup.qualifiers().contains(QUALIFIER_ALL) || lookup.qualifiers().contains(QUALIFIER_COARSE)) {
            qualifier = QUALIFIER_COARSE;
            blade = new CoarseBlade();
        } else if (lookup.qualifiers().contains(QUALIFIER_FINE)) {
            qualifier = QUALIFIER_FINE;
            blade = new FineBlade();
        } else {
            assert (lookup.qualifiers().isEmpty());
            qualifier = QUALIFIER_DULL;
            blade = new DullBlade();
        }

        return Optional.of(QualifiedInstance.create(blade, qualifier));
    }

    @Override
    public List<QualifiedInstance<AbstractBlade>> list(Lookup lookup) {
        List<QualifiedInstance<AbstractBlade>> result = new ArrayList<>();
        if (lookup.qualifiers().contains(QUALIFIER_ALL) || lookup.qualifiers().contains(QUALIFIER_COARSE)) {
            result.add(QualifiedInstance.create(new CoarseBlade(), QUALIFIER_COARSE));
        }

        if (lookup.qualifiers().contains(QUALIFIER_ALL) || lookup.qualifiers().contains(QUALIFIER_FINE)) {
            result.add(QualifiedInstance.create(new FineBlade(), QUALIFIER_FINE));
        }

        if (lookup.qualifiers().contains(QUALIFIER_ALL) || lookup.qualifiers().isEmpty()) {
            result.add(QualifiedInstance.create(new DullBlade(), QUALIFIER_DULL));
        }

        return result;
    }

}
