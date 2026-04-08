/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.codegen.api.stability;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.CodegenOptions;
import io.helidon.codegen.Option;
import io.helidon.codegen.api.stability.ApiStabilityScanner.Ref;
import io.helidon.common.Api;
import io.helidon.common.GenericType;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.Trees;

/**
 * Annotation processor checking usage of Helidon APIs.
 */
@Api.Internal
public class ApiStabilityProcessor extends AbstractProcessor {
    static final String SUPPRESS_DEPRECATION = "deprecation";
    private static final String SUPPRESS_JAVA_ALL = "all";

    private static final GenericType<Action> ACTION_TYPE = GenericType.create(Action.class);
    private static final Function<String, Action> ACTION_LAMBDA = str -> Action.valueOf(str.toUpperCase(Locale.ROOT));
    private static final Option<Action> API_ACTION = Option.create("helidon.api",
                                                                   "Action to take for API stability violations",
                                                                   Action.DEFAULT,
                                                                   ACTION_LAMBDA,
                                                                   ACTION_TYPE);
    private static final Option<Action> PREVIEW_ACTION = Option.create("helidon.api.preview",
                                                                       "Action to take for preview violations",
                                                                       Action.WARN,
                                                                       ACTION_LAMBDA,
                                                                       ACTION_TYPE);
    private static final StabilityMeta PREVIEW_META = new StabilityMeta(Api.Preview.class,
                                                                        "@Api.Preview",
                                                                        PREVIEW_ACTION,
                                                                        "preview",
                                                                        "APIs may change between minor versions.",
                                                                        Api.SUPPRESS_PREVIEW);
    private static final Option<Action> INCUBATING_ACTION = Option.create("helidon.api.incubating",
                                                                          "Action to take for incubating violations",
                                                                          Action.FAIL,
                                                                          ACTION_LAMBDA,
                                                                          ACTION_TYPE);
    private static final StabilityMeta INCUBATING_META = new StabilityMeta(Api.Incubating.class,
                                                                           "@Api.Incubating",
                                                                           INCUBATING_ACTION,
                                                                           "incubating",
                                                                           "APIs may change between minor versions, including "
                                                                                   + "removal.",
                                                                           Api.SUPPRESS_INCUBATING);
    private static final Option<Action> INTERNAL_ACTION = Option.create("helidon.api.internal",
                                                                        "Action to take for internal violations",
                                                                        Action.FAIL,
                                                                        ACTION_LAMBDA,
                                                                        ACTION_TYPE);
    private static final StabilityMeta INTERNAL_META = new StabilityMeta(Api.Internal.class,
                                                                         "@Api.Internal",
                                                                         INTERNAL_ACTION,
                                                                         "internal",
                                                                         "Do not use these APIs.",
                                                                         Api.SUPPRESS_INTERNAL);
    private static final Option<Action> DEPRECATED_ACTION = Option.create("helidon.api.deprecated",
                                                                          "Action to take for deprecated violations",
                                                                          Action.WARN,
                                                                          ACTION_LAMBDA,
                                                                          ACTION_TYPE);
    private static final StabilityMeta DEPRECATED_META = new StabilityMeta(Deprecated.class,
                                                                           "@Deprecated",
                                                                           DEPRECATED_ACTION,
                                                                           "deprecated",
                                                                           "Deprecated APIs may be removed in a future "
                                                                                   + "major version of Helidon.",
                                                                           SUPPRESS_DEPRECATION);

    private Messager messager;
    private final Set<CompilationUnitTree> compilationUnits = new LinkedHashSet<>();
    private final Set<ModuleElement> modules = new LinkedHashSet<>();

    private Action previewAction;
    private Action incubatingAction;
    private Action internalAction;
    private Action deprecatedAction;

    /**
     * Constructor required for {@link java.util.ServiceLoader}.
     */
    @Api.Internal
    public ApiStabilityProcessor() {
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);

        CodegenOptions options = new StabilityOptions(processingEnv);
        options.validate(Set.of(API_ACTION,
                                PREVIEW_ACTION,
                                INCUBATING_ACTION,
                                INTERNAL_ACTION,
                                DEPRECATED_ACTION));

        this.messager = processingEnv.getMessager();

