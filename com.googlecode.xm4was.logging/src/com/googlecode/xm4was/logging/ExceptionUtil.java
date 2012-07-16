package com.googlecode.xm4was.logging;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

public final class ExceptionUtil {
    private ExceptionUtil() {}
    
    public static ThrowableInfo[] process(Throwable throwable) {
        List<ThrowableInfo> list = new ArrayList<ThrowableInfo>();
        while (throwable != null && list.contains(throwable) == false) {
            list.add(new ThrowableInfo(throwable.toString(), throwable.getStackTrace()));
            throwable = throwable.getCause();
        }
        return list.toArray(new ThrowableInfo[list.size()]);
    }
    
    private static int countCommonFrames(StackTraceElement[] causeFrames, StackTraceElement[] wrapperFrames) {
        int causeFrameIndex = causeFrames.length - 1;
        int wrapperFrameIndex = wrapperFrames.length - 1;
        int commonFrames = 0;
        while (causeFrameIndex >= 0 && wrapperFrameIndex >= 0) {
            // Remove the frame from the cause trace if it is the same
            // as in the wrapper trace
            if (causeFrames[causeFrameIndex].equals(wrapperFrames[wrapperFrameIndex])) {
                commonFrames++;
            } else {
                break;
            }
            causeFrameIndex--;
            wrapperFrameIndex--;
        }
        return commonFrames;
    }

    public static void formatStackTrace(ThrowableInfo[] throwables, LineAppender appender) {
        int count = throwables.length;
        StackTraceElement[] nextTrace = throwables[count-1].getStackTrace();
        int commonFrames = -1;
        for (int i = count-1; i >= 0; i--) {
            StackTraceElement[] trace = nextTrace;
            // Number of frames not shared with the previous exception (cause)
            int newFrames = commonFrames == -1 ? 0 : trace.length-commonFrames;
            if (i == 0) {
                commonFrames = 0;
            } else {
                nextTrace = throwables[i-1].getStackTrace();
                commonFrames = countCommonFrames(trace, nextTrace);
            }
            if (i == count-1) {
                if (!appender.addLine(throwables[i].getMessage())) {
                    return;
                }
            } else {
                // If the wrapping exception was constructed without explicit message, then
                // the message will contain the message of the wrapped exception. If this is
                // the case, then we remove this duplicate message to shorten the stacktrace.
                String message = throwables[i].getMessage();
                String prevMessage = throwables[i+1].getMessage();
                if (message.endsWith(prevMessage)
                        && message.charAt(message.length()-prevMessage.length()-2) == ':'
                        && message.charAt(message.length()-prevMessage.length()-1) == ' ') {
                    message = message.substring(0, message.length()-prevMessage.length()-2);
                }
                if (!appender.addLine("Wrapped by: " + message)) {
                    return;
                }
            }
            for (int j = 0; j < trace.length-commonFrames; j++) {
                StackTraceElement frame = trace[j];
                String className = frame.getClassName();
                String methodName = frame.getMethodName();
                if (className.startsWith("sun.reflect.") && methodName.startsWith("invoke")) {
                    // Skip frames related to reflective invocation; they are generally
                    // non deterministic (they contain things such as
                    // sun.reflect.GeneratedMethodAccessor1026)
                    continue;
                } else if (className.startsWith("$Proxy")) {
                    className = "[proxy]";
                }
                StringBuilder buffer = new StringBuilder();
                buffer.append(' ');
                if (j < newFrames) {
                    buffer.append('+');
                } else if (i == 0 && j == trace.length-commonFrames-1) {
                    buffer.append('*');
                } else {
                    buffer.append('|');
                }
                buffer.append(' ');
                buffer.append(className);
                buffer.append('.');
                buffer.append(methodName);
                String fileName = frame.getFileName();
                if (fileName != null) {
                    buffer.append('(');
                    boolean match;
                    if (fileName.endsWith(".java")) {
                        int idx = className.lastIndexOf('.')+1;
                        int k;
                        match = true;
                        for (k=0; k < className.length()-idx && k < fileName.length()-5; k++) {
                            if (className.charAt(idx+k) != fileName.charAt(k)) {
                                match = false;
                                break;
                            }
                        }
                        if (match) {
                            match = idx+k == className.length() || className.charAt(idx+k) == '$';
                        }
                    } else {
                        match = false;
                    }
                    if (!match) {
                        buffer.append(fileName);
                        buffer.append(':');
                    }
                    buffer.append(frame.getLineNumber());
                    buffer.append(')');
                }
                if (!appender.addLine(buffer.toString())) {
                    return;
                }
            }
        }
    }
    
