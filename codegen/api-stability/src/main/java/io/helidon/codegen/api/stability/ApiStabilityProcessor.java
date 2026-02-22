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
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;

import io.helidon.codegen.CodegenOptions;
import io.helidon.codegen.Option;
import io.helidon.codegen.api.stability.ApiStabilityScanner.Ref;
import io.helidon.common.Api;
import io.helidon.common.GenericType;

import com.sun.source.util.Trees;

/**
 * Annotation processor checking usage of Helidon APIs.
 */
@Api.Private
public class ApiStabilityProcessor extends AbstractProcessor {

    private static final Option<Action> API_ACTION = Option.create("helidon.api",
                                                                   "Action to take for violations",
                                                                   Action.DEFAULT,
                                                                   str -> Action.valueOf(str.toUpperCase(
                                                                           Locale.ROOT)),
                                                                   GenericType.create(Action.class));
    private static final Option<Action> PREVIEW_ACTION = Option.create("helidon.api.preview",
                                                                       "Action to take for violations",
                                                                       Action.WARN,
                                                                       str -> Action.valueOf(str.toUpperCase(Locale.ROOT)),
                                                                       GenericType.create(Action.class));
    private static final Option<Action> INCUBATING_ACTION = Option.create("helidon.api.incubating",
                                                                          "Action to take for violations",
                                                                          Action.WARN,
                                                                          str -> Action.valueOf(str.toUpperCase(Locale.ROOT)),
                                                                          GenericType.create(Action.class));
    private static final Option<Action> PRIVATE_ACTION = Option.create("helidon.api.private",
                                                                       "Action to take for violations",
                                                                       Action.FAIL,
                                                                       str -> Action.valueOf(str.toUpperCase(Locale.ROOT)),
                                                                       GenericType.create(Action.class));
    private static final StabilityMeta PREVIEW_META = new StabilityMeta(Api.Preview.class,
                                                                        PREVIEW_ACTION,
                                                                        Api.SUPPRESS_PREVIEW,
                                                                        "preview",
                                                                        "APIs may change between minor versions.");
    private static final StabilityMeta INCUBATING_META = new StabilityMeta(Api.Incubating.class,
                                                                           INCUBATING_ACTION,
                                                                           Api.SUPPRESS_INCUBATING,
                                                                           "incubating",
                                                                           "APIs may change between minor versions, including "
                                                                                   + "removal.");
    private static final StabilityMeta PRIVATE_META = new StabilityMeta(Api.Private.class,
                                                                        PRIVATE_ACTION,
                                                                        Api.SUPPRESS_PRIVATE,
                                                                        "private",
                                                                        "Do not use these APIs.");

    private Messager messager;
    private Action previewAction;
    private Action incubatingAction;
    private Action privateAction;

    /**
     * Constructor required for {@link java.util.ServiceLoader}.
     */
    @Api.Private
    public ApiStabilityProcessor() {
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);

        CodegenOptions options = new StabilityOptions(processingEnv);
        options.validate(Set.of(API_ACTION, PREVIEW_ACTION, INCUBATING_ACTION, PRIVATE_ACTION));

        this.messager = processingEnv.getMessager();

        Action apiAction = API_ACTION.value(options);
        if (apiAction == Action.DEFAULT) {
            previewAction = PREVIEW_ACTION.value(options);
            incubatingAction = INCUBATING_ACTION.value(options);
            privateAction = PRIVATE_ACTION.value(options);
        } else {
            previewAction = PREVIEW_ACTION.findValue(options).orElse(apiAction);
            incubatingAction = INCUBATING_ACTION.findValue(options).orElse(apiAction);
            privateAction = PRIVATE_ACTION.findValue(options).orElse(apiAction);
        }
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        var trees = Trees.instance(processingEnv);

        Set<Ref> previewApis = new LinkedHashSet<>();
        Set<Ref> incubatingApis = new LinkedHashSet<>();
        Set<Ref> privateApis = new LinkedHashSet<>();

        for (var rootElement : roundEnv.getRootElements()) {
            var path = trees.getPath(rootElement);
            if (path != null) {
                var unit = path.getCompilationUnit();
                var scanner = new ApiStabilityScanner(trees, unit);
                scanner.scan(unit, null);
                previewApis.addAll(scanner.previewApis());
                incubatingApis.addAll(scanner.incubatingApis());
                privateApis.addAll(scanner.privateApis());
            }
        }

        if (!previewApis.isEmpty()) {
            log(previewAction, PREVIEW_META, previewApis);
        }

        if (!incubatingApis.isEmpty()) {
            log(incubatingAction, INCUBATING_META, incubatingApis);
        }

        if (!privateApis.isEmpty()) {
            log(privateAction, PRIVATE_META, privateApis);
        }

        return false;
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of("*");
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.RELEASE_21;
    }

    @Override
    public Set<String> getSupportedOptions() {
        return Set.of(API_ACTION.name(),
                      PREVIEW_ACTION.name(),
                      INCUBATING_ACTION.name(),
                      PRIVATE_ACTION.name());
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

        messager.printMessage(kind, "Usage of Helidon APIs annotated with @"
                + Api.class.getSimpleName() + "." + meta.annotation.getSimpleName() + ". " + meta.warning());

        messager.printMessage(kind, "This " + kind
                + " can be suppressed with @SuppressWarnings(\"" + meta.suppressValue() + "\") or "
                + "compiler argument -A" + meta.compilerOption().name() + "=ignore");

        for (Ref ref : usages) {
            if (ref.sourceLocation().isPresent()) {
                var source = ref.sourceLocation().get();
                if (kind == Diagnostic.Kind.ERROR) {
                    // also print code that uses it
                    messager.printMessage(kind,
                                          source.path() + ":[" + source.line()
                                                  + "," + source.column() + "] " + ref.name() + " is " + meta.name() + " API\n"
                                                  + source.code() + "\n"
                                                  + " ".repeat(source.locationOfErrorInCode()) + "^");
                } else {
                    // only print that it is used
                    messager.printMessage(kind,
                                          source.path() + ":[" + source.line()
                                                  + "," + source.column() + "] " + ref.name() + " is " + meta.name() + " API");
                }

            } else {
                messager.printMessage(kind, ref.name() + " is " + meta.name() + " API");
            }
        }
    }

    private record StabilityMeta(Class<? extends Annotation> annotation,
                                 Option<Action> compilerOption,
                                 String suppressValue,
                                 String name,
                                 String warning) {

    }
}
