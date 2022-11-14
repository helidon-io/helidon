/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.pico.tools.creator.impl;

import java.util.Collections;
import java.util.ServiceLoader;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.pico.DefaultQualifierAndValue;
import io.helidon.pico.types.DefaultTypeName;
import io.helidon.pico.test.utils.JsonUtils;
import io.helidon.pico.tools.creator.ActivatorCodeGenDetail;
import io.helidon.pico.tools.creator.ActivatorCreator;
import io.helidon.pico.tools.creator.ActivatorCreatorResponse;
import io.helidon.pico.tools.creator.ExternalModuleCreator;
import io.helidon.pico.tools.creator.ExternalModuleCreatorResponse;
import io.helidon.pico.tools.utils.CommonUtils;

import org.atinject.tck.auto.Convertible;
import org.atinject.tck.auto.Drivers;
import org.atinject.tck.auto.DriversSeat;
import org.atinject.tck.auto.accessories.SpareTire;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link io.helidon.pico.tools.creator.impl.DefaultExternalModuleCreator}. This test
 * effectively demonstrates the behavior for the maven-plugin.
 */
public class DefaultExternalModuleCreatorTest extends AbstractBaseCreator {

    private final ExternalModuleCreator externalModuleCreator = loadAndCreate(ExternalModuleCreator.class);

    @Test
    public void sanity() {
        assertNotNull(externalModuleCreator);
        assertEquals(DefaultExternalModuleCreator.class, externalModuleCreator.getClass());
    }

    /**
     * Basic tests for {@link io.helidon.pico.tools.creator.impl.DefaultExternalModuleCreator}.
     */
    @Test
    public void tck330Gen() {
        Thread.currentThread().setContextClassLoader(DefaultExternalModuleCreatorTest.class.getClassLoader());

        DefaultGeneralCodeGenPaths codeGenPaths = DefaultGeneralCodeGenPaths.builder()
                .generatedSourcesPath("target/generated-pico/sources")
                .outputPath("target/generated-pico/classes")
                .metaInfServicesPath("target/generated-pico/classes/META-INF/services")
                .build();

        DefaultActivatorCreatorConfigOptions activatorCreatorConfigOptions = DefaultActivatorCreatorConfigOptions.builder()
                .supportsJsr330InStrictMode(true)
                .build();

        DefaultExternalModuleCreatorRequest req = (DefaultExternalModuleCreatorRequest) DefaultExternalModuleCreatorRequest.builder()
                .packageNameToScan("org.atinject.tck.auto")
                .packageNameToScan("org.atinject.tck.auto.accessories")
                .serviceTypeQualifier(SpareTire.class.getName(),
                                      Collections.singleton(DefaultQualifierAndValue.createNamed("spare")))
                .serviceTypeQualifier(DriversSeat.class.getName(),
                                      Collections.singleton(DefaultQualifierAndValue.create(Drivers.class)))
                .activatorCreatorConfigOptions(activatorCreatorConfigOptions)
                .isInnerClassesProcessed(false)
                .codeGenPaths(codeGenPaths)
                .build();
        ExternalModuleCreatorResponse res = externalModuleCreator.prepareToCreateExternalModule(req);
        assertNotNull(res);
        assertTrue(res.isSuccess(), res.toString());
        assertEquals("[org.atinject.tck.auto.Convertible, org.atinject.tck.auto.DriversSeat, org.atinject.tck.auto.Engine,"
                        + " org.atinject.tck.auto.FuelTank, org.atinject.tck.auto.GasEngine, org.atinject.tck.auto"
                        + ".Seat, org.atinject.tck.auto.Seatbelt, org.atinject.tck.auto.Tire, org.atinject.tck.auto"
                        + ".V8Engine, org.atinject.tck.auto.accessories.Cupholder, org.atinject.tck.auto.accessories"
                        + ".RoundThing, org.atinject.tck.auto.accessories.SpareTire]",
                     res.getServiceTypeNames().toString());
        assertEquals("jakarta.inject.tck", res.getModuleName());
        assertNotNull(res.getActivatorCreatorRequest());

        DefaultActivatorCreator activatorCreator = (DefaultActivatorCreator) HelidonServiceLoader
                .create(ServiceLoader.load(ActivatorCreator.class))
                .iterator()
                .next();
        AbstractFilerMsgr directFiler = AbstractFilerMsgr.createDirectFiler(req.getCodeGenPaths(), null);
        CodeGenFiler codeGen = new CodeGenFiler(directFiler);
        activatorCreator.setCodeGenFiler(codeGen);

        ActivatorCreatorResponse response = activatorCreator.createModuleActivators(res.getActivatorCreatorRequest());
        assertNotNull(response);
        assertTrue(response.isSuccess());

        ActivatorCodeGenDetail convertibleDetail =
                response.getServiceTypeDetails().get(DefaultTypeName.create(Convertible.class));
        assertEquals(CommonUtils.loadStringFromResource("expected/convertible-dependencies.json").trim(),
                     JsonUtils.prettyPrintJson(convertibleDetail.getDependencies()));
    }

}
