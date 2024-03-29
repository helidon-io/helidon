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

package io.helidon.inject.maven.plugin;

import java.io.File;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import io.helidon.common.types.TypeName;
import io.helidon.inject.api.Application;
import io.helidon.inject.api.InjectionServices;
import io.helidon.inject.api.ServiceInfoCriteria;
import io.helidon.inject.api.ServiceProvider;
import io.helidon.inject.api.Services;
import io.helidon.inject.tools.spi.ApplicationCreator;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Build;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import static io.helidon.inject.maven.plugin.MavenPluginUtils.injectionServices;
import static io.helidon.inject.tools.ApplicationCreatorDefault.APPLICATION_NAME_SUFFIX;
import static io.helidon.inject.tools.ApplicationCreatorDefault.NAME_PREFIX;
import static io.helidon.inject.tools.ApplicationCreatorDefault.upperFirstChar;
import static io.helidon.inject.tools.ModuleUtils.toBasePath;
import static io.helidon.inject.tools.ModuleUtils.toSuggestedModuleName;

/**
 * A mojo wrapper to {@link ApplicationCreator} for test specific types.
 */
@Mojo(name = "test-application-create", defaultPhase = LifecyclePhase.TEST_COMPILE, threadSafe = true,
      requiresDependencyResolution = ResolutionScope.TEST)
@SuppressWarnings("unused")
public class TestApplicationCreatorMojo extends AbstractApplicationCreatorMojo {

    /**
     * The classname to use for the {@link Application} test class.
     * If not found the classname will be inferred.
     */
    @Parameter(property = "io.helidon.inject.application.class.name", readonly = true
               // note: the default value handling doesn't work here for "$$"!!
               //               defaultValue = DefaultApplicationCreator.APPLICATION_NAME
               )
    private String className;

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

    /**
     * Default constructor.
     */
    public TestApplicationCreatorMojo() {
    }

    @Override
    File getGeneratedSourceDirectory() {
        return generatedTestSourcesDirectory;
    }

    @Override
    File getOutputDirectory() {
        return testOutputDirectory;
    }

    @Override
    List<Path> getSourceRootPaths() {
        return getTestSourceRootPaths();
    }

    @Override
    LinkedHashSet<Path> getClasspathElements() {
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
    LinkedHashSet<Path> getModulepathElements() {
        return getClasspathElements();
    }

    @Override
    String getThisModuleName() {
        Build build = getProject().getBuild();
        Path basePath = toBasePath(build.getTestSourceDirectory());
        String moduleName = toSuggestedModuleName(basePath, Path.of(build.getTestOutputDirectory()), true).orElseThrow();
        return moduleName;
    }

    @Override
    String getGeneratedClassName() {
        return (className == null) ? NAME_PREFIX + "Test" + APPLICATION_NAME_SUFFIX : className;
    }

    @Override
    String getClassPrefixName() {
        return upperFirstChar("test");
    }

    /**
     * Excludes everything from source main scope.
     */
    @Override
    Set<TypeName> getServiceTypeNamesForExclusion() {
        Set<Path> classPath = getSourceClasspathElements();

        ClassLoader prev = Thread.currentThread().getContextClassLoader();
        URLClassLoader loader = ExecutableClassLoader.create(classPath, prev);

        try {
            Thread.currentThread().setContextClassLoader(loader);

            InjectionServices injectionServices = injectionServices(false);
            assert (!injectionServices.config().usesCompileTimeApplications());
            Services services = injectionServices.services();

            // retrieves all the services in the registry
            List<ServiceProvider<?>> allServices = services
                    .lookupAll(ServiceInfoCriteria.builder().build(), false);
            Set<TypeName> serviceTypeNames = toNames(allServices);
            getLog().debug("excluding service type names: " + serviceTypeNames);
            return serviceTypeNames;
        } finally {
            Thread.currentThread().setContextClassLoader(prev);
        }
    }

}
