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

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.lra.annotation.Compensate;
import org.eclipse.microprofile.lra.annotation.Complete;
import org.eclipse.microprofile.lra.annotation.Forget;
import org.eclipse.microprofile.lra.annotation.LRAStatus;
import org.eclipse.microprofile.lra.annotation.Status;
import org.eclipse.microprofile.lra.annotation.ws.rs.LRA;
import org.eclipse.microprofile.lra.annotation.ws.rs.Leave;

import javax.inject.Inject;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import static io.helidon.lra.rest.LRAConstants.AFTER;
import static io.helidon.lra.rest.LRAConstants.COMPENSATE;
import static io.helidon.lra.rest.LRAConstants.COMPLETE;
import static io.helidon.lra.rest.LRAConstants.FORGET;
import static io.helidon.lra.rest.LRAConstants.LEAVE;
import static io.helidon.lra.rest.LRAConstants.STATUS;
import static io.helidon.lra.rest.LRAConstants.TIMELIMIT_PARAM_NAME;
import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_CONTEXT_HEADER;
import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_PARENT_CONTEXT_HEADER;
import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_RECOVERY_HEADER;
import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.Type.MANDATORY;
import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.Type.NESTED;

@Provider
public class ServerLRAFilter implements ContainerRequestFilter, ContainerResponseFilter {
    private static final Logger LOGGER = Logger.getLogger(ServerLRAFilter.class.getName());
    private static final String CANCEL_ON_FAMILY_PROP = "CancelOnFamily";
    private static final String CANCEL_ON_PROP = "CancelOn";
    private static final String TERMINAL_LRA_PROP = "terminateLRA";
    private static final String SUSPENDED_LRA_PROP = "suspendLRA";
    private static final String NEW_LRA_PROP = "newLRA";
    private static final String ABORT_WITH_PROP = "abortWith";

    private static final Pattern START_END_QUOTES_PATTERN = Pattern.compile("^\"|\"$");
    private static final long DEFAULT_TIMEOUT_MILLIS = 0L;

    @Context
    protected ResourceInfo resourceInfo;

    private URI baseUri;

    @Inject
    private LRAParticipantRegistry lraParticipantRegistry;
    private final LRAClient lraClient;

    public ServerLRAFilter() {
        lraClient = new LRAClient();
    }

    private boolean isTxInvalid(ContainerRequestContext containerRequestContext, LRA.Type type, URI lraId,
                                boolean shouldNotBeNull, ArrayList<Progress> progress) {
        if (lraId == null && shouldNotBeNull) {
            abortWith(containerRequestContext, null, Response.Status.PRECONDITION_FAILED.getStatusCode(),
                    type.name() + " but no tx", progress);
            return true;
        } else if (lraId != null && !shouldNotBeNull) {
            abortWith(containerRequestContext, lraId.toASCIIString(), Response.Status.PRECONDITION_FAILED.getStatusCode(),
                    type.name() + " but found tx", progress);
            return true;
        }
        return false;
    }

