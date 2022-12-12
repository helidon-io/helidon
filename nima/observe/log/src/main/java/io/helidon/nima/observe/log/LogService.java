package io.helidon.nima.observe.log;

import java.io.OutputStreamWriter;
import java.util.Collections;
import java.util.Enumeration;
import java.util.IdentityHashMap;
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

import io.helidon.common.http.Http;
import io.helidon.config.Config;
import io.helidon.nima.http.media.EntityReader;
import io.helidon.nima.http.media.EntityWriter;
import io.helidon.nima.http.media.jsonp.JsonpMediaSupportProvider;
import io.helidon.nima.webserver.http.Handler;
import io.helidon.nima.webserver.http.HttpRules;
import io.helidon.nima.webserver.http.HttpService;
import io.helidon.nima.webserver.http.SecureHandler;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.nima.webserver.http.ServerResponse;
import io.helidon.nima.webserver.http1.Http1LoggingConnectionListener;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

public class LogService implements HttpService {
    private static final EntityWriter<JsonObject> WRITER = JsonpMediaSupportProvider.serverResponseWriter();
    private static final EntityReader<JsonObject> READER = JsonpMediaSupportProvider.serverRequestReader();
    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Map.of());
    private static final String IDLE_STRINGS = "%\n";

    private final LogManager logManager;
    private final Logger root;
    private final Map<Object, Consumer<String>> listeners = Collections.synchronizedMap(new IdentityHashMap<>());
    private final AtomicBoolean logHandlingInitialized = new AtomicBoolean();

    private LogService() {
        this.logManager = LogManager.getLogManager();
        this.root = Logger.getLogger("");
    }

    public static HttpService create(Config config) {
        // todo use config to check if this should enforced
        /*
        observe:
          log:
            allow-all: true

            /{.*}
         */

        return new LogService();
    }

    @Override
    public void routing(HttpRules rules) {
        Handler secureHandler = SecureHandler.create(false, true, "nima-observe");

        rules.any(SecureHandler.authorize().authenticate().roleHint("nima-observe"))
                .get("/loggers")
                .get("/loggers", SecureHandler.handler(this::allLoggersHandler)
                        .authenticate(true))
                .get("/loggers", this::allLoggersHandler)
                .get("/loggers/{logger}", this::loggerHandler)
                .post("/loggers/{logger}", this::setLevelHandler)
                .delete("/loggers/{logger}", this::unsetLoggerHandler)
                .get("/", this::logHandler);
    }

    @Override
    public void afterStop() {
        listeners.clear();
    }

    private void logHandler(ServerRequest req, ServerResponse res) throws Exception {
        initializeLogHandling();
        res.header(Http.HeaderValues.CONTENT_TYPE_TEXT_PLAIN);
        // do not cache more than x lines, to prevent OOM
        var q = new ArrayBlockingQueue<String>(1000);

        // we do not care if the offer fails, it means the consumer is slow and will miss some lines
        listeners.put(req, q::offer);

        try (OutputStreamWriter out = new OutputStreamWriter(res.outputStream())) {

            while (true) {
                try {
                    String poll = q.poll(5, TimeUnit.SECONDS);
                    if (poll == null) {
                        // check if we are still alive
                        out.write(IDLE_STRINGS);
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
        res.status(Http.Status.NO_CONTENT_204).send();
    }

    private void setLevelHandler(ServerRequest req, ServerResponse res) {
        System.out.println("Here");
        String logger = req.path().pathParameters().first("logger").orElse("");
        JsonObject requestJson = READER.read(JsonpMediaSupportProvider.JSON_OBJECT_TYPE,
                                             req.content().inputStream(),
                                             req.headers());

        Level desiredLevel = Level.parse(requestJson.getString("level"));
        Logger.getLogger(logger)
                .setLevel(desiredLevel);

        res.status(Http.Status.NO_CONTENT_204).send();
    }

    private void loggerHandler(ServerRequest req, ServerResponse res) {
        String logger = req.path().pathParameters().first("logger").orElse("");
        JsonObjectBuilder rootObject = JSON.createObjectBuilder();
        logger(rootObject, logger);

        write(req, res, rootObject.build());
    }

    private void allLoggersHandler(ServerRequest req, ServerResponse res) {
        JsonObjectBuilder rootObject = JSON.createObjectBuilder();

        levels(rootObject);
        loggers(rootObject);

        write(req, res, rootObject.build());
    }

    private void loggers(JsonObjectBuilder rootObject) {
        JsonObjectBuilder loggersJson = JSON.createObjectBuilder();

        Enumeration<String> loggerNames = logManager.getLoggerNames();
        while (loggerNames.hasMoreElements()) {
            logger(loggersJson, loggerNames.nextElement());
        }

        rootObject.add("loggers", loggersJson);
    }

    private void logger(JsonObjectBuilder parentJson, String loggerName) {
        Logger logger = Logger.getLogger(loggerName);

        Level configuredLevel = logger.getLevel();
        Level effectiveLevel = effectiveLevel(logger);

        JsonObjectBuilder loggerJson = JSON.createObjectBuilder();
        if (configuredLevel != null) {
            loggerJson.add("configuredLevel", configuredLevel.getName());
        }
        loggerJson.add("level", effectiveLevel.getName());

        parentJson.add("".equals(loggerName) ? "ROOT" : loggerName, loggerJson);
    }

    private void levels(JsonObjectBuilder rootObject) {
        JsonArrayBuilder levels = JSON.createArrayBuilder();
        levels.add(Level.OFF.getName())
                .add(Level.SEVERE.getName())
                .add(Level.WARNING.getName())
                .add(Level.INFO.getName())
                .add(Level.FINE.getName())
                .add(Level.FINER.getName())
                .add(Level.FINEST.getName());
        rootObject.add("levels", levels);
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
        WRITER.write(JsonpMediaSupportProvider.JSON_OBJECT_TYPE,
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
