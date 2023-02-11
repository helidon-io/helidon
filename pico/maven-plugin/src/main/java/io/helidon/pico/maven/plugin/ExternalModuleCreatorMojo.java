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

package io.helidon.pico.maven.plugin;

import java.io.File;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import io.helidon.pico.PicoServicesConfig;
import io.helidon.pico.QualifierAndValue;
import io.helidon.pico.tools.AbstractFilerMsgr;
import io.helidon.pico.tools.ActivatorCreator;
import io.helidon.pico.tools.ActivatorCreatorConfigOptions;
import io.helidon.pico.tools.ActivatorCreatorResponse;
import io.helidon.pico.tools.CodeGenFiler;
import io.helidon.pico.tools.CodeGenPaths;
import io.helidon.pico.tools.DefaultActivatorCreatorConfigOptions;
import io.helidon.pico.tools.DefaultCodeGenPaths;
import io.helidon.pico.tools.DefaultExternalModuleCreatorRequest;
import io.helidon.pico.tools.ExternalModuleCreator;
import io.helidon.pico.tools.ExternalModuleCreatorRequest;
import io.helidon.pico.tools.ExternalModuleCreatorResponse;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import static io.helidon.pico.maven.plugin.Utils.activatorCreator;
import static io.helidon.pico.maven.plugin.Utils.externalModuleCreator;

/**
 * Responsible for creating pico {@link io.helidon.pico.Activator}'s and a {@link io.helidon.pico.Module}
 * wrapping a set of packages from an external third-party jar.
 */
@Mojo(name = "external-module-create", defaultPhase = LifecyclePhase.GENERATE_SOURCES, threadSafe = true,
      requiresDependencyResolution = ResolutionScope.COMPILE)
@SuppressWarnings("unused")
public class ExternalModuleCreatorMojo extends AbstractCreatorMojo {

    /**
     * Sets the packages to be passed to the creator.
     * <p>
     * Example:
     * <pre>
     * &lt;packageNames&gt;
     *   &lt;org.inject.tck.auto&gt;
     *   &lt;org.inject.tck.auto.accessories&gt;
     * &lt;/packageNames&gt;
     * </pre>
     */
    @Parameter(required = true)
    private List<String> packageNames;

    /**
     * Sets the qualifiers to be passed to the creator.
     * <p>
     * Example:
     * <pre>
     * &lt;serviceTypeToQualifiers&gt;
     *   &lt;org.atinject.tck.auto.accessories.SpareTire&gt;
     *   &lt;qualifier&gt;
     *      &lt;qualifierTypeName&gt;
     *      &lt;/qualifierTypeName&gt;
     *      &lt;value&gt;
     *      &lt;/value&gt;
     *   &lt;/qualifier&gt;
     *   &lt;/org.atinject.tck.auto.accessories.SpareTire&gt;
     * &lt;/serviceTypeToQualifiers&gt;
     * </pre>
     */
    @Parameter(name = "serviceTypeQualifiers")
    private List<ServiceTypeQualifiers> serviceTypeQualifiers;

    /**
     * Establishes whether strict jsr-330 compliance is in effect.
     */
    @Parameter(name = "supportsJsr330Strict", property = PicoServicesConfig.KEY_SUPPORTS_JSR330 + ".strict")
    private boolean supportsJsr330Strict;

    /**
     * Specify where to place generated source files created by annotation processing.
     */
    @Parameter(defaultValue = "${project.build.directory}/generated-sources/" + PicoServicesConfig.NAME)
    private File generatedSourcesDirectory;

    /**
     * Default constructor.
     */
    public ExternalModuleCreatorMojo() {
    }

    /**
     * @return the package names that should be targeted for activator creation.
     */
    List<String> getPackageNames() {
        return packageNames;
    }

    /**
     * @return the explicit qualifiers that should be setup as part of activator creation
     */
    Map<String, Set<QualifierAndValue>> getServiceTypeToQualifiers() {
        if (Objects.isNull(serviceTypeQualifiers)) {
            return Collections.emptyMap();
        }

        Map<String, Set<QualifierAndValue>> result = new LinkedHashMap<>();
        serviceTypeQualifiers.forEach((serviceTypeQualifiers) -> result.putAll(serviceTypeQualifiers.toMap()));
        return result;
    }

    /**
     * @return true if jsr-330 strict mode is in effect
     */
    boolean isSupportsJsr330InStrictMode() {
        return supportsJsr330Strict;
    }

    /**
     * @return the generated sources directory
     */
    @Override
    File getGeneratedSourceDirectory() {
        return generatedSourcesDirectory;
    }

    /**
     * @return the output directory
     */
    File getOutputDirectory() {
        return new File(getProject().getBuild().getOutputDirectory());
    }

    @Override
    protected void innerExecute() throws MojoExecutionException {
        if (packageNames == null || packageNames.isEmpty()) {
            throw new MojoExecutionException("packageNames are required to be specified");
        }

        ClassLoader prev = Thread.currentThread().getContextClassLoader();
        Set<Path> classpath = getDependencies("compile");
        URLClassLoader loader = ExecutableClassLoader.create(classpath, prev);

        try {
            Thread.currentThread().setContextClassLoader(loader);

            ExternalModuleCreator externalModuleCreator = externalModuleCreator();

            ActivatorCreatorConfigOptions configOptions = DefaultActivatorCreatorConfigOptions.builder()
                    .supportsJsr330InStrictMode(isSupportsJsr330InStrictMode())
                    .build();
            String generatedSourceDir = getGeneratedSourceDirectory().getPath();
            CodeGenPaths codeGenPaths = DefaultCodeGenPaths.builder()
                    .generatedSourcesPath(generatedSourceDir)
                    .outputPath(getOutputDirectory().getPath())
                    .metaInfServicesPath(new File(getOutputDirectory(), "META-INF/services").getPath())
                    .build();
            AbstractFilerMsgr directFiler = AbstractFilerMsgr.createDirectFiler(codeGenPaths, getLogger());
            CodeGenFiler codeGenFiler = CodeGenFiler.create(directFiler);
            ExternalModuleCreatorRequest request = DefaultExternalModuleCreatorRequest.builder()
                    .packageNamesToScan(getPackageNames())
                    .serviceTypeToQualifiersMap(getServiceTypeToQualifiers())
                    .throwIfError(isFailOnWarning())
                    .activatorCreatorConfigOptions(configOptions)
                    .codeGenPaths(codeGenPaths)
                    .moduleName(Optional.ofNullable(getModuleName()))
                    .filer(codeGenFiler)
                    .build();
            ExternalModuleCreatorResponse res = externalModuleCreator.prepareToCreateExternalModule(request);
            if (res.success()) {
                getLog().debug("processed service type names: " + res.serviceTypeNames());
                if (getLog().isDebugEnabled()) {
                    getLog().debug("response: " + res);
                }

                // now proceed to creating the activators
                ActivatorCreator activatorCreator = activatorCreator();
                ActivatorCreatorResponse activatorCreatorResponse =
                        activatorCreator.createModuleActivators(res.activatorCreatorRequest());
                if (activatorCreatorResponse.success()) {
                    getProject().addCompileSourceRoot(generatedSourceDir);
                    getLog().info("successfully processed: " + activatorCreatorResponse.serviceTypeNames());
                } else {
                    getLog().error("failed to process", activatorCreatorResponse.error().orElse(null));
                }
            } else {
                getLog().error("failed to process", res.error().orElse(null));
            }
        } finally {
            Thread.currentThread().setContextClassLoader(prev);
        }
    }

}
