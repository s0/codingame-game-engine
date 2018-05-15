package com.codingame.gameengine.core;

import java.util.HashMap;
import java.util.Map;

public class Log {
    
    private enum Level {
        INFO, WARNING, ERROR
    }

    private static Log instance;
    
    private Map<Level, Boolean> levels = new HashMap<>();

    private Log() {
        for (Level level : Level.values()) {
            levels.put(level, true);
        }
    }

    public static Log getInstance() {
        if (instance == null) {
            instance = new Log();
        }
        return instance;
    }

    private void print(Level level, Object message) {
        if(levels.get(level)) {
            System.err.println(level + ": " + message);
        }
    }

    public void info(Object message) {
        print(Level.INFO, message);
    }

    public void warn(Object message) {
        print(Level.WARNING, message);
    }

    public void error(Object message) {
        print(Level.ERROR, message);
    }

    public void error(Object message, Exception e) {
        error(message);
        e.printStackTrace();
    }

    public boolean isInfoEnabled() {
        return levels.get(Level.INFO);
    }

    public boolean isWarnEnabled() {
        return levels.get(Level.WARNING);
    }

    public boolean isErrorEnabled() {
        return levels.get(Level.ERROR);
    }

    public void setInfoEnabled(boolean value) {
        levels.put(Level.INFO, value);
    }

    public void setWarnEnabled(boolean value) {
        levels.put(Level.WARNING, value);
    }

    public void setErrorEnabled(boolean value) {
        levels.put(Level.ERROR, value);
    }
}
