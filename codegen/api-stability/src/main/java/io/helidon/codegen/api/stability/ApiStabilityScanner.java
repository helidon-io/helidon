/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.codegen.api.stability;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

import io.helidon.common.Api;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;

import static io.helidon.codegen.api.stability.ApiStabilityProcessor.SUPPRESS_DEPRECATION;
import static io.helidon.common.Api.SUPPRESS_ALL;
import static io.helidon.common.Api.SUPPRESS_DEPRECATED;
import static io.helidon.common.Api.SUPPRESS_INCUBATING;
import static io.helidon.common.Api.SUPPRESS_INTERNAL;
import static io.helidon.common.Api.SUPPRESS_PREVIEW;

class ApiStabilityScanner extends TreeScanner<Void, Void> {

    private final Trees trees;
    private final CompilationUnitTree unit;
    private final List<Ref> internalApis = new ArrayList<>();
    private final List<Ref> incubatingApis = new ArrayList<>();
    private final List<Ref> previewApis = new ArrayList<>();
    private final List<Ref> deprecatedApis = new ArrayList<>();

    ApiStabilityScanner(Trees trees, CompilationUnitTree unit) {
        this.trees = trees;
        this.unit = unit;
    }

    @Override
    public Void visitMemberSelect(MemberSelectTree node, Void unused) {
        var path = trees.getPath(unit, node);
        if (path != null) {
            var elt = trees.getElement(path);
            if (elt instanceof TypeElement || elt instanceof ExecutableElement) {
                if (checkAnnotation(path, elt, Api.Internal.class, internalApis, SUPPRESS_INTERNAL)) {
                    return null;
                }
                if (checkAnnotation(path, elt, Api.Incubating.class, incubatingApis, SUPPRESS_INCUBATING)) {
                    return null;
                }
                if (checkAnnotation(path, elt, Api.Preview.class, previewApis, SUPPRESS_PREVIEW)) {
                    return null;
                }
                if (checkAnnotation(path, elt, Api.Deprecated.class, deprecatedApis, SUPPRESS_DEPRECATED, SUPPRESS_DEPRECATION)) {
                    return null;
                }
                return null;
            }
        }
        return super.visitMemberSelect(node, unused);
    }

    @Override
    public Void visitIdentifier(IdentifierTree node, Void unused) {
        var path = trees.getPath(unit, node);
        if (path != null) {
            var elt = trees.getElement(path);
            if (elt instanceof TypeElement || elt instanceof ExecutableElement) {
                if (checkAnnotation(path, elt, Api.Internal.class, internalApis, SUPPRESS_INTERNAL)) {
                    return null;
                }
                if (checkAnnotation(path, elt, Api.Incubating.class, internalApis, SUPPRESS_INCUBATING)) {
                    return null;
                }
                if (checkAnnotation(path, elt, Api.Preview.class, previewApis, SUPPRESS_PREVIEW)) {
                    return null;
                }
                if (checkAnnotation(path, elt, Api.Deprecated.class, deprecatedApis, SUPPRESS_DEPRECATED)) {
                    return null;
                }
                return null;
            }
        }
        return super.visitIdentifier(node, unused);
    }

    Collection<Ref> previewApis() {
        return previewApis;
    }

    Collection<Ref> incubatingApis() {
        return incubatingApis;
    }

    Collection<Ref> internalApis() {
        return internalApis;
    }

    Collection<Ref> deprecatedApis() {
        return deprecatedApis;
    }

    private boolean checkAnnotation(TreePath path,
                                    Element elt,
                                    Class<? extends Annotation> annotation,
                                    List<Ref> apiRefs,
                                    String... suppressStrings) {
        var annot = elt.getAnnotation(annotation);
        if (annot == null) {
            // no annotation of the provided type, this is a safe usage
            return false;
        }
        // this element is annotated with the API stability annotation
        var enclosingElt = enclosingElement(path);
        if (enclosingElt == null) {
            // cannot identify enclosing element
            return true;
        }
        var usingCode = usingCode(path, enclosingElt);
        if (usingCode.isEmpty()) {
            // we cannot identify who uses the element
            return true;
        } else {
            for (String suppressString : suppressStrings) {
                if (isSuppressed(usingCode.get().enlosingElement(), suppressString)) {
                    // annotation exists, but usage is suppressed on one of the containing elements
                    return true;
                }
            }
        }

        // exists and not suppressed, report first enclosing element
        apiRefs.add(new Ref(elt.toString(), usingCode));
        return true;
    }

