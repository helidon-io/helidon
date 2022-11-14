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

package io.helidon.pico.config.processor.tests;

import io.helidon.pico.config.api.ConfiguredBy;
import io.helidon.pico.config.processor.ConfiguredByProcessor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConfiguredByProcessorTest {

    @Test
    public void supportedAnnotationTypes() {
        ConfiguredByProcessor processor = new ConfiguredByProcessor();
        assertTrue(processor.getSupportedAnnotationTypes().contains(ConfiguredBy.class.getName()));
    }

//    @Test
//    public void badConfig1() throws Exception {
//        String name = "testsubjects/BadConfig1.java";
//        String contents = loadStringFromTestResources(name);
//        ConfiguredByProcessor processor = new ConfiguredByProcessor();
//        Reflect out = Reflect.compile(name, contents,
//                        new CompileOptions()
//                                .options("-Xlint:all", "-proc:only")
//                                .processors(processor)
////                                .withDiagnosticListener((diagnostic) -> {
////                                String message = diagnostic.getMessage(Locale.ENGLISH);
////                                diagMessages.add(message);
////                                logger.info(message);
////                        }));
//        );
//
//        assertNotNull(out);
//    }
//
//
//    public static String loadStringFromTestResources(String name) throws IOException {
//        return loadStringFromFile("./target/test-classes/" + name);
//    }
//
//    /**
//     * Loads a String from a file, wrapping any exception encountered to a {@link io.helidon.pico.tools.ToolsException}.
//     *
//     * @param fileName the file name to load
//     * @return the contents of the file
//     * @throws io.helidon.pico.tools.ToolsException if there were any exceptions encountered
//     */
//    public static String loadStringFromFile(String fileName) throws IOException {
//        Path filePath = Path.of(fileName);
//        assert (filePath.toFile().exists()) : filePath.toFile();
//        return Objects.requireNonNull(Files.readString(filePath));
//    }

}
