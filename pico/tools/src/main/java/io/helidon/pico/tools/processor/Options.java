/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.pico.tools.processor;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.processing.ProcessingEnvironment;

import io.helidon.pico.types.AnnotationAndValue;
import io.helidon.pico.PicoServicesConfig;
import io.helidon.pico.tools.creator.ActivatorCreatorConfigOptions;
import io.helidon.pico.tools.utils.CommonUtils;
import io.helidon.pico.tools.utils.ModuleUtils;

/**
 * Options that can be provided via -A (in annotation processing mode), or via system properties or env properties
 * if running normally.
 */
public class Options {

    /**
     * Put pico's annotation processing into debug mode.
     */
    public static final String TAG_DEBUG = PicoServicesConfig.FQN + ".debugAnnoProcessor";

    /**
     * Treat all super types as a contract for a given service type being added.
     */
    public static final String TAG_AUTO_ADD_NON_CONTRACT_INTERFACES = PicoServicesConfig.FQN + ".autoAddNonContractInterfaces";

    /**
     * Pre-creates a placeholder for an {@link io.helidon.pico.Application}.
     */
    public static final String TAG_APPLICATION_PRE_CREATE = ActivatorCreatorConfigOptions.TAG_APPLICATION_PRE_CREATE;

    /**
     * For future use.  Should the module-info.java be automatically patched to reflect the pico DI model.
     */
    public static final String TAG_AUTO_PATCH_MODULE_INFO = PicoServicesConfig.FQN + ".autoPatchModuleInfo";

    /**
     * Identify the module name being processed or the desired target module name.
     */
    public static final String TAG_MODULE_NAME = ModuleUtils.TAG_MODULE_NAME;

    /**
     * Identify the additional annotation type names that will trigger interception.
     */
    public static final String TAG_WHITE_LISTED_INTERCEPTOR_ANNOTATIONS = PicoServicesConfig.FQN
            + ".whiteListedInterceptorAnnotations";

    /**
     * Identify whether any application scopes (from ee) is translated to {@link jakarta.inject.Singleton}.
     */
    public static final String TAG_MAP_APPLICATION_TO_SINGLETON_SCOPE = PicoServicesConfig.FQN
            + ".mapApplicationToSingletonScope";

    /**
     * Identify whether any unsupported types should trigger annotation processing to keep going (the default is to fail).
     */
    public static final String TAG_IGNORE_UNSUPPORTED_ANNOTATIONS = PicoServicesConfig.FQN
            + ".ignoreUnsupportedAnnotations";

    /**
     * Identify the pico sidecar module-info.java file name.
     */
    public static final String TAG_PICO_MODULE_NAME = ModuleUtils.TAG_PICO_MODULE_NAME;

    private static final Map<String, String> OPTS = new HashMap<>();
    private static boolean initCalled;

    private Options() {
    }

    /**
     * Initialize (applicable for annotation processing only).
     *
     * @param processingEnv the processing env
     */
    public static void init(ProcessingEnvironment processingEnv) {
        if (initCalled) {
            return;
        }

        synchronized (OPTS) {
            if (initCalled) {
                return;
            }

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
            OPTS.put(TAG_PICO_MODULE_NAME,
                     getOption(TAG_PICO_MODULE_NAME, null, processingEnv));
            OPTS.put(TAG_WHITE_LISTED_INTERCEPTOR_ANNOTATIONS,
                     getOption(TAG_WHITE_LISTED_INTERCEPTOR_ANNOTATIONS, null, processingEnv));
            OPTS.put(TAG_MAP_APPLICATION_TO_SINGLETON_SCOPE,
                     getOption(TAG_MAP_APPLICATION_TO_SINGLETON_SCOPE, null, processingEnv));
            OPTS.put(TAG_IGNORE_UNSUPPORTED_ANNOTATIONS,
                     getOption(TAG_IGNORE_UNSUPPORTED_ANNOTATIONS, null, processingEnv));

            initCalled = true;
        }
    }

    /**
     * @param option the key (assumed to be meaningful to this class)
     * @return This only supports the subset of options that pico cares about, and should not be generally used for options.
     */
    public static boolean isOptionEnabled(String option) {
        return "true".equals(getOption(option, ""));
    }

    /**
     * @param option the key (assumed to be meaningful to this class)
     * @return This only supports the subset of options that pico cares about, and should not be generally used for options.
     */
    public static String getOption(String option) {
        return getOption(option, null);
    }

    /**
     * @param option the key (assumed to be meaningful to this class)
     * @param defaultVal the default value used if the associated value is null.
     * @return This only supports the subset of options that pico cares about, and should not be generally used for options.
     */
    public static String getOption(String option, String defaultVal) {
        assert (initCalled);
        assert (OPTS.containsKey(option));
        return OPTS.getOrDefault(option, defaultVal);
    }

    /**
     * Returns a non-null list of comma-delimited string value options.
     *
     * @param option the key (assumed to be meaningful to this class)
     * @return the list of string values that were comma-delimited
     */
    public static List<String> getOptionStringList(String option) {
        String result = getOption(option, null);
        if (!AnnotationAndValue.hasNonBlankValue(result)) {
            return Collections.emptyList();
        }

        return CommonUtils.toList(result);
    }

    private static boolean isOptionEnabled(String option, ProcessingEnvironment processingEnv) {
        if (Objects.nonNull(processingEnv)) {
            String val = processingEnv.getOptions().get(option);
            if (Objects.nonNull(val)) {
                return Boolean.parseBoolean(val);
            }
        }

        return getOption(option, "", processingEnv).equals("true");
    }

    private static String getOption(String option, String defaultVal, ProcessingEnvironment processingEnv) {
        if (Objects.nonNull(processingEnv)) {
            String val = processingEnv.getOptions().get(option);
            if (Objects.nonNull(val)) {
                return val;
            }
        }

        try {
            return CommonUtils.getProp(option, defaultVal);
        } catch (Throwable t) {
            // eat it
            return defaultVal;
        }
    }

}