    @Override
    public void filter(ContainerRequestContext containerRequestContext) {
        Config config = ConfigProvider.getConfig();
        Optional<String> lraParticipantURL = config.getOptionalValue("lra.participant.url", String.class);
        LOGGER.fine("lra.participant.url:" + lraParticipantURL);
        String path = !lraParticipantURL.isEmpty()? lraParticipantURL.get():
                "http://" + config.getOptionalValue("server.host", String.class).get() + ":" + config.getOptionalValue("server.port", String.class);
        path += containerRequestContext.getUriInfo().getBaseUri().getPath();
        try {
            baseUri = new URI(path);
            LOGGER.fine("ServerLRAFilter.filter baseUri.getPath():" + path + " resultant:" + baseUri);
        } catch (URISyntaxException e) {
            throw new WebApplicationException ("URISyntaxException thrown in ServerLRAFilter.filter:" + e);
        }
        Method method = resourceInfo.getResourceMethod();
        MultivaluedMap<String, String> headers = containerRequestContext.getHeaders();
        LRA.Type type = null;
        LRA transactional = AnnotationResolver.resolveAnnotation(LRA.class, method);
        URI lraId;
        URI newLRA = null;
        Long timeout = null;
        URI suspendedLRA = null;
        URI incommingLRA = null;
        URI recoveryUrl;
        boolean isLongRunning = false;
        boolean requiresActiveLRA = false;
        ArrayList<Progress> progress = null;
        if (transactional == null)  transactional = method.getDeclaringClass().getDeclaredAnnotation(LRA.class);
        if (transactional != null) {
            type = transactional.value();
            isLongRunning = !transactional.end();
            Response.Status.Family[] cancel0nFamily = transactional.cancelOnFamily();
            Response.Status[] cancel0n = transactional.cancelOn();
            if (cancel0nFamily.length != 0) {
                containerRequestContext.setProperty(CANCEL_ON_FAMILY_PROP, cancel0nFamily);
            }
            if (cancel0n.length != 0) {
                containerRequestContext.setProperty(CANCEL_ON_PROP, cancel0n);
            }
            if (transactional.timeLimit() != 0) {
                timeout = Duration.of(transactional.timeLimit(), transactional.timeUnit()).toMillis();
            }
        }
        boolean endAnnotation = AnnotationResolver.isAnnotationPresent(Complete.class, method)
                || AnnotationResolver.isAnnotationPresent(Compensate.class, method)
                || AnnotationResolver.isAnnotationPresent(Leave.class, method)
                || AnnotationResolver.isAnnotationPresent(Status.class, method)
                || AnnotationResolver.isAnnotationPresent(Forget.class, method);
        if (headers.containsKey(LRA_HTTP_CONTEXT_HEADER)) {
            try {
                incommingLRA = new URI(Current.getLast(headers.get(LRA_HTTP_CONTEXT_HEADER)));
            } catch (URISyntaxException e) {
                String msg = String.format("header %s contains an invalid URL %s",
                        LRA_HTTP_CONTEXT_HEADER, Current.getLast(headers.get(LRA_HTTP_CONTEXT_HEADER)));

                abortWith(containerRequestContext, null, Response.Status.PRECONDITION_FAILED.getStatusCode(),
                        msg, null);
                return; // user error, bail out
            }

            if (AnnotationResolver.isAnnotationPresent(Leave.class, method)) {
                // leave the LRA
                Map<String, String> terminateURIs = LRAClient.getTerminationUris(baseUri,
                        resourceInfo.getResourceClass(), containerRequestContext.getUriInfo(), timeout);
                String compensatorId = terminateURIs.get("Link");

                if (compensatorId == null) {
                    abortWith(containerRequestContext, incommingLRA.toASCIIString(),
                            Response.Status.BAD_REQUEST.getStatusCode(),
                            "Missing complete or compensate annotations", null);
                    return;
                }
                try {
                    lraClient.leaveLRA(incommingLRA, compensatorId);
                    progress = updateProgress(progress, ProgressStep.Left, null);
                } catch (WebApplicationException e) {
                    progress = updateProgress(progress, ProgressStep.LeaveFailed, e.getMessage());
                    abortWith(containerRequestContext, incommingLRA.toASCIIString(),
                            e.getResponse().getStatus(),
                            e.getMessage(), progress);
                    return;
                } catch (ProcessingException e) {
                    progress = updateProgress(progress, ProgressStep.LeaveFailed, e.getMessage());
                    abortWith(containerRequestContext, incommingLRA.toASCIIString(),
                            Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),
                            e.getMessage(), progress);
                    return;
                }
            }
        }
        if (type == null) {
            if (!endAnnotation)  Current.clearContext(headers);
            if (incommingLRA != null) {
                Current.push(incommingLRA);
                containerRequestContext.setProperty(SUSPENDED_LRA_PROP, incommingLRA);
            }
            return;
        }
        if (!headers.containsKey(LRA_HTTP_CONTEXT_HEADER)) {
            Object lraContext = containerRequestContext.getProperty(LRA_HTTP_CONTEXT_HEADER);
            if (lraContext != null)  incommingLRA = (URI) lraContext;
        }
        if (endAnnotation && incommingLRA == null)  return;
        if (incommingLRA != null) {
            // set the parent context header
            try {
                headers.putSingle(LRA_HTTP_PARENT_CONTEXT_HEADER, Current.getFirstParent(incommingLRA));
            } catch (UnsupportedEncodingException e) {
                abortWith(containerRequestContext, incommingLRA.toASCIIString(),
                        Response.Status.PRECONDITION_FAILED.getStatusCode(),
                        String.format("incoming LRA %s contains an invalid parent: %s", incommingLRA, e.getMessage()),
                        progress);
                return;
            }
        }

