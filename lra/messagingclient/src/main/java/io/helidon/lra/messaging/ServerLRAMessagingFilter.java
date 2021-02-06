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
package io.helidon.lra.messaging;

import io.narayana.lra.LRAConstants;
import org.eclipse.microprofile.lra.annotation.*;
import org.eclipse.microprofile.lra.annotation.ws.rs.LRA;
import org.eclipse.microprofile.lra.annotation.ws.rs.Leave;

import javax.ws.rs.*;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.*;
import javax.ws.rs.ext.Provider;
import java.io.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.*;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Pattern;

import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_CONTEXT_HEADER;
import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.Type.MANDATORY;
import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.Type.NESTED;


import io.narayana.lra.AnnotationResolver;
import io.narayana.lra.Current;
import io.narayana.lra.client.NarayanaLRAClient;
import io.narayana.lra.logging.LRALogger;
import org.eclipse.microprofile.lra.annotation.Compensate;
import org.eclipse.microprofile.lra.annotation.Complete;
import org.eclipse.microprofile.lra.annotation.Forget;
import org.eclipse.microprofile.lra.annotation.LRAStatus;
import org.eclipse.microprofile.lra.annotation.Status;
import org.eclipse.microprofile.reactive.messaging.Incoming;

import javax.ws.rs.container.ResourceInfo;

import static io.narayana.lra.LRAConstants.AFTER;
import static io.narayana.lra.LRAConstants.COMPENSATE;
import static io.narayana.lra.LRAConstants.COMPLETE;
import static io.narayana.lra.LRAConstants.FORGET;
import static io.narayana.lra.LRAConstants.LEAVE;
import static io.narayana.lra.LRAConstants.STATUS;
import static io.narayana.lra.LRAConstants.TIMELIMIT_PARAM_NAME;
import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_PARENT_CONTEXT_HEADER;
import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_RECOVERY_HEADER;

@Provider
public class ServerLRAMessagingFilter {
    private static final String CANCEL_ON_FAMILY_PROP = "CancelOnFamily";
    private static final String CANCEL_ON_PROP = "CancelOn";
    private static final String TERMINAL_LRA_PROP = "terminateLRA";
    private static final String SUSPENDED_LRA_PROP = "suspendLRA";
    private static final String NEW_LRA_PROP = "newLRA";
    private static final String ABORT_WITH_PROP = "abortWith";

    private static final Pattern START_END_QUOTES_PATTERN = Pattern.compile("^\"|\"$");
    private static final long DEFAULT_TIMEOUT_MILLIS = 0L;

    static private boolean isMessagingURLFactoryRegistered = false;

    public ResourceInfo resourceInfo;
    private final io.narayana.lra.client.NarayanaLRAClient lraClient;

    public ServerLRAMessagingFilter() {
        lraClient = new io.narayana.lra.client.NarayanaLRAClient();
        if (!isMessagingURLFactoryRegistered) {
            URL.setURLStreamHandlerFactory(protocol -> "messaging".equals(protocol) ? new URLStreamHandler() {
                protected URLConnection openConnection(URL url) throws IOException {
                    return new URLConnection(url) {
                        public void connect() throws IOException {
                            System.out.println("Connected!");
                        }
                    };
                }
            } : null);
            isMessagingURLFactoryRegistered = true;
        }
    }

    private boolean isTxInvalid(MessagingRequestContext MessagingRequestContext, LRA.Type type, URI lraId,
                                boolean shouldNotBeNull, ArrayList<Progress> progress) {
        if (lraId == null && shouldNotBeNull) {
            abortWith(MessagingRequestContext, null, Response.Status.PRECONDITION_FAILED.getStatusCode(),
                    type.name() + " but no tx", progress);
            return true;
        } else if (lraId != null && !shouldNotBeNull) {
            abortWith(MessagingRequestContext, lraId.toASCIIString(), Response.Status.PRECONDITION_FAILED.getStatusCode(),
                    type.name() + " but found tx", progress);
            return true;
        }

        return false;
    }

