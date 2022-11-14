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

package io.helidon.pico.maven.plugin;

import java.io.File;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import io.helidon.pico.maven.plugin.utils.ExecutableClassLoader;
import io.helidon.pico.DefaultServiceInfo;
import io.helidon.pico.PicoServices;
import io.helidon.pico.PicoServicesConfig;
import io.helidon.pico.ServiceProvider;
import io.helidon.pico.types.TypeName;
import io.helidon.pico.spi.impl.DefaultPicoServicesConfig;
import io.helidon.pico.tools.types.SimpleModuleDescriptor;
import io.helidon.pico.tools.utils.CommonUtils;
import io.helidon.pico.tools.utils.ModuleUtils;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

/**
 * A mojo wrapper to {@link io.helidon.pico.tools.creator.ApplicationCreator} for test specific types.
 */
@Mojo(name = "test-application-create", defaultPhase = LifecyclePhase.TEST_COMPILE, threadSafe = true,
      requiresDependencyResolution = ResolutionScope.TEST)
public class TestApplicationCreatorMojo extends AbstractApplicationCreatorMojo {

    /**
     * The classname to use for the Pico {@link io.helidon.pico.Application} test class.
     * If not found the classname will be inferred.
     */
    @Parameter(property = PicoServicesConfig.FQN + ".application.class.name", readonly = true,
               defaultValue = PicoServicesConfig.NAME + "testApplication")
    private String className;

//    /**
//     * This folder is added to the list of those folders containing source to be compiled for testing. Use this if your
//     * plugin generates test source code.
//     */
//    @Parameter(property = "testSourceRoot")
//    private File testSourceRoot;

    /**
     * Specify where to place generated source files created by annotation processing.
     * Only applies to JDK 1.6+
     */
    @Parameter(defaultValue = "${project.build.directory}/generated-test-sources/test-annotations")
    private File generatedTestSourcesDirectory;

    /**
     * The directory where compiled test classes go.
     */
    @Parameter(defaultValue = "${project.build.testOutputDirectory}", required = true, readonly = true)
    private File testOutputDirectory;

    @Override
    protected File getGeneratedSourceDirectory() {
        return generatedTestSourcesDirectory;
    }

    @Override
    protected File getOutputDirectory() {
        return testOutputDirectory;
    }

    @Override
    protected List<Path> getSourceRootPaths() {
        return getTestSourceRootPaths();
    }

    @Override
    protected LinkedHashSet<Path> getClasspathElements() {
        MavenProject project = getProject();
        LinkedHashSet<Path> result = new LinkedHashSet<>(project.getTestArtifacts().size());
        result.add(new File(project.getBuild().getTestOutputDirectory()).toPath());
        for (Object a : project.getTestArtifacts()) {
            result.add(((Artifact) a).getFile().toPath());
        }
        result.addAll(super.getClasspathElements());
        return result;
    }

    @Override
    protected LinkedHashSet<Path> getModulepathElements() {
        return getClasspathElements();
    }

    @Override
    protected String getThisModuleName() {
        MavenProject project = getProject();
        String moduleName = ModuleUtils.toSuggestedModuleName(null,
                                                              Path.of(project.getBuild().getTestOutputDirectory()),
                                                              false);
        moduleName = Objects.nonNull(moduleName)
                ? moduleName
                : ModuleUtils.toSuggestedModuleName(super.getThisModuleName(),
                                                    SimpleModuleDescriptor.DEFAULT_TEST_SUFFIX,
                                                    SimpleModuleDescriptor.DEFAULT_MODULE_NAME);
        return moduleName;
    }

    @Override
    protected String getGeneratedClassName() {
        return className;
    }

    @Override
    protected String getClassPrefixName() {
        return SimpleModuleDescriptor.DEFAULT_TEST_SUFFIX;
    }

    /**
     * Excludes everything from source main scope.
     */
    @Override
    protected Set<TypeName> getServiceTypeNamesForExclusion() {
        Set<Path> classPath = getSourceClasspathElements();

        ClassLoader prev = Thread.currentThread().getContextClassLoader();
        URLClassLoader loader = ExecutableClassLoader.create(classPath, prev);

        try {
            Thread.currentThread().setContextClassLoader(loader);

            // we have to reset here since we most likely have new services found in this loader/context...
            PicoServices picoServices = CommonUtils.safeGetPicoRefServices(true);
            ((DefaultPicoServicesConfig) picoServices.config().get())
                    .setValue(DefaultPicoServicesConfig.KEY_BIND_APPLICATION, true);

            // retrieves all the services in the registry...
            List<ServiceProvider<Object>> services = picoServices.services()
                    .lookup(DefaultServiceInfo.builder().build(), false);
            Set<TypeName> serviceTypeNames = toNames(services);
            getLog().debug("excluding service type names: " + serviceTypeNames);
            return serviceTypeNames;
        } finally {
            Thread.currentThread().setContextClassLoader(prev);
        }
    }

}
