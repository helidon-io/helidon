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

import io.helidon.lra.Constants;
import io.helidon.lra.rest.AnnotationResolver;
import io.helidon.microprofile.messaging.MessagingMethod;
import io.helidon.lra.rest.LRAClient;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.lra.annotation.*;
import org.eclipse.microprofile.lra.annotation.ws.rs.LRA;
import org.eclipse.microprofile.lra.annotation.ws.rs.Leave;

import javax.ws.rs.*;
import javax.ws.rs.core.*;
import javax.ws.rs.ext.Provider;
import java.io.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.*;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_CONTEXT_HEADER;
import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.Type.MANDATORY;
import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.Type.NESTED;

import io.helidon.lra.rest.Current;
import org.eclipse.microprofile.lra.annotation.Compensate;
import org.eclipse.microprofile.lra.annotation.Complete;
import org.eclipse.microprofile.lra.annotation.Forget;
import org.eclipse.microprofile.lra.annotation.LRAStatus;
import org.eclipse.microprofile.lra.annotation.Status;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Outgoing;

import static io.helidon.lra.rest.LRAConstants.AFTER;
import static io.helidon.lra.rest.LRAConstants.COMPENSATE;
import static io.helidon.lra.rest.LRAConstants.COMPLETE;
import static io.helidon.lra.rest.LRAConstants.FORGET;
import static io.helidon.lra.rest.LRAConstants.LEAVE;
import static io.helidon.lra.rest.LRAConstants.STATUS;
import static io.helidon.lra.rest.LRAConstants.TIMELIMIT_PARAM_NAME;
import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_PARENT_CONTEXT_HEADER;
import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_RECOVERY_HEADER;

@Provider
public class ServerLRAMessagingFilter {
    private static final Logger LOGGER = Logger.getLogger(ServerLRAMessagingFilter.class.getName());
    private static final String CANCEL_ON_FAMILY_PROP = "CancelOnFamily";
    private static final String CANCEL_ON_PROP = "CancelOn";
    private static final String TERMINAL_LRA_PROP = "terminateLRA";
    private static final String SUSPENDED_LRA_PROP = "suspendLRA";
    private static final String NEW_LRA_PROP = "newLRA";
    private static final String ABORT_WITH_PROP = "abortWith";
    private static final String MP_MESSAGING_INCOMING_PREFIX = "mp.messaging.incoming.";
    private static final String MP_MESSAGING_OUTGOING_PREFIX = "mp.messaging.outgoing.";
    private static final String LINK_TEXT = "Link";
    private static final Pattern START_END_QUOTES_PATTERN = Pattern.compile("^\"|\"$");
    private static final long DEFAULT_TIMEOUT_MILLIS = 0L;
    private static boolean isMessagingURLFactoryRegistered = false;
    private final Method method;
    private MessagingRequestContext messagingRequestContext;
    private boolean isCancel;
    private final LRAClient lraClient;
    private static Config config = null;