        switch (type) {
            case MANDATORY:
                if (isTxInvalid(containerRequestContext, type, incommingLRA, true, progress)) {
                    return;
                }

                lraId = incommingLRA;
                requiresActiveLRA = true;

                break;
            case NEVER:
                if (isTxInvalid(containerRequestContext, type, incommingLRA, false, progress)) {
                    return;
                }
                lraId = null;
                break;
            case NOT_SUPPORTED:
                suspendedLRA = incommingLRA;
                lraId = null;
                break;
            case NESTED:
            case REQUIRED:
                if (incommingLRA != null) {
                    if (type == NESTED) {
                        headers.putSingle(LRA_HTTP_PARENT_CONTEXT_HEADER, incommingLRA.toASCIIString());
                        suspendedLRA = incommingLRA;

                        if (progress == null) {
                            progress = new ArrayList<>();
                        }

                        newLRA = lraId = startLRA(containerRequestContext, incommingLRA, method, timeout, progress);

                        if (newLRA == null) {
                            // startLRA will have called abortWith on the request context
                            // the failure plus any previous actions (the leave request) will be reported via the response filter
                            return;
                        }
                    } else {
                        lraId = incommingLRA;
                        // incomingLRA will be resumed
                        requiresActiveLRA = true;
                    }

                } else {
                    if (progress == null) { // NB my IDE seems to think this check is redundant
                        progress = new ArrayList<>();
                    }
                    newLRA = lraId = startLRA(containerRequestContext, null, method, timeout, progress);

                    if (newLRA == null) {
                        // startLRA will have called abortWith on the request context
                        // the failure and any previous actions (the leave request) will be reported via the response filter
                        return;
                    }
                }

                break;
            case REQUIRES_NEW:
//                    previous = AtomicAction.suspend();
                suspendedLRA = incommingLRA;

                if (progress == null) {
                    progress = new ArrayList<>();
                }
                newLRA = lraId = startLRA(containerRequestContext,null, method, timeout, progress);

                if (newLRA == null) {
                    // startLRA will have called abortWith on the request context
                    // the failure and any previous actions (the leave request) will be reported via the response filter
                    return;
                }

                break;
            case SUPPORTS:
                lraId = incommingLRA;

                // incomingLRA will be resumed if not null

                break;
            default:
                lraId = incommingLRA;
        }

        if (lraId == null) {
            // the method call needs to run without a transaction
            Current.clearContext(headers);

            if (suspendedLRA != null) {
                containerRequestContext.setProperty(SUSPENDED_LRA_PROP, suspendedLRA);
            }

            return; // non transactional
        }

        if (!isLongRunning) {
            containerRequestContext.setProperty(TERMINAL_LRA_PROP, lraId);
        }

        // store state with the current thread. TODO for the async version use containerRequestContext.setProperty("lra", Current.peek());
        Current.updateLRAContext(lraId, headers); // make the current LRA available to the called method

        if (newLRA != null) {
            if (suspendedLRA != null) {
                containerRequestContext.setProperty(SUSPENDED_LRA_PROP, incommingLRA);
            }

            containerRequestContext.setProperty(NEW_LRA_PROP, newLRA);
        }

        Current.push(lraId);

        try {
            lraClient.setCurrentLRA(lraId); // make the current LRA available to the called method
        } catch (Exception e) {
            // should not happen since lraId has already been validated
            // (perhaps we should not use the client API to set the context)
            abortWith(containerRequestContext, lraId.toASCIIString(),
                    Response.Status.BAD_REQUEST.getStatusCode(),
                    e.getMessage(),
                    progress);
            return; // any previous actions (such as leave and start requests) will be reported via the response filter
        }

