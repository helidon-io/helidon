/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import io.helidon.codegen.CodegenOptions;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * Abstract base for all creator mojo's.
 */
abstract class AbstractCreatorMojo extends AbstractMojo {
    /**
     * Tag controlling whether we fail on error.
     */
    static final String TAG_FAIL_ON_ERROR = "inject.failOnError";

    /**
     * Tag controlling whether we fail on warnings.
     */
    static final String TAG_FAIL_ON_WARNING = "inject.failOnWarning";

    // ----------------------------------------------------------------------
    // Configurables
    // ----------------------------------------------------------------------
    /**
     * The module name to apply. If not found the module name will be inferred
     * from {@code module-info.java} if present, or defined as {@code unnamed/package name}.
     *
     * @see io.helidon.codegen.CodegenOptions#TAG_CODEGEN_MODULE
     */
    @Parameter(property = CodegenOptions.TAG_CODEGEN_MODULE)
    private String moduleName;
    /**
     * The package name to apply. If not found the package name will be inferred.
     *
     * @see io.helidon.codegen.CodegenOptions#TAG_CODEGEN_PACKAGE
     */
    @Parameter(property = CodegenOptions.TAG_CODEGEN_PACKAGE)
    private String packageName;
    /**
     * Indicates whether the build will continue even if there are compilation errors.
     */
    @Parameter(property = TAG_FAIL_ON_ERROR, defaultValue = "true")
    private boolean failOnError;
    /**
     * Indicates whether the build will continue even if there are any warnings.
     */
    @Parameter(property = TAG_FAIL_ON_WARNING)
    private boolean failOnWarning;
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
     * The target directory where to place output.
     */
    @Parameter(defaultValue = "${project.build.directory}", readonly = true)
    private String targetDir;

    /**
     * Default constructor.
     */
    protected AbstractCreatorMojo() {
    }

    @Override
    public final void execute() throws MojoExecutionException, MojoFailureException {
        try {
            getLog().info("Started " + getClass().getSimpleName());
            innerExecute();
        } catch (MojoFailureException | MojoExecutionException e) {
            if (failOnError) {
                throw e;
            }
            getLog().warn("Failed to process " + getClass().getSimpleName(), e);
        } catch (Throwable t) {
            if (failOnError) {
                throw new MojoExecutionException(t);
            }
            getLog().warn("Failed to process " + getClass().getSimpleName(), t);
        }
    }

    /**
     * Handle execution of this plugin. The {@link #execute()} method handles exceptions according to
     * {@code failOnError} configuration.
     *
     * @throws org.apache.maven.plugin.MojoExecutionException as needed
     * @throws org.apache.maven.plugin.MojoFailureException   as needed
     */
    protected abstract void innerExecute() throws MojoExecutionException, MojoFailureException;

    /**
     * The project build directory.
     *
     * @return the project build directory
     */
    protected Path projectBuildTargetDir() {
        return Paths.get(targetDir);
    }

    /**
     * The scratch directory.
     *
     * @return the scratch directory
     */
    protected Path injectScratchDir() {
        return projectBuildTargetDir().resolve("inject");
    }

    /**
     * The target package name.
     *
     * @return the target package name, if configured
     */
    protected Optional<String> packageNameFromMavenConfig() {
        return Optional.ofNullable(packageName);
    }

    /**
     * The module name of current module.
     *
     * @return the module name, if configured
     */
    protected Optional<String> moduleNameFromMavenConfig() {
        return Optional.ofNullable(moduleName);
    }

    /**
     * The Maven project.
     *
     * @return maven project
     */
    protected MavenProject mavenProject() {
        return project;
    }

    /**
     * Whether to fail on error.
     * Handled in {@link #execute()} by default.
     *
     * @return if processing should fail on error
     */
    protected boolean failOnError() {
        return failOnError;
    }

    /**
     * Whether to fail on warning.
     *
     * @return if processing should fail on warning
     */
    protected boolean failOnWarning() {
        return failOnWarning;
    }

    /**
     * List of compiler arguments (expected to start with {@code -A}).
     *
     * @return compiler arguments
     */
    protected List<String> getCompilerArgs() {
        return compilerArgs == null ? List.of() : compilerArgs;
    }
}
