package com.sekhar.go.scm.plugin;

import static java.util.Arrays.asList;

import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;

import com.google.gson.GsonBuilder;
import com.sekhar.go.scm.plugin.ftp.FtpHelper;
import com.sekhar.go.scm.plugin.model.FTPConfig;
import com.sekhar.go.scm.plugin.util.StringUtil;
import com.thoughtworks.go.plugin.api.GoApplicationAccessor;
import com.thoughtworks.go.plugin.api.GoPlugin;
import com.thoughtworks.go.plugin.api.GoPluginIdentifier;
import com.thoughtworks.go.plugin.api.annotation.Extension;
import com.thoughtworks.go.plugin.api.logging.Logger;
import com.thoughtworks.go.plugin.api.request.GoPluginApiRequest;
import com.thoughtworks.go.plugin.api.response.GoPluginApiResponse;

@Extension
public class FTPPluginImpl implements GoPlugin {
    private static Logger LOGGER = Logger.getLoggerFor(FTPPluginImpl.class);

    public static final String EXTENSION_NAME = "scm";
    private static final List<String> goSupportedVersions = asList("1.0");

    public static final String REQUEST_SCM_CONFIGURATION = "scm-configuration";
    public static final String REQUEST_SCM_VIEW = "scm-view";
    public static final String REQUEST_VALIDATE_SCM_CONFIGURATION = "validate-scm-configuration";
    public static final String REQUEST_CHECK_SCM_CONNECTION = "check-scm-connection";
    public static final String REQUEST_LATEST_REVISION = "latest-revision";
    public static final String REQUEST_LATEST_REVISIONS_SINCE = "latest-revisions-since";
    public static final String REQUEST_CHECKOUT = "checkout";

    private static final String DATE_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";
    public static final int SUCCESS_RESPONSE_CODE = 200;
    public static final int INTERNAL_ERROR_RESPONSE_CODE = 500;
    @Override
    public void initializeGoApplicationAccessor(GoApplicationAccessor goApplicationAccessor) {
       
    }

    @Override
    public GoPluginApiResponse handle(GoPluginApiRequest goPluginApiRequest) {
    	LOGGER.info("requestName="+goPluginApiRequest.requestName());
        if (goPluginApiRequest.requestName().equals(REQUEST_SCM_CONFIGURATION)) {
            return handleSCMConfiguration();
        } else if (goPluginApiRequest.requestName().equals(REQUEST_SCM_VIEW)) {
            try {
                return handleSCMView();
            } catch (IOException e) {
                String message = "Failed to find template: " + e.getMessage();
                return renderJSON(500, message);
            }
        } else if (goPluginApiRequest.requestName().equals(REQUEST_VALIDATE_SCM_CONFIGURATION)) {
            return handleSCMValidation(goPluginApiRequest);
        } else if (goPluginApiRequest.requestName().equals(REQUEST_CHECK_SCM_CONNECTION)) {
            return handleSCMCheckConnection(goPluginApiRequest);
        } else if (goPluginApiRequest.requestName().equals(REQUEST_LATEST_REVISION)) {
        	return handleGetLatestRevision(goPluginApiRequest);
        } else if (goPluginApiRequest.requestName().equals(REQUEST_LATEST_REVISIONS_SINCE)) {
        	return handleGetLatestRevision(goPluginApiRequest);
        } else if (goPluginApiRequest.requestName().equals(REQUEST_CHECKOUT)) {
            return handleCheckout(goPluginApiRequest);
        }
        return null;
    }

    @Override
    public GoPluginIdentifier pluginIdentifier() {
        return new GoPluginIdentifier(EXTENSION_NAME, goSupportedVersions);
    }

