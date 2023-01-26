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

import io.helidon.pico.ServiceProvider;
import io.helidon.pico.tests.pico.tbox.impl.DullBlade;
import io.helidon.pico.tests.pico.Verification;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

public abstract class AbstractSaw extends Verification implements Tool {
    @Inject protected Provider<AbstractBlade> fieldInjectedProtectedProviderInAbstractBase;
    @Inject protected Optional<AbstractBlade> fieldInjectedProtectedOptionalInAbstractBase;
    @Inject protected List<AbstractBlade> fieldInjectedProtectedListInAbstractBase;
    @Inject protected List<AbstractBlade> fieldInjectedProtectedProviderListInAbstractBase;

    @Inject Provider<AbstractBlade> fieldInjectedPkgPrivateProviderInAbstractBase;
    @Inject Optional<AbstractBlade> fieldInjectedPkgPrivateOptionalInAbstractBase;
    @Inject List<AbstractBlade> fieldInjectedPkgPrivateListInAbstractBase;
    @Inject List<Provider<AbstractBlade>> fieldInjectedPkgPrivateProviderListInAbstractBase;

    Provider<AbstractBlade> setterInjectedPkgPrivateProviderInAbstractBase;
    Optional<AbstractBlade> setterInjectedPkgPrivateOptionalInAbstractBase;
    List<AbstractBlade> setterInjectedPkgPrivateListInAbstractBase;
    List<Provider<AbstractBlade>> setterInjectedPkgPrivateProviderListInAbstractBase;

    int setterInjectedPkgPrivateProviderInAbstractBaseInjectedCount;
    int setterInjectedPkgPrivateOptionalInAbstractBaseInjectedCount;
    int setterInjectedPkgPrivateListInAbstractBaseInjectedCount;
    int setterInjectedPkgPrivateProviderListInAbstractBaseInjectedCount;

    @Inject
    void setBladeProvider(
            Provider<AbstractBlade> blade) {
        setterInjectedPkgPrivateProviderInAbstractBase = blade;
        setterInjectedPkgPrivateProviderInAbstractBaseInjectedCount++;
    }

    @Inject
    void setBladeOptional(
            Optional<AbstractBlade> blade) {
        setterInjectedPkgPrivateOptionalInAbstractBase = blade;
        setterInjectedPkgPrivateOptionalInAbstractBaseInjectedCount++;
    }

    @Inject
    void setBladeList(
            List<AbstractBlade> blades) {
        setterInjectedPkgPrivateListInAbstractBase = blades;
        setterInjectedPkgPrivateListInAbstractBaseInjectedCount++;
    }

    @Inject
    void setBladeProviders(
            List<Provider<AbstractBlade>> blades) {
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
                + ".setterInjectedPkgPrivateProviderListInAbstractBase", null, 1, ServiceProvider.class);

        verifyInjected(fieldInjectedPkgPrivateProviderInAbstractBase, getClass()
                + ".fieldInjectedPkgPrivateProviderInAbstractBase", null, true, DullBlade.class);
        verifyInjected(fieldInjectedPkgPrivateOptionalInAbstractBase, getClass()
                + ".fieldInjectedPkgPrivateOptionalInAbstractBase", null, true, DullBlade.class);
        verifyInjected(fieldInjectedPkgPrivateListInAbstractBase, getClass()
                + ".fieldInjectedPkgPrivateListInAbstractBase", null, 1, DullBlade.class);
        verifyInjected(fieldInjectedPkgPrivateProviderListInAbstractBase, getClass()
                + ".fieldInjectedPkgPrivateProviderListInAbstractBase", null, 1,  ServiceProvider.class);

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
                + ".fieldInjectedPkgPrivateProviderListInAbstractBase", null, 1, ServiceProvider.class);
    }

}
