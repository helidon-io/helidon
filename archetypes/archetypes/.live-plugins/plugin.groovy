/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

import com.intellij.openapi.extensions.*
import com.intellij.patterns.XmlPatterns
import com.intellij.psi.*
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceContributorEP
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.*
import com.intellij.util.ProcessingContext

// depends-on-plugin org.jetbrains.kotlin

/*
    This script provides some IntelliJ support for the archetype scripts.
    It supports navigation for the src attributes of <exec> and <source> elements.
    Setup:
    - Install the plugin named "LivePlugin" from the IntelliJ marketplace.
    - Then right click on this script and select "Run live plugin"
    - Optionally, enable "Run Plugins on IDE start" on the Live Plugins panel
    Development:
    - Mark the parent directory as as source or resource root
    - Enable "Add LivePlugin and IDE Jars to Project" on the Live Plugins panel
    - Resolve imports > Add library to classpath > "LivePlugin and IDE jar"
    See:
    - https://github.com/dkandalov/live-plugin
    - https://gist.github.com/dkandalov/49f17eb3f6a2e87fdb1f2dc3d1eba609
 */

class ArchetypeScriptReference extends PsiPolyVariantReferenceBase<XmlAttributeValue> {

    static final String SOURCE_ROOT = "src/main/archetype"

    ArchetypeScriptReference(XmlAttributeValue element) {
        super(element, true)
    }

    def fileRef() {
        def elementFile = element.containingFile.virtualFile
        if (element.value.startsWith("/")) {
            def index = elementFile.path.lastIndexOf(SOURCE_ROOT)
            def path = elementFile.path.substring(0, index + SOURCE_ROOT.length()) + element.value
            return elementFile.fileSystem.findFileByPath(path)
        } else {
            return elementFile.parent.findFileByRelativePath(element.value)
        }
    }

    @Override
    ResolveResult[] multiResolve(boolean incomplete) {
        def result = fileRef()
        if (result != null) {
            def psiManager = PsiManager.getInstance(element.project)
            def file = psiManager.findFile(result)
            if (file != null) {
                return new ResolveResult[]{new PsiElementResolveResult(file)}
            }
        }
        return ResolveResult.EMPTY_ARRAY
    }

    @Override
    boolean isReferenceTo(PsiElement element) {
        return false
    }
}

class ArchetypeScriptContributor extends PsiReferenceContributor {

    @Override
    void registerReferenceProviders(PsiReferenceRegistrar registrar) {
        registrar.registerReferenceProvider(XmlPatterns.xmlAttributeValue(), new PsiReferenceProvider() {
            @Override
            PsiReference[] getReferencesByElement(PsiElement element, ProcessingContext context) {
                // enable only for archetype-script documents and src attributes
                def doc = PsiTreeUtil.getParentOfType(element, XmlDocument.class)
                def attr = PsiTreeUtil.getParentOfType(element, XmlAttribute.class)
                def tag = PsiTreeUtil.getParentOfType(element, XmlTag.class)
                if (doc != null && doc.rootTag.name == "archetype-script"
                        && attr != null && attr.name == "src"
                        && tag != null && (tag.name == "exec" || tag.name == "source")) {
                    return new PsiReference[]{
                            new ArchetypeScriptReference((XmlAttributeValue) element)
                    }
                }
                return PsiReference.EMPTY_ARRAY
            }
        }, PsiReferenceRegistrar.LOWER_PRIORITY)
    }
}

def extension = new PsiReferenceContributorEP().tap {
    language = "XML"
    implementationClass = ArchetypeScriptContributor.class.name
    pluginDescriptor = new DefaultPluginDescriptor(PluginId.getId("LivePlugin"),
            ArchetypeScriptContributor.class.classLoader)
}
PsiReferenceContributor.EP_NAME.point.registerExtension(extension, pluginDisposable)
