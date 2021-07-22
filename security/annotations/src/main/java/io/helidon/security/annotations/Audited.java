/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

package io.helidon.security.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.helidon.security.AuditEvent.AuditSeverity;

/**
 * An annotation to specify server resources to be audited.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE, ElementType.FIELD})
@Documented
@Inherited
public @interface Audited {
    /**
     * Default event type: {@value}.
     */
    String DEFAULT_EVENT_TYPE = "request";
    /**
     * Default message format: {@value}.
     * The output is:
     * &lt;STATUS&gt; &lt;METHOD&gt; &lt;PATH&gt; &lt;TRANSPORT&gt; &lt;RESOURCE_TYPE&gt;
     * requested by &lt;SUBJECT&gt;
     */
    String DEFAULT_MESSAGE_FORMAT = "%3$s %1$s \"%2$s\" %5$s %6$s requested by %4$s";
    /**
     * Default severity for OK status.
     */
    AuditSeverity DEFAULT_OK_SEVERITY = AuditSeverity.SUCCESS;
    /**
     * Default severity for non-OK statuses.
     */
    AuditSeverity DEFAULT_ERROR_SEVERITY = AuditSeverity.FAILURE;

    /**
     * Event type of this audit event.
     *
     * @return event type, defaults to "request"
     */
    String value() default DEFAULT_EVENT_TYPE;

    /**
     * The message format of this audit event.
     * The following parameters will be provided (in order):
     * <ul>
     * <li>method: method requested (GET, POST for http)</li>
     * <li>path: the requested path (optional)</li>
     * <li>status: status code/status string (depends on protocol and integrated framework)</li>
     * <li>subject: current security subject</li>
     * <li>transport: transport (such as http)</li>
     * <li>resourceType: resource type requested (optional)</li>
     * <li>targetUri: full uri (as available, optional)</li>
     * </ul>
     *
     * @return message format to create message to be audited
     */
    String messageFormat() default DEFAULT_MESSAGE_FORMAT;

    /**
     * Severity of request with successful response (in http, this would be 1** 2** and 3** statuses).
     *
     * @return severity to use
     */
    AuditSeverity okSeverity() default AuditSeverity.SUCCESS;

    /**
     * Severity of request with unsuccessful response (in http, this would be 4** and 5** status).
     *
     * @return severity to use
     */
    AuditSeverity errorSeverity() default AuditSeverity.FAILURE;
}