    public void filter(MessagingRequestContext MessagingRequestContext) {
        // Note that this filter uses abortWith instead of throwing exceptions on encountering exceptional
        // conditions. This facilitates async because filters for asynchronous JAX-RS methods are
        // not allowed to throw exceptions.
        Method method = resourceInfo.getResourceMethod();
        MultivaluedMap<String, String> headers = MessagingRequestContext.getHeaders();
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

        if (transactional == null) {
            transactional = method.getDeclaringClass().getDeclaredAnnotation(LRA.class);
        }

        if (transactional != null) {
            type = transactional.value();
            isLongRunning = !transactional.end();
            Response.Status.Family[] cancel0nFamily = transactional.cancelOnFamily();
            Response.Status[] cancel0n = transactional.cancelOn();

            if (cancel0nFamily.length != 0) {
                MessagingRequestContext.setProperty(CANCEL_ON_FAMILY_PROP, cancel0nFamily);
            }

            if (cancel0n.length != 0) {
                MessagingRequestContext.setProperty(CANCEL_ON_PROP, cancel0n);
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

                abortWith(MessagingRequestContext, null, Response.Status.PRECONDITION_FAILED.getStatusCode(),
                        msg, null);
                return; // user error, bail out
            }

            if (AnnotationResolver.isAnnotationPresent(Leave.class, method)) {
                // leave the LRA
                Map<String, String> terminateURIs = getTerminationUris(
                        resourceInfo.getResourceClass(), MessagingRequestContext.getUriInfo(), timeout);
                String compensatorId = terminateURIs.get("Link");

                if (compensatorId == null) {
                    abortWith(MessagingRequestContext, incommingLRA.toASCIIString(),
                            Response.Status.BAD_REQUEST.getStatusCode(),
                            "Missing complete or compensate annotations", null);
                    return; // user error, bail out
                }

                try {
                    lraClient.leaveLRA(incommingLRA, compensatorId);
                    progress = updateProgress(progress, ProgressStep.Left, null); // leave succeeded
                } catch (WebApplicationException e) {
                    progress = updateProgress(progress, ProgressStep.LeaveFailed, e.getMessage()); // leave may have failed
                    abortWith(MessagingRequestContext, incommingLRA.toASCIIString(),
                            e.getResponse().getStatus(),
                            e.getMessage(), progress);
                    return; // the error will be handled or reported via the response filter
                } catch (ProcessingException e) { // a remote coordinator was unavailable
                    progress = updateProgress(progress, ProgressStep.LeaveFailed, e.getMessage()); // leave may have failed
                    abortWith(MessagingRequestContext, incommingLRA.toASCIIString(),
                            Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),
                            e.getMessage(), progress);
                    return; // the error will be handled or reported via the response filter
                }

                // let the participant know which lra he left by leaving the header intact
            }
        }

        if (type == null) {
            if (!endAnnotation) {
                Current.clearContext(headers);
            }

            if (incommingLRA != null) {
                Current.push(incommingLRA);
                MessagingRequestContext.setProperty(SUSPENDED_LRA_PROP, incommingLRA);
            }

            return; // not transactional
        }

        // check the incoming request for an LRA context
        if (!headers.containsKey(LRA_HTTP_CONTEXT_HEADER)) {
            Object lraContext = MessagingRequestContext.getProperty(LRA_HTTP_CONTEXT_HEADER);

            if (lraContext != null) {
                incommingLRA = (URI) lraContext;
            }
        }

        if (endAnnotation && incommingLRA == null) {
            return;
        }

        if (incommingLRA != null) {
            // set the parent context header
            try {
                headers.putSingle(LRA_HTTP_PARENT_CONTEXT_HEADER, Current.getFirstParent(incommingLRA));
            } catch (UnsupportedEncodingException e) {
                abortWith(MessagingRequestContext, incommingLRA.toASCIIString(),
                        Response.Status.PRECONDITION_FAILED.getStatusCode(),
                        String.format("incoming LRA %s contains an invalid parent: %s", incommingLRA, e.getMessage()),
                        progress);
                return; // any previous actions (the leave request) will be reported via the response filter
            }
        }