        Action apiAction = API_ACTION.value(options);
        if (apiAction == Action.DEFAULT) {
            previewAction = PREVIEW_ACTION.value(options);
            incubatingAction = INCUBATING_ACTION.value(options);
            internalAction = INTERNAL_ACTION.value(options);
            deprecatedAction = DEPRECATED_ACTION.value(options);
        } else {
            previewAction = PREVIEW_ACTION.findValue(options).orElse(apiAction);
            incubatingAction = INCUBATING_ACTION.findValue(options).orElse(apiAction);
            internalAction = INTERNAL_ACTION.findValue(options).orElse(apiAction);
            deprecatedAction = DEPRECATED_ACTION.findValue(options).orElse(apiAction);
        }
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        try {
            var trees = Trees.instance(processingEnv);

            for (var rootElement : roundEnv.getRootElements()) {
                addCompilationUnit(trees, compilationUnits, rootElement);
                var moduleElement = moduleElement(rootElement);
                addCompilationUnit(trees, compilationUnits, moduleElement);
                if (moduleElement instanceof ModuleElement module) {
                    modules.add(module);
                }
            }

            if (!roundEnv.processingOver()) {
                return false;
            }

            // deferring validation as a single pass when processing is over (as we generate nothing)
            Set<Ref> previewApis = new LinkedHashSet<>();
            Set<Ref> incubatingApis = new LinkedHashSet<>();
            Set<Ref> privateApis = new LinkedHashSet<>();
            Set<Ref> deprecatedApis = new LinkedHashSet<>();

            for (var unit : compilationUnits) {
                var scanner = new ApiStabilityScanner(trees, unit);
                scanner.scan(unit, null);
                previewApis.addAll(scanner.previewApis());
                incubatingApis.addAll(scanner.incubatingApis());
                privateApis.addAll(scanner.internalApis());
                deprecatedApis.addAll(scanner.deprecatedApis());
            }

            scanModuleDirectives(modules, previewApis, incubatingApis, privateApis, deprecatedApis);

            if (!previewApis.isEmpty()) {
                log(previewAction, PREVIEW_META, previewApis);
            }

            if (!incubatingApis.isEmpty()) {
                log(incubatingAction, INCUBATING_META, incubatingApis);
            }

            if (!privateApis.isEmpty()) {
                log(internalAction, INTERNAL_META, privateApis);
            }

            if (!deprecatedApis.isEmpty()) {
                log(deprecatedAction, DEPRECATED_META, deprecatedApis);
            }

        } catch (CodegenException e) {
            // exceptions are consumed, so we do not fail user's build when we have a problem in our own code
            // build should only fail if we discover bad API usage

            var origElement = e.originatingElement().orElse(null);
            if (origElement instanceof Element el) {
                messager.printWarning("Failed in API Stability processor, "
                                              + "exception will not be re-thrown. Message: " + e.getMessage(),
                                      el);
            } else {
                messager.printWarning("Failed in API Stability processor, "
                                              + "exception will not be re-thrown. Message: " + e.getMessage()
                                              + ", originating element: " + origElement);
            }
        } catch (Throwable e) {
            // exceptions are consumed, so we do not fail user's build when we have a problem in our own code
            // build should only fail if we discover bad API usage

            messager.printWarning("Failed in ApiStabilityProcessor. Exception class: " + e.getClass().getName()
                                          + ", message: " + e.getMessage());
        }

        return false;
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of("*");
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public Set<String> getSupportedOptions() {
        return Set.of(API_ACTION.name(),
                      PREVIEW_ACTION.name(),
                      INCUBATING_ACTION.name(),
                      INTERNAL_ACTION.name(),
                      DEPRECATED_ACTION.name());
    }

    private static void addCompilationUnit(Trees trees,
                                           Set<CompilationUnitTree> compilationUnits,
                                           Element element) {
        if (element == null) {
            return;
        }

        var path = trees.getPath(element);
        if (path != null) {
            compilationUnits.add(path.getCompilationUnit());
        }
    }

    private static Element moduleElement(Element element) {
        Element current = element;
        while (current != null) {
            if (current.getKind() == ElementKind.MODULE) {
                return current;
            }
            current = current.getEnclosingElement();
        }
        return null;
    }

