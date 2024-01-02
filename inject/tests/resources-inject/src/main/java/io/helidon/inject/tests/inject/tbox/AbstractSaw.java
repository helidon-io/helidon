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
import io.helidon.inject.tests.inject.Verification;
import io.helidon.inject.tests.inject.tbox.impl.DullBlade;

public abstract class AbstractSaw extends Verification implements Tool {
    @Injection.Inject protected Supplier<AbstractBlade> fieldInjectedProtectedProviderInAbstractBase;
    @Injection.Inject protected Optional<AbstractBlade> fieldInjectedProtectedOptionalInAbstractBase;
    @Injection.Inject protected List<AbstractBlade> fieldInjectedProtectedListInAbstractBase;
    @Injection.Inject protected List<AbstractBlade> fieldInjectedProtectedProviderListInAbstractBase;

    @Injection.Inject Supplier<AbstractBlade> fieldInjectedPkgPrivateProviderInAbstractBase;
    @Injection.Inject Optional<AbstractBlade> fieldInjectedPkgPrivateOptionalInAbstractBase;
    @Injection.Inject List<AbstractBlade> fieldInjectedPkgPrivateListInAbstractBase;
    @Injection.Inject List<Supplier<AbstractBlade>> fieldInjectedPkgPrivateProviderListInAbstractBase;

    Supplier<AbstractBlade> setterInjectedPkgPrivateProviderInAbstractBase;
    Optional<AbstractBlade> setterInjectedPkgPrivateOptionalInAbstractBase;
    List<AbstractBlade> setterInjectedPkgPrivateListInAbstractBase;
    List<Supplier<AbstractBlade>> setterInjectedPkgPrivateProviderListInAbstractBase;

    int setterInjectedPkgPrivateProviderInAbstractBaseInjectedCount;
    int setterInjectedPkgPrivateOptionalInAbstractBaseInjectedCount;
    int setterInjectedPkgPrivateListInAbstractBaseInjectedCount;
    int setterInjectedPkgPrivateProviderListInAbstractBaseInjectedCount;

    @Injection.Inject
    void setBladeProvider(Supplier<AbstractBlade> blade) {
        setterInjectedPkgPrivateProviderInAbstractBase = blade;
        setterInjectedPkgPrivateProviderInAbstractBaseInjectedCount++;
    }

    @Injection.Inject
    void setBladeOptional(Optional<AbstractBlade> blade) {
        setterInjectedPkgPrivateOptionalInAbstractBase = blade;
        setterInjectedPkgPrivateOptionalInAbstractBaseInjectedCount++;
    }

    @Injection.Inject
    void setBladeList(List<AbstractBlade> blades) {
        setterInjectedPkgPrivateListInAbstractBase = blades;
        setterInjectedPkgPrivateListInAbstractBaseInjectedCount++;
    }

    @Injection.Inject
    public void setBladeProviders(List<Supplier<AbstractBlade>> blades) {
        setterInjectedPkgPrivateProviderListInAbstractBase = blades;
        setterInjectedPkgPrivateProviderListInAbstractBaseInjectedCount++;
    }

    public void verifyState() {
        verifyInjected(fieldInjectedProtectedOptionalInAbstractBase, getClass()
                + ".fieldInjectedProtectedOptionalInAbstractBase", null, true, DullBlade.class);
        verifyInjected(fieldInjectedProtectedProviderInAbstractBase, getClass()
                + ".fieldInjectedProtectedProviderInAbstractBase", null, true, DullBlade.class);
        verifyInjected(fieldInjectedProtectedListInAbstractBase, getClass()
                + ".fieldInjectedProtectedListInAbstractBase", null, 1, AbstractBlade.class);
        verifyInjected(setterInjectedPkgPrivateProviderListInAbstractBase, getClass()
                + ".setterInjectedPkgPrivateProviderListInAbstractBase", null, 1, Supplier.class);

        verifyInjected(fieldInjectedPkgPrivateProviderInAbstractBase, getClass()
                + ".fieldInjectedPkgPrivateProviderInAbstractBase", null, true, DullBlade.class);
        verifyInjected(fieldInjectedPkgPrivateOptionalInAbstractBase, getClass()
                + ".fieldInjectedPkgPrivateOptionalInAbstractBase", null, true, DullBlade.class);
        verifyInjected(fieldInjectedPkgPrivateListInAbstractBase, getClass()
                + ".fieldInjectedPkgPrivateListInAbstractBase", null, 1, DullBlade.class);
        verifyInjected(fieldInjectedPkgPrivateProviderListInAbstractBase, getClass()
                + ".fieldInjectedPkgPrivateProviderListInAbstractBase", null, 1,  Supplier.class);

        verifyInjected(setterInjectedPkgPrivateProviderInAbstractBase, getClass()
                + ".setBladeProvider(Provider<AbstractBlade> blade)",
                       setterInjectedPkgPrivateProviderInAbstractBaseInjectedCount, true, DullBlade.class);
        verifyInjected(setterInjectedPkgPrivateOptionalInAbstractBase, getClass()
                + ".setBladeOptional(Optional<AbstractBlade> blade)",
                       setterInjectedPkgPrivateOptionalInAbstractBaseInjectedCount, true, DullBlade.class);
        verifyInjected(setterInjectedPkgPrivateListInAbstractBase, getClass()
                + ".setBladeList(List<AbstractBlade> blades)",
                       setterInjectedPkgPrivateListInAbstractBaseInjectedCount, 1, DullBlade.class);
        verifyInjected(fieldInjectedPkgPrivateProviderListInAbstractBase, getClass()
                + ".fieldInjectedPkgPrivateProviderListInAbstractBase", null, 1, Supplier.class);
    }

}
