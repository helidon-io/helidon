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

import java.util.List;
import java.util.Optional;

import io.helidon.pico.DefaultContextualServiceQuery;
import io.helidon.pico.DefaultServiceInfoCriteria;
import io.helidon.pico.InjectionPointInfo;
import io.helidon.pico.InjectionPointProvider;
import io.helidon.pico.ServiceProvider;
import io.helidon.pico.tests.pico.tbox.AbstractBlade;
import io.helidon.pico.tests.pico.tbox.AbstractSaw;
import io.helidon.pico.tests.pico.tbox.Lubricant;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

/**
 * Kept intentionally in a different package from {@link AbstractSaw} for testing.
 */
@Singleton
@SuppressWarnings("unused")
public class HandSaw extends AbstractSaw {

    private Optional<Lubricant> ctorInjectedLubricantInSubClass;
    private Optional<Lubricant> setterInjectedLubricantInSubClass;

    private int setterInjectedLubricantInSubClassInjectedCount;

    @Inject @Named(FineBlade.NAME) Provider<AbstractBlade> fineBladeFieldInjectedPkgPrivateProviderInSubClass;
    @Inject @Named(FineBlade.NAME) Optional<AbstractBlade> fineBladeFieldInjectedPkgPrivateOptionalInSubClass;
    @Inject @Named(FineBlade.NAME) List<AbstractBlade> fineBladeFieldInjectedPkgPrivateListInSubClass;

    Provider<AbstractBlade> setterInjectedPkgPrivateProviderInSubClass;
    Optional<AbstractBlade> setterInjectedPkgPrivateOptionalInSubClass;
    List<AbstractBlade> setterInjectedPkgPrivateListInSubClass;
    List<InjectionPointProvider<AbstractBlade>> setterInjectedAllProviderListInSubClass;

    int setterInjectedPkgPrivateProviderInSubClassInjectedCount;
    int setterInjectedPkgPrivateOptionalInSubClassInjectedCount;
    int setterInjectedPkgPrivateListInSubClassInjectedCount;
    int setterInjectedAllProviderListInSubClassInjectedCount;

    HandSaw() {
    }

    @Inject
    public HandSaw(
            Optional<Lubricant> lubricant) {
        ctorInjectedLubricantInSubClass = lubricant;
    }

    @Override
    public Optional<String> named() {
        return Optional.of(getClass().getSimpleName());
    }

    @Inject
    protected void injectLubricant(
            Optional<Lubricant> lubricant) {
        setterInjectedLubricantInSubClass = lubricant;
        setterInjectedLubricantInSubClassInjectedCount++;
    }

    @Inject
    void setBladeProvider(
            Provider<AbstractBlade> blade) {
        setterInjectedPkgPrivateProviderInSubClass = blade;
        setterInjectedPkgPrivateProviderInSubClassInjectedCount++;
    }

    @Inject
    void setBladeOptional(
            Optional<AbstractBlade> blade) {
        setterInjectedPkgPrivateOptionalInSubClass = blade;
        setterInjectedPkgPrivateOptionalInSubClassInjectedCount++;
    }

    @Inject
    void setBladeList(
            List<AbstractBlade> blades) {
        setterInjectedPkgPrivateListInSubClass = blades;
        setterInjectedPkgPrivateListInSubClassInjectedCount++;
    }

    @Inject
    void setAllBlades(
            @Named("*") List<Provider<AbstractBlade>> blades) {
        setterInjectedAllProviderListInSubClass = (List) blades;
        setterInjectedAllProviderListInSubClassInjectedCount++;
    }

    @Override
    public void verifyState() {
        verifyInjected(ctorInjectedLubricantInSubClass, getClass()
                + "." + InjectionPointInfo.CONSTRUCTOR, null, false, null);
        verifyInjected(setterInjectedLubricantInSubClass, getClass()
                + ".injectLubricant(Optional<Lubricant> lubricant)", setterInjectedLubricantInSubClassInjectedCount, false, null);

        verifyInjected(fineBladeFieldInjectedPkgPrivateProviderInSubClass, getClass()
                + ".fineBladeFieldInjectedPkgPrivateProviderInSubClass", null, true, FineBlade.class);
        verifyInjected(fineBladeFieldInjectedPkgPrivateOptionalInSubClass, getClass() +
                ".fineBladeFieldInjectedPkgPrivateOptionalInSubClass", null, true, FineBlade.class);
        verifyInjected(fineBladeFieldInjectedPkgPrivateListInSubClass, getClass() +
                ".fineBladeFieldInjectedPkgPrivateListInSubClass", null, 1, FineBlade.class);

        verifyInjected(setterInjectedPkgPrivateProviderInSubClass, getClass() +
                ".setBladeProvider(Provider<AbstractBlade> blade)", setterInjectedPkgPrivateProviderInSubClassInjectedCount, true,  DullBlade.class);
        verifyInjected(setterInjectedPkgPrivateOptionalInSubClass, getClass() +
                ".setBladeOptional(Optional<AbstractBlade> blade)", setterInjectedPkgPrivateOptionalInSubClassInjectedCount, true, DullBlade.class);
        verifyInjected(setterInjectedPkgPrivateListInSubClass, getClass() +
                ".setBladeList(List<AbstractBlade> blades)", setterInjectedPkgPrivateListInSubClassInjectedCount, 1, AbstractBlade.class);
        verifyInjected(setterInjectedAllProviderListInSubClass, getClass() +
                 ".setAllBlades(List<AbstractBlade> blades)", setterInjectedAllProviderListInSubClassInjectedCount, 1, ServiceProvider.class);
        List<AbstractBlade> blades = setterInjectedAllProviderListInSubClass.get(0)
                .list(DefaultContextualServiceQuery.builder()
                              .serviceInfoCriteria(DefaultServiceInfoCriteria.builder()
                                                           .addContractImplemented(AbstractBlade.class.getName())
                                                           .build())
                              .build());
        verifyInjected(blades, getClass() +
                "<all blades>", null, 3, AbstractBlade.class);

        super.verifyState();
    }

}