    private void scanModuleDirectives(Set<ModuleElement> modules,
                                      Set<Ref> previewApis,
                                      Set<Ref> incubatingApis,
                                      Set<Ref> privateApis,
                                      Set<Ref> deprecatedApis) {
        for (var module : modules) {
            for (var directive : module.getDirectives()) {
                switch (directive.getKind()) {
                case USES -> {
                    var usesDirective = (ModuleElement.UsesDirective) directive;
                    addModuleDirectiveUsages(usesDirective.getService(),
                                             module,
                                             previewApis,
                                             incubatingApis,
                                             privateApis,
                                             deprecatedApis);
                }
                case PROVIDES -> {
                    var providesDirective = (ModuleElement.ProvidesDirective) directive;
                    addModuleDirectiveUsages(providesDirective.getService(),
                                             module,
                                             previewApis,
                                             incubatingApis,
                                             privateApis,
                                             deprecatedApis);

                    for (var implementation : providesDirective.getImplementations()) {
                        addModuleDirectiveUsages(implementation,
                                                 module,
                                                 previewApis,
                                                 incubatingApis,
                                                 privateApis,
                                                 deprecatedApis);
                    }
                }
                default -> {
                    // no-op
                }
                }
            }
        }
    }

    private void addModuleDirectiveUsages(TypeElement usedElement,
                                          ModuleElement module,
                                          Set<Ref> previewApis,
                                          Set<Ref> incubatingApis,
                                          Set<Ref> privateApis,
                                          Set<Ref> deprecatedApis) {
        addModuleDirectiveUsage(usedElement, module, Api.Internal.class, privateApis, Api.SUPPRESS_INTERNAL);
        addModuleDirectiveUsage(usedElement,
                                module,
                                Api.Incubating.class,
                                incubatingApis,
                                Api.SUPPRESS_INCUBATING);
        addModuleDirectiveUsage(usedElement, module, Api.Preview.class, previewApis, Api.SUPPRESS_PREVIEW);
        addModuleDirectiveUsage(usedElement,
                                module,
                                Deprecated.class,
                                deprecatedApis,
                                SUPPRESS_DEPRECATION);
    }

    private void addModuleDirectiveUsage(TypeElement usedElement,
                                         ModuleElement module,
                                         Class<? extends Annotation> annotation,
                                         Set<Ref> apiRefs,
                                         String... suppressStrings) {
        if (usedElement.getAnnotation(annotation) == null) {
            return;
        }

        for (String suppressString : suppressStrings) {
            if (isSuppressed(module, suppressString)) {
                return;
            }
        }

        apiRefs.add(new Ref(usedElement.toString(), Optional.empty()));
    }

    private boolean isSuppressed(Element element, String suppressString) {
        var current = element;
        while (current != null) {
            var suppressWarnings = current.getAnnotation(SuppressWarnings.class);
            if (suppressWarnings != null) {
                for (var value : suppressWarnings.value()) {
                    if (SUPPRESS_JAVA_ALL.equals(value)
                            || Api.SUPPRESS_ALL.equals(value)
                            || suppressString.equals(value)) {
                        return true;
                    }
                }
            }
            current = current.getEnclosingElement();
        }
        return false;
    }

    private void log(Action previewAction, StabilityMeta meta, Set<Ref> usages) {
        if (previewAction == Action.IGNORE) {
            return;
        }

        Action action;
        if (previewAction == Action.DEFAULT) {
            action = meta.compilerOption().defaultValue();
        } else {
            action = previewAction;
        }

        Diagnostic.Kind kind = action == Action.WARN ? Diagnostic.Kind.WARNING : Diagnostic.Kind.ERROR;

        messager.printMessage(kind, "Usage of Helidon APIs annotated with "
                + meta.displayName() + ". " + meta.warning());

        messager.printMessage(kind, "This " + kind
                + " can be suppressed with " + suppressionMessage(meta)
                + " or "
                + "compiler argument -A" + meta.compilerOption().name() + "=ignore");

        for (Ref ref : usages) {
            if (ref.sourceLocation().isPresent()) {
                var source = ref.sourceLocation().get();
                messager.printMessage(kind,
                                      source.path() + ":[" + source.line()
                                              + "," + source.column() + "] " + ref.name() + " is " + meta.name() + " API");
            } else {
                messager.printMessage(kind, ref.name() + " is " + meta.name() + " API");
            }
        }
    }

    private static String suppressionMessage(StabilityMeta meta) {
        return Arrays.stream(meta.suppressValues())
                .map(it -> "@SuppressWarnings(\"" + it + "\")")
                .reduce((left, right) -> left + " or " + right)
                .orElseThrow();
    }

    private record StabilityMeta(Class<? extends Annotation> annotation,
                                 String displayName,
                                 Option<Action> compilerOption,
                                 String name,
                                 String warning,
                                 String... suppressValues) {

    }
}
