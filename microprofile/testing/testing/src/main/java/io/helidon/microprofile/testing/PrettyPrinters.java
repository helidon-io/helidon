/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.microprofile.testing;

import java.util.function.Consumer;

import io.helidon.microprofile.testing.HelidonTestInfo.ClassInfo;
import io.helidon.microprofile.testing.HelidonTestInfo.MethodInfo;

/**
 * Pretty printers.
 */
class PrettyPrinters {

    private PrettyPrinters() {
    }

    /**
     * Create a {@link HelidonTestInfo} printer.
     *
     * @param info info
     * @return printer consumer
     */
    static Consumer<PrettyPrinter> testInfo(HelidonTestInfo<?> info) {
        return switch (info) {
            case ClassInfo classInfo -> classInfo(classInfo);
            case MethodInfo methodInfo -> methodInfo(methodInfo);
        };
    }

    /**
     * Create a {@link MethodInfo} printer.
     *
     * @param info info
     * @return printer consumer
     */
    static Consumer<PrettyPrinter> methodInfo(MethodInfo info) {
        return printer -> printer
                .value("class", info.classInfo().element().getName())
                .value("method", info.element().getName())
                .value("requiresReset", info.requiresReset())
                .apply(testDescriptor(info));
    }

    /**
     * Create a {@link ClassInfo} printer.
     *
     * @param info info
     * @return printer consumer
     */
    static Consumer<PrettyPrinter> classInfo(ClassInfo info) {
        return printer -> printer
                .value("class", info.element().getName())
                .apply(testDescriptor(info));
    }

    /**
     * Create a {@link HelidonTestDescriptor} printer.
     *
     * @param desc descriptor
     * @return printer consumer
     */
    static Consumer<PrettyPrinter> testDescriptor(HelidonTestDescriptor<?> desc) {
        return printer -> printer
                .value("resetPerTest", desc.resetPerTest())
                .value("pinningDetection", desc.pinningDetection())
                .value("pinningThreshold", desc.pinningThreshold())
                .value("disableDiscovery", desc.disableDiscovery())
                .values("addExtensions", desc.addExtensions(), a -> a.value().getName())
                .values("addBeans", desc.addBeans(), a -> a.value().getName())
                .object("configuration", desc.configuration()
                        .map(PrettyPrinters::configuration)
                        .orElse(PrettyPrinter.EMPTY))
                .objects("addConfig", desc.addConfigs(), PrettyPrinters::addConfig)
                .objects("addConfigBlock", desc.addConfigBlocks(), PrettyPrinters::addConfigBlock);
    }

    /**
     * Create a {@link Configuration} printer.
     *
     * @param a annotation
     * @return printer consumer
     */
    static Consumer<PrettyPrinter> configuration(Configuration a) {
        return printer -> printer
                .object("configuration", p -> p
                        .value("useExisting", a.useExisting())
                        .value("profile", a.profile())
                        .values("configSources", a.configSources()));
    }

    /**
     * Create a {@link AddConfig} printer.
     *
     * @param a annotation
     * @return printer consumer
     */
    static Consumer<PrettyPrinter> addConfig(AddConfig a) {
        return printer -> printer
                .value("key", a.key())
                .value("value", a.value());
    }

    /**
     * Create a {@link AddConfigBlock} printer.
     *
     * @param a annotation
     * @return printer consumer
     */
    static Consumer<PrettyPrinter> addConfigBlock(AddConfigBlock a) {
        return printer -> printer
                .value("type", a.type())
                .block("value", a.value());
    }
}
