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

package io.helidon.codegen.apt;

import java.util.List;

import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;

import io.helidon.codegen.CodegenEvent;
import io.helidon.codegen.CodegenLogger;
import io.helidon.codegen.CodegenOptions;
import io.helidon.common.types.TypeName;

class AptLogger implements CodegenLogger {
    private final System.Logger logger;
    private final Messager messager;
    private final ProcessingEnvironment env;

    AptLogger(ProcessingEnvironment env, CodegenOptions options) {
        this.messager = env.getMessager();
        this.env = env;
        this.logger = System.getLogger(AptLogger.class.getName());
    }

    @Override
    public void log(CodegenEvent event) {
        // we always log to system logger if info or below
        // we only log to messager if info or above
        switch (event.level()) {
        case TRACE, DEBUG -> logSystem(event);
        case INFO -> {
            logSystem(event);
            logApt(event);
        }
        case WARNING, ERROR -> {
            logApt(event);
        }
        default -> logSystem(event);
        }
    }

    private void logApt(CodegenEvent event) {
        Diagnostic.Kind kind = mapKind(event.level());
        if (kind == Diagnostic.Kind.OTHER) {
            // not supported
            return;
        }

        List<Object> objects = event.objects();
        messager.printMessage(kind,
                              event.message(),
                              findElement(objects),
                              findAnnotation(objects),
                              findAnnotationValue(objects));
    }

    private AnnotationValue findAnnotationValue(List<Object> objects) {
        for (Object object : objects) {
            if (object instanceof AnnotationValue value) {
                return value;
            }
        }
        return null;
    }

    private AnnotationMirror findAnnotation(List<Object> objects) {
        for (Object object : objects) {
            if (object instanceof AnnotationMirror mirror) {
                return mirror;
            }
        }
        return null;
    }

    private Element findElement(List<Object> objects) {
        for (Object object : objects) {
            if (object instanceof Element e) {
                return e;
            }
        }
        for (Object object : objects) {
            if (object instanceof TypeName t) {
                TypeElement element = env.getElementUtils().getTypeElement(t.declaredName());
                if (element != null) {
                    return element;
                }
            }
        }
        return null;
    }

    private Diagnostic.Kind mapKind(System.Logger.Level level) {
        return switch (level) {
            case ALL, OFF, DEBUG, TRACE -> Diagnostic.Kind.OTHER;
            case INFO -> Diagnostic.Kind.NOTE;
            case WARNING -> Diagnostic.Kind.WARNING;
            case ERROR -> Diagnostic.Kind.ERROR;
        };
    }

    private void logSystem(CodegenEvent event) {
        if (logger.isLoggable(event.level())) {
            logger.log(event.level(),
                       event.message(),
                       event.throwable().orElse(null));
        }
    }
}
