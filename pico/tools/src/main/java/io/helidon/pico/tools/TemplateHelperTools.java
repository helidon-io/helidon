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

package io.helidon.pico.tools;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import io.helidon.common.types.TypeName;

/**
 * Tools to assist with {@link io.helidon.pico.tools.spi.CustomAnnotationTemplateCreator}'s.
 */
public interface TemplateHelperTools {

    /**
     * Convenience method to help with the typical/generic case where the request + the provided generatedType
     * is injected into the supplied template to produce the response.
     * <p>
     * The substitutions are as follows:
     * <ul>
     * <li> substitutions.put("generatedSticker", {generated-sticker});
     * <li> substitutions.put("generatedTypeName", req.getGeneratedTypeName().getName());
     * <li> substitutions.put("annoTypeName", TypeNameImpl.toName(req.getAnnoType()));
     * <li> substitutions.put("packageName", req.getGeneratedTypeName().getPackageName());
     * <li> substitutions.put("className", req.getGeneratedTypeName().getClassName());
     * <li> substitutions.put("enclosingClassTypeName", req.getEnclosingClassType().getName());
     * <li> substitutions.put("enclosingClassAnnotations", req.getEnclosingClassAnnotations());
     * <li> substitutions.put("basicServiceInfo", req.getBasicServiceInfo());
     * <li> substitutions.put("weight", DefaultServiceInfo.weightOf(req.getBasicServiceInfo());
     * <li> substitutions.put("enclosingClassTypeName.packageName", req.getEnclosingClassType().getPackageName());
     * <li> substitutions.put("enclosingClassTypeName.className", req.getEnclosingClassType().getClassName());
     * <li> substitutions.put("elementKind", req.getElementKind());
     * <li> substitutions.put("elementName", req.getElementName());
     * <li> substitutions.put("elementAccess", req.getElementAccess());
     * <li> substitutions.put("elementIsStatic", req.isElementStatic());
     * <li> substitutions.put("elementEnclosingTypeName", req.getElementEnclosingType().getName());
     * <li> substitutions.put("elementEnclosingTypeName.packageName", req.getElementEnclosingType().getPackageName());
     * <li> substitutions.put("elementEnclosingTypeName.className", req.getElementEnclosingType().getClassName());
     * <li> substitutions.put("elementArgs", req.getElementArgs());
     * <li> substitutions.put("elementArgs-declaration", req.getElementArgs());
     * </ul>
     *
     * @param request           the request, as populated as indicated above
     * @param generatedType     the type that should be code generated
     * @param codeGenTemplateSupplier  the resolver strategy to apply - example {@link #supplyUsingLiteralTemplate(CharSequence)}
     * @param propertiesFn      optionally, this function the planned properties will be passed providing an override mechanism
     *
     * @return the response, or empty if the template was not resolvable
     */
    Optional<CustomAnnotationTemplateResponse> produceStandardCodeGenResponse(
            CustomAnnotationTemplateRequest request,
            TypeName generatedType,
            Supplier<? extends CharSequence> codeGenTemplateSupplier,
            Function<Map<String, Object>, Map<String, Object>> propertiesFn);

    /**
     * Returns the default resolver that will rely on a resource lookup of resources/pico/{templateProfile}/{templateName}.
     * Note: This will only work for non-module based usages, and therefore is not recommended for general use.
     *
     * @param templateProfile   the template profile to apply (must be exported by the spi provider module; "default" is reserved for internal use)
     * @param templateName      the template name
     *
     * @return the generic resource based resolver
     */
    Supplier<CharSequence> supplyFromResources(String templateProfile,
                                               String templateName);

    /**
     * Returns a literal resolver to the static template provided.
     *
     * @param template the resolved template
     *
     * @return the resolver that directly returns the template
     */
    Supplier<CharSequence> supplyUsingLiteralTemplate(CharSequence template);

}
