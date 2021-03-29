/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
package io.helidon.lra.rest;

import org.eclipse.microprofile.lra.annotation.*;
import org.eclipse.microprofile.lra.annotation.ws.rs.Leave;

import javax.enterprise.context.RequestScoped;
import javax.ws.rs.*;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.*;
import java.io.Closeable;
import java.io.UnsupportedEncodingException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

import static io.helidon.lra.rest.LRAConstants.*;
import static javax.ws.rs.core.Response.Status.*;
import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_RECOVERY_HEADER;

@RequestScoped
public class LRAClient implements Closeable {
    private static final Logger LOGGER = Logger.getLogger(LRAClient.class.getName());
    public static final String LRA_COORDINATOR_URL_KEY = "lra.coordinator.url";

    private static final String START_PATH = "/start";
    private static final String LEAVE_PATH = "/%s/remove";
    private static final String STATUS_PATH = "/%s/status";
    private static final String CLOSE_PATH = "/%s/close";
    private static final String CANCEL_PATH = "/%s/cancel";

    private static final String LINK_TEXT = "Link";

    private static final long CLIENT_TIMEOUT = Long.getLong("lra.internal.client.timeout", 10);
    private static final long START_TIMEOUT = Long.getLong("lra.internal.client.timeout.start", CLIENT_TIMEOUT);
    private static final long JOIN_TIMEOUT = Long.getLong("lra.internal.client.timeout.join", CLIENT_TIMEOUT);
    private static final long END_TIMEOUT = Long.getLong("lra.internal.client.end.timeout", CLIENT_TIMEOUT);
    private static final long LEAVE_TIMEOUT = Long.getLong("lra.internal.client.leave.timeout", CLIENT_TIMEOUT);
    private static final long QUERY_TIMEOUT = Long.getLong("lra.internal.client.query.timeout", CLIENT_TIMEOUT);

    private URI coordinatorUrl;

    public LRAClient() {
        this(System.getProperty(LRAClient.LRA_COORDINATOR_URL_KEY,
                "http://localhost:8080/" + COORDINATOR_PATH_NAME));
    }

    public LRAClient(String coordinatorUrl) {
        try {
            this.coordinatorUrl = new URI(coordinatorUrl);
        } catch (URISyntaxException use) {
            throw new IllegalStateException("Cannot convert the provided coordinator url String "
                    + coordinatorUrl + " to URI format", use);
        }
    }

    public void setCurrentLRA(URI lraId) {
        try {
            this.coordinatorUrl = LRAConstants.getLRACoordinatorUrl(lraId);
        } catch (IllegalStateException e) {
            throwGenericLRAException(lraId, BAD_REQUEST.getStatusCode(), e.getClass().getName() + ":" + e.getMessage(), null);
        }
    }

    public URI startLRA(URI parentLRA, String clientID, Long timeout, ChronoUnit unit) throws WebApplicationException {
        Client client = null;
        Response response = null;
        URI lra;
        if (clientID == null)  clientID = "";
        if (timeout == null) {
            timeout = 0L;
        } else if (timeout < 0) {
            throwGenericLRAException(parentLRA, BAD_REQUEST.getStatusCode(),
                    "Invalid timeout value: " + timeout, null);
            return null;
        }
        if (unit == null) {
            unit = ChronoUnit.SECONDS;
        }
        try {
            String encodedParentLRA = parentLRA == null ? "" : URLEncoder.encode(parentLRA.toString(), StandardCharsets.UTF_8.name());
            client = getClient();
            response = client.target(coordinatorUrl)
                .path(START_PATH)
                .queryParam(CLIENT_ID_PARAM_NAME, clientID)
                .queryParam(TIMELIMIT_PARAM_NAME, Duration.of(timeout, unit).toMillis())
                .queryParam(PARENT_LRA_PARAM_NAME, encodedParentLRA)
                .request()
                .async()
                .post(null)
                .get(START_TIMEOUT, TimeUnit.SECONDS);
            if (isUnexpectedResponseStatus(response, Response.Status.CREATED)) {
                String responseEntity = response.hasEntity() ? response.readEntity(String.class) : "";
                throwGenericLRAException(null, response.getStatus(),
                        "LRA start returned an unexpected status code: " + response.getStatus() + ", response '" + responseEntity + "'", null);
                return null;
            }
            lra = URI.create(response.getHeaderString(HttpHeaders.LOCATION));
            Current.push(lra);
            return lra;
        } catch (UnsupportedEncodingException uee) {
            throwGenericLRAException(null, INTERNAL_SERVER_ERROR.getStatusCode(),
                    "Cannot connect to the LRA coordinator: " + coordinatorUrl + " as provided parent LRA URL '" + parentLRA +
                            "' is not in URI format (" + uee.getClass().getName() + ":" + uee.getCause().getMessage() + ")", uee);
            return null;
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new WebApplicationException("start LRA client request timed out, try again later", e,
                    Response.Status.SERVICE_UNAVAILABLE.getStatusCode());
        } finally {
            if (client != null) {
                client.close();
            }
        }
    }

