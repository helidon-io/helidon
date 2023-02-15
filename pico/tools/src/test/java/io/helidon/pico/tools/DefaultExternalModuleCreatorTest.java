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

package io.helidon.pico.tools;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import io.helidon.common.testing.junit5.OptionalMatcher;
import io.helidon.common.types.TypeName;
import io.helidon.pico.DefaultQualifierAndValue;

import org.atinject.tck.auto.Drivers;
import org.atinject.tck.auto.DriversSeat;
import org.atinject.tck.auto.accessories.SpareTire;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Tests for {@link io.helidon.pico.tools.DefaultExternalModuleCreator}. This test
 * effectively demonstrates the behavior of the pico-maven-plugin.
 */
class DefaultExternalModuleCreatorTest extends AbstractBaseCreator {

    final ExternalModuleCreator externalModuleCreator = loadAndCreate(ExternalModuleCreator.class);

    @Test
    void sanity() {
        assertThat(externalModuleCreator.getClass(), equalTo(DefaultExternalModuleCreator.class));
    }

    @Test
    void tck330Gen() {
        Thread.currentThread().setContextClassLoader(DefaultExternalModuleCreatorTest.class.getClassLoader());

        CodeGenPaths codeGenPaths = DefaultCodeGenPaths.builder()
                .generatedSourcesPath("target/pico/generated-sources")
                .outputPath("target/pico/generated-classes")
                .build();
        AbstractFilerMsgr directFiler = AbstractFilerMsgr
                .createDirectFiler(codeGenPaths, System.getLogger(getClass().getName()));
        CodeGenFiler filer = CodeGenFiler.create(directFiler);

        ActivatorCreatorConfigOptions activatorCreatorConfigOptions = DefaultActivatorCreatorConfigOptions.builder()
                .supportsJsr330InStrictMode(true)
                .build();

        ExternalModuleCreatorRequest req = DefaultExternalModuleCreatorRequest.builder()
                .addPackageNamesToScan("org.atinject.tck.auto")
                .addPackageNamesToScan("org.atinject.tck.auto.accessories")
                .addServiceTypeToQualifiersMap(SpareTire.class.getName(),
                                         Set.of(DefaultQualifierAndValue.createNamed("spare")))
                .addServiceTypeToQualifiersMap(DriversSeat.class.getName(),
                                         Set.of(DefaultQualifierAndValue.create(Drivers.class)))
                .activatorCreatorConfigOptions(activatorCreatorConfigOptions)
                .innerClassesProcessed(false)
                .codeGenPaths(codeGenPaths)
                .filer(filer)
                .build();
        ExternalModuleCreatorResponse res = externalModuleCreator.prepareToCreateExternalModule(req);
        assertThat(res.toString(), res.success(), is(true));
        List<String> desc = res.serviceTypeNames().stream().map(TypeName::name).collect(Collectors.toList());
        assertThat(desc, containsInAnyOrder(
                "org.atinject.tck.auto.Convertible",
                "org.atinject.tck.auto.DriversSeat",
                "org.atinject.tck.auto.Engine",
                "org.atinject.tck.auto.FuelTank",
                "org.atinject.tck.auto.GasEngine",
                "org.atinject.tck.auto.Seat",
                "org.atinject.tck.auto.Seatbelt",
                "org.atinject.tck.auto.Tire",
                "org.atinject.tck.auto.V8Engine",
                "org.atinject.tck.auto.accessories.Cupholder",
                "org.atinject.tck.auto.accessories.RoundThing",
                "org.atinject.tck.auto.accessories.SpareTire"
        ));
        assertThat(res.moduleName(), OptionalMatcher.optionalValue(equalTo("jakarta.inject.tck")));
        assertThat(res.activatorCreatorRequest(), notNullValue());

        ActivatorCreator activatorCreator = loadAndCreate(ActivatorCreator.class);
        ActivatorCreatorResponse response = activatorCreator.createModuleActivators(res.activatorCreatorRequest());
        assertThat(response.toString(), response.success(), is(true));
    }

}
