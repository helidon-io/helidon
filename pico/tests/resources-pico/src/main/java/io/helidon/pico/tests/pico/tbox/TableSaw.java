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

package io.helidon.pico.tests.pico.tbox;

import java.util.List;
import java.util.Optional;

import io.helidon.pico.InjectionPointInfo;
import io.helidon.pico.tests.pico.tbox.impl.CoarseBlade;
import io.helidon.pico.tests.pico.tbox.impl.DullBlade;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

/**
 * Intentionally in the same package as {AbstractSaw}.
 */
@Singleton
@SuppressWarnings("unused")
public class TableSaw extends AbstractSaw {

    private Optional<Lubricant> ctorInjectedLubricantInSubClass;
    private Optional<Lubricant> setterInjectedLubricantInSubClass;

    private int setterInjectedLubricantInSubClassInjectedCount;

    @Inject @Named(CoarseBlade.NAME) Provider<AbstractBlade> coarseBladeFieldInjectedPkgPrivateProviderInSubClass;
    @Inject @Named(CoarseBlade.NAME) Optional<AbstractBlade> coarseBladeFieldInjectedPkgPrivateOptionalInSubClass;
    @Inject @Named(CoarseBlade.NAME) List<AbstractBlade> coarseBladeFieldInjectedPkgPrivateListInSubClass;
    @Inject @Named(CoarseBlade.NAME) List<Provider<AbstractBlade>> coarseBladeFieldInjectedPkgPrivateProviderListInSubClass;

    Provider<AbstractBlade> setterInjectedPkgPrivateProviderInSubClass;
    Optional<AbstractBlade> setterInjectedPkgPrivateOptionalInSubClass;
    List<AbstractBlade> setterInjectedPkgPrivateListInSubClass;
    List<Provider<AbstractBlade>> setterInjectedPkgPrivateProviderListInSubClass;

    int setterInjectedPkgPrivateProviderInSubClassInjectedCount;
    int setterInjectedPkgPrivateOptionalInSubClassInjectedCount;
    int setterInjectedPkgPrivateListInSubClassInjectedCount;
    int setterInjectedPkgPrivateProviderListInSubClassInjectedCount;

    TableSaw() {
    }

    @Inject
    public TableSaw(
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
    void setBladeProviderInSubclass(
            Provider<AbstractBlade> blade) {
        setterInjectedPkgPrivateProviderInSubClass = blade;
        setterInjectedPkgPrivateProviderInSubClassInjectedCount++;
    }

    @Inject
    void setBladeOptionalInSubclass(
            Optional<AbstractBlade> blade) {
        setterInjectedPkgPrivateOptionalInSubClass = blade;
        setterInjectedPkgPrivateOptionalInSubClassInjectedCount++;
    }

    @Inject
    void setAllBladesInSubclass(
            @Named("*") List<AbstractBlade> blades) {
        setterInjectedPkgPrivateListInSubClass = blades;
        setterInjectedPkgPrivateListInSubClassInjectedCount++;
    }

    @Inject
    void setBladeProviderListInSubclass(
            List<Provider<AbstractBlade>> blades) {
        setterInjectedPkgPrivateProviderListInSubClass = blades;
        setterInjectedPkgPrivateProviderListInSubClassInjectedCount++;
    }

    @Override
    public void verifyState() {
        verifyInjected(ctorInjectedLubricantInSubClass, getClass() + "." + InjectionPointInfo.CONSTRUCTOR, null, false, null);
        verifyInjected(setterInjectedLubricantInSubClass, getClass() + ".injectLubricant(Optional<Lubricant> lubricant)", setterInjectedLubricantInSubClassInjectedCount, false, null);

        verifyInjected(coarseBladeFieldInjectedPkgPrivateProviderInSubClass, getClass()
                + ".coarseBladeFieldInjectedPkgPrivateProviderInSubClass", null, true, CoarseBlade.class);
        verifyInjected(coarseBladeFieldInjectedPkgPrivateOptionalInSubClass, getClass()
                + ".coarseBladeFieldInjectedPkgPrivateOptionalInSubClass", null, true, CoarseBlade.class);
        verifyInjected(coarseBladeFieldInjectedPkgPrivateListInSubClass, getClass()
                + ".coarseBladeFieldInjectedPkgPrivateListInSubClass", null, 1, CoarseBlade.class);

        verifyInjected(setterInjectedPkgPrivateProviderInSubClass, getClass()
                + ".setBladeProvider(Provider<AbstractBlade> blade)", setterInjectedPkgPrivateProviderInSubClassInjectedCount, true, DullBlade.class);
        verifyInjected(setterInjectedPkgPrivateOptionalInSubClass, getClass()
                + ".setBladeOptional(Optional<AbstractBlade> blade)", setterInjectedPkgPrivateOptionalInSubClassInjectedCount, true, DullBlade.class);
        verifyInjected(setterInjectedPkgPrivateListInSubClass, getClass()
                + ".setAllBladesInSubclass(List<AbstractBlade> blades)", setterInjectedPkgPrivateListInSubClassInjectedCount, 3, AbstractBlade.class);

        super.verifyState();
    }

}
