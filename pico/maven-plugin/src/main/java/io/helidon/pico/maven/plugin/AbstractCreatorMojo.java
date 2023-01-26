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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

import io.helidon.pico.PicoServicesConfig;
import io.helidon.pico.tools.AbstractCreator;
import io.helidon.pico.tools.ModuleInfoDescriptor;
import io.helidon.pico.tools.Msgr;
import io.helidon.pico.tools.TemplateHelper;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * Abstract base for all pico creator mojo's.
 */
@SuppressWarnings("unused")
public abstract class AbstractCreatorMojo extends AbstractMojo {
    private final System.Logger logger = System.getLogger(getClass().getName());

    static final String DEFAULT_SOURCE = AbstractCreator.DEFAULT_SOURCE;
    static final String DEFAULT_TARGET = AbstractCreator.DEFAULT_TARGET;

    /**
     * Tag controlling whether we fail on error.
     */
    static final String TAG_FAIL_ON_ERROR = PicoServicesConfig.FQN + ".failOnError";
    /**
     * Tag controlling whether we fail on warnings.
     */
    static final String TAG_FAIL_ON_WARNING = PicoServicesConfig.FQN + ".failOnWarning";


    // ----------------------------------------------------------------------
    // Pico Configurables
    // ----------------------------------------------------------------------

    /**
     * The template name to use for codegen.
     */
    @Parameter(property = TemplateHelper.TAG_TEMPLATE_NAME, readonly = true, defaultValue =
            TemplateHelper.DEFAULT_TEMPLATE_NAME)
    private String templateName;

    /**
     * The module name to apply. If not found the module name will be inferred.
     */
    @Parameter(property = ModuleInfoDescriptor.TAG_MODULE_NAME, readonly = true)
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
