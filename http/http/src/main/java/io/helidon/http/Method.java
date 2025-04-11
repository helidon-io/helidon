/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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

package io.helidon.http;

import java.util.Collection;
import java.util.Objects;

import io.helidon.common.buffers.Ascii;

/**
 * HTTP request methods.
 * <p>
 * Although the constants are instances of this class, they can be compared using instance equality, as the only
 * way to obtain an instance is through method {@link #create(String)}, which ensures the same instance is returned for
 * known methods.
 * <p>
 * Methods that are not known (e.g. there is no constant for them) must be compared using {@link #equals(Object)} as usual.
 * <p>
 * This class also contains string method names, such as {@link #GET_NAME} to allow usage in annotations.
 */
public final class Method {
    /**
     * {@value} method name.
     */
    public static final String GET_NAME = "GET";
    /**
     * {@value} method name.
     */
    public static final String POST_NAME = "POST";
    /**
     * {@value} method name.
     */
    public static final String PUT_NAME = "PUT";
    /**
     * {@value} method name.
     */
    public static final String DELETE_NAME = "DELETE";
    /**
     * {@value} method name.
     */
    public static final String HEAD_NAME = "HEAD";
    /**
     * {@value} method name.
     */
    public static final String PATCH_NAME = "PATCH";
    /**
     * {@value} method name.
     */
    public static final String OPTIONS_NAME = "OPTIONS";
    /**
     * {@value} method name.
     */
    public static final String TRACE_NAME = "TRACE";
    /**
     * {@value} method name.
     */
    public static final String CONNECT_NAME = "CONNECT";

    /**
     * The GET method means retrieve whatever information (in the form of an entity) is identified by the Request-URI.
     * If the Request-URI refers to a data-producing process, it is the produced data which shall be returned as the entity
     * in the response and not the source text of the process, unless that text happens to be the output of the tryProcess.
     */
    public static final Method GET = new Method(GET_NAME, true);
    /**
     * The POST method is used to request that the origin server acceptedTypes the entity enclosed in the request
     * as a new subordinate of the resource identified by the Request-URI in the Request-Line.
     * The actual function performed by the POST method is determined by the server and is usually dependent on the
     * Request-URI. The posted entity is subordinate to that URI in the same way that a file is subordinate to a directory
     * containing it, a news article is subordinate to a newsgroup to which it is posted, or a record is subordinate
     * to a database.
     */
    public static final Method POST = new Method(POST_NAME, true);
    /**
     * The PUT method requests that the enclosed entity be stored under the supplied Request-URI. If the Request-URI refers
     * to an already existing resource, the enclosed entity SHOULD be considered as a modified version of the one residing
     * on the origin server. If the Request-URI does not point to an existing resource, and that URI is capable of being
     * defined as a new resource by the requesting user agent, the origin server can create the resource with that URI.
     * If a new resource is created, the origin server MUST inform the user agent via the 201 (Created) response.
     * If an existing resource is modified, either the 200 (OK) or 204 (No Content) response codes SHOULD be sent to indicate
     * successful completion of the request. If the resource could not be created or modified with the Request-URI,
     * an appropriate error response SHOULD be given that reflects the nature of the problem. The recipient of the entity
     * MUST NOT ignore any Content-* (e.g. Content-Range) headers that it does not understand or implement and MUST return
     * a 501 (Not Implemented) response in such cases.
     */
    public static final Method PUT = new Method(PUT_NAME, true);
    /**
     * The DELETE method requests that the origin server delete the resource identified by the Request-URI.
     * This method MAY be overridden by human intervention (or other means) on the origin server. The client cannot
     * be guaranteed that the operation has been carried out, even if the status code returned from the origin server
     * indicates that the action has been completed successfully. However, the server SHOULD NOT indicate success unless,
     * at the time the response is given, it intends to delete the resource or move it to an inaccessible location.
     */
    public static final Method DELETE = new Method(DELETE_NAME, true);
    /**
     * The HEAD method is identical to {@link #GET} except that the server MUST NOT return a message-body in the response.
     * The metainformation contained in the HTTP headers in response to a HEAD request SHOULD be identical to the information
     * sent in response to a GET request. This method can be used for obtaining metainformation about the entity implied
     * by the request without transferring the entity-body itself. This method is often used for testing hypertext links
     * for validity, accessibility, and recent modification.
     */
    public static final Method HEAD = new Method(HEAD_NAME, true);
    /**
     * The OPTIONS method represents a request for information about the communication options available
     * on the request/response chain identified by the Request-URI. This method allows the client to determine the options
     * and/or requirements  associated with a resource, or the capabilities of a server, without implying a resource action
     * or initiating a resource retrieval.
     */
    public static final Method OPTIONS = new Method(OPTIONS_NAME, true);
    /**
     * The TRACE method is used to invoke a remote, application-layer loop-back of the request message.
     * The final recipient of the request SHOULD reflect the message received back to the client as the entity-body
     * of a 200 (OK) response. The final recipient is either the origin server or the first proxy or gateway to receive
     * a Max-Forwards value of zero (0) in the request (see section 14.31). A TRACE request MUST NOT include an entity.
     */
    public static final Method TRACE = new Method(TRACE_NAME, true);
    /**
     * The PATCH method as described in RFC 5789 is used to perform an update to an existing resource, where the request
     * payload only has to contain the instructions on how to perform the update. This is in contrast to PUT which
     * requires that the payload contains the new version of the resource.
     * If an existing resource is modified, either the 200 (OK) or 204 (No Content) response codes SHOULD be sent to indicate
     * successful completion of the request.
     */
    public static final Method PATCH = new Method(PATCH_NAME, true);

