package com.applatix.jenkins.applatixplugin;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import hudson.model.TaskListener;
import java.lang.Exception;
import java.lang.String;
import java.util.Map;
import javax.net.ssl.SSLContext;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.conn.ssl.SSLContexts;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.CloseableHttpClient;
import org.json.JSONArray;
import org.json.JSONObject;

public class ApplatixHelper {

    private String axUrl;
    private String axUsername;
    private String axPassword;
    private TaskListener listener;

    private static final String version = "v1";
    private static final String loginPath = "/" + version + "/auth/login";
    private static final String templatesPath = "/" + version + "/templates";
    private static final String servicesPath = "/" + version + "/services";

    public ApplatixHelper(String axUrl, String axUsername, String axPassword, TaskListener listener) {
        this.axUrl = axUrl;
        this.axUsername = axUsername;
        this.axPassword = axPassword;
        this.listener = listener;

        try {
            SSLContext sslcontext = SSLContexts.custom().
                                   loadTrustMaterial(null, new TrustSelfSignedStrategy()).
                                   build();
            SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(sslcontext,
                SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
            CloseableHttpClient httpclient = HttpClients.custom().setSSLSocketFactory(sslsf).build();
            Unirest.setHttpClient(httpclient);
        } catch (Exception e) {
            LoggingHelper.log(this.listener, e.getMessage());
            throw new RuntimeException(e);
        }

        try {
            login();
        } catch (Exception e) {
            LoggingHelper.log(this.listener, "Fail to login Applatix system, please check your credential.");
            LoggingHelper.log(this.listener, e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public void login() {
        JSONObject credential = new JSONObject();
        try {
            credential.put("username", this.axUsername);
            credential.put("password", this.axPassword);
        }
        catch (org.json.JSONException e) {
            throw new Error("Fail to parse credential");
        }

        try {
            HttpResponse loginResponse = Unirest.post(this.axUrl + loginPath)
                    .body(credential)
                    .asJson();
            if (loginResponse.getStatus() > 200 || loginResponse.getStatus() < 200) {
                throw new Exception(loginResponse.getBody().toString());
            }
        } catch (Exception e) {
            LoggingHelper.log(this.listener, e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public String getServiceTemplateId(String repo, String branch, String serviceTemplateName) {
        try {
            HttpResponse<JsonNode> getServiceTemplateResponse = Unirest.get(this.axUrl + templatesPath)
                    .header("accept", "application/json")
                    .queryString("repo", repo)
                    .queryString("branch", branch)
                    .queryString("name", serviceTemplateName)
                    .asJson();
            JSONArray serviceTemplates = getServiceTemplateResponse.getBody().getObject().getJSONArray("data");
            if (serviceTemplates == null) {
                throw new Exception("No service template " + serviceTemplateName + " found.");
            } else {
                return serviceTemplates.getJSONObject(0).getString("id");
            }
        } catch (Exception e) {
            LoggingHelper.log(this.listener, e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public String createService(String id, Map<String, String> parameters) {
        JSONObject serviceBody = new JSONObject();
        JSONObject parametersJson = new JSONObject();
        for(Map.Entry<String, String> entry: parameters.entrySet()) {
            try {
                parametersJson.put(entry.getKey(), entry.getValue());
            }
            catch (org.json.JSONException e) {
                throw new Error("Fail to parse the parameters");
            }
        }

        try {
            serviceBody.put("template_id", id);
            serviceBody.put("parameters", parametersJson);
            LoggingHelper.log(this.listener, "Service Request:  " + serviceBody.toString());
        } catch (Exception e) {
            LoggingHelper.log(this.listener, e.getMessage());
            throw new RuntimeException(e);
        }

        try {
            HttpResponse<JsonNode> createServiceResponse = Unirest.post(this.axUrl + servicesPath)
                    .header("accept", "application/json")
                    .body(serviceBody)
                    .asJson();
            if (createServiceResponse.getStatus() >= 300 || createServiceResponse.getStatus() < 200) {
                throw new Exception(createServiceResponse.getBody().toString());
            } else {
                String serviceId = createServiceResponse.getBody().getObject().getString("id");
                return serviceId;
            }
        } catch (Exception e) {
            LoggingHelper.log(this.listener, e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public int getServiceStatus(String serviceId) {
        try {
            HttpResponse<JsonNode> getServiceStatusResponse = Unirest.get(this.axUrl + servicesPath + "/" + serviceId)
                    .header("accept", "application/json")
                    .asJson();
            if (getServiceStatusResponse.getStatus() >= 300 || getServiceStatusResponse.getStatus() < 200) {
                LoggingHelper.log(this.listener, "Could not find the task");
                throw new Exception(getServiceStatusResponse.getBody().toString());
            } else {
                return getServiceStatusResponse.getBody().getObject().getInt("status");
            }
        } catch (Exception e) {
            LoggingHelper.log(this.listener, e.getMessage());
            throw new RuntimeException(e);
        }
    }
}
