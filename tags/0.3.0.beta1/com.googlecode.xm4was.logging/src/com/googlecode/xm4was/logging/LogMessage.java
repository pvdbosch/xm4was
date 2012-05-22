package com.googlecode.xm4was.logging;

import java.io.PrintWriter;
import java.io.Writer;

public final class LogMessage {
    private long sequence;
    private final int level;
    private final long timestamp;
    private final String loggerName;
    private final String applicationName;
    private final String moduleName;
    private final String componentName;
    private final String message;
    private final Throwable throwable;
    
    public LogMessage(int level, long timestamp, String loggerName,
            String applicationName, String moduleName, String componentName,
            String message, Throwable throwable) {
        this.level = level;
        this.timestamp = timestamp;
        this.loggerName = loggerName;
        this.applicationName = applicationName;
        this.moduleName = moduleName;
        this.componentName = componentName;
        this.message = message;
        this.throwable = throwable;
    }
    
    void setSequence(long sequence) {
        this.sequence = sequence;
    }

    public long getSequence() {
        return sequence;
    }

    public int getLevel() {
        return level;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getLoggerName() {
        return loggerName;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public String getModuleName() {
        return moduleName;
    }

    public String getComponentName() {
        return componentName;
    }

    public String getMessage() {
        return message;
    }
    
    public String format(int maxMessageSize) {
        // The format is designed to be forward compatible. The string contains a sequence of
        // colon-separated fields enclosed in brackets, followed by the log message itself.
        // This means that new fields can be added easily and that no escaping is required for
        // the message.
        StringBuilder buffer = new StringBuilder();
        buffer.append('[');
        buffer.append(sequence);
        buffer.append(':');
        buffer.append(level);
        buffer.append(':');
        buffer.append(timestamp);
        buffer.append(':');
        if (loggerName != null) {
            buffer.append(loggerName);
        }
        buffer.append(':');
        if (applicationName != null) {
            buffer.append(applicationName);
        }
        buffer.append(':');
        if (moduleName != null) {
            buffer.append(moduleName);
        }
        buffer.append(':');
        if (componentName != null) {
            buffer.append(componentName);
        }
        buffer.append(']');
        if (maxMessageSize < 0) {
            maxMessageSize = Integer.MAX_VALUE;
        }
        if (message.length() > maxMessageSize) {
            buffer.append(message.substring(0, maxMessageSize));
            maxMessageSize = 0; 
        } else {
            buffer.append(message);
            maxMessageSize -= message.length();
        }
        if (throwable != null && maxMessageSize > 0) {
            Writer out = new StringBuilderWriter(buffer);
            if (maxMessageSize > 0) {
                out = new LengthLimitedWriter(out, maxMessageSize);
            }
            PrintWriter pw = new PrintWriter(out, false);
            pw.println();
            throwable.printStackTrace(pw);
            pw.flush();
        }
        return buffer.toString();
    }
}
