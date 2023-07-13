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

package io.helidon.inject.tools;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import io.helidon.common.types.TypeName;
import io.helidon.inject.api.Qualifier;
import io.helidon.inject.tools.spi.ActivatorCreator;
import io.helidon.inject.tools.spi.ExternalModuleCreator;

import org.atinject.tck.auto.Drivers;
import org.atinject.tck.auto.DriversSeat;
import org.atinject.tck.auto.accessories.SpareTire;
import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.junit5.OptionalMatcher.optionalValue;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Tests for {@link ExternalModuleCreatorDefault}. This test
 * effectively demonstrates the behavior of the injection {@code maven-plugin}.
 */
class ExternalModuleCreatorDefaultTest extends AbstractBaseCreator {

    final ExternalModuleCreator externalModuleCreator = loadAndCreate(ExternalModuleCreator.class);

    @Test
    void sanity() {
        assertThat(externalModuleCreator.getClass(), equalTo(ExternalModuleCreatorDefault.class));
    }

    @Test
    void tck330Gen() {
        Thread.currentThread().setContextClassLoader(ExternalModuleCreatorDefaultTest.class.getClassLoader());

        CodeGenPaths codeGenPaths = CodeGenPaths.builder()
                .generatedSourcesPath("target/inject/generated-sources")
                .outputPath("target/inject/generated-classes")
                .build();
        AbstractFilerMessager directFiler = AbstractFilerMessager
                .createDirectFiler(codeGenPaths, System.getLogger(getClass().getName()));
        CodeGenFiler filer = CodeGenFiler.create(directFiler);

        ActivatorCreatorConfigOptions activatorCreatorConfigOptions = ActivatorCreatorConfigOptions.builder()
                .supportsJsr330InStrictMode(true)
                .build();

        ExternalModuleCreatorRequest req = ExternalModuleCreatorRequest.builder()
                .addPackageNamesToScan("org.atinject.tck.auto")
                .addPackageNamesToScan("org.atinject.tck.auto.accessories")
                .addServiceTypeToQualifiersMap(SpareTire.class.getName(),
                                         Set.of(Qualifier.createNamed("spare")))
                .addServiceTypeToQualifiersMap(DriversSeat.class.getName(),
                                         Set.of(Qualifier.create(Drivers.class)))
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
        assertThat(res.moduleName(), optionalValue(equalTo("jakarta.inject.tck")));
        assertThat(res.activatorCreatorRequest(), notNullValue());

        ActivatorCreator activatorCreator = loadAndCreate(ActivatorCreator.class);
        ActivatorCreatorResponse response = activatorCreator.createModuleActivators(res.activatorCreatorRequest());
        assertThat(response.toString(), response.success(), is(true));
    }

}
