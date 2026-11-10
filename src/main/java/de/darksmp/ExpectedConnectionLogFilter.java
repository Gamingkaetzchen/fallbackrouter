package de.darksmp;

import com.velocitypowered.api.proxy.Player;
import java.net.ConnectException;
import java.util.Locale;
import java.util.UUID;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.message.ParameterizedMessage;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.filter.AbstractFilter;

/** Only replaces known connection-refused diagnostics owned by an active fallback. */
final class ExpectedConnectionLogFilter extends AbstractFilter implements AutoCloseable {
    private static final String PLAYER_LOGGER = "com.velocitypowered.proxy.connection.client.ConnectedPlayer";
    // A broad ConnectException filter could hide unrelated outages. Require this
    // exact Velocity diagnostic as well as the player/destination ownership check.
    private static final String CONNECT_MESSAGE = "{}: unable to connect to server {}";
    private final LoggerContext context;
    private final Handler handler;

    @FunctionalInterface
    interface Handler {
        boolean handled(UUID playerId, String target, Throwable failure);
    }

    private ExpectedConnectionLogFilter(LoggerContext context, Handler handler) {
        this.context = context;
        this.handler = handler;
    }

    static AutoCloseable install(Handler handler) {
        if (!(LogManager.getContext(false) instanceof LoggerContext context)) {
            throw new IllegalStateException("Velocity's Log4j Core context is unavailable");
        }

        ExpectedConnectionLogFilter filter = new ExpectedConnectionLogFilter(context, handler);
        filter.start();
        // Correlate before async logging queues can outlive the active attempt.
        // A logger-config/appender filter may run after routing state was cleared.
        // The logger stage also makes the short warning and DEBUG copy before
        // the original error is queued; a catch in the kick event is too late.
        context.addFilter(filter);
        return filter;
    }

    @Override
    public Result filter(LogEvent event) {
        return evaluate(event.getLoggerName(), event.getLevel(), event.getMessage(), event.getThrown());
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, Message message, Throwable thrown) {
        return evaluate(logger.getName(), level, message, thrown);
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, Object message, Throwable thrown) {
        return message instanceof Message typed ? evaluate(logger.getName(), level, typed, thrown) : Result.NEUTRAL;
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, String format, Object... arguments) {
        if (!PLAYER_LOGGER.equals(logger.getName()) || !Level.ERROR.equals(level)
                || !CONNECT_MESSAGE.equals(format)) return Result.NEUTRAL;
        return evaluate(logger.getName(), level, new ParameterizedMessage(format, arguments), null);
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, String format, Object first, Object second, Object third) {
        return filter(logger, level, marker, format, new Object[]{first, second, third});
    }

    private Result evaluate(String loggerName, Level level, Message message, Throwable thrown) {
        if (!PLAYER_LOGGER.equals(loggerName) || !Level.ERROR.equals(level)
                || message == null || !CONNECT_MESSAGE.equals(message.getFormat())) return Result.NEUTRAL;
        Throwable failure = thrown == null ? message.getThrowable() : thrown;
        if (!isConnectionRefused(failure)) return Result.NEUTRAL;
        Object[] arguments = message.getParameters();
        if (arguments == null || arguments.length < 2 || !(arguments[0] instanceof Player player)
                || !(arguments[1] instanceof String target)) return Result.NEUTRAL;
        try {
            return handler.handled(player.getUniqueId(), target, failure) ? Result.DENY : Result.NEUTRAL;
        } catch (RuntimeException ignored) {
            // If correlation fails, retain the complete original diagnostic.
            return Result.NEUTRAL;
        }
    }

    private boolean isConnectionRefused(Throwable failure) {
        for (int depth = 0; failure != null && depth < 10; depth++, failure = failure.getCause()) {
            if (failure instanceof ConnectException && failure.getMessage() != null
                    && failure.getMessage().toLowerCase(Locale.ROOT).contains("connection refused")) return true;
        }
        return false;
    }

    @Override
    public void close() {
        context.removeFilter(this);
        stop();
    }
}
