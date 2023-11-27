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

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.CodegenFiler;
import io.helidon.codegen.CodegenOptions;
import io.helidon.codegen.IndentType;
import io.helidon.codegen.classmodel.ClassModel;

class AptFiler implements CodegenFiler {
    private final Filer filer;
    private final String indent;

    AptFiler(ProcessingEnvironment env, CodegenOptions options) {
        this.filer = env.getFiler();

        IndentType value = CodegenOptions.INDENT_TYPE.value(options);
        int codegenRepeat = CodegenOptions.INDENT_COUNT.value(options);

        this.indent = String.valueOf(value.character()).repeat(codegenRepeat);
    }

    @Override
    public Path writeSourceFile(ClassModel classModel, Object... originatingElements) {
        Element[] elements = toElements(originatingElements);

        try {
            JavaFileObject sourceFile = filer.createSourceFile(classModel.typeName().fqName(), elements);
            try (Writer os = sourceFile.openWriter()) {
                classModel.write(os, indent);
            }
            return Path.of(sourceFile.toUri());
        } catch (IOException e) {
            throw new CodegenException("Failed to write source file for type: " + classModel.typeName(),
                                       e,
                                       originatingElement(elements, classModel.typeName()));
        }
    }

    @Override
    public Path writeResource(byte[] resource, String location, Object... originatingElements) {
        Element[] elements = toElements(originatingElements);

        try {
            FileObject fileObject = filer.createResource(StandardLocation.CLASS_OUTPUT, "", location, elements);
            try (OutputStream os = fileObject.openOutputStream()) {
                os.write(resource);
            }
            return Path.of(fileObject.toUri());
        } catch (IOException e) {
            throw new CodegenException("Failed to write resource file " + location,
                                       e,
                                       originatingElement(elements, location));
        }
    }

    private Object originatingElement(Element[] elements, Object alternative) {
        if (elements.length == 0) {
            return alternative;
        }
        return elements[0];
    }

    private Element[] toElements(Object[] originatingElements) {
        List<Element> result = new ArrayList<>();
        for (Object originatingElement : originatingElements) {
            if (originatingElement instanceof Element element) {
                result.add(element);
            }
        }
        return result.toArray(new Element[0]);
    }
}