    private Optional<SourceRef> usingCode(TreePath path, Element enclosingElement) {
        TreePath node = path.getParentPath();
        while (node != null) {
            switch (node.getLeaf().getKind()) {
            case CLASS -> {
                var elt = trees.getElement(node);
                if (elt != null && !enclosingElement.equals(elt)) {
                    return sourceRef(node, elt);
                }
            }
            case IMPORT -> {
                return sourceRef(node, classElement(node.getCompilationUnit()));
            }
            case METHOD, VARIABLE -> {
                return sourceRef(node, enclosingElement(node));
            }
            default -> {
                // no-op
            }
            }
            node = node.getParentPath();
        }
        return Optional.empty();
    }

    private Element classElement(CompilationUnitTree compilationUnit) {
        // imports belong to the compilation unit, and not the top level class
        // we want to be able to suppress warnings on the first class of the compilation unit
        for (Tree typeDecl : compilationUnit.getTypeDecls()) {

            if (typeDecl.getKind() == Tree.Kind.CLASS) {
                return trees.getElement(trees.getPath(compilationUnit, typeDecl));
            }
        }
        return null;
    }

    /**
     * Reference to the source of the usage.
     *
     * @param node             node using the API
     * @param enclosingElement element most relevant to the usage (enclosing method, class)
     * @return source reference
     */
    private Optional<SourceRef> sourceRef(TreePath node, Element enclosingElement) {
        CompilationUnitTree compilationUnit = node.getCompilationUnit();
        var sources = trees.getSourcePositions();
        var startPosition = sources.getStartPosition(compilationUnit, node.getLeaf());
        var endPosition = sources.getEndPosition(compilationUnit, node.getLeaf());
        var lineMap = compilationUnit.getLineMap();
        long lineNumber = lineMap.getLineNumber(startPosition);
        long column = lineMap.getColumnNumber(startPosition);
        var lineStartPosition = lineMap.getStartPosition(lineNumber);
        long diff = startPosition - lineStartPosition;

        try {
            var code = compilationUnit.getSourceFile()
                    .getCharContent(true)
                    .subSequence((int) lineStartPosition, (int) endPosition);
            var filePath = compilationUnit.getSourceFile()
                    .toUri()
                    .getPath();
            return Optional.of(new SourceRef((int) lineNumber,
                                             (int) column,
                                             (int) startPosition,
                                             (int) endPosition,
                                             (int) diff,
                                             code.toString(),
                                             filePath,
                                             enclosingElement));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private boolean isSuppressed(Element enclosingElement, String suppressString) {
        var e = enclosingElement;
        while (e != null) {
            var annot = e.getAnnotation(SuppressWarnings.class);
            if (annot != null) {
                for (var s : annot.value()) {
                    if (SUPPRESS_ALL.equals(s)) {
                        return true;
                    }
                    if (suppressString.equals(s)) {
                        return true;
                    }
                }

            }
            e = e.getEnclosingElement();
        }
        return false;
    }

    private Element enclosingElement(TreePath node) {
        var n = node;
        while (n != null) {
            switch (n.getLeaf().getKind()) {
            case VARIABLE -> {
                var elt = trees.getElement(n);
                if (elt != null && elt.getKind() == ElementKind.FIELD) {
                    return elt;
                }
            }
            case METHOD, CLASS -> {
                return trees.getElement(n);
            }
            default -> {
                // no-op
            }
            }
            n = n.getParentPath();
        }
        return trees.getElement(node);
    }

    record SourceRef(int line,
                     int column,
                     int startPosition,
                     int endPosition,
                     int locationOfErrorInCode,
                     String code,
                     String path,
                     Element enlosingElement) {
    }

    record Ref(String name,
               Optional<SourceRef> sourceLocation) {
    }
}