    private GoPluginApiResponse handleGetLatestRevision(GoPluginApiRequest goPluginApiRequest) {
        Map<String, Object> responseMap = (Map<String, Object>) parseJSON(goPluginApiRequest.requestBody());
        Map<String, String> configuration = keyValuePairs(responseMap, "scm-configuration");
        FTPConfig gitConfig = getGitConfig(configuration);
        String flyweightFolder = (String) responseMap.get("flyweight-folder");
        LOGGER.warn("flyweight: " + flyweightFolder);
        Map<String, Object> fieldMap = new HashMap<String, Object>();
        try {
        	Map<String, Object> response = new HashMap<String, Object>();
        	//added a dummy revision, this has to be replaced with list of files changes from previous revision or pull from FTP Server
        	Map<String, Object> revisionResponse = new HashMap<String, Object>();
        	revisionResponse.put("revision", "1");
        	revisionResponse.put("timestamp", new SimpleDateFormat(DATE_PATTERN).format(new Date()));
        	revisionResponse.put("user", "sekhar");
        	revisionResponse.put("revisionComment", "test comment");
            List<Map> modifiedFilesMapList = new ArrayList<Map>();
            Map<String, String> modifiedFileMap = new HashMap<String, String>();
            modifiedFileMap.put("fileName", "main");
            modifiedFileMap.put("action", "modified");
            modifiedFilesMapList.add(modifiedFileMap);
            revisionResponse.put("modifiedFiles", modifiedFilesMapList);
        	response.put("revision", revisionResponse);
        	return renderJSON(SUCCESS_RESPONSE_CODE, response);
        } catch (Throwable t) {
            LOGGER.warn("get latest revision: ", t);
            return renderJSON(INTERNAL_ERROR_RESPONSE_CODE, null);
        }
    }

    
    private GoPluginApiResponse handleSCMConfiguration() {
        Map<String, Object> response = new HashMap<String, Object>();
        response.put("url", createField("Ftp URL", null, true, true, false, "0"));
        response.put("username", createField("Username", null, false, false, false, "1"));
        response.put("password", createField("Password", null, false, false, true, "2"));
        response.put("home", createField("Ftp Home", null, true, false, false, "3"));
        response.put("rootDir", createField("Root Directory", null, true, false, false, "4"));
        return renderJSON(SUCCESS_RESPONSE_CODE, response);
    }

    private GoPluginApiResponse handleSCMView() throws IOException {
        Map<String, Object> response = new HashMap<String, Object>();
        response.put("displayValue", "FTP");
        response.put("template", IOUtils.toString(getClass().getResourceAsStream("/views/scm.template.html"), "UTF-8"));
        return renderJSON(SUCCESS_RESPONSE_CODE, response);
    }

    private GoPluginApiResponse handleSCMValidation(GoPluginApiRequest goPluginApiRequest) {
        Map<String, Object> responseMap = (Map<String, Object>) parseJSON(goPluginApiRequest.requestBody());
        Map<String, String> configuration = keyValuePairs(responseMap, "scm-configuration");
        final FTPConfig gitConfig = getGitConfig(configuration);
        Map<String, Object> response = new HashMap<String, Object>();
        ArrayList<String> messages = new ArrayList<String>();
        validate(gitConfig,response,messages);
        response.put("messages", messages);
        return renderJSON(SUCCESS_RESPONSE_CODE, messages);
    }

    private GoPluginApiResponse handleSCMCheckConnection(GoPluginApiRequest goPluginApiRequest) {
        Map<String, Object> responseMap = (Map<String, Object>) parseJSON(goPluginApiRequest.requestBody());
        Map<String, String> configuration = keyValuePairs(responseMap, "scm-configuration");
        FTPConfig gitConfig = getGitConfig(configuration);

        Map<String, Object> response = new HashMap<String, Object>();
        ArrayList<String> messages = new ArrayList<String>();
        checkConnection(gitConfig, response, messages);

        if (response.get("status") == null) {
            response.put("status", "success");
            messages.add("Could connect to URL successfully");
        }
        response.put("messages", messages);
        return renderJSON(SUCCESS_RESPONSE_CODE, response);
    }

    

