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

import io.helidon.builder.api.Prototype;
import io.helidon.common.types.TypeName;

/**
 * This builder represents the request arguments to pass to the {@link GenericTemplateCreator}.
 */
@Prototype.Blueprint
interface GenericTemplateCreatorRequestBlueprint {

    /**
     * The custom annotation template request.
     *
     * @return the custom annotation template request
     */
    CustomAnnotationTemplateRequest customAnnotationTemplateRequest();

    /**
     * The type name that should will be code generated.
     *
     * @return the type name that will be code generated
     */
    TypeName generatedTypeName();

    /**
     * The (mustache / handlebars) template.
     *
     * @return the (mustache / handlebars) template
     */
    CharSequence template();

    /**
     * The overriding properties to apply that will supersede the default values that are specified below.
     * <ul>
     * <li> properties.put("generatedSticker", {generated-sticker});
     * <li> properties.put("generatedTypeName", req.getGeneratedTypeName().getName());
     * <li> properties.put("annoTypeName", TypeNameImpl.toName(req.getAnnoType()));
     * <li> properties.put("packageName", req.getGeneratedTypeName().getPackageName());
     * <li> properties.put("className", req.getGeneratedTypeName().getClassName());
     * <li> properties.put("enclosingClassTypeName", req.getEnclosingClassType().getName());
     * <li> properties.put("enclosingClassAnnotations", req.getEnclosingClassAnnotations());
     * <li> properties.put("basicServiceInfo", req.getBasicServiceInfo());
     * <li> properties.put("weight", ServiceInfo.weightOf(req.getBasicServiceInfo());
     * <li> properties.put("enclosingClassTypeName.packageName", req.getEnclosingClassType().getPackageName());
     * <li> properties.put("enclosingClassTypeName.className", req.getEnclosingClassType().getClassName());
     * <li> properties.put("elementKind", req.getElementKind());
     * <li> properties.put("elementName", req.getElementName());
     * <li> properties.put("elementAccess", req.getElementAccess());
     * <li> properties.put("elementIsStatic", req.isElementStatic());
     * <li> properties.put("elementEnclosingTypeName", req.getElementEnclosingType().getName());
     * <li> properties.put("elementEnclosingTypeName.packageName", req.getElementEnclosingType().getPackageName());
     * <li> properties.put("elementEnclosingTypeName.className", req.getElementEnclosingType().getClassName());
     * <li> properties.put("elementArgs", req.getElementArgs());
     * <li> properties.put("elementArgs-declaration", req.getElementArgs());
     * </ul>
     *
     * @return the overriding properties to apply
     */
    Map<String, Object> overrideProperties();

}
