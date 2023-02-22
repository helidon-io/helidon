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
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import io.helidon.builder.types.DefaultTypeName;
import io.helidon.builder.types.TypeName;
import io.helidon.pico.Module;
import io.helidon.pico.PicoServicesConfig;
import io.helidon.pico.ServiceProvider;
import io.helidon.pico.tools.AbstractCreator;
import io.helidon.pico.tools.ModuleInfoDescriptor;
import io.helidon.pico.tools.ModuleUtils;
import io.helidon.pico.tools.Msgr;
import io.helidon.pico.tools.Options;
import io.helidon.pico.tools.TemplateHelper;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import static io.helidon.pico.tools.ModuleUtils.toSuggestedGeneratedPackageName;

/**
 * Abstract base for all pico creator mojo's.
 */
@SuppressWarnings("unused")
public abstract class AbstractCreatorMojo extends AbstractMojo {
    private final System.Logger logger = System.getLogger(getClass().getName());

    static final String DEFAULT_SOURCE = AbstractCreator.DEFAULT_SOURCE;
    static final String DEFAULT_TARGET = AbstractCreator.DEFAULT_TARGET;

    static final TrafficCop TRAFFIC_COP = new TrafficCop();

    /**
     * Tag controlling whether we fail on error.
     */
    static final String TAG_FAIL_ON_ERROR = PicoServicesConfig.FQN + ".failOnError";

    /**
     * Tag controlling whether we fail on warnings.
     */
    static final String TAG_FAIL_ON_WARNING = PicoServicesConfig.FQN + ".failOnWarning";

    /**
     * The file name written to ./target/pico/ to track the last package name generated for this application.
     * This application package name is what we fall back to for the application name and the module name if not otherwise
     * specified directly.
     */
    protected static final String APPLICATION_PACKAGE_FILE_NAME = ModuleUtils.APPLICATION_PACKAGE_FILE_NAME;

    // ----------------------------------------------------------------------
    // Pico Configurables
    // ----------------------------------------------------------------------

    /**
     * The template name to use for codegen.
     */
    @Parameter(property = TemplateHelper.TAG_TEMPLATE_NAME, readonly = true, defaultValue = TemplateHelper.DEFAULT_TEMPLATE_NAME)
    private String templateName;

    /**
     * The module name to apply. If not found the module name will be inferred.
     */
    @Parameter(property = Options.TAG_MODULE_NAME, readonly = true)
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
    @Parameter(property = "maven.compiler.source", defaultValue = DEFAULT_SOURCE)
    private String source;

    /**
     * The -target argument for the Java compiler.
     * Note: using the same as maven-compiler for convenience and least astonishment.
     */
    @Parameter(property = "maven.compiler.target", defaultValue = DEFAULT_TARGET)
    private String target;

    @Parameter(defaultValue = "${project.build.directory}", readonly = true)
    private String targetDir;

    /**
     * The package name to apply. If not found the package name will be inferred.
     */
    @Parameter(property = PicoServicesConfig.FQN + ".package.name", readonly = true)
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
     * Sets the debug flag.
     * See {@link io.helidon.pico.PicoServicesConfig#TAG_DEBUG}.
     */
    @Parameter(property = PicoServicesConfig.TAG_DEBUG, readonly = true)
    private boolean isDebugEnabled;

    /**
     * Default constructor.
     */
    protected AbstractCreatorMojo() {
    }

    /**
     * Returns true if debug is enabled.
     *
     * @return true if in debug mode
     */
    protected boolean isDebugEnabled() {
        return isDebugEnabled;
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
    protected File getPicoScratchDir() {
        return new File(getProjectBuildTargetDir(), PicoServicesConfig.NAME);
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

    String getTemplateName() {
        return templateName;
    }

    String getModuleName() {
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
        return compilerArgs;
    }

    @Override
    public void execute() throws MojoExecutionException {
        try (TrafficCop.GreenLight greenLight = TRAFFIC_COP.waitForGreenLight()) {
            getLog().info("Started " + getClass().getName() + " for " + getProject());
            innerExecute();
            getLog().info("Finishing " + getClass().getName() + " for " + getProject());
            Utils.resetAll();
        } catch (Throwable t) {
            MojoExecutionException me = new MojoExecutionException("creator failed", t);
            getLog().error(me.getMessage(), t);
            throw me;
        } finally {
            getLog().info("Finished " + getClass().getName() + " for " + getProject());
        }
    }

    /**
     * Determines the primary package name (which also typically doubles as the application name).
     *
     * @param optModuleSp the module service provider
     * @param typeNames   the type names
     * @param descriptor  the descriptor
     * @param persistIt   pass true to write it to scratch, so that we can use it in the future for this module
     * @return the package name (which also typically doubles as the application name)
     */
    protected String determinePackageName(
            Optional<ServiceProvider<Module>> optModuleSp,
            Collection<TypeName> typeNames,
            ModuleInfoDescriptor descriptor,
            boolean persistIt) {
        String packageName = getPackageName();
        if (packageName == null) {
            // check for the existence of the file
            packageName = loadAppPackageName().orElse(null);
            if (packageName != null) {
                return packageName;
            }

            ServiceProvider<Module> moduleSp = optModuleSp.orElse(null);
            if (moduleSp != null) {
                packageName = DefaultTypeName.createFromTypeName(moduleSp.serviceInfo().serviceTypeName()).packageName();
            } else {
                packageName = toSuggestedGeneratedPackageName(descriptor, typeNames, PicoServicesConfig.NAME);
            }
        }

        Objects.requireNonNull(packageName, "unable to determine package name");

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
        return ModuleUtils.loadAppPackageName(getPicoScratchDir().toPath());
    }

    /**
     * Persist the package name into scratch for later usage.
     *
     * @param packageName the package name
     */
    protected void saveAppPackageName(
            String packageName) {
        ModuleUtils.saveAppPackageName(getPicoScratchDir().toPath(), packageName);
    }

    /**
     * Gated/controlled by the {@link io.helidon.pico.maven.plugin.TrafficCop}.
     *
     * @throws MojoExecutionException if any mojo problems occur
     */
    protected abstract void innerExecute() throws MojoExecutionException;

    LinkedHashSet<Path> getDependencies(
            String optionalScopeFilter) {
        MavenProject project = getProject();
        LinkedHashSet<Path> result = new LinkedHashSet<>(project.getDependencyArtifacts().size());
        for (Object a : project.getDependencyArtifacts()) {
            Artifact artifact = (Artifact) a;
            if (Objects.isNull(optionalScopeFilter) || optionalScopeFilter.equals(artifact.getScope())) {
                result.add(((Artifact) a).getFile().toPath());
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


    class Messager2LogAdapter implements Msgr {
        @Override
        public void debug(String message) {
            getLog().debug(message);
        }

        @Override
        public void debug(String message, Throwable t) {
            getLog().debug(message, t);
        }

        @Override
        public void log(String message) {
            getLog().info(message);
        }

        @Override
        public void warn(String message) {
            getLog().warn(message);
        }

        @Override
        public void warn(String message, Throwable t) {
            getLog().warn(message, t);
        }

        @Override
        public void error(String message, Throwable t) {
            getLog().error(message, t);
        }
    }

}