    public void cancelLRA(URI lraId) throws WebApplicationException {
        endLRA(lraId, false);
    }

    public void closeLRA(URI lraId) throws WebApplicationException {
        endLRA(lraId, true);
    }

    public URI joinLRA(URI lraId, Long timeLimit,
                       URI compensateUri, URI completeUri, URI forgetUri, URI leaveUri, URI afterUri, URI statusUri,
                       String compensatorData) throws WebApplicationException {
        LOGGER.fine("joinLRA ...lraId = " + lraId + ", timeLimit = " + timeLimit + ", " +
                "compensateUri = " + compensateUri + ", completeUri = " + completeUri + ", " +
                "forgetUri = " + forgetUri + ", leaveUri = " + leaveUri + ", " +
                "afterUri = " + afterUri + ", statusUri = " + statusUri + ", compensatorData = " + compensatorData);
        return enlistCompensator(lraId, timeLimit, "",
                compensateUri, completeUri,
                forgetUri, leaveUri, afterUri, statusUri,
                compensatorData);
    }

    public URI joinLRA(URI lraId, Long timeLimit,
                       URI participantUri, String compensatorData) throws WebApplicationException {
        validateURI(participantUri, false, "Invalid participant URL: %s");
        StringBuilder linkHeaderValue
                = makeLink(new StringBuilder(), null, "participant", participantUri.toASCIIString());
        return enlistCompensator(lraId, timeLimit, linkHeaderValue.toString(), compensatorData);
    }

    public void leaveLRA(URI lraId, String body) throws WebApplicationException {
        Client client = null;
        Response response;
        try {
            client = getClient();
            response = client.target(coordinatorUrl)
                .path(String.format(LEAVE_PATH, LRAConstants.getLRAUid(lraId)))
                .request()
                .async()
                .put(body == null ? Entity.text("") : Entity.text(body))
            .get(LEAVE_TIMEOUT, TimeUnit.SECONDS);
            if (OK.getStatusCode() != response.getStatus()) {
                throwGenericLRAException(null, response.getStatus(), "Leaving LRA " + lraId + " from coordinator " + coordinatorUrl
                    + " finished with unexpected response code: " + response.getStatusInfo(), null);
            }
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new WebApplicationException("leave LRA client request timed out, try again later",
                    Response.Status.SERVICE_UNAVAILABLE.getStatusCode());
        } finally {
            if (client != null) {
                client.close();
            }
        }
    }

