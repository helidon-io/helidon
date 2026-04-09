/*
 * Copyright (c) 2022, 2026 Oracle and/or its affiliates.
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

package io.helidon.webserver.observe.log;

import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.Enumeration;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Filter;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.HttpMediaType;
import io.helidon.http.Status;
import io.helidon.http.media.EntityReader;
import io.helidon.http.media.EntityWriter;
import io.helidon.http.media.json.JsonSupport;
import io.helidon.json.JsonArray;
import io.helidon.json.JsonObject;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.SecureHandler;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.webserver.http1.Http1LoggingConnectionListener;

class LogService implements HttpService {
    private static final EntityWriter<JsonObject> WRITER = JsonSupport.serverResponseWriter();
    private static final EntityReader<JsonObject> READER = JsonSupport.serverRequestReader();
    private static final String DEFAULT_IDLE_STRING = "%\n";

    private final LogManager logManager;
    private final Logger root;
    private final Map<Object, Consumer<String>> listeners = Collections.synchronizedMap(new IdentityHashMap<>());
    private final AtomicBoolean logHandlingInitialized = new AtomicBoolean();
    private final boolean permitAll;
    private final boolean logStreamEnabled;
    private final Header logStreamMediaTypeHeader;
    private final Duration logStreamIdleTimeout;
    private final int logStreamQueueSize;
    private final String logStreamIdleString;
    private final Charset logStreamCharset;

    LogService(LogObserverConfig config) {
        this.logManager = LogManager.getLogManager();
        this.root = Logger.getLogger("");

        this.permitAll = config.permitAll();

        LogStreamConfig stream = config.stream();
        this.logStreamEnabled = stream.enabled();
        this.logStreamIdleTimeout = stream.idleMessageTimeout();
        this.logStreamQueueSize = stream.queueSize();
        this.logStreamIdleString = stream.idleString();

        HttpMediaType streamType = stream.contentType();
        this.logStreamMediaTypeHeader = HeaderValues.createCached(HeaderNames.CONTENT_TYPE,
                                                                  streamType.text());
        this.logStreamCharset = streamType.charset()
                .map(Charset::forName)
                .orElse(StandardCharsets.UTF_8);
    }

    @Override
    public void routing(HttpRules rules) {
        if (!permitAll) {
            rules.any(SecureHandler.authorize("observe"));
        }

        rules.get("/loggers")
                .get("/loggers", this::allLoggersHandler)
                .get("/loggers/{logger}", this::loggerHandler)
                .post("/loggers/{logger}", this::setLevelHandler)
                .delete("/loggers/{logger}", this::unsetLoggerHandler);

        if (logStreamEnabled) {
            rules.get("/", this::logHandler);
        }
    }

    @Override
    public void afterStop() {
        listeners.clear();
    }

    private void logHandler(ServerRequest req, ServerResponse res) throws Exception {
        initializeLogHandling();
        res.header(logStreamMediaTypeHeader);
        // do not cache more than x lines, to prevent OOM
        var q = new ArrayBlockingQueue<String>(logStreamQueueSize);

        // we do not care if the offer fails, it means the consumer is slow and will miss some lines
        listeners.put(req, q::offer);

        try (OutputStreamWriter out = new OutputStreamWriter(res.outputStream(), logStreamCharset)) {

            while (true) {
                try {
                    String poll = q.poll(logStreamIdleTimeout.toMillis(), TimeUnit.MILLISECONDS);
                    if (poll == null) {
                        // check if we are still alive
                        out.write(logStreamIdleString);
                        out.flush();
                    } else {
                        out.write(poll);
                    }
                } catch (InterruptedException e) {
                    // interrupted - we should finish
                    return;
                }
            }
        } finally {
            listeners.remove(req);
        }
    }

    private void initializeLogHandling() {
        if (logHandlingInitialized.compareAndSet(false, true)) {
            for (java.util.logging.Handler handler : root.getHandlers()) {
                handler.setFilter(new LogMessageFilter(handler.getFormatter(), handler.getFilter(), listeners));
                break;
            }
        }
    }

    private void unsetLoggerHandler(ServerRequest req, ServerResponse res) {
        String logger = req.path().pathParameters().first("logger").orElse("");
        Logger.getLogger(logger).setLevel(null);
        res.status(Status.NO_CONTENT_204).send();
    }

    private void setLevelHandler(ServerRequest req, ServerResponse res) {
        String logger = req.path().pathParameters().first("logger").orElse("");
        JsonObject requestJson = READER.read(JsonSupport.JSON_OBJECT_TYPE,
                                             req.content().inputStream(),
                                             req.headers());

        Level desiredLevel = Level.parse(requestJson.stringValue("level").orElse(null));
        Logger.getLogger(logger)
                .setLevel(desiredLevel);

        res.status(Status.NO_CONTENT_204).send();
    }

    private void loggerHandler(ServerRequest req, ServerResponse res) {
        String logger = req.path().pathParameters().first("logger").orElse("");
        JsonObject.Builder rootObject = JsonObject.builder();
        logger(rootObject, logger);

        write(req, res, rootObject.build());
    }

    private void allLoggersHandler(ServerRequest req, ServerResponse res) {
        JsonObject.Builder rootObject = JsonObject.builder();

        levels(rootObject);
        loggers(rootObject);

        write(req, res, rootObject.build());
    }

    private void loggers(JsonObject.Builder rootObject) {
        JsonObject.Builder loggersJson = JsonObject.builder();

        Enumeration<String> loggerNames = logManager.getLoggerNames();
        while (loggerNames.hasMoreElements()) {
            logger(loggersJson, loggerNames.nextElement());
        }

        rootObject.set("loggers", loggersJson.build());
    }

    private void logger(JsonObject.Builder parentJson, String loggerName) {
        Logger logger = Logger.getLogger(loggerName);

        Level configuredLevel = logger.getLevel();
        Level effectiveLevel = effectiveLevel(logger);

        JsonObject.Builder loggerJson = JsonObject.builder();
        if (configuredLevel != null) {
            loggerJson.set("configuredLevel", configuredLevel.getName());
        }
        loggerJson.set("level", effectiveLevel.getName());

        parentJson.set("".equals(loggerName) ? "ROOT" : loggerName, loggerJson.build());
    }

    private void levels(JsonObject.Builder rootObject) {
        rootObject.set("levels",
                       JsonArray.createStrings(List.of(Level.OFF.getName(),
                                                       Level.SEVERE.getName(),
                                                       Level.WARNING.getName(),
                                                       Level.INFO.getName(),
                                                       Level.FINE.getName(),
                                                       Level.FINER.getName(),
                                                       Level.FINEST.getName())));
    }

    private Level effectiveLevel(Logger logger) {
        Level level = logger.getLevel();
        if (level == null) {
            if (logger == root) {
                // we did not get a log level for parent, just assume the default
                return Level.INFO;
            }

            Logger parent = logger.getParent();
            if (parent == null) {
                return effectiveLevel(root);
            }

            return effectiveLevel(parent);
        }
        return level;
    }

    private void write(ServerRequest req, ServerResponse res, JsonObject json) {
        WRITER.write(JsonSupport.JSON_OBJECT_TYPE,
                     json,
                     res.outputStream(),
                     req.headers(),
                     res.headers());
    }

    private static class LogMessageFilter implements Filter {
        private final Formatter formatter;
        private final Filter filter;
        private final Map<Object, Consumer<String>> listeners;
        private final Set<LoggerAndLevel> excludedLoggers;

        LogMessageFilter(Formatter formatter, Filter filter, Map<Object, Consumer<String>> listeners) {
            this.formatter = formatter;
            this.filter = filter;
            this.listeners = listeners;

            excludedLoggers = Set.of(new LoggerAndLevel(Http1LoggingConnectionListener.class.getName() + ".send", Level.FINER),
                                     new LoggerAndLevel(Http1LoggingConnectionListener.class.getName() + ".send", Level.FINE));
        }

        @Override
        public boolean isLoggable(LogRecord record) {
            boolean result = filter == null || filter.isLoggable(record);

            if (result) {
                // now we have a problem that when tracing of HTTP is enabled, we create an infinite loop
                // so we will not send data related to network traffic itself over to listeners
                if (!excludedLoggers.contains(new LoggerAndLevel(record.getLoggerName(), record.getLevel()))) {
                    fire(formatter.format(record));
                }
            }

            return result;
        }

        private void fire(String message) {
            listeners.values().forEach(it -> {
                try {
                    it.accept(message);
                } catch (Exception ignored) {
                    // ignore exception, we cannot stop printing to stdout
                }
            });
        }
    }

    private record LoggerAndLevel(String logger, Level level) {
    }
}
