/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

/**
 * The Builder annotation processor module.
 *
 * @deprecated replaced with helidon-builder-codegen in
 *     combination with helidon-codegen-apt
 */
@Deprecated(forRemoval = true, since = "4.1.0")
module io.helidon.builder.processor {
    requires java.compiler;
    requires jdk.compiler;

    requires io.helidon.common.types;
    requires io.helidon.common.processor;
    requires io.helidon.common.processor.classmodel;

    exports io.helidon.builder.processor;

    provides javax.annotation.processing.Processor
            with io.helidon.builder.processor.BlueprintProcessor;
	
}
