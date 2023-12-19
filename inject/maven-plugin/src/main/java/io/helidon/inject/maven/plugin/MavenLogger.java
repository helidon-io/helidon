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

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.helidon.codegen.CodegenEvent;
import io.helidon.codegen.CodegenLogger;
import io.helidon.common.types.TypeName;

import io.github.classgraph.ClassInfo;
import io.github.classgraph.FieldInfo;
import io.github.classgraph.MethodInfo;
import org.apache.maven.plugin.logging.Log;

class MavenLogger implements CodegenLogger {
    private final Log log;
    private final List<String> warnings = new CopyOnWriteArrayList<>();
    private final List<String> errors = new CopyOnWriteArrayList<>();
    private final Consumer<String> warningConsumer;

    private MavenLogger(Log log, boolean failOnWarning) {
        this.log = log;
        if (failOnWarning) {
            // keep them
            warningConsumer = warnings::add;
        } else {
            // throw away
            warningConsumer = it -> {
            };
        }
    }

    public static MavenLogger create(Log log, boolean failOnWarning) {
        return new MavenLogger(log, failOnWarning);
    }

    @Override
    public void log(CodegenEvent event) {
        String message = toMessage(event);

        switch (event.level()) {
        case TRACE, DEBUG -> log(log::debug, log::debug, event, message);
        case INFO -> log(log::info, log::info, event, message);
        case WARNING -> {
            warningConsumer.accept(message);
            log(log::warn, log::warn, event, message);
        }
        case ERROR -> {
            errors.add(message);
            log(log::error, log::error, event, message);
        }
        default -> {
        }
        }
    }

    boolean hasErrors() {
        return !errors.isEmpty() && !warnings.isEmpty();
    }

    List<String> messages() {
        return Stream.concat(
                errors.stream()
                        .map(it -> "error: " + it),
                warnings.stream()
                        .map(it -> "warning: " + it)
                )
                .toList();
    }

    private void log(Consumer<String> messageLog,
                     BiConsumer<? super String, Throwable> throwableLog,
                     CodegenEvent event,
                     String message) {
        Optional<Throwable> throwable = event.throwable();
        if (throwable.isPresent()) {
            throwableLog.accept(message, throwable.get());
        } else {
            messageLog.accept(message);
        }
    }

    private String toMessage(CodegenEvent event) {
        List<Object> objects = event.objects();
        if (objects.isEmpty()) {
            return event.message();
        }
        return event.message() + ", originating in: " + objects.stream()
                .map(this::toString)
                .collect(Collectors.joining(", "));
    }

    private String toString(Object object) {
        if (object instanceof TypeName type) {
            return type.fqName();
        }
        if (object instanceof ClassInfo ci) {
            return ci.getName();
        }
        if (object instanceof MethodInfo mi) {
            return mi.getClassInfo().getName() + "#" + mi.toStringWithSimpleNames();
        }
        if (object instanceof FieldInfo fi) {
            return fi.getClassInfo().getName() + "." + fi.getName();
        }
        return String.valueOf(object);
    }
}