    /**
     * The HTTP CONNECT method starts two-way communications with the requested resource. It can be used to open a tunnel.
     */
    public static final Method CONNECT = new Method(CONNECT_NAME, true);

    static {
        // THIS MUST BE AFTER THE LAST CONSTANT
        MethodHelper.methodsDone();
    }

    private final String name;
    private final int length;

    private final boolean instance;

    private Method(String name, boolean instance) {
        this.name = name;
        this.length = name.length();
        this.instance = instance;

        if (instance) {
            MethodHelper.add(this);
        }
    }

    /**
     * Create new HTTP request method instance from the provided name.
     * <p>
     * In case the method name is recognized as one of the {@link Method standard HTTP methods},
     * the respective enumeration
     * value is returned.
     *
     * @param name the method name. Must not be {@code null} or empty and must be a legal HTTP method name string.
     * @return HTTP request method instance representing an HTTP method with the provided name.
     * @throws IllegalArgumentException In case of illegal method name or in case the name is empty or {@code null}.
     */
    public static Method create(String name) {
        if (name.equals(GET_NAME)) {
            return GET;
        }

        String methodName = Ascii.toUpperCase(name);

        Method method = MethodHelper.byName(methodName);
        if (method == null) {
            // validate that it only contains characters allowed by a method
            HttpToken.validate(methodName);
            return new Method(methodName, false);
        }
        return method;
    }

    /**
     * Create a predicate for the provided methods.
     *
     * @param methods methods to check against
     * @return a predicate that will validate the method is one of the methods provided; if methods are empty, the predicate
     *         will always return {@code true}
     */
    public static MethodPredicate predicate(Method... methods) {
        return switch (methods.length) {
            case 0 -> MethodPredicates.TruePredicate.get();
            case 1 -> methods[0].instance
                    ? new MethodPredicates.SingleMethodEnumPredicate(methods[0])
                    : new MethodPredicates.SingleMethodPredicate(methods[0]);
            default -> new MethodPredicates.MethodsPredicate(methods);
        };
    }

    /**
     * Create a predicate for the provided methods.
     *
     * @param methods methods to check against
     * @return a predicate that will validate the method is one of the methods provided; if methods are empty, the predicate
     *         will always return {@code true}
     */
    public static MethodPredicate predicate(Collection<Method> methods) {
        switch (methods.size()) {
        case 0:
            return MethodPredicates.TruePredicate.get();
        case 1:
            Method first = methods.iterator().next();
            return first.instance
                    ? new MethodPredicates.SingleMethodEnumPredicate(first)
                    : new MethodPredicates.SingleMethodPredicate(first);

        default:
            return new MethodPredicates.MethodsPredicate(methods.toArray(new Method[0]));
        }
    }

    /**
     * Name of the method (such as {@code GET} or {@code POST}).
     *
     * @return a method name.
     * @deprecated use {@link #text()} instead, this method conflicts with enum
     */
    @Deprecated
    public String name() {
        return text();
    }

    /**
     * Name of the method (such as {@code GET} or {@code POST}).
     *
     * @return a method name.
     */
    public String text() {
        return name;
    }

    /**
     * Number of characters.
     *
     * @return number of characters of this method
     */
    public int length() {
        return length;
    }

    @Override
    public String toString() {
        return text();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Method method = (Method) o;
        return name.equals(method.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}
