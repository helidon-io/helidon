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

package io.helidon.inject.tests.inject.tbox;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.inject.service.Injection;
import io.helidon.inject.tests.inject.tbox.impl.CoarseBlade;
import io.helidon.inject.tests.inject.tbox.impl.DullBlade;

/**
 * Intentionally in the same package as {AbstractSaw}.
 */
@Injection.Singleton
@SuppressWarnings("unused")
public class TableSaw extends AbstractSaw {

    private Optional<Lubricant> ctorInjectedLubricantInSubClass;
    private Optional<Lubricant> setterInjectedLubricantInSubClass;

    private int setterInjectedLubricantInSubClassInjectedCount;

    @Injection.Inject @Injection.Named(CoarseBlade.NAME) Supplier<AbstractBlade> coarseBladeFieldInjectedPkgPrivateProviderInSubClass;
    @Injection.Inject @Injection.Named(CoarseBlade.NAME) Optional<AbstractBlade> coarseBladeFieldInjectedPkgPrivateOptionalInSubClass;
    @Injection.Inject @Injection.Named(CoarseBlade.NAME) List<AbstractBlade> coarseBladeFieldInjectedPkgPrivateListInSubClass;
    @Injection.Inject @Injection.Named(CoarseBlade.NAME) List<Supplier<AbstractBlade>> coarseBladeFieldInjectedPkgPrivateProviderListInSubClass;

    Supplier<AbstractBlade> setterInjectedPkgPrivateProviderInSubClass;
    Optional<AbstractBlade> setterInjectedPkgPrivateOptionalInSubClass;
    List<AbstractBlade> setterInjectedPkgPrivateListInSubClass;
    List<Supplier<AbstractBlade>> setterInjectedPkgPrivateProviderListInSubClass;

    int setterInjectedPkgPrivateProviderInSubClassInjectedCount;
    int setterInjectedPkgPrivateOptionalInSubClassInjectedCount;
    int setterInjectedPkgPrivateListInSubClassInjectedCount;
    int setterInjectedPkgPrivateProviderListInSubClassInjectedCount;

    TableSaw() {
    }

    @Injection.Inject
    public TableSaw(Optional<Lubricant> lubricant) {
        ctorInjectedLubricantInSubClass = lubricant;
    }

    @Override
    public String name() {
        return getClass().getSimpleName();
    }

    @Injection.Inject
    protected void injectLubricant(Optional<Lubricant> lubricant) {
        setterInjectedLubricantInSubClass = lubricant;
        setterInjectedLubricantInSubClassInjectedCount++;
    }

    @Injection.Inject
    void setBladeProviderInSubclass(Supplier<AbstractBlade> blade) {
        setterInjectedPkgPrivateProviderInSubClass = blade;
        setterInjectedPkgPrivateProviderInSubClassInjectedCount++;
    }

    @Injection.Inject
    void setBladeOptionalInSubclass(Optional<AbstractBlade> blade) {
        setterInjectedPkgPrivateOptionalInSubClass = blade;
        setterInjectedPkgPrivateOptionalInSubClassInjectedCount++;
    }

    @Injection.Inject
    void setAllBladesInSubclass(@Injection.Named("*") List<AbstractBlade> blades) {
        setterInjectedPkgPrivateListInSubClass = blades;
        setterInjectedPkgPrivateListInSubClassInjectedCount++;
    }

    @Injection.Inject
    void setBladeProviderListInSubclass(List<Supplier<AbstractBlade>> blades) {
        setterInjectedPkgPrivateProviderListInSubClass = blades;
        setterInjectedPkgPrivateProviderListInSubClassInjectedCount++;
    }

    @Override
    public void verifyState() {
        verifyInjected(ctorInjectedLubricantInSubClass, getClass() + ".<init>" , null, false, null);
        verifyInjected(setterInjectedLubricantInSubClass, getClass() + ".injectLubricant(Optional<Lubricant> lubricant)", setterInjectedLubricantInSubClassInjectedCount, false, null);

        // injection point provider uses cardinality of the provider implementation (scope is not handled by registry)
        verifyInjected(coarseBladeFieldInjectedPkgPrivateProviderInSubClass, getClass()
                + ".coarseBladeFieldInjectedPkgPrivateProviderInSubClass", null, false, CoarseBlade.class);
        verifyInjected(coarseBladeFieldInjectedPkgPrivateOptionalInSubClass, getClass()
                + ".coarseBladeFieldInjectedPkgPrivateOptionalInSubClass", null, true, CoarseBlade.class);
        verifyInjected(coarseBladeFieldInjectedPkgPrivateListInSubClass, getClass()
                + ".coarseBladeFieldInjectedPkgPrivateListInSubClass", null, 1, CoarseBlade.class);

        // injection point provider uses cardinality of the provider implementation (scope is not handled by registry)
        verifyInjected(setterInjectedPkgPrivateProviderInSubClass, getClass()
                + ".setBladeProvider(Provider<AbstractBlade> blade)", setterInjectedPkgPrivateProviderInSubClassInjectedCount, false, DullBlade.class);
        verifyInjected(setterInjectedPkgPrivateOptionalInSubClass, getClass()
                + ".setBladeOptional(Optional<AbstractBlade> blade)", setterInjectedPkgPrivateOptionalInSubClassInjectedCount, true, DullBlade.class);
        verifyInjected(setterInjectedPkgPrivateListInSubClass, getClass()
                + ".setAllBladesInSubclass(List<AbstractBlade> blades)", setterInjectedPkgPrivateListInSubClassInjectedCount, 3, AbstractBlade.class);

        super.verifyState();
    }

}