        if (!endAnnotation) {
//            Map<String, String> terminateURIs = LRAClient.getTerminationUris(resourceInfo.getResourceClass(), containerRequestContext.getUriInfo(), timeout);
            Map<String, String> terminateURIs = LRAClient.getTerminationUris(baseUri, resourceInfo.getResourceClass(), containerRequestContext.getUriInfo(), timeout);
            String timeLimitStr = terminateURIs.get(TIMELIMIT_PARAM_NAME);
            long timeLimit = timeLimitStr == null ? DEFAULT_TIMEOUT_MILLIS : Long.parseLong(timeLimitStr);

            LRAParticipant participant = lraParticipantRegistry != null ?
                lraParticipantRegistry.getParticipant(resourceInfo.getResourceClass().getName()) : null;

            if (terminateURIs.containsKey("Link") || participant != null) {
                try {
                    if (participant != null) {
                        participant.augmentTerminationURIs(terminateURIs, baseUri);
                    }

                    recoveryUrl = lraClient.joinLRA(lraId, timeLimit,
                            toURI(terminateURIs.get(COMPENSATE)),
                            toURI(terminateURIs.get(COMPLETE)),
                            toURI(terminateURIs.get(FORGET)),
                            toURI(terminateURIs.get(LEAVE)),
                            toURI(terminateURIs.get(AFTER)),
                            toURI(terminateURIs.get(STATUS)),
                            null);

                    progress = updateProgress(progress, ProgressStep.Joined, null);

                    headers.putSingle(LRA_HTTP_RECOVERY_HEADER,
                            START_END_QUOTES_PATTERN.matcher(recoveryUrl.toASCIIString()).replaceAll(""));
                } catch (WebApplicationException e) {
                    progress = updateProgress(progress, ProgressStep.JoinFailed, e.getMessage());
                    abortWith(containerRequestContext, lraId.toASCIIString(),
                            e.getResponse().getStatus(),
                            String.format("%s: %s", e.getClass().getSimpleName(), e.getMessage()), progress);
                    // the failure plus any previous actions (such as leave and start requests) will be reported via the response filter
                } catch (URISyntaxException e) {
                    progress = updateProgress(progress, ProgressStep.JoinFailed, e.getMessage()); // one or more of the participant end points was invalid
                    abortWith(containerRequestContext, lraId.toASCIIString(),
                            Response.Status.BAD_REQUEST.getStatusCode(),
                            String.format("%s %s: %s", lraId, e.getClass().getSimpleName(), e.getMessage()), progress);
                    // the failure plus any previous actions (such as leave and start requests) will be reported via the response filter
                } catch (ProcessingException e) {
                    progress = updateProgress(progress, ProgressStep.JoinFailed, e.getMessage()); // a remote coordinator was unavailable
                    abortWith(containerRequestContext, lraId.toASCIIString(),
                            Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),
                            String.format("%s %s,", e.getClass().getSimpleName(), e.getMessage()), progress);
                    // the failure plus any previous actions (such as leave and start requests) will be reported via the response filter
                }
            } else if (requiresActiveLRA && lraClient.getStatus(lraId) != LRAStatus.Active) {
                Current.clearContext(headers);
                Current.pop(lraId);
                containerRequestContext.removeProperty(SUSPENDED_LRA_PROP);

                if (type == MANDATORY) {
                    abortWith(containerRequestContext, lraId.toASCIIString(),
                            Response.Status.PRECONDITION_FAILED.getStatusCode(),
                            "LRA should have been active: ", progress);
                    // any previous actions (such as leave and start requests) will be reported via the response filter
                }
            }
        }
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        // a request is leaving the container so clear any context on the thread and fix up the LRA response header
        ArrayList<Progress> progress = cast(requestContext.getProperty(ABORT_WITH_PROP));
        Object suspendedLRA = requestContext.getProperty(SUSPENDED_LRA_PROP);
        URI current = Current.peek();
        URI toClose = (URI) requestContext.getProperty(TERMINAL_LRA_PROP);
        boolean isCancel = isJaxRsCancel(requestContext, responseContext);

        try {
            if (current != null && isCancel) {
                try {
                    // do not attempt to cancel if the request filter tried but failed to start a new LRA
                    if (progress == null || progressDoesNotContain(progress, ProgressStep.StartFailed)) {
                        lraClient.cancelLRA(current);
                        progress = updateProgress(progress, ProgressStep.Ended, null);
                    }
                } catch (NotFoundException ignore) {
                    // must already be cancelled (if the intercepted method caused it to cancel)
                    // or completed (if the intercepted method caused it to complete)
                    progress = updateProgress(progress, ProgressStep.Ended, null);
                } catch (WebApplicationException e) {
                    progress = updateProgress(progress, ProgressStep.CancelFailed, e.getMessage());
                } catch (ProcessingException e) {
                    Method method = resourceInfo.getResourceMethod();
                    progress = updateProgress(progress, ProgressStep.CancelFailed, e.getMessage());
                    toClose = null;
                } finally {
                    if (current.toASCIIString().equals(
                            Current.getLast(requestContext.getHeaders().get(LRA_HTTP_CONTEXT_HEADER)))) {
                        // the callers context was ended so invalidate it
                        requestContext.getHeaders().remove(LRA_HTTP_CONTEXT_HEADER);
                    }

                    if (toClose != null && toClose.toASCIIString().equals(current.toASCIIString())) {
                        toClose = null; // don't attempt to finish the LRA twice
                    }
                }
            }

            if (toClose != null) {
                try {
                    // do not attempt to close or cancel if the request filter tried but failed to start a new LRA
                    if (progress == null || progressDoesNotContain(progress, ProgressStep.StartFailed)) {
                        if (isCancel) {
                            lraClient.cancelLRA(toClose);
                        } else {
                            lraClient.closeLRA(toClose);
                        }

                        progress = updateProgress(progress, ProgressStep.Ended, null);
                    }
                } catch (NotFoundException ignore) {
                    // must already be cancelled (if the intercepted method caused it to cancel)
                    // or completed (if the intercepted method caused it to complete
                    progress = updateProgress(progress, ProgressStep.Ended, null);
                } catch (WebApplicationException | ProcessingException e) {
                    progress = updateProgress(progress,
                            isCancel ? ProgressStep.CancelFailed : ProgressStep.CloseFailed, e.getMessage());
                } finally {
                    requestContext.getHeaders().remove(LRA_HTTP_CONTEXT_HEADER);

                    if (toClose.toASCIIString().equals(
                            Current.getLast(requestContext.getHeaders().get(LRA_HTTP_CONTEXT_HEADER)))) {
                        // the callers context was ended so invalidate it
                        requestContext.getHeaders().remove(LRA_HTTP_CONTEXT_HEADER);
                    }
                }
            }

            if (responseContext.getStatus() == Response.Status.OK.getStatusCode() &&
                    LRAClient.isAsyncCompletion(resourceInfo.getResourceMethod())) {
            }

            if (progress != null) {
                String failureMessage =  processLRAOperationFailures(progress);

                if (failureMessage != null) {
                    responseContext.setEntity(failureMessage, null, MediaType.TEXT_PLAIN_TYPE);
                }
            }
        } finally {
            if (suspendedLRA != null) {
                Current.push((URI) suspendedLRA);
            }

            Current.updateLRAContext(responseContext);

            Current.popAll();
        }
    }

    private boolean isJaxRsCancel(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        int status = responseContext.getStatus();
        Response.Status.Family[] cancel0nFamily = (Response.Status.Family[]) requestContext.getProperty(CANCEL_ON_FAMILY_PROP);
        Response.Status[] cancel0n = (Response.Status[]) requestContext.getProperty(CANCEL_ON_PROP);

        if (cancel0nFamily != null) {
            if (Arrays.stream(cancel0nFamily).anyMatch(f -> Response.Status.Family.familyOf(status) == f)) {
                return true;
            }
        }

        if (cancel0n != null) {
            return Arrays.stream(cancel0n).anyMatch(f -> status == f.getStatusCode());
        }

        return false;
    }

    private enum ProgressStep {
        Left ("leave succeeded"),
        LeaveFailed("leave failed"),
        Started("start succeeded"),
        StartFailed("start failed"),
        Joined("join succeeded"),
        JoinFailed("join failed"),
        Ended("end succeeded"),
        CloseFailed("close failed"),
        CancelFailed("cancel failed");

        final String status;

        ProgressStep(final String status) {
            this.status = status;
        }

        @Override
        public String toString() {
            return status;
        }
    }

    private static class Progress {
        static EnumSet<ProgressStep> failures = EnumSet.of(
                ProgressStep.LeaveFailed,
                ProgressStep.StartFailed,
                ProgressStep.JoinFailed,
                ProgressStep.CloseFailed,
                ProgressStep.CancelFailed);

        ProgressStep progress;
        String reason;

        public Progress(ProgressStep progress, String reason) {
            this.progress = progress;
            this.reason = reason;
        }

        public boolean wasSuccessful() {
            return !failures.contains(progress);
        }
    }

    private String processLRAOperationFailures(ArrayList<Progress> progress) {
        StringJoiner badOps = new StringJoiner(", ");
        StringJoiner goodOps = new StringJoiner(", ");
        StringBuilder code = new StringBuilder("-");

        progress.forEach(p -> {
            if (p.wasSuccessful()) {
                code.insert(0, p.progress.ordinal());
                goodOps.add(String.format("%s (%s)", p.progress.name(), p.progress.status));
            } else {
                code.append(p.progress.ordinal());
                badOps.add(String.format("%s (%s)", p.progress.name(), p.reason));
            }
        });

        if (badOps.length() != 0) {
            return "warning";
        }

        return null;
    }

    private boolean progressDoesNotContain(ArrayList<Progress> progress, ProgressStep step) {
        return progress.stream().noneMatch(p -> p.progress == step);
    }

    // add another step to the list of steps performed so far
    private ArrayList<Progress> updateProgress(ArrayList<Progress> progress, ProgressStep step, String reason) {
        if (progress == null) {
            progress = new ArrayList<>();
        }

        progress.add(new Progress(step, reason));

        return progress;
    }

    // the processing performed by the request filter caused the request to abort (without executing application code)
    private void abortWith(ContainerRequestContext containerRequestContext, String lraId, int statusCode,
                           String message, Collection<Progress> reasons) {
        containerRequestContext.abortWith(Response.status(statusCode).build());
        containerRequestContext.setProperty(ABORT_WITH_PROP, reasons);
        Method method = resourceInfo.getResourceMethod();
    }

    private URI toURI(String uri) throws URISyntaxException {
        return uri == null ? null : new URI(uri);
    }

    public static <T extends Collection<?>> T cast(Object obj) {
        return (T) obj;
    }

    private URI startLRA(ContainerRequestContext containerRequestContext, URI parentLRA, Method method, Long timeout,
                         ArrayList<Progress> progress) {
        String clientId = method.getDeclaringClass().getName() + "#" + method.getName();

        try {
            URI lra = lraClient.startLRA(parentLRA, clientId, timeout, ChronoUnit.MILLIS);
            updateProgress(progress, ProgressStep.Started, null);
            return lra;
        } catch (WebApplicationException e) {
            updateProgress(progress, ProgressStep.StartFailed, e.getMessage());

            abortWith(containerRequestContext, null,
                    e.getResponse().getStatus(),
                    String.format("%s %s", e.getClass().getSimpleName(), e.getMessage()),
                    progress);
        } catch (ProcessingException e) {
            updateProgress(progress, ProgressStep.StartFailed, e.getMessage());

            abortWith(containerRequestContext, null,
                    Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),
                    String.format("%s %s", e.getClass().getSimpleName(), e.getMessage()),
                    progress);
        }
        return null;
    }
}