    public static Map<String, String> getTerminationUris(URI baseUri, Class<?> compensatorClass, UriInfo uriInfo, Long timeout) {
        Map<String, String> paths = new HashMap<>();
        final boolean[] asyncTermination = {false};
        List<String> matchedURIs = uriInfo.getMatchedURIs();
        int matchedURI = (matchedURIs.size() > 1 ? 1 : 0);
        final String uriPrefix = baseUri + matchedURIs.get(matchedURI);
        String timeoutValue = timeout != null ? Long.toString(timeout) : "0";
        Arrays.stream(compensatorClass.getMethods()).forEach(method -> {
            Path pathAnnotation = method.getAnnotation(Path.class);
            if (pathAnnotation != null) {
                if (checkMethod(paths, method, COMPENSATE, pathAnnotation,
                        method.getAnnotation(Compensate.class), uriPrefix) != 0) {
                    paths.put(TIMELIMIT_PARAM_NAME, timeoutValue);

                    if (isAsyncCompletion(method)) {
                        asyncTermination[0] = true;
                    }
                }
                if (checkMethod(paths, method, COMPLETE, pathAnnotation,
                        method.getAnnotation(Complete.class), uriPrefix) != 0) {
                    paths.put(TIMELIMIT_PARAM_NAME, timeoutValue);

                    if (isAsyncCompletion(method)) {
                        asyncTermination[0] = true;
                    }
                }
                checkMethod(paths, method, STATUS, pathAnnotation,
                        method.getAnnotation(Status.class), uriPrefix);
                checkMethod(paths, method, FORGET, pathAnnotation,
                        method.getAnnotation(Forget.class), uriPrefix);
                checkMethod(paths, method, LEAVE, pathAnnotation, method.getAnnotation(Leave.class), uriPrefix);
                checkMethod(paths, method, AFTER, pathAnnotation, method.getAnnotation(AfterLRA.class), uriPrefix);
            }
        });
        if (asyncTermination[0] && !paths.containsKey(STATUS) && !paths.containsKey(FORGET)) {
            throw new WebApplicationException(
                    Response.status(BAD_REQUEST)
                            .entity("LRA participant class with asynchronous termination but no @Status or @Forget annotations")
                            .build());
        }
        StringBuilder linkHeaderValue = new StringBuilder();
        if (paths.size() != 0) {
            paths.forEach((k, v) -> makeLink(linkHeaderValue, null, k, v));
            paths.put(LINK_TEXT, linkHeaderValue.toString());
        }
        return paths;
    }

