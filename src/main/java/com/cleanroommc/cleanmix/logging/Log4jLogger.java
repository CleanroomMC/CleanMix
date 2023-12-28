package com.cleanroommc.cleanmix.logging;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.logging.ILogger;
import org.spongepowered.asm.logging.Level;

public class Log4jLogger implements ILogger {

    private enum Log4JLevel {

        FATAL(org.apache.logging.log4j.Level.FATAL),
        ERROR(org.apache.logging.log4j.Level.ERROR),
        WARN(org.apache.logging.log4j.Level.WARN),
        INFO(org.apache.logging.log4j.Level.INFO),
        DEBUG(org.apache.logging.log4j.Level.DEBUG),
        TRACE(org.apache.logging.log4j.Level.TRACE);

        private static final Log4JLevel[] VALUES = Log4JLevel.values();

        private final org.apache.logging.log4j.Level level;

        Log4JLevel(org.apache.logging.log4j.Level level) {
            this.level = level;
        }

    }

    private final String name;
    private final Logger logger;

    public Log4jLogger(String name) {
        this.name = name;
        this.logger = LogManager.getLogger(name);
    }

    @Override
    public String getId() {
        return name;
    }

    @Override
    public String getType() {
        return "Log4J2";
    }

    @Override
    public void catching(Level level, Throwable t) {
        this.logger.catching(Log4JLevel.VALUES[level.ordinal()].level, t);
    }

    @Override
    public void catching(Throwable t) {
        this.logger.catching(t);
    }

    @Override
    public void debug(String message, Object... params) {
        this.logger.debug(message, params);
    }

    @Override
    public void debug(String message, Throwable t) {
        this.logger.debug(message, t);
    }

    @Override
    public void error(String message, Object... params) {
        this.logger.error(message, params);
    }

    @Override
    public void error(String message, Throwable t) {
        this.logger.error(message, t);
    }

    @Override
    public void fatal(String message, Object... params) {
        this.logger.fatal(message, params);
    }

    @Override
    public void fatal(String message, Throwable t) {
        this.logger.fatal(message, t);
    }

    @Override
    public void info(String message, Object... params) {
        this.logger.info(message, params);
    }

    @Override
    public void info(String message, Throwable t) {
        this.logger.info(message, t);
    }

    @Override
    public void log(Level level, String message, Object... params) {
        this.logger.log(Log4JLevel.VALUES[level.ordinal()].level, message, params);
    }

    @Override
    public void log(Level level, String message, Throwable t) {
        this.logger.log(Log4JLevel.VALUES[level.ordinal()].level, message, t);
    }

    @Override
    public <T extends Throwable> T throwing(T t) {
        return this.logger.throwing(t);
    }

    @Override
    public void trace(String message, Object... params) {
        this.logger.trace(message, params);
    }

    @Override
    public void trace(String message, Throwable t) {
        this.logger.trace(message, t);
    }

    @Override
    public void warn(String message, Object... params) {
        this.logger.warn(message, params);
    }

    @Override
    public void warn(String message, Throwable t) {
        this.logger.warn(message, t);
    }

}
