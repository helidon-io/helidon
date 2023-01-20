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

package io.helidon.pico.processor;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;

import io.helidon.pico.PicoServicesConfig;
import io.helidon.pico.tools.Options;

import jakarta.annotation.ManagedBean;
import jakarta.annotation.Resource;
import jakarta.annotation.Resources;
import jakarta.inject.Singleton;

/**
 * When used will recognize constructs that are explicitly known to be unsupported in Pico's reference implementation.
 * Examples include:
 * <ul>
 *  <li>{@link jakarta.annotation.ManagedBean} and "javax.annotation.ManagedBean"
 *  <li>{@link jakarta.annotation.Resource} and "javax...."
 *  <li>{@link jakarta.annotation.Resources} and "javax...."
 *  <li>Any scopes from jakarta.enterprise api module(s) other than ApplicationScoped, which can optionally be mapped to
 *      Singleton scope.
 * </ul>
 */
public class UnsupportedConstructsProcessor extends AbstractProcessor {
    private static final System.Logger LOGGER = System.getLogger(UnsupportedConstructsProcessor.class.getName());

    static final String APPLICATION_SCOPED_TYPE_NAME = "jakarta.enterprise.context.ApplicationScoped";

    private static final Set<String> UNSUPPORTED_TARGETS;
    static {
        UNSUPPORTED_TARGETS = new HashSet<>();
        UNSUPPORTED_TARGETS.add(ManagedBean.class.getName());
        UNSUPPORTED_TARGETS.add(Resource.class.getName());
        UNSUPPORTED_TARGETS.add(Resources.class.getName());
        UNSUPPORTED_TARGETS.add(APPLICATION_SCOPED_TYPE_NAME);
        UNSUPPORTED_TARGETS.add("jakarta.enterprise.context.BeforeDestroyed");
        UNSUPPORTED_TARGETS.add("jakarta.enterprise.context.ConversationScoped");
        UNSUPPORTED_TARGETS.add("jakarta.enterprise.context.Dependent");
        UNSUPPORTED_TARGETS.add("jakarta.enterprise.context.Destroyed");
        UNSUPPORTED_TARGETS.add("jakarta.enterprise.context.Initialized");
        UNSUPPORTED_TARGETS.add("jakarta.enterprise.context.NormalScope");
        UNSUPPORTED_TARGETS.add("jakarta.enterprise.context.RequestScoped");
        UNSUPPORTED_TARGETS.add("jakarta.enterprise.context.SessionScoped");
        UNSUPPORTED_TARGETS.add("jakarta.enterprise.context.control.ActivateRequestContext");
        UNSUPPORTED_TARGETS.add("jakarta.enterprise.event.Observes");
        UNSUPPORTED_TARGETS.add("jakarta.enterprise.event.ObservesAsync");
        UNSUPPORTED_TARGETS.add("jakarta.enterprise.inject.Alternative");
        UNSUPPORTED_TARGETS.add("jakarta.enterprise.inject.Disposes");
        UNSUPPORTED_TARGETS.add("jakarta.enterprise.inject.Intercepted");
        UNSUPPORTED_TARGETS.add("jakarta.enterprise.inject.Model");
        UNSUPPORTED_TARGETS.add("jakarta.enterprise.inject.Produces");
        UNSUPPORTED_TARGETS.add("jakarta.enterprise.inject.Specializes");
        UNSUPPORTED_TARGETS.add("jakarta.enterprise.inject.Stereotype");
        UNSUPPORTED_TARGETS.add("jakarta.enterprise.inject.TransientReference");
        UNSUPPORTED_TARGETS.add("jakarta.enterprise.inject.Typed");
        UNSUPPORTED_TARGETS.add("jakarta.enterprise.inject.Vetoed");
        UNSUPPORTED_TARGETS.add("jakarta.enterprise.util.Nonbinding");
    }

    /**
     * Service loader based constructor.
     *
     * @deprecated
     */
    public UnsupportedConstructsProcessor() {
    }

    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return UNSUPPORTED_TARGETS;
    }

    @Override
    public void init(
            ProcessingEnvironment processingEnv) {
        this.processingEnv = processingEnv;
        Options.init(processingEnv);
        super.init(processingEnv);
    }

    @Override
    public boolean process(
            Set<? extends TypeElement> annotations,
            RoundEnvironment roundEnv) {
        if (!annotations.isEmpty()) {
            Set<String> annotationTypeNames = annotations.stream().map(Object::toString).collect(Collectors.toSet());
            if (Options.isOptionEnabled(Options.TAG_MAP_APPLICATION_TO_SINGLETON_SCOPE)) {
                annotationTypeNames.remove(APPLICATION_SCOPED_TYPE_NAME);
            }

            if (!annotationTypeNames.isEmpty()) {
                if (Options.isOptionEnabled(Options.TAG_IGNORE_UNSUPPORTED_ANNOTATIONS)) {
                    String msg = "ignoring unsupported annotations: " + annotationTypeNames;
                    LOGGER.log(System.Logger.Level.DEBUG, msg);
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, msg);
                    return false;
                }

                String msg = "This module contains unsupported annotations for " + PicoServicesConfig.NAME
                                + " to process: " + annotationTypeNames + ".\n";
                if (annotationTypeNames.contains(APPLICATION_SCOPED_TYPE_NAME)) {
                    msg += "'" + APPLICATION_SCOPED_TYPE_NAME + "' can be optionally mapped to '" + Singleton.class.getName()
                                + "' scope by passing -A" + Options.TAG_MAP_APPLICATION_TO_SINGLETON_SCOPE + "=true.\n";
                }
                msg += "Use -A" + Options.TAG_IGNORE_UNSUPPORTED_ANNOTATIONS + "=true to ignore all unsupported annotations.";

                LOGGER.log(System.Logger.Level.ERROR, msg);
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, msg);
            }
        }

        return false;
    }

}
