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
import java.util.stream.Collectors;

import io.helidon.common.types.TypeName;
import io.helidon.inject.api.InjectionServices;
import io.helidon.inject.api.ServiceInfoCriteria;
import io.helidon.inject.api.ServiceProvider;
import io.helidon.inject.api.Services;
import io.helidon.inject.tools.spi.ApplicationCreator;

import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.junit5.OptionalMatcher.optionalEmpty;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

/**
 * Tests for {@link ActivatorCreatorDefault}.
 */
class ApplicationCreatorDefaultTest extends AbstractBaseCreator {

    final ApplicationCreator applicationCreator = loadAndCreate(ApplicationCreator.class);

    @Test
    void sanity() {
        assertThat(applicationCreator.getClass(), equalTo(ApplicationCreatorDefault.class));
    }

    /**
     * Most of the testing will need to occur downstream from this module.
     */
    @Test
    void codegenHelloWorldApplication() {
        ApplicationCreator creator = this.applicationCreator;
        ServiceInfoCriteria allServices = ServiceInfoCriteria.builder().build();

        InjectionServices injectionServices = InjectionServices.injectionServices().orElseThrow();
        Services services = injectionServices.services();
        List<ServiceProvider<?>> serviceProviders = services.lookupAll(allServices);
        assertThat(serviceProviders.size(), is(0));

        List<TypeName> serviceTypeNames = serviceProviders.stream()
                .map(sp -> sp.serviceInfo().serviceTypeName())
                .toList();

        // note: this test needs to align with target/inject/... for this to work/test properly
        CodeGenPaths codeGenPaths = CodeGenPaths.builder()
                .generatedSourcesPath("target/inject/generated-sources")
                .outputPath("target/inject/generated-classes")
                .build();
        AbstractFilerMessager directFiler = AbstractFilerMessager
                .createDirectFiler(codeGenPaths, System.getLogger(getClass().getName()));
        CodeGenFiler filer = CodeGenFiler.create(directFiler);

        String classpath = System.getProperty("java.class.path");
        String separator = System.getProperty("path.separator");
        String[] ignoredClasspath = classpath.split(separator);
        ApplicationCreatorRequest req = ApplicationCreatorRequest.builder()
                .codeGen(ApplicationCreatorCodeGen.builder()
                                 .className(ActivatorCreatorDefault.applicationClassName("test"))
                                 .classPrefixName("test")
                                 .build())
                .codeGenPaths(codeGenPaths)
                .configOptions(ApplicationCreatorConfigOptions.builder()
                                       .permittedProviderTypes(PermittedProviderType.ALL)
                                       .build())
                .filer(filer)
                .messager(directFiler)
                .serviceTypeNames(serviceTypeNames)
                .generatedServiceTypeNames(serviceTypeNames)
                .build();

        ApplicationCreatorResponse res = creator.createApplication(req);
        assertThat(res.error(), optionalEmpty());
        assertThat(res.success(), is(true));
        assertThat(res.serviceTypeNames().stream().map(TypeName::name).collect(Collectors.toList()),
                   contains("inject.Injection$$TestApplication"));
        assertThat(res.templateName(), equalTo("default"));
        assertThat(res.moduleName(), optionalEmpty());
    }

}
