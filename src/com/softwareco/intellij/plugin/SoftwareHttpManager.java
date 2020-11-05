package com.softwareco.intellij.plugin;

import com.softwareco.intellij.plugin.managers.FileManager;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.*;
import org.apache.http.entity.StringEntity;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SoftwareHttpManager implements Callable<HttpResponse> {

    public static final Logger LOG = Logger.getLogger("Software");

    private String payload;
    private String api;
    private String httpMethodName;
    private HttpClient httpClient;
    private String overridingJwt;

    public SoftwareHttpManager(String api, String httpMethodName, String payload, String overridingJwt, HttpClient httpClient) {
        this.payload = payload;
        this.api = api;
        this.httpMethodName = httpMethodName;
        this.overridingJwt = overridingJwt;
        this.httpClient = httpClient;
    }

    @Override
    public HttpResponse call() {
        HttpUriRequest req = null;
        try {
            HttpResponse response = null;

            switch (httpMethodName) {
                case HttpPost.METHOD_NAME:
                    req = new HttpPost("" + SoftwareCoUtils.api_endpoint + this.api);
                    if (payload != null) {
                        //
                        // add the json payload
                        //
                        StringEntity params = new StringEntity(payload);
                        ((HttpPost)req).setEntity(params);
                    }
                    break;
                case HttpDelete.METHOD_NAME:
                    req = new HttpDelete(SoftwareCoUtils.api_endpoint + "" + this.api);
                    break;
                case HttpPut.METHOD_NAME:
                    req = new HttpPut(SoftwareCoUtils.api_endpoint + "" + this.api);
                    break;
                default:
                    req = new HttpGet(SoftwareCoUtils.api_endpoint + "" + this.api);
                    break;
            }


            String jwtToken = (this.overridingJwt != null) ? this.overridingJwt : FileManager.getItem("jwt");
            // obtain the jwt session token if we have it
            if (jwtToken != null) {
                req.addHeader("Authorization", jwtToken);
            }

            SoftwareCoUtils.TimesData timesData = SoftwareCoUtils.getTimesData();
            req.addHeader("X-SWDC-Plugin-Id", String.valueOf(SoftwareCoUtils.pluginId));
            req.addHeader("X-SWDC-Plugin-Name", SoftwareCoUtils.getPluginName());
            req.addHeader("X-SWDC-Plugin-Version", SoftwareCoUtils.getVersion());
            req.addHeader("X-SWDC-Plugin-OS", SoftwareCoUtils.getOs());
            req.addHeader("X-SWDC-Plugin-TZ", timesData.timezone);
            req.addHeader("X-SWDC-Plugin-Offset", String.valueOf(timesData.offset));

            req.addHeader("Content-type", "application/json");

            if (payload != null) {
                LOG.log(Level.INFO, SoftwareCoUtils.pluginName + ": Sending API request: {0}, payload: {1}", new Object[]{api, payload});
            }

            // execute the request
            response = httpClient.execute(req);

            //
            // Return the response
            //
            return response;
        } catch (IOException e) {
            LOG.log(Level.WARNING, SoftwareCoUtils.pluginName + ": Unable to make api request.{0}", e.getMessage());
            LOG.log(Level.INFO, SoftwareCoUtils.pluginName + ": Sending API request: " + this.api);
        }

        return null;
    }
}
