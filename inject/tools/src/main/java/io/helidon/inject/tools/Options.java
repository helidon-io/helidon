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

package io.helidon.inject.tools;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.processing.ProcessingEnvironment;

import io.helidon.inject.api.Application;
import io.helidon.inject.api.InjectionServices;

/**
 * Options that can be provided via -A (in annotation processing mode), or via system properties or env properties
 * if running normally.
 * @deprecated Helidon inject is deprecated and will be replaced in a future version
 */
@Deprecated(forRemoval = true, since = "4.0.8")
public class Options {

    /**
     * Tag for putting Injection's annotation processing into debug mode.
     */
    public static final String TAG_DEBUG = InjectionServices.TAG_DEBUG;
    /**
     * Tag to accept this is a preview feature (so no warning is printed).
     */
    public static final String TAG_ACCEPT_PREVIEW = "inject.acceptPreview";
    /**
     * Treat all super types as a contract for a given service type being added.
     */
    public static final String TAG_AUTO_ADD_NON_CONTRACT_INTERFACES = "inject.autoAddNonContractInterfaces";
    /**
     * Pre-creates a placeholder for an {@link Application}.
     */
    public static final String TAG_APPLICATION_PRE_CREATE = "inject.application.pre.create";
    /**
     * Identify the module name being processed or the desired target module name.
     */
    public static final String TAG_MODULE_NAME = "modulename";
    /**
     * Identify the sidecar (module-info.java.inject) module file name or path.
     */
    public static final String TAG_INJECTION_MODULE_NAME = "inject." + TAG_MODULE_NAME;
    /**
     * Identify whether any application scopes (from ee) is translated to {@link jakarta.inject.Singleton}.
     */
    public static final String TAG_MAP_APPLICATION_TO_SINGLETON_SCOPE = "inject.mapApplicationToSingletonScope";
    /**
     * Identify whether any unsupported types should trigger annotation processing to keep going (the default is to fail).
     */
    public static final String TAG_IGNORE_UNSUPPORTED_ANNOTATIONS = "inject.ignoreUnsupportedAnnotations";
    /**
     * Identify invalid usage of the {@code module-info.java} for appropriate Injection references (the default is to fail).
     */
    public static final String TAG_IGNORE_MODULE_USAGE = "inject.ignoreModuleUsage";
    /**
     * For future use. Should the module-info.java be automatically patched to reflect the DI model.
     */
    static final String TAG_AUTO_PATCH_MODULE_INFO = "inject.autoPatchModuleInfo";
    /**
     * Identify the additional annotation type names that will trigger interception.
     */
    static final String TAG_ALLOW_LISTED_INTERCEPTOR_ANNOTATIONS = "inject.allowListedInterceptorAnnotations";

    private static final Map<String, String> OPTS = new HashMap<>();

    private Options() {
    }

    /**
     * Initialize (applicable for annotation processing only).
     *
     * @param processingEnv the processing env
     */
    public static void init(ProcessingEnvironment processingEnv) {
        Objects.requireNonNull(processingEnv);
        if (OPTS.isEmpty()) {
            OPTS.put(TAG_DEBUG,
                     String.valueOf(isOptionEnabled(TAG_DEBUG, processingEnv)));
            OPTS.put(TAG_AUTO_ADD_NON_CONTRACT_INTERFACES,
                     String.valueOf(isOptionEnabled(TAG_AUTO_ADD_NON_CONTRACT_INTERFACES, processingEnv)));
            OPTS.put(TAG_APPLICATION_PRE_CREATE,
                     String.valueOf(isOptionEnabled(TAG_APPLICATION_PRE_CREATE, processingEnv)));
            OPTS.put(TAG_AUTO_PATCH_MODULE_INFO,
                     String.valueOf(isOptionEnabled(TAG_AUTO_PATCH_MODULE_INFO, processingEnv)));
            OPTS.put(TAG_MODULE_NAME,
                     getOption(TAG_MODULE_NAME, null, processingEnv));
            OPTS.put(TAG_INJECTION_MODULE_NAME,
                     getOption(TAG_INJECTION_MODULE_NAME, null, processingEnv));
            OPTS.put(TAG_ALLOW_LISTED_INTERCEPTOR_ANNOTATIONS,
                     getOption(TAG_ALLOW_LISTED_INTERCEPTOR_ANNOTATIONS, null, processingEnv));
            OPTS.put(TAG_MAP_APPLICATION_TO_SINGLETON_SCOPE,
                     getOption(TAG_MAP_APPLICATION_TO_SINGLETON_SCOPE, null, processingEnv));
            OPTS.put(TAG_IGNORE_UNSUPPORTED_ANNOTATIONS,
                     getOption(TAG_IGNORE_UNSUPPORTED_ANNOTATIONS, null, processingEnv));
            OPTS.put(TAG_IGNORE_MODULE_USAGE,
                     getOption(TAG_IGNORE_MODULE_USAGE, null, processingEnv));
            OPTS.put(TAG_ACCEPT_PREVIEW, getOption(TAG_ACCEPT_PREVIEW, null, processingEnv));
        }
    }

    /**
     * Only supports the subset of options that Injection cares about, and should not be generally used for options.
     *
     * @param option the key (assumed to be meaningful to this class)
     * @return true if the option is enabled
     */
    public static boolean isOptionEnabled(String option) {
        return "true".equals(getOption(option, ""));
    }

    /**
     * This only supports the subset of options that Injection cares about, and should not be generally used for options.
     *
     * @param option the key (assumed to be meaningful to this class)
     * @return the option value
     */
    public static Optional<String> getOption(String option) {
        return Optional.ofNullable(getOption(option, null));
    }

    /**
     * Returns a non-null list of comma-delimited string value options.
     *
     * @param option the key (assumed to be meaningful to this class)
     * @return the list of string values that were comma-delimited
     */
    static List<String> getOptionStringList(String option) {
        String result = getOption(option, null);
        if (!CommonUtils.hasValue(result)) {
            return List.of();
        }

        return CommonUtils.toList(result);
    }

    /**
     * This only supports the subset of options that Injection cares about, and should not be generally used for options.
     *
     * @param option     the key (assumed to be meaningful to this class)
     * @param defaultVal the default value used if the associated value is null.
     * @return the option value
     */
    private static String getOption(String option,
                                    String defaultVal) {
        assert (OPTS.containsKey(option));
        return OPTS.getOrDefault(option, defaultVal);
    }

    private static boolean isOptionEnabled(String option,
                                           ProcessingEnvironment processingEnv) {

        String val = processingEnv.getOptions().get(option);
        if (val != null) {
            return Boolean.parseBoolean(val);
        }

        return getOption(option, "", processingEnv).equals("true");
    }

    private static String getOption(String option,
                                    String defaultVal,
                                    ProcessingEnvironment processingEnv) {
        String val = processingEnv.getOptions().get(option);
        if (val != null) {
            return val;
        }

        return defaultVal;
    }

}
