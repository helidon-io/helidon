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
import java.util.stream.Collectors;

import io.helidon.common.types.DefaultTypeName;
import io.helidon.common.types.TypeName;
import io.helidon.pico.DefaultServiceInfoCriteria;
import io.helidon.pico.PicoServices;
import io.helidon.pico.ServiceInfoCriteria;
import io.helidon.pico.ServiceProvider;
import io.helidon.pico.Services;

import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.junit5.OptionalMatcher.optionalEmpty;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

/**
 * Tests for {@link io.helidon.pico.tools.DefaultActivatorCreator}.
 */
class DefaultApplicationCreatorTest extends AbstractBaseCreator {

    final ApplicationCreator applicationCreator = loadAndCreate(ApplicationCreator.class);

    @Test
    void sanity() {
        assertThat(applicationCreator.getClass(), equalTo(DefaultApplicationCreator.class));
    }

    /**
     * Most of the testing will need to occur downstream from this module.
     */
    @Test
    void codegenHelloWorldApplication() {
        ApplicationCreator creator = this.applicationCreator;
        ServiceInfoCriteria allServices = DefaultServiceInfoCriteria.builder().build();

        PicoServices picoServices = PicoServices.picoServices().orElseThrow();
        Services services = picoServices.services();
        List<ServiceProvider<Object>> serviceProviders = services.lookupAll(allServices);

        List<TypeName> serviceTypeNames = serviceProviders.stream()
                .map(sp -> DefaultTypeName.createFromTypeName(sp.serviceInfo().serviceTypeName()))
                .collect(Collectors.toList());

        CodeGenPaths codeGenPaths = DefaultCodeGenPaths.builder()
                .generatedSourcesPath("target/pico/generated-sources")
                .outputPath("target/pico/generated-classes")
                .build();
        AbstractFilerMsgr directFiler = AbstractFilerMsgr
                .createDirectFiler(codeGenPaths, System.getLogger(getClass().getName()));
        CodeGenFiler filer = CodeGenFiler.create(directFiler);

        String classpath = System.getProperty("java.class.path");
        String separator = System.getProperty("path.separator");
        String[] cp = classpath.split(separator);
        ApplicationCreatorRequest req = DefaultApplicationCreatorRequest.builder()
                .codeGen(DefaultApplicationCreatorCodeGen.builder()
                                        .className(DefaultApplicationCreator.toApplicationClassName("test"))
                                        .classPrefixName("test")
                                        .build())
                .codeGenPaths(codeGenPaths)
                .configOptions(DefaultApplicationCreatorConfigOptions.builder()
                                       .permittedProviderTypes(ApplicationCreatorConfigOptions.PermittedProviderType.ALL)
                                       .build())
                .filer(filer)
                .messager(directFiler)
                .serviceTypeNames(serviceTypeNames)
                .build();

        ApplicationCreatorResponse res = creator.createApplication(req);
        assertThat(res.error(), optionalEmpty());
        assertThat(res.success(), is(true));
        assertThat(res.serviceTypeNames().stream().map(TypeName::name).collect(Collectors.toList()),
                   contains("pico.picotestApplication"));
        assertThat(res.templateName(), equalTo("default"));
        assertThat(res.moduleName(), optionalEmpty());
    }

}
