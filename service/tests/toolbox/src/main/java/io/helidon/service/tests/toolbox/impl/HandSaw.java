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

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.service.registry.Service;
import io.helidon.service.tests.toolbox.AbstractBlade;
import io.helidon.service.tests.toolbox.AbstractSaw;
import io.helidon.service.tests.toolbox.Lubricant;
import io.helidon.service.tests.toolbox.Verification;

/**
 * Kept intentionally in a different package from {@link AbstractSaw} for testing.
 */
@Service.Singleton
public class HandSaw extends AbstractSaw {

    @Service.Inject @Service.Named(FineBlade.NAME) Supplier<AbstractBlade> fineBladeFieldInjectedPkgPrivateProviderInSubClass;
    @Service.Inject @Service.Named(FineBlade.NAME) Optional<AbstractBlade> fineBladeFieldInjectedPkgPrivateOptionalInSubClass;
    @Service.Inject @Service.Named(FineBlade.NAME) List<AbstractBlade> fineBladeFieldInjectedPkgPrivateListInSubClass;
    Supplier<AbstractBlade> setterInjectedPkgPrivateProviderInSubClass;
    Optional<AbstractBlade> setterInjectedPkgPrivateOptionalInSubClass;
    List<AbstractBlade> setterInjectedPkgPrivateListInSubClass;

    int setterInjectedPkgPrivateProviderInSubClassInjectedCount;
    int setterInjectedPkgPrivateOptionalInSubClassInjectedCount;
    int setterInjectedPkgPrivateListInSubClassInjectedCount;

    private Optional<Lubricant> ctorInjectedLubricantInSubClass;
    private Optional<Lubricant> setterInjectedLubricantInSubClass;
    private int setterInjectedLubricantInSubClassInjectedCount;

    HandSaw() {
    }

    @Service.Inject
    public HandSaw(Optional<Lubricant> lubricant) {
        ctorInjectedLubricantInSubClass = lubricant;
    }

    @Override
    public String name() {
        return getClass().getSimpleName();
    }

    @Override
    public void verifyState() {
        Verification.verifyInjected(ctorInjectedLubricantInSubClass, getClass()
                + ".<init>", null, false, null);
        Verification.verifyInjected(setterInjectedLubricantInSubClass, getClass()
                + ".injectLubricant(Optional<Lubricant> lubricant)", setterInjectedLubricantInSubClassInjectedCount, false, null);

        // we use cardinality of the InjectionPointProvider
        Verification.verifyInjected(fineBladeFieldInjectedPkgPrivateProviderInSubClass, getClass()
                + ".fineBladeFieldInjectedPkgPrivateProviderInSubClass", null, false, FineBlade.class);
        Verification.verifyInjected(fineBladeFieldInjectedPkgPrivateOptionalInSubClass, getClass() +
                ".fineBladeFieldInjectedPkgPrivateOptionalInSubClass", null, true, FineBlade.class);
        Verification.verifyInjected(fineBladeFieldInjectedPkgPrivateListInSubClass, getClass() +
                ".fineBladeFieldInjectedPkgPrivateListInSubClass", null, 1, FineBlade.class);

        // we use cardinality of the InjectionPointProvider
        Verification.verifyInjected(setterInjectedPkgPrivateProviderInSubClass,
                                    getClass() +
                                            ".setBladeProvider(Provider<AbstractBlade> blade)",
                                    setterInjectedPkgPrivateProviderInSubClassInjectedCount,
                                    false,
                                    DullBlade.class);
        Verification.verifyInjected(setterInjectedPkgPrivateOptionalInSubClass,
                                    getClass() +
                                            ".setBladeOptional(Optional<AbstractBlade> blade)",
                                    setterInjectedPkgPrivateOptionalInSubClassInjectedCount,
                                    true,
                                    DullBlade.class);
        Verification.verifyInjected(setterInjectedPkgPrivateListInSubClass,
                                    getClass() +
                                            ".setBladeList(List<AbstractBlade> blades)",
                                    setterInjectedPkgPrivateListInSubClassInjectedCount,
                                    1,
                                    AbstractBlade.class);

        super.verifyState();
    }

    @Service.Inject
    protected void injectLubricant(Optional<Lubricant> lubricant) {
        setterInjectedLubricantInSubClass = lubricant;
        setterInjectedLubricantInSubClassInjectedCount++;
    }

    @Service.Inject
    void setBladeProvider(Supplier<AbstractBlade> blade) {
        setterInjectedPkgPrivateProviderInSubClass = blade;
        setterInjectedPkgPrivateProviderInSubClassInjectedCount++;
    }

    @Service.Inject
    void setBladeOptional(Optional<AbstractBlade> blade) {
        setterInjectedPkgPrivateOptionalInSubClass = blade;
        setterInjectedPkgPrivateOptionalInSubClassInjectedCount++;
    }

    @Service.Inject
    void setBladeList(List<AbstractBlade> blades) {
        setterInjectedPkgPrivateListInSubClass = blades;
        setterInjectedPkgPrivateListInSubClassInjectedCount++;
    }
}
