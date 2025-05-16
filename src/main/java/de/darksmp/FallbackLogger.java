package de.darksmp;

import org.slf4j.Logger;

public class FallbackLogger {
    private final Logger logger;

    public FallbackLogger(Logger logger) {
        this.logger = logger;
    }

    public void info(String msg) {
        logger.info("[FallbackRouter] " + msg);
    }

    public void warn(String msg) {
        logger.warn("[FallbackRouter] " + msg);
    }

    public void error(String msg) {
        logger.error("[FallbackRouter] " + msg);
    }
}