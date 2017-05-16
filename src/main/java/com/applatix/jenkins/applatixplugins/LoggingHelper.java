package com.applatix.jenkins.applatixplugins;

import hudson.model.TaskListener;

public class LoggingHelper {

    public static void log(final TaskListener listener, String message) {
        log(listener, message, null);
    }

    public static void log(final TaskListener listener, String message, String secondary) {
        String completeMessage;
        if(secondary == null || secondary.isEmpty()) {
            completeMessage = "[Applatix Plugins] " + message;
        } else {
            completeMessage = "[Applatix Plugins] " + message + "\n> " + secondary;
        }

        if(listener == null) {
            System.out.println(message);
        } else {
            listener.getLogger().println(completeMessage);
        }
    }
}