    public static ThrowableInfo[] parse(String s) {
        BufferedReader reader = new BufferedReader(new StringReader(s));
        List<ThrowableInfo> throwables = new ArrayList<ThrowableInfo>();
        String message = null;
        List<StackTraceElement> stackTrace = new ArrayList<StackTraceElement>();
        try {
            String line;
            do {
                line = reader.readLine();
                boolean close;
                boolean startNew;
                if (line == null) {
                    close = message != null;
                    startNew = false;
                } else if (line.startsWith("\tat ") && line.charAt(line.length()-1) == ')') {
                    if (message == null) {
                        // Probably a truncated stacktrace
                        return null;
                    }
                    int parenIdx = line.indexOf('(');
                    if (parenIdx == -1) {
                        return null;
                    }
                    int methodIdx = line.lastIndexOf('.', parenIdx);
                    if (methodIdx == -1) {
                        return null;
                    }
                    String className = line.substring(4, methodIdx);
                    String methodName = line.substring(methodIdx+1, parenIdx);
                    int colonIdx = line.indexOf(':', parenIdx+1);
                    String file;
                    int sourceLine;
                    if (colonIdx == -1) {
                        file = null;
                        String source = line.substring(parenIdx+1, line.length()-1);
                        sourceLine = source.equals("Native Method") ? -2 : -1;
                    } else {
                        file = line.substring(parenIdx+1, colonIdx);
                        try {
                            sourceLine = Integer.parseInt(line.substring(colonIdx+1, line.length()-1));
                        } catch (NumberFormatException ex) {
                            return null;
                        }
                    }
                    stackTrace.add(new StackTraceElement(className, methodName, file, sourceLine));
                    close = false;
                    startNew = false;
                } else if (message == null) {
                    close = false;
                    startNew = true;
                } else if (line.startsWith("\t... ") && line.endsWith(" more")) {
                    if (throwables.isEmpty()) {
                        // Malformed stacktrace: only nested exceptions can have a shortened stack trace
                        return null;
                    } else {
                        int more;
                        try {
                            more = Integer.parseInt(line.substring(5, line.length()-5));
                        } catch (NumberFormatException ex) {
                            return null;
                        }
                        close = true;
                        startNew = false;
                        StackTraceElement[] previousStackTrace = throwables.get(throwables.size()-1).getStackTrace();
                        for (int i=0; i<more; i++) {
                            stackTrace.add(previousStackTrace[previousStackTrace.length-more+i]);
                        }
                    }
                } else {
                    close = message != null;
                    startNew = true;
                }
                if (close) {
                    throwables.add(new ThrowableInfo(message, stackTrace.toArray(new StackTraceElement[stackTrace.size()])));
                    message = null;
                    stackTrace.clear();
                }
                if (startNew) {
                    if (throwables.isEmpty()) {
                        message = line;
                    } else if (line.startsWith("Caused by: ")) {
                        message = line.substring(11);
                    } else {
                        // Malformed stacktrace
                        return null;
                    }
                }
            } while (line != null);
        } catch (IOException ex) {
            return null;
        }
        return throwables.toArray(new ThrowableInfo[throwables.size()]);
    }
}