    public ServerLRAMessagingFilter(Method method) {
        lraClient = new LRAClient();
        this.method = method;
        if (!isMessagingURLFactoryRegistered) {
            URL.setURLStreamHandlerFactory(protocol -> "messaging".equals(protocol) ? new URLStreamHandler() {
                protected URLConnection openConnection(URL url) {
                    return new URLConnection(url) {
                        public void connect() {
                        }
                    };
                }
            } : null);
            isMessagingURLFactoryRegistered = true;
        }
        if (config == null) config = ConfigProvider.getConfig();
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

    /**
     * The @Incoming, @Outgoing, and @LRA annotations of this method are processed
     * as are the other LRA related methods in it's declaring class.
     *
     * @param message Appropriate LRA properties are processed on the message in order to import/infect and propagate LRA
     */
    public void beforeMethodInvocation(Message message) {
        messagingRequestContext = new MessagingRequestContext(message);
        MultivaluedMap<String, String> headers = messagingRequestContext.getHeaders();
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
                messagingRequestContext.setProperty(CANCEL_ON_FAMILY_PROP, cancel0nFamily);
            }
            if (cancel0n.length != 0) {
                messagingRequestContext.setProperty(CANCEL_ON_PROP, cancel0n);
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

                abortWith(messagingRequestContext, null, Response.Status.PRECONDITION_FAILED.getStatusCode(),
                        msg, null);
                return;
            }
            if (AnnotationResolver.isAnnotationPresent(Leave.class, method)) {
                Map<String, String> terminateURIs = getTerminationUris(
                        method.getDeclaringClass(), timeout);
                String compensatorId = terminateURIs.get("Link");
                if (compensatorId == null) {
                    abortWith(messagingRequestContext, incommingLRA.toASCIIString(),
                            Response.Status.BAD_REQUEST.getStatusCode(),
                            "Missing complete or compensate annotations", null);
                    return;
                }
                try {
                    lraClient.leaveLRA(incommingLRA, compensatorId);
                    progress = updateProgress(progress, ProgressStep.Left, null);
                } catch (WebApplicationException e) {
                    progress = updateProgress(progress, ProgressStep.LeaveFailed, e.getMessage());
                    abortWith(messagingRequestContext, incommingLRA.toASCIIString(),
                            e.getResponse().getStatus(),
                            e.getMessage(), progress);
                    return;
                } catch (ProcessingException e) {
                    progress = updateProgress(progress, ProgressStep.LeaveFailed, e.getMessage());
                    abortWith(messagingRequestContext, incommingLRA.toASCIIString(),
                            Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),
                            e.getMessage(), progress);
                    return; // the error will be handled or reported via the response filter
                }
            }
        }
        if (type == null) {
            if (!endAnnotation) {
                Current.clearContext(headers);
            }
            if (incommingLRA != null) {
                Current.push(incommingLRA);
                messagingRequestContext.setProperty(SUSPENDED_LRA_PROP, incommingLRA);
            }
            return;
        }
        if (!headers.containsKey(LRA_HTTP_CONTEXT_HEADER)) {
            Object lraContext = messagingRequestContext.getProperty(LRA_HTTP_CONTEXT_HEADER);
            if (lraContext != null) {
                incommingLRA = (URI) lraContext;
            }
        }
        if (endAnnotation && incommingLRA == null) {
            return;
        }
        if (incommingLRA != null) {
            try {
                headers.putSingle(LRA_HTTP_PARENT_CONTEXT_HEADER, Current.getFirstParent(incommingLRA));
            } catch (UnsupportedEncodingException e) {
                abortWith(messagingRequestContext, incommingLRA.toASCIIString(),
                        Response.Status.PRECONDITION_FAILED.getStatusCode(),
                        String.format("incoming LRA %s contains an invalid parent: %s", incommingLRA, e.getMessage()),
                        progress);
                return;
            }
        }

        switch (type) {
            case MANDATORY:
                if (isTxInvalid(messagingRequestContext, type, incommingLRA, true, progress)) {
                    return;
                }
                lraId = incommingLRA;
                requiresActiveLRA = true;
                break;
            case NEVER:
                if (isTxInvalid(messagingRequestContext, type, incommingLRA, false, progress)) {
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
                        newLRA = lraId = startLRA(messagingRequestContext, incommingLRA, method, timeout, progress);
                        if (newLRA == null) {
                            return;
                        }
                    } else {
                        lraId = incommingLRA;
                        requiresActiveLRA = true;
                    }

                } else {
                    if (progress == null) {
                        progress = new ArrayList<>();
                    }
                    newLRA = lraId = startLRA(messagingRequestContext, null, method, timeout, progress);
                    if (newLRA == null) {
                        return;
                    }
                }
                break;
            case REQUIRES_NEW:
                suspendedLRA = incommingLRA;
                if (progress == null) {
                    progress = new ArrayList<>();
                }
                newLRA = lraId = startLRA(messagingRequestContext, null, method, timeout, progress);
                if (newLRA == null) {
                    return;
                }
                break;
            case SUPPORTS:
                lraId = incommingLRA;
                break;
            default:
                lraId = incommingLRA;
        }
        if (lraId == null) {
            Current.clearContext(headers);
            if (suspendedLRA != null) {
                messagingRequestContext.setProperty(SUSPENDED_LRA_PROP, suspendedLRA);
            }
            return;
        }
        if (!isLongRunning) {
            messagingRequestContext.setProperty(TERMINAL_LRA_PROP, lraId);
        }
        Current.updateLRAContext(lraId, headers);
        if (newLRA != null) {
            if (suspendedLRA != null) {
                messagingRequestContext.setProperty(SUSPENDED_LRA_PROP, incommingLRA);
            }

            messagingRequestContext.setProperty(NEW_LRA_PROP, newLRA);
        }
        Current.push(lraId);
        try {
            lraClient.setCurrentLRA(lraId);
        } catch (Exception e) {
            abortWith(messagingRequestContext, lraId.toASCIIString(),
                    Response.Status.BAD_REQUEST.getStatusCode(),
                    e.getMessage(),
                    progress);
            return;
        }
        if (!endAnnotation) {
            Map<String, String> terminateURIs = getTerminationUris(method.getDeclaringClass(), timeout);
            String timeLimitStr = terminateURIs.get(TIMELIMIT_PARAM_NAME);
            long timeLimit = timeLimitStr == null ? DEFAULT_TIMEOUT_MILLIS : Long.parseLong(timeLimitStr);
            if (terminateURIs.containsKey("Link")) {
                try {
                    recoveryUrl = lraClient.joinLRA(lraId, timeLimit,
                            toURI(terminateURIs.get(COMPENSATE)),
                            toURI(terminateURIs.get(COMPLETE)),
                            toURI(terminateURIs.get(FORGET)),
                            toURI(terminateURIs.get(LEAVE)),
                            toURI(terminateURIs.get(AFTER)),
                            toURI(terminateURIs.get(STATUS)),
                            "compensatorData"); //todo compensatorData
                    progress = updateProgress(progress, ProgressStep.Joined, null);
                    headers.putSingle(LRA_HTTP_RECOVERY_HEADER,
                            START_END_QUOTES_PATTERN.matcher(recoveryUrl.toASCIIString()).replaceAll(""));
                } catch (WebApplicationException e) {
                    progress = updateProgress(progress, ProgressStep.JoinFailed, e.getMessage());
                    abortWith(messagingRequestContext, lraId.toASCIIString(),
                            e.getResponse().getStatus(),
                            String.format("%s: %s", e.getClass().getSimpleName(), e.getMessage()), progress);
                    // the failure plus any previous actions (such as leave and start requests) will be reported via the response filter
                } catch (URISyntaxException e) {
                    progress = updateProgress(progress, ProgressStep.JoinFailed, e.getMessage()); // one or more of the participant end points was invalid
                    abortWith(messagingRequestContext, lraId.toASCIIString(),
                            Response.Status.BAD_REQUEST.getStatusCode(),
                            String.format("%s %s: %s", lraId, e.getClass().getSimpleName(), e.getMessage()), progress);
                    // the failure plus any previous actions (such as leave and start requests) will be reported via the response filter
                } catch (ProcessingException e) {
                    progress = updateProgress(progress, ProgressStep.JoinFailed, e.getMessage()); // a remote coordinator was unavailable
                    abortWith(messagingRequestContext, lraId.toASCIIString(),
                            Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),
                            String.format("%s %s,", e.getClass().getSimpleName(), e.getMessage()), progress);
                    // the failure plus any previous actions (such as leave and start requests) will be reported via the response filter
                }
            } else if (requiresActiveLRA && lraClient.getStatus(lraId) != LRAStatus.Active) {
                Current.clearContext(headers);
                Current.pop(lraId);
                messagingRequestContext.removeProperty(SUSPENDED_LRA_PROP);

                if (type == MANDATORY) {
                    abortWith(messagingRequestContext, lraId.toASCIIString(),
                            Response.Status.PRECONDITION_FAILED.getStatusCode(),
                            "LRA should have been active: ", progress);
                    // any previous actions (such as leave and start requests) will be reported via the response filter
                }
            }
        }
    }

    public void setCancel() {
        isCancel = true;
    }

    private boolean isCancel() {
        return isCancel;
    }

    public void afterMethodInvocation(MessagingMethod method, Object message, boolean isError) {
        if (message != null && true) { //(message instanceof OutgoingJmsMessage || message instanceof KafkaMessage)) { //todo OutgoingJmsMessage
            if (method.getMethod().getAnnotation(Complete.class) != null) {
                LOGGER.info("Complete reply is " + "COMPLETESUCCESS");
                messagingRequestContext.addMessageProperty("HELIDONLRAOPERATION", isError ? "COMPLETEFAIL" : "COMPLETESUCCESS");
            } else if (method.getMethod().getAnnotation(Compensate.class) != null) {
                LOGGER.info("Compensate reply is " + "COMPENSATESUCCESS");
                messagingRequestContext.addMessageProperty("HELIDONLRAOPERATION", isError ? "COMPENSATEFAIL" : "COMPENSATESUCCESS");
            } else if (method.getMethod().getAnnotation(AfterLRA.class) != null) {
                LOGGER.info("AfterLRA reply is " + "AFTERLRASUCCESS");
                messagingRequestContext.addMessageProperty("HELIDONLRAOPERATION", isError ? "AFTERLRAFAIL" : "AFTERLRASUCCESS");
            } else if (method.getMethod().getAnnotation(Forget.class) != null) {
                LOGGER.info("AfterLRA reply is " + "FORGETSUCCESS");
                messagingRequestContext.addMessageProperty("HELIDONLRAOPERATION", isError ? "FORGETFAIL" : "FORGETSUCCESS");
            } else if (method.getMethod().getAnnotation(Status.class) != null) {
                LOGGER.info("Status reply is " + "STATUSSUCCESS");
                messagingRequestContext.addMessageProperty("HELIDONLRAOPERATION", isError ? "STATUSFAIL" : "STATUSSUCCESS");
            }
            messagingRequestContext.setMessageProperties(message);
        } else {
            LOGGER.warning("Unexpected object/message type message:" + message);
        }
        ArrayList<Progress> progress = cast(messagingRequestContext.getProperty(ABORT_WITH_PROP));
        Object suspendedLRA = messagingRequestContext.getProperty(SUSPENDED_LRA_PROP);
        URI current = Current.peek();
        URI toClose = (URI) messagingRequestContext.getProperty(TERMINAL_LRA_PROP);
        boolean isCancel = isCancel();
        try {
            if (current != null && isCancel) {
                try {
                    if (progress == null || progressDoesNotContain(progress, ProgressStep.StartFailed)) {
                        lraClient.cancelLRA(current);
                        progress = updateProgress(progress, ProgressStep.Ended, null);
                    }
                } catch (NotFoundException ignore) {
                    progress = updateProgress(progress, ProgressStep.Ended, null);
                } catch (WebApplicationException e) {
                    progress = updateProgress(progress, ProgressStep.CancelFailed, e.getMessage());
                } catch (ProcessingException e) {
                    LOGGER.info("ProcessingException: " + e.getMessage() + " " + this.method.getDeclaringClass().getName() + "#" + this.method.getName() + " " + current.toASCIIString());
                    progress = updateProgress(progress, ProgressStep.CancelFailed, e.getMessage());
                    toClose = null;
                } finally {
                    if (current.toASCIIString().equals(
                            Current.getLast(messagingRequestContext.getHeaders().get(LRA_HTTP_CONTEXT_HEADER)))) {
                        messagingRequestContext.removeHeader(LRA_HTTP_CONTEXT_HEADER);
                    }
                    if (toClose != null && toClose.toASCIIString().equals(current.toASCIIString())) {
                        toClose = null;
                    }
                }
            }
            if (toClose != null) {
                try {
                    if (progress == null || progressDoesNotContain(progress, ProgressStep.StartFailed)) {
                        if (isCancel) {
                            LOGGER.info("Cancel toClose:" + toClose);
                            lraClient.cancelLRA(toClose);
                        } else {
                            LOGGER.info("Close toClose:" + toClose);
                            lraClient.closeLRA(toClose);
                        }
                        progress = updateProgress(progress, ProgressStep.Ended, null);
                    }
                } catch (NotFoundException ignore) {
                    progress = updateProgress(progress, ProgressStep.Ended, null);
                } catch (WebApplicationException | ProcessingException e) {
                    progress = updateProgress(progress,
                            isCancel ? ProgressStep.CancelFailed : ProgressStep.CloseFailed, e.getMessage());
                } finally {
                    messagingRequestContext.removeHeader(LRA_HTTP_CONTEXT_HEADER);
                    if (toClose.toASCIIString().equals(
                            Current.getLast(messagingRequestContext.getHeaders().get(LRA_HTTP_CONTEXT_HEADER)))) {
                        messagingRequestContext.removeHeader(LRA_HTTP_CONTEXT_HEADER);
                    }
                }
            }
            if (progress != null) {
                String failureMessage = processLRAOperationFailures(progress);
                if (failureMessage != null) {
                    LOGGER.warning(failureMessage);
                    //todo set failure on response message - confirm if afterMethodInvocation is called even if onMethodInvocationFailure occurs/is called
                }
            }
        } finally {
            if (suspendedLRA != null) {
                Current.push((URI) suspendedLRA);
            }
//   todo         Current.updateLRAContext(responseContext);
            Current.popAll();
        }
    }


    private enum ProgressStep {
        Left("leave succeeded"),
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
            return String.format("%s: %s (%s)", code, badOps, goodOps);
        }

        return null;
    }

    private boolean progressDoesNotContain(ArrayList<Progress> progress, ProgressStep step) {
        return progress.stream().noneMatch(p -> p.progress == step);
    }

    private ArrayList<Progress> updateProgress(ArrayList<Progress> progress, ProgressStep step, String reason) {
        if (progress == null) {
            progress = new ArrayList<>();
        }
        progress.add(new Progress(step, reason));
        return progress;
    }

    private void abortWith(MessagingRequestContext MessagingRequestContext, String lraId, int statusCode,
                           String message, Collection<Progress> reasons) {
        MessagingRequestContext.abortWith(Response.status(statusCode).build());
        MessagingRequestContext.setProperty(ABORT_WITH_PROP, reasons);
        LOGGER.info(message + " " + method.getDeclaringClass().getName() + "#" + method.getName() + " lraId:" + lraId);
    }

    private URI toURI(String uri) throws URISyntaxException {
        return uri == null ? null : new URI(uri);
    }

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


    public static Map<String, String> getTerminationUris(Class<?> classWithLRAAnnotations, Long timeout) {
        Map<String, String> annotationToURLMap = new HashMap<>();
        final boolean[] asyncTermination = {false};
        String timeoutValue = timeout != null ? Long.toString(timeout) : "0";
        Arrays.stream(classWithLRAAnnotations.getMethods()).forEach(method -> {
            if (checkMethod(annotationToURLMap, method, COMPENSATE, method.getAnnotation(Compensate.class)) != 0) {
                annotationToURLMap.put(TIMELIMIT_PARAM_NAME, timeoutValue);
            }
            if (checkMethod(annotationToURLMap, method, COMPLETE, method.getAnnotation(Complete.class)) != 0) {
                annotationToURLMap.put(TIMELIMIT_PARAM_NAME, timeoutValue);
            }
            checkMethod(annotationToURLMap, method, STATUS, method.getAnnotation(Status.class));
            checkMethod(annotationToURLMap, method, FORGET, method.getAnnotation(Forget.class));
            checkMethod(annotationToURLMap, method, LEAVE, method.getAnnotation(Leave.class));
            checkMethod(annotationToURLMap, method, AFTER, method.getAnnotation(AfterLRA.class));
        }); //todo validate for case where there is LRA annotation but no others
        if (asyncTermination[0] && !annotationToURLMap.containsKey(STATUS) && !annotationToURLMap.containsKey(FORGET)) {
            LOGGER.info("error_asyncTerminationBeanMissStatusAndForget:" + classWithLRAAnnotations);

            throw new WebApplicationException(
                    Response.status(BAD_REQUEST)
                            .entity("LRA participant class with asynchronous termination but no @Status or @Forget annotations")
                            .build());
        }
        StringBuilder linkHeaderValue = new StringBuilder();
        if (annotationToURLMap.size() != 0) {
            annotationToURLMap.forEach((k, v) -> makeLink(linkHeaderValue, null, k, v));
            annotationToURLMap.put(LINK_TEXT, linkHeaderValue.toString());
        }
        return annotationToURLMap;
    }


    private static StringBuilder makeLink(StringBuilder b, String uriPrefix, String key, String value) {

        if (value == null) {
            return b;
        }

        String terminationUri = uriPrefix == null ? value : String.format("%s%s", uriPrefix, value);
        Link link = Link.fromUri(terminationUri).title(key + " URI").rel(key).type(MediaType.TEXT_PLAIN).build();

        if (b.length() != 0) {
            b.append(',');
        }

        return b.append(link);
    }

    /**
     * @param annotationToURLMap
     * @param method
     * @param lraRelatedAnnotationName The LRA related annotation name such as COMPLETE, COMPENSATE, STATUS, AFTER, LEAVE, and FORGET
     * @param annotationClass
     * @return 0 if annotation
     */
    private static int checkMethod(Map<String, String> annotationToURLMap,
                                   Method method, String lraRelatedAnnotationName,
                                   Annotation annotationClass) {
        if (annotationClass == null) return 0;
        String channelValue, outgoingchannelValue, url = "";
        channelValue = method.getAnnotation(Incoming.class).value();
        outgoingchannelValue = method.getAnnotation(Outgoing.class).value();
        String connector = config.getValue(MP_MESSAGING_INCOMING_PREFIX + channelValue + ".connector", String.class);
        //channel is for information/debug purposes only
        if (connector.equals("helidon-aq")) {
            url = String.format("%s%s?%s=%s&%s=%s&%s=%s", "messaging://", connector,
                    "channel", channelValue,
                    "destination", config.getValue(MP_MESSAGING_INCOMING_PREFIX + channelValue + ".destination", String.class),
                    "type", config.getValue(MP_MESSAGING_INCOMING_PREFIX + channelValue + ".type", String.class)
//                            "ispropagation", config.getValue(MP_MESSAGING_INCOMING_PREFIX + channelValue + ".destination", String.class)
                    //  this is determined by config for now if true than coordinator should send via topic instead of queue
            );
            annotationToURLMap.put(lraRelatedAnnotationName, url);
        } else if (connector.equals("helidon-kafka")) {
            //todo if outgoingchannelValue !=null
            LOGGER.info("config.getValue(MP_MESSAGING_OUTGOING_PREFIX + channelValue + \".topic\", String.class):" + config.getValue(MP_MESSAGING_OUTGOING_PREFIX + outgoingchannelValue + ".topic", String.class));
            LOGGER.info("config.getValue(MP_MESSAGING_OUTGOING_PREFIX + channelValue + \".topic\", String.class):" + config.getValue(MP_MESSAGING_OUTGOING_PREFIX + outgoingchannelValue + ".topic", String.class));
            LOGGER.info("config.getValue(MP_MESSAGING_OUTGOING_PREFIX + channelValue + \".topic\", String.class):" + config.getValue(MP_MESSAGING_OUTGOING_PREFIX + outgoingchannelValue + ".topic", String.class));
            LOGGER.info("config.getValue(MP_MESSAGING_OUTGOING_PREFIX + channelValue + \".topic\", String.class):" + config.getValue(MP_MESSAGING_OUTGOING_PREFIX + outgoingchannelValue + ".topic", String.class));
            LOGGER.info("config.getValue(MP_MESSAGING_OUTGOING_PREFIX + channelValue + \".topic\", String.class):" + config.getValue(MP_MESSAGING_OUTGOING_PREFIX + outgoingchannelValue + ".topic", String.class));
            LOGGER.info("config.getValue(MP_MESSAGING_OUTGOING_PREFIX + channelValue + \".topic\", String.class):" + config.getValue(MP_MESSAGING_OUTGOING_PREFIX + outgoingchannelValue + ".topic", String.class));
            LOGGER.info("config.getValue(MP_MESSAGING_OUTGOING_PREFIX + channelValue + \".topic\", String.class):" + config.getValue(MP_MESSAGING_OUTGOING_PREFIX + outgoingchannelValue + ".topic", String.class));
            LOGGER.info("config.getValue(MP_MESSAGING_OUTGOING_PREFIX + channelValue + \".topic\", String.class):" + config.getValue(MP_MESSAGING_OUTGOING_PREFIX + outgoingchannelValue + ".topic", String.class));
            url = String.format("%s%s?%s=%s&%s=%s&%s=%s&%s=%s&%s=%s", "messaging://", connector,
                    Constants.CHANNEL, channelValue,
                    Constants.BOOTSTRAPSERVERS, config.getValue(MP_MESSAGING_INCOMING_PREFIX + channelValue + ".bootstrap.servers", String.class),
                    Constants.TOPIC, config.getValue(MP_MESSAGING_INCOMING_PREFIX + channelValue + ".topic", String.class),
                    Constants.GROUPID, config.getOptionalValue(MP_MESSAGING_INCOMING_PREFIX + channelValue + ".group.id", String.class),
                    Constants.REPLYTOPIC, config.getValue(MP_MESSAGING_OUTGOING_PREFIX + outgoingchannelValue + ".topic", String.class)
//           todo                 "replyfromtopic", config.getValue(MP_MESSAGING_OUTGOING_PREFIX + channelValue + ".topic", String.class), //this comes from config
            );
            annotationToURLMap.put(lraRelatedAnnotationName, url);
        } else {
            LOGGER.warning("Unsupported connector annotated for LRA:" + connector);
        }
        LOGGER.fine("ServerLRAMessagingFilter.checkMethod lraRelatedAnnotationName:" + lraRelatedAnnotationName +
                " url:" + url);
        return 1;
    }


}