    public static boolean isAsyncCompletion(Method method) {
        if (method.isAnnotationPresent(Complete.class) || method.isAnnotationPresent(Compensate.class)) {
            for (Annotation[] ann : method.getParameterAnnotations()) {
                for (Annotation an : ann) {
                    if (Suspended.class.isAssignableFrom(an.annotationType())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static int checkMethod(Map<String, String> paths,
                                   Method method, String rel,
                                   Path pathAnnotation,
                                   Annotation annotationClass,
                                   String uriPrefix) {
        if (annotationClass == null) {
            return 0;
        }
        for (Annotation annotation : method.getDeclaredAnnotations()) {
            String name = annotation.annotationType().getName();
            if (name.equals(GET.class.getName()) ||
                    name.equals(PUT.class.getName()) ||
                    name.equals(POST.class.getName()) ||
                    name.equals(DELETE.class.getName())) {
                String pathValue = pathAnnotation.value();
                pathValue = pathValue.startsWith("/") ? pathValue : "/" + pathValue;
                String url = String.format("%s%s?%s=%s", uriPrefix, pathValue, LRAConstants.HTTP_METHOD_NAME, name);
                paths.put(rel, url);
                break;
            }
        }
        return 1;
    }

    public LRAStatus getStatus(URI uri) throws WebApplicationException {
        Client client = null;
        Response response;
        URL lraId;
        try {
            lraId = uri.toURL();
        } catch (MalformedURLException mue) {
            throwGenericLRAException(null,
                    Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),
                    "Could not convert LRA to a URL : " + mue.getClass().getName() + ":" + mue.getMessage(), mue);
            return null;
        }
        try {
            client = getClient();
            response = client.target(coordinatorUrl)
                .path(String.format(STATUS_PATH, LRAConstants.getLRAUid(uri)))
                .request()
                    .async()
                .get()
                    .get(QUERY_TIMEOUT, TimeUnit.SECONDS);
            if (response.getStatus() == NOT_FOUND.getStatusCode()) {
                String responseEntity = response.hasEntity() ? response.readEntity(String.class) : "";
                String errorMsg = "The requested LRA it '" + lraId + "' was not found and the status can't be obtained, "
                        + "response content: " + responseEntity;
                throw new NotFoundException(errorMsg, Response.status(NOT_FOUND).entity(errorMsg).build());
            }
            if (response.getStatus() == NO_CONTENT.getStatusCode()) {
                return LRAStatus.Active;
            }
            if (response.getStatus() != OK.getStatusCode()) {
                throwGenericLRAException(uri, response.getStatus(),  "LRA coordinator returned an invalid status code", null);
            }
            if (!response.hasEntity()) {
                throwGenericLRAException(uri, Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),
                    "LRA coordinator#getStatus returned 200 OK but no content: lra: " + lraId, null);
            }
            try {
                return fromString(response.readEntity(String.class));
            } catch (IllegalArgumentException iae) {
                throwGenericLRAException(uri,Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),
                    "LRA coordinator returned an invalid status", iae);
            }
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new WebApplicationException("get LRA status client request timed out, try again later",
                    Response.Status.SERVICE_UNAVAILABLE.getStatusCode());
        } finally {
            if (client != null) {
                client.close();
            }
        }
        return null;
    }

    private static LRAStatus fromString(String status) {
        if (status == null) {
            throw new IllegalArgumentException("The status parameter is null");
        }
        return LRAStatus.valueOf(status);
    }

    private static StringBuilder makeLink(StringBuilder b, String uriPrefix, String key, String value) {
        if (value == null) {
            return b;
        }
        String terminationUri = uriPrefix == null ? value : String.format("%s%s", uriPrefix, value);
        Link link =  Link.fromUri(terminationUri).title(key + " URI").rel(key).type(MediaType.TEXT_PLAIN).build();
        if (b.length() != 0)   b.append(',');
        return b.append(link);
    }

    private URI enlistCompensator(URI lraUri, Long timelimit, String uriPrefix,
                                  URI compensateUri, URI completeUri,
                                  URI forgetUri, URI leaveUri, URI afterUri, URI statusUri,
                                  String compensatorData) {
        validateURI(completeUri, true, "Invalid complete URL: %s");
        validateURI(compensateUri, true, "Invalid compensate URL: %s");
        validateURI(leaveUri, true, "Invalid status URL: %s");
        validateURI(afterUri, true, "Invalid after URL: %s");
        validateURI(forgetUri, true, "Invalid forgetUri URL: %s");
        validateURI(statusUri, true, "Invalid status URL: %s");
        Map<String, URI> terminateURIs = new HashMap<>();
        terminateURIs.put(COMPENSATE, compensateUri);
        terminateURIs.put(COMPLETE, completeUri);
        terminateURIs.put(LEAVE, leaveUri);
        terminateURIs.put(AFTER, afterUri);
        terminateURIs.put(STATUS, statusUri);
        terminateURIs.put(FORGET, forgetUri);
        StringBuilder linkHeaderValue = new StringBuilder();
        terminateURIs.forEach((k, v) -> makeLink(linkHeaderValue, uriPrefix, k, v == null ? null : v.toASCIIString()));
        return enlistCompensator(lraUri, timelimit, linkHeaderValue.toString(), compensatorData);
    }

    private URI enlistCompensator(URI uri, Long timelimit, String linkHeader, String compensatorData) {
        Client client = null;
        Response response;
        URL lraId = null;
        try {
            lraId = uri.toURL();
        } catch (MalformedURLException mue) {
            throwGenericLRAException(null, Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),
                    "Could not convert LRA to a URL : " + mue.getClass().getName() + ":" + mue.getMessage(), mue);
        }
        if (timelimit == null || timelimit < 0)  timelimit = 0L;
        try {
            client = getClient();
            try {
                response = client.target(coordinatorUrl)
                    .path(LRAConstants.getLRAUid(uri))
                    .queryParam(TIMELIMIT_PARAM_NAME, timelimit)
                    .request()
                    .header("Link", linkHeader)
                        .async()
                    .put(Entity.text(compensatorData == null ? linkHeader : compensatorData))
                .get(JOIN_TIMEOUT, TimeUnit.SECONDS);
            String responseEntity = response.hasEntity() ? response.readEntity(String.class) : "";
            if (response.getStatus() == Response.Status.PRECONDITION_FAILED.getStatusCode()) {
                String errorMsg = lraId + ": Too late to join with this LRA";
                throw new WebApplicationException(errorMsg,
                        Response.status(PRECONDITION_FAILED).entity(errorMsg).build());
            } else if (response.getStatus() == NOT_FOUND.getStatusCode()) {
                throw new WebApplicationException(uri.toASCIIString(),
                        Response.status(GONE).entity(uri.toASCIIString()).build());
            } else if (response.getStatus() != OK.getStatusCode()) {
                throwGenericLRAException(uri, response.getStatus(), "unable to register participant", null);
            }
            String recoveryUrl = null;
            try {
                recoveryUrl = response.getHeaderString(LRA_HTTP_RECOVERY_HEADER);
                LOGGER.info("recoveryUrl:" + recoveryUrl);
                if (recoveryUrl == null) return null;
                String url = URLDecoder.decode(recoveryUrl, StandardCharsets.UTF_8.name());
                return new URI(url);
            } catch (URISyntaxException | UnsupportedEncodingException e) {
                throwGenericLRAException(null, Response.Status.SERVICE_UNAVAILABLE.getStatusCode(),
                        "join " + lraId + " returned an invalid recovery URI '" + recoveryUrl + "' : " + responseEntity, e);
                return null;
            }
        } catch (WebApplicationException webApplicationException) {
            throw new WebApplicationException(uri.toASCIIString(), GONE);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new WebApplicationException("join LRA client request timed out, try again later",
                    Response.Status.SERVICE_UNAVAILABLE.getStatusCode());
        }
        } finally {
            if (client != null) {
                client.close();
            }
        }
    }

    private void endLRA(URI lra, boolean confirm) throws WebApplicationException {
        Client client = null;
        Response response;
        try {
            client = getClient();
            String lraUid = LRAConstants.getLRAUid(lra);
            try {
                response = client.target(coordinatorUrl)
                        .path(confirm ? String.format(CLOSE_PATH, lraUid) : String.format(CANCEL_PATH, lraUid))
                        .request()
                        .async()
                        .put(Entity.text(""))
                        .get(END_TIMEOUT, TimeUnit.SECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                throw new WebApplicationException("end LRA client request timed out, try again later",
                        Response.Status.SERVICE_UNAVAILABLE.getStatusCode());
            }
            if (isUnexpectedResponseStatus(response, OK, Response.Status.ACCEPTED, NOT_FOUND)) {
                throwGenericLRAException(lra, INTERNAL_SERVER_ERROR.getStatusCode(),
                        "LRA finished with an unexpected status code: " + response.getStatus(), null);
            }
            if (response.getStatus() == NOT_FOUND.getStatusCode()) {
                throw new NotFoundException("NOTFOUND",
                        Response.status(NOT_FOUND).entity(lra.toASCIIString()).build());
            }
        } finally {
            Current.pop(lra);

            if (client != null) {
                client.close();
            }
        }
    }

    private void validateURI(URI uri, boolean nullAllowed, String message) {
        if (uri == null) {
            if (!nullAllowed) {
                throwGenericLRAException(null, NOT_ACCEPTABLE.getStatusCode(),
                        String.format(message, "null value"), null);
            }
        } else {
            try {
                uri.toURL();
            } catch (MalformedURLException mue) {
                throwGenericLRAException(null, NOT_ACCEPTABLE.getStatusCode(),
                        String.format(message, mue.getClass().getName() +":" + mue.getMessage()) + " uri=" + uri, mue);
            }
        }
    }

    private boolean isUnexpectedResponseStatus(Response response, Response.Status... expected) {
        for (Response.Status anExpected : expected) {
            if (response.getStatus() == anExpected.getStatusCode()) {
                return false;
            }
        }
        return true;
    }

    public URI getCurrent() {
        return Current.peek();
    }

    public void close() {
    }

    private void throwGenericLRAException(URI lraId, int statusCode, String message, Throwable cause) throws WebApplicationException {
        String errorMsg = String.format("%s: %s", lraId, message);
        throw new WebApplicationException(errorMsg, cause, Response.status(statusCode).entity(errorMsg).build());
    }

    private Client getClient() {
        return ClientBuilder.newClient();
    }
}
