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
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.ModuleInfo;
import io.helidon.common.types.TypeName;
import io.helidon.inject.ServiceProvider;
import io.helidon.inject.service.ModuleComponent;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import static io.helidon.inject.maven.plugin.MavenUtil.toSuggestedGeneratedPackageName;

/**
 * Abstract base for all creator mojo's.
 */
@SuppressWarnings({"unused", "FieldCanBeLocal", "FieldMayBeFinal"})
public abstract class AbstractCreatorMojo extends AbstractMojo {
    /**
     * The file name written to ./target/inject/ to track the last package name generated for this application.
     * This application package name is what we fall back to for the application name and the module name if not otherwise
     * specified directly.
     */
    protected static final String APPLICATION_PACKAGE_FILE_NAME = "app-package-name.txt";

    /**
     * Tag controlling whether we fail on error.
     */
    static final String TAG_FAIL_ON_ERROR = "inject.failOnError";

    /**
     * Tag controlling whether we fail on warnings.
     */
    static final String TAG_FAIL_ON_WARNING = "inject.failOnWarning";

    static final String TAG_PACKAGE_NAME = "inject.package.name";
    private final System.Logger logger = System.getLogger(getClass().getName());

    // ----------------------------------------------------------------------
    // Configurables
    // ----------------------------------------------------------------------
    /**
     * The module name to apply. If not found the module name will be inferred.
     */
    @Parameter(property = "helidon.codegen.module-name", readonly = true)
    private String moduleName;

    // ----------------------------------------------------------------------
    // Generic Configurables
    // ----------------------------------------------------------------------

    /**
     * The current project instance. This is used for propagating generated-sources paths as
     * compile/testCompile source roots.
     */
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /**
     * Indicates whether the build will continue even if there are compilation errors.
     */
    @Parameter(property = TAG_FAIL_ON_ERROR, defaultValue = "true")
    private boolean failOnError = true;

    /**
     * Indicates whether the build will continue even if there are any warnings.
     */
    @Parameter(property = TAG_FAIL_ON_WARNING)
    private boolean failOnWarning;

    /**
     * The -source argument for the Java compiler.
     * Note: using the same as maven-compiler for convenience and least astonishment.
     */
    @Parameter(property = "maven.compiler.source", defaultValue = "21")
    private String source;

    /**
     * The -target argument for the Java compiler.
     * Note: using the same as maven-compiler for convenience and least astonishment.
     */
    @Parameter(property = "maven.compiler.target", defaultValue = "21")
    private String target;

    /**
     * The target directory where to place output.
     */
    @Parameter(defaultValue = "${project.build.directory}", readonly = true)
    private String targetDir;

    /**
     * The package name to apply. If not found the package name will be inferred.
     */
    @Parameter(property = TAG_PACKAGE_NAME, readonly = true)
    private String packageName;

    /**
     * Sets the arguments to be passed to the compiler.
     * <p>
     * Example:
     * <pre>
     * &lt;compilerArgs&gt;
     *   &lt;arg&gt;-Xmaxerrs&lt;/arg&gt;
     *   &lt;arg&gt;1000&lt;/arg&gt;
     *   &lt;arg&gt;-Xlint&lt;/arg&gt;
     *   &lt;arg&gt;-J-Duser.language=en_us&lt;/arg&gt;
     * &lt;/compilerArgs&gt;
     * </pre>
     */
    @Parameter
    private List<String> compilerArgs;

    /**
     * Default constructor.
     */
    protected AbstractCreatorMojo() {
    }

    @Override
    public void execute() throws MojoExecutionException {
        try {
            getLog().info("Started " + getClass().getName() + " for " + getProject());
            innerExecute();
            getLog().info("Finishing " + getClass().getName() + " for " + getProject());
        } catch (Throwable t) {
            MojoExecutionException me = new MojoExecutionException("Injection maven-plugin execution failed", t);
            getLog().error(me.getMessage(), t);
            throw me;
        } finally {
            getLog().info("Finished " + getClass().getName() + " for " + getProject());
        }
    }

    /**
     * The project build directory.
     *
     * @return the project build directory
     */
    protected File getProjectBuildTargetDir() {
        return new File(targetDir);
    }

    /**
     * The scratch directory.
     *
     * @return the scratch directory
     */
    protected File getInjectScratchDir() {
        return new File(getProjectBuildTargetDir(), "inject");
    }

    /**
     * The target package name.
     *
     * @return the target package name
     */
    protected String getPackageName() {
        return packageName;
    }

    System.Logger getLogger() {
        return logger;
    }

    String moduleName() {
        return moduleName;
    }

    MavenProject getProject() {
        return project;
    }

    boolean isFailOnError() {
        return failOnError;
    }

    boolean isFailOnWarning() {
        return failOnWarning;
    }

    String getSource() {
        return source;
    }

    String getTarget() {
        return target;
    }

    List<String> getCompilerArgs() {
        return compilerArgs == null ? List.of() : compilerArgs;
    }