        switch (type) {
            case MANDATORY: // a txn must be present
                if (isTxInvalid(MessagingRequestContext, type, incommingLRA, true, progress)) {
                    // isTxInvalid will have called abortWith (thus aborting the rest of the filter chain)
                    return; // any previous actions (eg the leave request) will be reported via the response filter
                }

                lraId = incommingLRA;
                requiresActiveLRA = true;

                break;
            case NEVER: // a txn must not be present
                if (isTxInvalid(MessagingRequestContext, type, incommingLRA, false, progress)) {
                    // isTxInvalid will have called abortWith (thus aborting the rest of the filter chain)
                    return; // any previous actions (the leave request) will be reported via the response filter
                }

                lraId = null; // must not run with any context

                break;
            case NOT_SUPPORTED:
                suspendedLRA = incommingLRA;
                lraId = null; // must not run with any context

                break;
            case NESTED:
                // FALLTHRU
            case REQUIRED:
                if (incommingLRA != null) {
                    if (type == NESTED) {
                        headers.putSingle(LRA_HTTP_PARENT_CONTEXT_HEADER, incommingLRA.toASCIIString());

                        // if there is an LRA present nest a new LRA under it
                        suspendedLRA = incommingLRA;

                        if (progress == null) {
                            progress = new ArrayList<>();
                        }

                        newLRA = lraId = startLRA(MessagingRequestContext, incommingLRA, method, timeout, progress);

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
                    newLRA = lraId = startLRA(MessagingRequestContext, null, method, timeout, progress);

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
                newLRA = lraId = startLRA(MessagingRequestContext,null, method, timeout, progress);

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
                MessagingRequestContext.setProperty(SUSPENDED_LRA_PROP, suspendedLRA);
            }

            return; // non transactional
        }

        if (!isLongRunning) {
            MessagingRequestContext.setProperty(TERMINAL_LRA_PROP, lraId);
        }

        // store state with the current thread. TODO for the async version use MessagingRequestContext.setProperty("lra", Current.peek());
        Current.updateLRAContext(lraId, headers); // make the current LRA available to the called method

        if (newLRA != null) {
            if (suspendedLRA != null) {
                MessagingRequestContext.setProperty(SUSPENDED_LRA_PROP, incommingLRA);
            }

            MessagingRequestContext.setProperty(NEW_LRA_PROP, newLRA);
        }

        Current.push(lraId);

        try {
            lraClient.setCurrentLRA(lraId); // make the current LRA available to the called method
        } catch (Exception e) {
            // should not happen since lraId has already been validated
            // (perhaps we should not use the client API to set the context)
            abortWith(MessagingRequestContext, lraId.toASCIIString(),
                    Response.Status.BAD_REQUEST.getStatusCode(),
                    e.getMessage(),
                    progress);
            return; // any previous actions (such as leave and start requests) will be reported via the response filter
        }

        // TODO make sure it is possible to do compensations inside a new LRA
        if (!endAnnotation) { // don't enlist for methods marked with Compensate, Complete or Leave
            URI baseUri = MessagingRequestContext.getUriInfo().getBaseUri();

            Map<String, String> terminateURIs = getTerminationUris(resourceInfo.getResourceClass(), MessagingRequestContext.getUriInfo(), timeout);
            String timeLimitStr = terminateURIs.get(TIMELIMIT_PARAM_NAME);
            long timeLimit = timeLimitStr == null ? DEFAULT_TIMEOUT_MILLIS : Long.parseLong(timeLimitStr);

//            LRAParticipant participant = lraParticipantRegistry != null ?
//                    lraParticipantRegistry.getParticipant(resourceInfo.getResourceClass().getName()) : null;

//            if (terminateURIs.containsKey("Link") || participant != null) {
            if (terminateURIs.containsKey("Link") ) {
                try {
//                    if (participant != null) {
//                        participant.augmentTerminationURIs(terminateURIs, baseUri);
//                    }

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
                    abortWith(MessagingRequestContext, lraId.toASCIIString(),
                            e.getResponse().getStatus(),
                            String.format("%s: %s", e.getClass().getSimpleName(), e.getMessage()), progress);
                    // the failure plus any previous actions (such as leave and start requests) will be reported via the response filter
                } catch (URISyntaxException e) {
                    progress = updateProgress(progress, ProgressStep.JoinFailed, e.getMessage()); // one or more of the participant end points was invalid
                    abortWith(MessagingRequestContext, lraId.toASCIIString(),
                            Response.Status.BAD_REQUEST.getStatusCode(),
                            String.format("%s %s: %s", lraId, e.getClass().getSimpleName(), e.getMessage()), progress);
                    // the failure plus any previous actions (such as leave and start requests) will be reported via the response filter
                } catch (ProcessingException e) {
                    progress = updateProgress(progress, ProgressStep.JoinFailed, e.getMessage()); // a remote coordinator was unavailable
                    abortWith(MessagingRequestContext, lraId.toASCIIString(),
                            Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),
                            String.format("%s %s,", e.getClass().getSimpleName(), e.getMessage()), progress);
                    // the failure plus any previous actions (such as leave and start requests) will be reported via the response filter
                }
            } else if (requiresActiveLRA && lraClient.getStatus(lraId) != LRAStatus.Active) {
                Current.clearContext(headers);
                Current.pop(lraId);
                MessagingRequestContext.removeProperty(SUSPENDED_LRA_PROP);

                if (type == MANDATORY) {
                    abortWith(MessagingRequestContext, lraId.toASCIIString(),
                            Response.Status.PRECONDITION_FAILED.getStatusCode(),
                            "LRA should have been active: ", progress);
                    // any previous actions (such as leave and start requests) will be reported via the response filter
                }
            }
        }
    }

    public void filter(MessagingRequestContext requestContext, ContainerResponseContext responseContext) {
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
                    LRALogger.i18NLogger.warn_lraFilterContainerRequest("ProcessingException: " + e.getMessage(),
                            method.getDeclaringClass().getName() + "#" + method.getName(), current.toASCIIString());

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
                    NarayanaLRAClient.isAsyncCompletion(resourceInfo.getResourceMethod())) {
                LRALogger.i18NLogger.warn_lraParticipantqForAsync(
                        resourceInfo.getResourceMethod().getDeclaringClass().getName(),
                        resourceInfo.getResourceMethod().getName(),
                        Response.Status.ACCEPTED.getStatusCode(),
                        Response.Status.OK.getStatusCode());
            }

            /*
             * report any failed steps (ie if progress contains any failures) to the caller.
             * If either filter encountered a failure they may have completed partial actions and
             * we need tell the caller which steps failed and which ones succeeded. We use a
             * different warning code for each scenario:
             */
            if (progress != null) {
                String failureMessage =  processLRAOperationFailures(progress);

                if (failureMessage != null) {
                    LRALogger.logger.warn(LRALogger.i18NLogger.warn_LRAStatusInDoubt(failureMessage));

                    // the actual failure(s) will also have been added to the i18NLogger logs at the time they occured
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

    private boolean isJaxRsCancel(MessagingRequestContext requestContext, ContainerResponseContext responseContext) {
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

    // the request filter may perform multiple and in failure scenarios the LRA may be left in an ambiguous state:
    // the following structure is used to track progress so that such failures can be reported in the response
    // filter processing
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

    // list of steps (both successful and unsuccesful) performed so far by the request and response filter
    // and is used for error reporting
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

    // convert the list of steps carried out by the filters into a warning message
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

        /*
         * return a string which encodes the result:
         * <major code>-<failed op codes>-<successful op codes>: <details of failed ops> (<details of successful ops>)
         *
         * where
         *
         * <major code>: corresponds to the id of the message in the logs
         * <failed op codes>: each digit corresponds to the enum ordinal calue of the ProgressStep enum value that was successful
         * <successful op codes>: each digit corresponds to the enum ordinal value of the ProgressStep enum value that failed
         * <details of failed ops>: comma separated list of failed operation details "<op name> (<exception message>)"
         * <details of successful ops>: comma separated list of successful operation details "<op name> (<op description>)"
         */

        if (badOps.length() != 0) {
            return LRALogger.i18NLogger.warn_LRAStatusInDoubt(String.format("%s: %s (%s)", code, badOps, goodOps));
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
    private void abortWith(MessagingRequestContext MessagingRequestContext, String lraId, int statusCode,
                           String message, Collection<Progress> reasons) {
        // the response filter will set the entity body
        MessagingRequestContext.abortWith(Response.status(statusCode).build());
        // make the reason for the failure available to the response filter
        MessagingRequestContext.setProperty(ABORT_WITH_PROP, reasons);

        Method method = resourceInfo.getResourceMethod();
        LRALogger.i18NLogger.warn_lraFilterContainerRequest(message,
                method.getDeclaringClass().getName() + "#" + method.getName(),
                lraId == null ? "context" : lraId);
    }

    private URI toURI(String uri) throws URISyntaxException {
        return uri == null ? null : new URI(uri);
    }

    @SuppressWarnings("unchecked")
    public static <T extends Collection<?>> T cast(Object obj) {
        return (T) obj;
    }

    private URI startLRA(MessagingRequestContext MessagingRequestContext, URI parentLRA, Method method, Long timeout,
                         ArrayList<Progress> progress) {
        // timeout should already have been converted to milliseconds
        String clientId = method.getDeclaringClass().getName() + "#" + method.getName();

        try {
            URI lra = lraClient.startLRA(parentLRA, clientId, timeout, ChronoUnit.MILLIS, false);
            updateProgress(progress, ProgressStep.Started, null);
            return lra;
        } catch (WebApplicationException e) {
            updateProgress(progress, ProgressStep.StartFailed, e.getMessage());

            abortWith(MessagingRequestContext, null,
                    e.getResponse().getStatus(),
                    String.format("%s %s", e.getClass().getSimpleName(), e.getMessage()),
                    progress);
        } catch (ProcessingException e) {
            updateProgress(progress, ProgressStep.StartFailed, e.getMessage());

            abortWith(MessagingRequestContext, null,
                    Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),
                    String.format("%s %s", e.getClass().getSimpleName(), e.getMessage()),
                    progress);
        }
        return null;
    }



    public static Map<String, String> getTerminationUris(Class<?> compensatorClass, UriInfo uriInfo, Long timeout) {
        Map<String, String> incomingchannels = new HashMap<>();
        final boolean[] asyncTermination = {false};
        URI baseUri = uriInfo.getBaseUri();

        /*
         * Calculate which path to prepend to the LRA participant methods. If there is more than one matching URI
         * then the second matched URI comes from either the class level Path annotation or from a sub-resource locator.
         * In both cases the second matched URI can be used as a prefix for the LRA participant URIs:
         */
        List<String> matchedURIs = uriInfo.getMatchedURIs();
        int matchedURI = (matchedURIs.size() > 1 ? 1 : 0);
        final String uriPrefix = baseUri + matchedURIs.get(matchedURI); //todo euqates to http://localhost:8080/aqinventorymessaging

        String timeoutValue = timeout != null ? Long.toString(timeout) : "0";

        Arrays.stream(compensatorClass.getMethods()).forEach(method -> {
//            Path incomingAnnotation = method.getAnnotation(Path.class);
            Incoming incomingAnnotation = method.getAnnotation(Incoming.class);

            if (incomingAnnotation != null) {

                if (checkMethod(incomingchannels, method, COMPENSATE, incomingAnnotation,
                        method.getAnnotation(Compensate.class), uriPrefix) != 0) {
                    incomingchannels.put(TIMELIMIT_PARAM_NAME, timeoutValue);

                    if (isAsyncCompletion(method)) {
                        asyncTermination[0] = true;
                    }
                }

                if (checkMethod(incomingchannels, method, COMPLETE, incomingAnnotation,
                        method.getAnnotation(Complete.class), uriPrefix) != 0) {
                    incomingchannels.put(TIMELIMIT_PARAM_NAME, timeoutValue);

                    if (isAsyncCompletion(method)) {
                        asyncTermination[0] = true;
                    }
                }
                checkMethod(incomingchannels, method, STATUS, incomingAnnotation,
                        method.getAnnotation(Status.class), uriPrefix);
                checkMethod(incomingchannels, method, FORGET, incomingAnnotation,
                        method.getAnnotation(Forget.class), uriPrefix);

                checkMethod(incomingchannels, method, LEAVE, incomingAnnotation, method.getAnnotation(Leave.class), uriPrefix);
                checkMethod(incomingchannels, method, AFTER, incomingAnnotation, method.getAnnotation(AfterLRA.class), uriPrefix);
            }
        });

        if (asyncTermination[0] && !incomingchannels.containsKey(STATUS) && !incomingchannels.containsKey(FORGET)) {
            LRALogger.i18NLogger.error_asyncTerminationBeanMissStatusAndForget(compensatorClass);

            throw new WebApplicationException(
                    Response.status(BAD_REQUEST)
                            .entity("LRA participant class with asynchronous termination but no @Status or @Forget annotations")
                            .build());
        }

        StringBuilder linkHeaderValue = new StringBuilder();

        if (incomingchannels.size() != 0) {
            incomingchannels.forEach((k, v) -> makeLink(linkHeaderValue, null, k, v));
            incomingchannels.put(LINK_TEXT, linkHeaderValue.toString());
        }

        return incomingchannels;
    }


    private static final String LINK_TEXT = "Link";

    private static StringBuilder makeLink(StringBuilder b, String uriPrefix, String key, String value) {

        if (value == null) {
            return b;
        }

        String terminationUri = uriPrefix == null ? value : String.format("%s%s", uriPrefix, value);
        Link link =  Link.fromUri(terminationUri).title(key + " URI").rel(key).type(MediaType.TEXT_PLAIN).build();

        if (b.length() != 0) {
            b.append(',');
        }

        return b.append(link);
    }

    private static int checkMethod(Map<String, String> incomingChannels,
                                   Method method, String rel,
//                                   Path incomingAnnotation,
                                   Incoming incomingAnnotation,
                                   Annotation annotationClass,
                                   String uriPrefix) {
        /*
         * If the annotationClass is null the requested participant annotation is not present,
         * but we also need to check for conformance with the interoperability spec,
         * ie look for incomingChannels of the form:
         * `<participant URL>/compensate`
         * `<participant URL>/complete`
         * etc
         */
        if (annotationClass == null) {
            // TODO support standard compenators with: && !incomingAnnotation.value().endsWith(rel)) {
            // ie ones that do not use the @Compensate annotation
            return 0;
        }

        // search for a matching JAX-RS method
        for (Annotation annotation : method.getDeclaredAnnotations()) {
            String name = annotation.annotationType().getName();
                String channelValue = incomingAnnotation.value();
                System.out.println("ServerLRAMessagingFilter.checkMethod channelValue:" + channelValue);
//                pathValue = pathValue.startsWith("/") ? pathValue : "/" + pathValue;
                String url = String.format("%s%s?%s=%s", uriPrefix, channelValue, LRAConstants.HTTP_METHOD_NAME, name);

                        //todo add the channel properties to this url...
                incomingChannels.put(rel, "messaging://" + channelValue + "/" + ""); //todo would add the channel info here -todo optimize to not send it everytime
//                incomingChannels.put(rel, url);
        }
        return 1;
    }

    public static boolean isAsyncCompletion(Method method) {
        if (method.isAnnotationPresent(Complete.class) || method.isAnnotationPresent(Compensate.class)) {
            for (Annotation[] ann : method.getParameterAnnotations()) {
                for (Annotation an : ann) {
                    if (Suspended.class.isAssignableFrom(an.annotationType())) {
                        LRALogger.logger.warn("JAX-RS @Suspended annotation is untested");
                        return true;
                    }
                }
            }
        }

        return false;
    }


}
