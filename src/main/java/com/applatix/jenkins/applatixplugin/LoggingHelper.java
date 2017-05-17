package com.applatix.jenkins.applatixplugin;

import hudson.model.TaskListener;

public class LoggingHelper {

    public static void log(final TaskListener listener, String message) {
        log(listener, message, null);
    }

    public static void log(final TaskListener listener, String message, String secondary) {
        String completeMessage;
        if(secondary == null || secondary.isEmpty()) {
            completeMessage = "[Applatix Plugin] " + message;
        } else {
            completeMessage = "[Applatix Plugin] " + message + "\n> " + secondary;
        }

        if(listener == null) {
            System.out.println(message);
        } else {
            listener.getLogger().println(completeMessage);
        }
    }
}
