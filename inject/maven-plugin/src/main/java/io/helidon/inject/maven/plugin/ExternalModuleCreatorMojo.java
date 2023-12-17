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
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.CodegenOptions;
import io.helidon.codegen.CodegenScope;
import io.helidon.codegen.ModuleInfo;
import io.helidon.codegen.scan.ScanModuleInfo;
import io.helidon.common.types.TypeName;
import io.helidon.inject.codegen.InjectOptions;
import io.helidon.inject.service.Qualifier;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import static java.util.function.Predicate.not;

/**
 * Responsible for creating {@link io.helidon.inject.service.ServiceDescriptor}'s and
 * a {@link io.helidon.inject.service.ModuleComponent}
 * wrapping a set of packages from an external third-party jar.
 */
@Mojo(name = "external-module-create", defaultPhase = LifecyclePhase.GENERATE_SOURCES, threadSafe = true,
      requiresDependencyResolution = ResolutionScope.COMPILE)
@SuppressWarnings("unused")
public class ExternalModuleCreatorMojo extends AbstractCreatorMojo {
    private static final String UNNAMED_MODULE = "unnamed";

    /**
     * Sets the packages to be passed to the creator. If not defined, all types in the referenced modules will be processed.
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
    @Parameter(name = "supportsJsr330Strict", property = "inject.supports-jsr330.strict")
    private boolean supportsJsr330Strict;

    /**
     * Specify where to place generated source files created by annotation processing.
     */
    @Parameter(defaultValue = "${project.build.directory}/generated-sources/annotations")
    private File generatedSourcesDirectory;

    /**
     * Default constructor.
     */
    public ExternalModuleCreatorMojo() {
    }

    @Override
    protected void innerExecute() throws MojoFailureException {
        Set<Path> classpath = getDependencies("compile");

        try (ScanResult scan = new ClassGraph()
                .overrideClasspath(classpath)
                .enableAllInfo()
                .scan()) {
            processInjectCodegen(scan);
        } catch (CodegenException e) {
            throw new MojoFailureException("Failed to generate service descriptors", e);
        }
    }

    /**
     * @return the explicit qualifiers that should be setup as part of activator creation
     */
    Map<TypeName, Set<Qualifier>> getServiceTypeToQualifiers() {
        if (serviceTypeQualifiers == null) {
            return Map.of();
        }

        Map<TypeName, Set<Qualifier>> result = new LinkedHashMap<>();
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

    private void processInjectCodegen(ScanResult scan) throws MojoFailureException {
        Path generatedSourceDir = getGeneratedSourceDirectory().toPath();
        Path outputDirectory = getOutputDirectory().toPath();
        CodegenScope scope = CodegenScope.PRODUCTION;

        MavenLogger mavenLogger = MavenLogger.create(getLog(), isFailOnWarning());

        Set<String> packagesToScan = resolvePackages();
        Set<ClassInfo> candidates = candidates(scan, packagesToScan::contains);
        if (candidates.isEmpty()) {
            throw new MojoFailureException("Did not discovery and candidates to processing in packages: " + packagesToScan);
        }

        Optional<ModuleInfo> moduleInfo = discoverModuleInfo(scan, candidates);

        MavenScanContext scanContext = MavenScanContext.create(MavenOptions.create(toOptions()),
                                                               scan,
                                                               scope,
                                                               generatedSourceDir,
                                                               outputDirectory,
                                                               mavenLogger,
                                                               moduleInfo.orElse(null));

        HelidonScanProcessor processor = new HelidonScanProcessor(scanContext);

        boolean generated = processor.process(candidates, getServiceTypeToQualifiers());

        if (generated) {
            scanContext.logger().log(System.Logger.Level.DEBUG, "Types were generated by "
                    + ExternalModuleCreatorMojo.class.getName() + ", adding source root: "
                    + generatedSourceDir.toAbsolutePath());
            getProject().addCompileSourceRoot(generatedSourceDir.toString());
        } else {
            scanContext.logger().log(System.Logger.Level.DEBUG, "Nothing was generated by "
                    + ExternalModuleCreatorMojo.class.getName());
        }

        if (mavenLogger.hasErrors()) {
            throw new MojoFailureException("Errors while processing code generation for injection:\n"
                                                   + String.join("\n", mavenLogger.messages()));
        }
    }

    private Set<String> toOptions() {
        Set<String> options = new HashSet<>(getCompilerArgs());
        options.add("-A" + InjectOptions.JSR_330_STRICT.name() + "=" + isSupportsJsr330InStrictMode());
        String moduleName = moduleName();
        if (moduleName != null) {
            options.add("-A" + CodegenOptions.CODEGEN_MODULE.name() + "=" + moduleName);
        }

        return options;
    }

    private Set<ClassInfo> candidates(ScanResult scan, Predicate<String> packagePredicate) {
        return scan.getAllClasses()
                .stream()
                .filter(it -> packagePredicate.test(it.getPackageName()))
                .filter(not(ClassInfo::isInterface))
                .filter(not(ClassInfo::isExternalClass))
                .filter(not(ClassInfo::isAnonymousInnerClass))
                // .filter(it -> !it.isInnerClass())
                .collect(Collectors.toSet());

    }

    private Optional<ModuleInfo> discoverModuleInfo(ScanResult scanResult, Set<ClassInfo> candidates) {
        // now make sure we have only a single module
        Set<String> moduleNames = candidates.stream()
                .map(this::toModuleName)
                .collect(Collectors.toSet());

        if (moduleNames.size() != 1) {
            throw new CodegenException("All types processed by external module Mojo must reside in a single module, but found: "
                                               + moduleNames);
        }
        String moduleName = moduleNames.iterator().next();
        if (UNNAMED_MODULE.equals(moduleName)) {
            return Optional.empty();
        }
        var moduleInfo = scanResult.getModuleInfo(moduleName);
        if (moduleInfo == null) {
            return Optional.empty();
        }
        try {
            var moduleRef = moduleInfo.getModuleRef();
            if (moduleRef == null) {
                return Optional.empty();
            }
            return ScanModuleInfo.map(moduleRef);
        } catch (Exception e) {
            getLog().debug("Failed to get module ref for " + moduleInfo);
            return Optional.empty();
        }
    }

    private Set<String> resolvePackages() {
        if (packageNames == null) {
            return Set.of();
        }
        return Set.copyOf(packageNames);
    }

    private boolean sameOrNull(String first, String second) {
        if (first == null) {
            return second == null;
        }
        if (second == null) {
            return false;
        }
        return first.equals(second);
    }

    private String toModuleName(ClassInfo classInfo) {
        io.github.classgraph.ModuleInfo moduleInfo = classInfo.getModuleInfo();
        if (moduleInfo == null) {
            return UNNAMED_MODULE;
        }
        return moduleInfo.getName();
    }
}
