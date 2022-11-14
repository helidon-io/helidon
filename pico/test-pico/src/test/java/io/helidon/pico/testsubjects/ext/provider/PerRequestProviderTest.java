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

package io.helidon.pico.testsubjects.ext.provider;

import io.helidon.pico.PicoServices;
import io.helidon.pico.ServiceProvider;
import io.helidon.pico.Services;
import io.helidon.pico.spi.ext.ExtendedServices;
import io.helidon.pico.spi.ext.Resetable;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class PerRequestProviderTest {

    private PicoServices picoServices;
    private Services services;

    @BeforeEach
    public void init() {
        PicoServices picoServices = PicoServices.picoServices().get();
        assert (picoServices instanceof Resetable);
        ((Resetable) picoServices).reset();
        this.picoServices = picoServices;
        this.services = picoServices.services();
        assert (services instanceof ExtendedServices);
    }

    @Test
    public void myConcreteClassContractTest() {
        ServiceProvider<MyConcreteClassContract> sp = services.lookupFirst(MyConcreteClassContract.class);
        assertThat(ServiceProvider.toDescription(sp),
                   is("MyServices$MyConcreteClassContractPerRequestIPProvider$$picoActivator:io.helidon.pico"
                              + ".testsubjects.ext.provider.MyServices$MyConcreteClassContractPerRequestIPProvider"
                              + ":INIT"));
        MyConcreteClassContract instance0 = sp.get();
        assertThat(instance0.toString(),
                   is("MyConcreteClassContractPerRequestIPProvider:instance_0, null, null, true, MyConcreteClassContractPerRequestProvider:instance_0"));
        MyConcreteClassContract instance1 = sp.get();
        assertThat(instance1.toString(),
                   is("MyConcreteClassContractPerRequestIPProvider:instance_1, null, null, true, MyConcreteClassContractPerRequestProvider:instance_0"));
    }

}