    /**
     * Determines the primary package name (which also typically doubles as the application name).
     *
     * @param optModuleSp the module service provider
     * @param typeNames   the type names
     * @param descriptor  the descriptor
     * @param sourceRoots source roots that contain current sources
     * @param persistIt   pass true to write it to scratch, so that we can use it in the future for this module
     * @return the package name (which also typically doubles as the application name)
     */
    protected String determinePackageName(Optional<ServiceProvider<ModuleComponent>> optModuleSp,
                                          Collection<TypeName> typeNames,
                                          ModuleInfo descriptor,
                                          List<Path> sourceRoots,
                                          boolean persistIt) {
        String packageName = getPackageName();
        if (packageName == null) {
            // check for the existence of the file
            packageName = loadAppPackageName().orElse(null);
            if (packageName != null) {
                return packageName;
            }

            ServiceProvider<ModuleComponent> moduleSp = optModuleSp.orElse(null);
            if (moduleSp != null) {
                packageName = moduleSp.serviceType().packageName();
            } else {
                if (descriptor == null) {
                    if (!sourceRoots.isEmpty()) {
                        packageName = packageFromSourceRoots(sourceRoots);
                    }
                    if (packageName == null) {
                        packageName = toSuggestedGeneratedPackageName(typeNames, "inject");
                    }
                } else {
                    if (!(sourceRoots.isEmpty() && descriptor.name().equals(ModuleInfo.DEFAULT_MODULE_NAME))) {
                        packageName = packageFromSourceRoots(sourceRoots);
                    }
                    if (packageName == null) {
                        packageName = toSuggestedGeneratedPackageName(typeNames, "inject", descriptor);
                    }
                }
            }
        }

        if (packageName == null || packageName.isBlank()) {
            throw new CodegenException("Unable to determine the package name. The package name can be set using "
                                               + TAG_PACKAGE_NAME);
        }

        if (persistIt) {
            // record it to scratch file for later consumption (during test build for example)
            saveAppPackageName(packageName);
        }

        return packageName;
    }

    /**
     * Attempts to load the app package name from what was previously recorded.
     *
     * @return the app package name that was loaded
     */
    protected Optional<String> loadAppPackageName() {
        return MavenUtil.loadAppPackageName(getInjectScratchDir().toPath());
    }

    /**
     * Persist the package name into scratch for later usage.
     *
     * @param packageName the package name
     */
    protected void saveAppPackageName(String packageName) {
        MavenUtil.saveAppPackageName(getInjectScratchDir().toPath(), packageName);
    }

    /**
     *
     *
     * @throws MojoExecutionException if any mojo problems occur
     */
    protected abstract void innerExecute() throws MojoExecutionException, MojoFailureException;

    LinkedHashSet<Path> getDependencies(String optionalScopeFilter) {
        MavenProject project = getProject();
        LinkedHashSet<Path> result = new LinkedHashSet<>();
        for (Object a : project.getCompileArtifacts()) {
            Artifact artifact = (Artifact) a;
            if (optionalScopeFilter == null || optionalScopeFilter.equals(artifact.getScope())) {
                result.add(artifact.getFile().toPath());
            }
        }
        return result;
    }

    LinkedHashSet<Path> getSourceClasspathElements() {
        MavenProject project = getProject();
        LinkedHashSet<Path> result = new LinkedHashSet<>(project.getCompileArtifacts().size());
        result.add(new File(project.getBuild().getOutputDirectory()).toPath());
        for (Object a : project.getCompileArtifacts()) {
            result.add(((Artifact) a).getFile().toPath());
        }
        return result;
    }

    /**
     * Provides a convenient way to handle test scope. Returns the classpath for source files (or test sources) only.
     */
    LinkedHashSet<Path> getClasspathElements() {
        return getSourceClasspathElements();
    }

    abstract File getGeneratedSourceDirectory();

    private String packageFromSourceRoots(List<Path> sourceRoots) {
        // we are interested in the shortest path that contains a java file other than module-info.java
        AtomicReference<String> foundPackage = new AtomicReference<>();

        for (Path sourceRoot : sourceRoots) {
            try {
                if (!Files.exists(sourceRoot)) {
                    continue;
                }
                Files.walkFileTree(sourceRoot, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                        if (foundPackage.get() == null) {
                            return FileVisitResult.CONTINUE;
                        }
                        String packageName = toPackageName(sourceRoot, dir);
                        if (packageName.isBlank()) {
                            return FileVisitResult.CONTINUE;
                        }
                        if (packageName.length() > foundPackage.get().length()) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        String fileName = String.valueOf(file.getFileName());
                        if (fileName.equals("module-info.java")) {
                            return FileVisitResult.CONTINUE;
                        }
                        if (fileName.endsWith(".java")) {
                            String packageName = toPackageName(sourceRoot, file.getParent());
                            String current = foundPackage.get();
                            if (current == null || current.length() > packageName.length()) {
                                foundPackage.set(packageName);
                            }
                            return FileVisitResult.SKIP_SIBLINGS;
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        return foundPackage.get();
    }

    private String toPackageName(Path root, Path resolved) {
        Path relativize = root.relativize(resolved);
        return relativize.toString().replace('\\', '/').replace('/', '.');
    }
}
