package com.applatix.jenkins.applatixplugins;

import hudson.Launcher;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.*;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import com.applatix.jenkins.applatixplugins.ApplatixHelper;

public class ApplatixPlugins extends Builder implements SimpleBuildStep {
    private final String axUrl;
    private final String axUsername;
    private final String axPassword;
    private final String axServiceTemplateName;
    private final String axServiceTemplateRepository;
    private final String axServiceTemplateBranch;
    private final List<KeyValuePair> axServiceTemplateParameters;

    private String axClientInitFailureMessage;
    public static final String configuredImproperlyError = "Applatix configured improperly in project settings \n";

    public String getAxUrl() { return axUrl; }
    public String getAxUsername() { return axUsername; }
    public String getAxPassword() { return axPassword; }
    public String getAxServiceTemplateName() { return axServiceTemplateName; }
    public String getAxServiceTemplateRepository() { return axServiceTemplateRepository; }
    public String getAxServiceTemplateBranch() { return axServiceTemplateBranch; }
    public List<KeyValuePair> getAxServiceTemplateParameters() { return axServiceTemplateParameters; }

    @DataBoundConstructor
    public ApplatixPlugins(String axUrl, String axUsername, String axPassword, String axServiceTemplateName,
                           String axServiceTemplateRepository, String axServiceTemplateBranch,
                           List<KeyValuePair> axServiceTemplateParameters) throws InterruptedException, IOException {
        this.axUrl = axUrl;
        this.axUsername = axUsername;
        this.axPassword = axPassword;
        this.axServiceTemplateName = axServiceTemplateName;
        this.axServiceTemplateRepository = axServiceTemplateRepository;
        this.axServiceTemplateBranch = axServiceTemplateBranch;
        this.axServiceTemplateParameters = axServiceTemplateParameters;
    }

    @Override
    public void perform(Run<?,?> build, FilePath workspace, Launcher launcher, TaskListener listener) throws InterruptedException, IOException {
        Map<String, String> parameters = new HashMap();
        parameters.put("session.repo", this.axServiceTemplateRepository);
        parameters.put("session.commit", this.axServiceTemplateBranch);
        if (this.axServiceTemplateParameters != null) {
            for (KeyValuePair para : this.axServiceTemplateParameters) {
                parameters.put(para.key, para.value);
            }
        }
        LoggingHelper.log(listener, "Service Template: " + this.axServiceTemplateName);
        final ApplatixHelper axHelper;
        try {
            axHelper = new ApplatixHelper(this.axUrl, this.axUsername, this.axPassword, listener);
        } catch (Exception e) {
            LoggingHelper.log(listener, configuredImproperlyError, e.getMessage());
            build.setResult(Result.FAILURE);
            return;
        }

        final String serviceId;
        try {
            String serviceTemplateId = axHelper.getServiceTemplateId(this.axServiceTemplateRepository,
                    this.axServiceTemplateBranch, this.axServiceTemplateName);
            if (serviceTemplateId == null) {
                LoggingHelper.log(listener, "Could not get service template " + this.axServiceTemplateName);
                build.setResult(Result.FAILURE);
                return;
            }
            LoggingHelper.log(listener, "Service Template ID: " + serviceTemplateId);
            serviceId = axHelper.createService(serviceTemplateId, parameters);
            if (serviceId == null) {
                LoggingHelper.log(listener, "Fail to start a task on Applatix");
                build.setResult(Result.FAILURE);
                return;
            } else {
                LoggingHelper.log(listener, "Task starts ...");
            }
        } catch (Exception e) {
            LoggingHelper.log(listener, e.getMessage());
            build.setResult(Result.FAILURE);
            return;
        }
        LoggingHelper.log(listener, "The task URL on Applatix:");
        LoggingHelper.log(listener, this.axUrl + "/app/jobs/job-details/" + serviceId);

        do {
            Thread.sleep(5000L);
        } while(axHelper.getServiceStatus(serviceId) == 1 || axHelper.getServiceStatus(serviceId) == 2 || axHelper.getServiceStatus(serviceId) == 255);

        int serviceStatusInt = axHelper.getServiceStatus(serviceId);
        LoggingHelper.log(listener, "Task Complete");
        if (serviceStatusInt == 0) {
            return;
        } else {
            build.setResult(Result.FAILURE);
            return;
        }
    }

    /**
     * Represents parameters key/value entries defined by users in their jobs.
     */
    public static class KeyValuePair implements Cloneable {

        private final String key;
        private final String value;

        @DataBoundConstructor
        public KeyValuePair(String key, String value) {
            this.key = key;
            this.value = value;
        }

        public Object clone() {
            return new KeyValuePair(getKey(), getValue());
        }

        @Override
        public boolean equals(Object obj) {
            if(obj == null) {
                return false;
            }
            if(getClass() != obj.getClass()) {
                return false;
            }
            final KeyValuePair other = (KeyValuePair) obj;
            if((this.key == null) ? (other.key != null) : !this.key.equals(other.key)) {
                return false;
            }
            return true;
        }

        public String getKey() {
            return key;
        }

        public String getValue() {
            return value;
        }
    }

    // Overridden for better type safety.
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    /**
     * Descriptor for ApplatixPlugins. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     */
    @Symbol("applatix")
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        private String axUrl;
        private String axUsername;
        private String axPassword;
        private String axServiceTemplateName;
        private String axServiceTemplateRepository;
        private String axServiceTemplateBranch;
        private String axServiceTemplateParameters;

        public DescriptorImpl() {
            load();
        }

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // Indicates that this builder can be used with all kinds of project types 
            return true;
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            // To persist global configuration information,
            // set that to properties and call save().

            if(formData.has("axUrl")) {
                axUrl = formData.getString("axUrl");
            }
            if(formData.has("axUsername")) {
                axUsername = formData.getString("axUsername");
            }
            if(formData.has("axPassword")) {
                axPassword = formData.getString("axPassword");
            }
            if(formData.has("axServiceTemplateName")) {
                axServiceTemplateName = formData.getString("axServiceTemplateName");
            }
            if(formData.has("axServiceTemplateRepository")) {
                axServiceTemplateRepository = formData.getString("axServiceTemplateRepository");
            }
            if(formData.has("axServiceTemplateBranch")) {
                axServiceTemplateBranch = formData.getString("axServiceTemplateBranch");
            }
            if(formData.has("axServiceTemplateParameters")) {
                axServiceTemplateParameters = formData.getString("axServiceTemplateParameters");
            }

            req.bindJSON(this, formData);
            save();

            return super.configure(req,formData);
        }

        public String getDisplayName() {
            return "Applatix System Integration";
        }
    }
}

