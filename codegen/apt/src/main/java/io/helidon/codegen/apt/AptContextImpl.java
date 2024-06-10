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

package io.helidon.codegen.apt;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

import io.helidon.codegen.CodegenContextBase;
import io.helidon.codegen.CodegenOptions;
import io.helidon.codegen.CodegenScope;
import io.helidon.codegen.ModuleInfo;
import io.helidon.codegen.ModuleInfoSourceParser;
import io.helidon.codegen.Option;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;

class AptContextImpl extends CodegenContextBase implements AptContext {
    private static final Pattern SCOPE_PATTERN = Pattern.compile("(\\w+).*classes");

    private final ProcessingEnvironment env;
    private final ModuleInfo moduleInfo;

    AptContextImpl(ProcessingEnvironment env,
                   CodegenOptions options,
                   Set<Option<?>> supportedOptions,
                   AptFiler aptFiler,
                   AptLogger aptLogger,
                   CodegenScope scope,
                   ModuleInfo moduleInfo /* may be null*/) {
        super(options, supportedOptions, aptFiler, aptLogger, scope);

        this.env = env;
        this.moduleInfo = moduleInfo;
    }

    static AptContext create(ProcessingEnvironment env, Set<Option<?>> supportedOptions) {
        CodegenOptions options = AptOptions.create(env);

        CodegenScope scope = guessScope(env, options);
        Optional<ModuleInfo> module = findModule(env.getFiler());

        return new AptContextImpl(env,
                                  options,
                                  supportedOptions,
                                  new AptFiler(env, options),
                                  new AptLogger(env, options),
                                  scope,
                                  module.orElse(null));
    }

    @Override
    public ProcessingEnvironment aptEnv() {
        return env;
    }

    @Override
    public Optional<TypeInfo> typeInfo(TypeName typeName) {
        return AptTypeInfoFactory.create(this, typeName);
    }

    @Override
    public Optional<TypeInfo> typeInfo(TypeName typeName, Predicate<TypedElementInfo> elementPredicate) {
        return AptTypeInfoFactory.create(this, typeName, elementPredicate);
    }

    @Override
    public Optional<ModuleInfo> module() {
        return Optional.ofNullable(moduleInfo);
    }

    private static Optional<ModuleInfo> findModule(Filer filer) {
        // expected is source location
        try {
            FileObject resource = filer.getResource(StandardLocation.SOURCE_PATH, "", "module-info.java");
            try (InputStream in = resource.openInputStream()) {
                return Optional.of(ModuleInfoSourceParser.parse(in));
            }
        } catch (IOException ignored) {
            // it is not in sources, let's see if it got generated
        }
        // generated
        try {
            FileObject resource = filer.getResource(StandardLocation.SOURCE_OUTPUT, "", "module-info.java");
            try (InputStream in = resource.openInputStream()) {
                return Optional.of(ModuleInfoSourceParser.parse(in));
            }
        } catch (IOException ignored) {
            // not in generated source either
        }
        // we do not see a module info
        return Optional.empty();
    }

    private static CodegenScope guessScope(ProcessingEnvironment env, CodegenOptions options) {
        CodegenScope scopeFromOptions = CodegenOptions.CODEGEN_SCOPE.findValue(options).orElse(null);

        if (scopeFromOptions != null) {
            return scopeFromOptions;
        }
        try {
            URI resourceUri = env.getFiler()
                    .getResource(StandardLocation.CLASS_OUTPUT, "does.not.exist", "DefinitelyDoesNotExist")
                    .toUri();

            // should be something like:
            // file:///projects/helidon_4/inject/tests/resources-inject/target/test-classes/does/not/exist/DefinitlyDoesNotExist
            String resourceUriString = resourceUri.toString();
            if (!resourceUriString.endsWith("/does/not/exist/DefinitelyDoesNotExist")) {
                // cannot guess, not ending in expected string, assume production scope
                return CodegenScope.PRODUCTION;
            }
            // full URI
            resourceUriString = resourceUriString
                    .substring(0, resourceUriString.length() - "/does/not/exist/DefinitelyDoesNotExist".length());
            // file:///projects/helidon_4/inject/tests/resources-inject/target/test-classes
            int lastSlash = resourceUriString.lastIndexOf('/');
            if (lastSlash < 0) {
                // cannot guess, no path, assume production scope
                return CodegenScope.PRODUCTION;
            }
            resourceUriString = resourceUriString.substring(lastSlash + 1);
            // test-classes
            Matcher matcher = SCOPE_PATTERN.matcher(resourceUriString);
            if (matcher.matches()) {
                return new CodegenScope(matcher.group(1));
            }
            // not matched, either production (just "classes"), or could not match - assume production scope
            return CodegenScope.PRODUCTION;
        } catch (IOException e) {
            // we assume production scope
            return CodegenScope.PRODUCTION;
        }
    }
}
