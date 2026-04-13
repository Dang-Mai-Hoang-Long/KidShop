package com.example.demo.config;

import org.springframework.core.NestedExceptionUtils;

public final class ExceptionMessageHelper {

    private ExceptionMessageHelper() {
    }

    public static String describe(Throwable exception) {
        if (exception == null) {
            return "Lỗi không xác định.";
        }

        String detailedMessage = buildChainMessage(exception);
        if (detailedMessage != null && !detailedMessage.isBlank()) {
            return detailedMessage;
        }

        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            Throwable rootCause = NestedExceptionUtils.getMostSpecificCause(exception);
            if (rootCause != null && rootCause != exception) {
                message = rootCause.getMessage();
            }
        }

        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }

        return message;
    }

    private static String buildChainMessage(Throwable exception) {
        StringBuilder builder = new StringBuilder();
        Throwable current = exception;
        int depth = 0;

        while (current != null && depth < 4) {
            if (builder.length() > 0) {
                builder.append(" -> ");
            }

            builder.append(current.getClass().getSimpleName());

            String message = current.getMessage();
            if (message != null && !message.isBlank()) {
                builder.append(": ").append(message);
            }

            current = current.getCause();
            depth++;
        }

        return builder.toString();
    }
}