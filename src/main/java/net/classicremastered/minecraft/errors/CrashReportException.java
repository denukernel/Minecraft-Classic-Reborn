package net.classicremastered.minecraft.errors;

public class CrashReportException extends RuntimeException {
    public CrashReportException(String message, Throwable cause) {
        super(message, cause);
    }

    public CrashReportException(String message) {
        super(message);
    }
}