    private GoPluginApiResponse handleCheckout(GoPluginApiRequest goPluginApiRequest) {
        Map<String, Object> responseMap = (Map<String, Object>) parseJSON(goPluginApiRequest.requestBody());
        Map<String, String> configuration = keyValuePairs(responseMap, "scm-configuration");
        FTPConfig gitConfig = getGitConfig(configuration);
       
        String destinationFolder = (String) responseMap.get("destination-folder");
        Map<String, Object> revisionMap = (Map<String, Object>) responseMap.get("revision");
        String revision = (String) revisionMap.get("revision");
        LOGGER.warn("destination: " + destinationFolder + ". commit: " + revision);

        try {
        	Map<String, Object> response = new HashMap<String, Object>();
            ArrayList<String> messages = new ArrayList<String>();
        	FtpHelper ftpHelper = getFTPHelper(gitConfig);
        	ftpHelper.fetchSourceCode(response,messages,destinationFolder);
            response.put("status", "success");
            messages.add("Checked out to revision " + revision);
            response.put("messages", messages);

            return renderJSON(SUCCESS_RESPONSE_CODE, response);
        } catch (Throwable t) {
            LOGGER.warn("files download issues: ", t);
            return renderJSON(INTERNAL_ERROR_RESPONSE_CODE, null);
        }
    }

    private FTPConfig getGitConfig(Map<String, String> configuration) {
        return new FTPConfig(configuration.get("url"), configuration.get("username"), configuration.get("password"), configuration.get("home"),configuration.get("rootDir"));
    }

    private FtpHelper getFTPHelper(FTPConfig gitConfig) {
        return new FtpHelper(gitConfig);
    }
    
    private Object parseJSON(String json) {
        return new GsonBuilder().create().fromJson(json, Object.class);
    }

    private Map<String, String> keyValuePairs(Map<String, Object> map, String mainKey) {
        Map<String, String> keyValuePairs = new HashMap<String, String>();
        Map<String, Object> fieldsMap = (Map<String, Object>) map.get(mainKey);
        for (String field : fieldsMap.keySet()) {
            Map<String, Object> fieldProperties = (Map<String, Object>) fieldsMap.get(field);
            String value = (String) fieldProperties.get("value");
            keyValuePairs.put(field, value);
        }
        return keyValuePairs;
    }

    private Map<String, Object> createField(String displayName, String defaultValue, boolean isPartOfIdentity, boolean isRequired, boolean isSecure, String displayOrder) {
        Map<String, Object> fieldProperties = new HashMap<String, Object>();
        fieldProperties.put("display-name", displayName);
        fieldProperties.put("default-value", defaultValue);
        fieldProperties.put("part-of-identity", isPartOfIdentity);
        fieldProperties.put("required", isRequired);
        fieldProperties.put("secure", isSecure);
        fieldProperties.put("display-order", displayOrder);
        return fieldProperties;
    }

    public void validate(FTPConfig gitConfig, Map<String, Object> response, ArrayList<String> messages) {
    	if (StringUtil.isEmpty(gitConfig.getUrl())) {
            response.put("status", "failure");
            messages.add("URL is empty");
        }else if(StringUtil.isEmpty(gitConfig.getUsername())) {
        	response.put("status", "failure");
            messages.add("Username is empty");
        }else if(StringUtil.isEmpty(gitConfig.getPassword())) {
        	response.put("status", "failure");
            messages.add("Password is empty");
        }else if(StringUtil.isEmpty(gitConfig.getHome())) {
        	response.put("status", "failure");
            messages.add("Home Directory is empty");
        }
    }

    public void checkConnection(FTPConfig gitConfig, Map<String, Object> response, ArrayList<String> messages) {
        try {
        	validate(gitConfig, response, messages);
            if(messages.size()==0){
    			FtpHelper ftpHelper = new FtpHelper(gitConfig);
            	boolean success = ftpHelper.checkConnection();
            	if(!success) {
            		response.put("status", "failure");
                    messages.add("Could not login to the server");
            	}
            }
        } catch (Exception e) {
            response.put("status", "failure");
            if (e.getMessage() != null) {
                messages.add(e.getMessage());
            } else {
                messages.add(e.getClass().getCanonicalName());
            }
        }
    }

    private GoPluginApiResponse renderJSON(final int responseCode, Object response) {
        final String json = response == null ? null : new GsonBuilder().create().toJson(response);
        return new GoPluginApiResponse() {
            @Override
            public int responseCode() {
                return responseCode;
            }

            @Override
            public Map<String, String> responseHeaders() {
                return null;
            }

            @Override
            public String responseBody() {
                return json;
            }
        };
    }
}
