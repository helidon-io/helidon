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

package io.helidon.pico.tests.pico.tbox.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import io.helidon.pico.ContextualServiceQuery;
import io.helidon.pico.DefaultQualifierAndValue;
import io.helidon.pico.InjectionPointProvider;
import io.helidon.pico.QualifierAndValue;
import io.helidon.pico.ServiceInfoCriteria;
import io.helidon.pico.tests.pico.tbox.AbstractBlade;

import jakarta.inject.Named;
import jakarta.inject.Singleton;

/**
 * Provides contextual injection for blades.
 */
@Singleton
@Named("*")
public class BladeProvider implements InjectionPointProvider<AbstractBlade> {

    static final QualifierAndValue all = DefaultQualifierAndValue.createNamed("*");
    static final QualifierAndValue coarse = DefaultQualifierAndValue.createNamed("coarse");
    static final QualifierAndValue fine = DefaultQualifierAndValue.createNamed("fine");

    @Override
    public Optional<AbstractBlade> first(
            ContextualServiceQuery query) {
        Objects.requireNonNull(query);
        assert (query.injectionPointInfo().orElseThrow().listWrapped()) : query;
        ServiceInfoCriteria criteria = query.serviceInfoCriteria();
        assert (criteria.contractsImplemented().size() == 1) : criteria;
        assert (criteria.contractsImplemented().contains(AbstractBlade.class.getName())) : criteria;

        AbstractBlade blade;
        if (criteria.qualifiers().contains(all) || criteria.qualifiers().contains(coarse)) {
            blade = new CoarseBlade();
        } else if (criteria.qualifiers().contains(fine)) {
            blade = new FineBlade();
        } else {
            assert (criteria.qualifiers().isEmpty());
            blade = new DullBlade();
        }

        return Optional.of(blade);
    }

    @Override
    public List<AbstractBlade> list(
            ContextualServiceQuery query) {
        Objects.requireNonNull(query);
        assert (query.injectionPointInfo().orElseThrow().listWrapped()) : query;
        ServiceInfoCriteria criteria = query.serviceInfoCriteria();

        List<AbstractBlade> result = new ArrayList<>();
        if (criteria.qualifiers().contains(all) || criteria.qualifiers().contains(coarse)) {
            result.add(new CoarseBlade());
        }

        if (criteria.qualifiers().contains(all) || criteria.qualifiers().contains(fine)) {
            result.add(new FineBlade());
        }

        if (criteria.qualifiers().contains(all) || criteria.qualifiers().isEmpty()) {
            result.add(new DullBlade());
        }

        if (query.expected() && result.isEmpty()) {
            throw new AssertionError();
        }

        return result;
    }

}
