/**
 * Copyright (c) 2018 by Software.com
 * All rights reserved
 */
package com.softwareco.intellij.plugin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.softwareco.intellij.plugin.fs.FileManager;
import com.softwareco.intellij.plugin.models.SessionSummary;
import com.softwareco.intellij.plugin.sessiondata.SessionDataManager;
import com.softwareco.intellij.plugin.tree.CodeTimeToolWindow;
import com.softwareco.intellij.plugin.wallclock.WallClockManager;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.SystemUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.HttpClientBuilder;

import javax.swing.*;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.*;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class SoftwareCoUtils {

    public static final Logger LOG = Logger.getLogger("SoftwareCoUtils");

    // set the api endpoint to use
    public final static String api_endpoint = "https://api.software.com";
    // set the launch url to use
    public final static String launch_url = "https://app.software.com";

    public static HttpClient httpClient;
    public static HttpClient pingClient;

    public static JsonParser jsonParser = new JsonParser();

    // sublime = 1, vs code = 2, eclipse = 3, intellij = 4, visual studio = 6, atom = 7
    public static int pluginId = 4;
    public static String VERSION = null;
    public static String pluginName = null;

    public final static ExecutorService EXECUTOR_SERVICE = Executors.newCachedThreadPool();

    private final static int EOF = -1;

    private static boolean fetchingResourceInfo = false;
    private static JsonObject lastResourceInfo = new JsonObject();
    private static boolean loggedInCacheState = false;

    private static boolean appAvailable = true;
    private static boolean showStatusText = true;
    private static String lastMsg = "";
    private static String lastTooltip = "";

    private static int lastDayOfMonth = 0;

    private static int DASHBOARD_LABEL_WIDTH = 25;
    private static int DASHBOARD_VALUE_WIDTH = 25;
    private static int MARKER_WIDTH = 4;

    private static String SERVICE_NOT_AVAIL =
            "Our service is temporarily unavailable.\n\nPlease try again later.\n";

    // jwt_from_apptoken_call
    public static String jwt = null;

    // Spotify variables
    private static String CLIENT_ID = null;
    private static String CLIENT_SECRET = null;
    private static String ACCESS_TOKEN = null;
    private static String REFRESH_TOKEN = null;
    private static String userStatus = null;
    private static boolean spotifyCacheState = false;
    public static String defaultbtn = "play";
    public static String spotifyUserId = null;
    public static List<String> playlistids = new ArrayList<>();
    public static String currentPlaylistId = null;
    public static List<String> playlistTracks = new ArrayList<>();
    public static String currentTrackId = null;
    public static String currentTrackName = null;
    public static List<String> spotifyDeviceIds = new ArrayList<>();
    public static String currentDeviceId = null;
    public static String currentDeviceName = null;
    public static int playerCounter = 0;
    public static String spotifyStatus = "Not Connected";

    // Slack variables
    private static boolean slackCacheState = false;

    static {
        // initialize the HttpClient
        RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(5000)
                .setConnectionRequestTimeout(5000)
                .setSocketTimeout(5000)
                .build();

        pingClient = HttpClientBuilder.create().setDefaultRequestConfig(config).build();
        httpClient = HttpClientBuilder.create().build();
    }

    public static boolean isLoggedIn() {
        return loggedInCacheState;
    }

    public static class UserStatus {
        public boolean loggedIn;
    }

    public static String getHostname() {
        List<String> cmd = new ArrayList<String>();
        cmd.add("hostname");
        String hostname = getSingleLineResult(cmd, 1);
        return hostname;
    }

    public static String getUserHomeDir() {
        return System.getProperty("user.home");
    }

    public static String getOs() {
        String osName = SystemUtils.OS_NAME;
        String osVersion = SystemUtils.OS_VERSION;
        String osArch = SystemUtils.OS_ARCH;

        String osInfo = "";
        if (osArch != null) {
            osInfo += osArch;
        }
        if (osInfo.length() > 0) {
            osInfo += "_";
        }
        if (osVersion != null) {
            osInfo += osVersion;
        }
        if (osInfo.length() > 0) {
            osInfo += "_";
        }
        if (osName != null) {
            osInfo += osName;
        }

        return osInfo;
    }

    public static boolean isLinux() {
        return (isWindows() || isMac()) ? false : true;
    }

    public static boolean isWindows() {
        return SystemInfo.isWindows;
    }

    public static boolean isMac() {
        return SystemInfo.isMac;
    }

    public static void updateServerStatus(boolean isOnlineStatus) {
        appAvailable = isOnlineStatus;
    }

    public static boolean isAppAvailable() {
        return appAvailable;
    }

    public static SoftwareResponse makeApiCall(String api, String httpMethodName, String payload) {
        return makeApiCall(api, httpMethodName, payload, null);
    }

    public static SoftwareResponse makeApiCall(String api, String httpMethodName, String payload, String overridingJwt) {

        SoftwareResponse softwareResponse = new SoftwareResponse();

        SoftwareHttpManager httpTask = null;
        if (api.contains("/ping") || api.contains("/sessions") || api.contains("/dashboard") || api.contains("/users/plugin/accounts")) {
            // if the server is having issues, we'll timeout within 5 seconds for these calls
            httpTask = new SoftwareHttpManager(api, httpMethodName, payload, overridingJwt, httpClient);
        } else {
            if (httpMethodName.equals(HttpPost.METHOD_NAME)) {
                // continue, POSTS encapsulated "invokeLater" with a timeout of 5 seconds
                httpTask = new SoftwareHttpManager(api, httpMethodName, payload, overridingJwt, pingClient);
            } else {
                if (!appAvailable) {
                    // bail out
                    softwareResponse.setIsOk(false);
                    return softwareResponse;
                }
                httpTask = new SoftwareHttpManager(api, httpMethodName, payload, overridingJwt, httpClient);
            }
        }
        Future<HttpResponse> response = EXECUTOR_SERVICE.submit(httpTask);

        //
        // Handle the Future if it exist
        //
        if (response != null) {
            try {
                HttpResponse httpResponse = response.get();
                if (httpResponse != null) {
                    int statusCode = httpResponse.getStatusLine().getStatusCode();
                    if (statusCode < 300) {
                        softwareResponse.setIsOk(true);
                    }
                    softwareResponse.setCode(statusCode);
                    HttpEntity entity = httpResponse.getEntity();
                    JsonObject jsonObj = null;
                    if (entity != null) {
                        try {
                            ContentType contentType = ContentType.getOrDefault(entity);
                            String mimeType = contentType.getMimeType();
                            String jsonStr = getStringRepresentation(entity);
                            softwareResponse.setJsonStr(jsonStr);
                            // LOG.log(Level.INFO, "Code Time: API response {0}", jsonStr);
                            if (jsonStr != null && mimeType.indexOf("text/plain") == -1) {
                                Object jsonEl = null;
                                try {
                                    jsonEl = jsonParser.parse(jsonStr);
                                } catch (Exception e) {
                                    //
                                }

                                if (jsonEl != null && jsonEl instanceof JsonElement) {
                                    try {
                                        JsonElement el = (JsonElement)jsonEl;
                                        if (el.isJsonPrimitive()) {
                                            if (statusCode < 300) {
                                                softwareResponse.setDataMessage(el.getAsString());
                                            } else {
                                                softwareResponse.setErrorMessage(el.getAsString());
                                            }
                                        } else {
                                            jsonObj = ((JsonElement) jsonEl).getAsJsonObject();
                                            softwareResponse.setJsonObj(jsonObj);
                                        }
                                    } catch (Exception e) {
                                        LOG.log(Level.WARNING, "Unable to parse response data: {0}", e.getMessage());
                                    }
                                }
                            }
                        } catch (IOException e) {
                            String errorMessage = pluginName + ": Unable to get the response from the http request, error: " + e.getMessage();
                            softwareResponse.setErrorMessage(errorMessage);
                            LOG.log(Level.WARNING, errorMessage);
                        }
                    }

                    if (statusCode >= 400 && statusCode < 500 && jsonObj != null) {
                        if (jsonObj.has("code")) {
                            String code = jsonObj.get("code").getAsString();
                            if (code != null && code.equals("DEACTIVATED")) {
                                softwareResponse.setDeactivated(true);
                            }
                        }
                    }
                }
            } catch (InterruptedException | ExecutionException e) {
                String errorMessage = pluginName + ": Unable to get the response from the http request, error: " + e.getMessage();
                softwareResponse.setErrorMessage(errorMessage);
                LOG.log(Level.WARNING, errorMessage);
            }
        }

        return softwareResponse;
    }

    private static String getStringRepresentation(HttpEntity res) throws IOException {
        if (res == null) {
            return null;
        }

        ContentType contentType = ContentType.getOrDefault(res);
        String mimeType = contentType.getMimeType();
        boolean isPlainText = mimeType.indexOf("text/plain") != -1;

        InputStream inputStream = res.getContent();

        // Timing information--- verified that the data is still streaming
        // when we are called (this interval is about 2s for a large response.)
        // So in theory we should be able to do somewhat better by interleaving
        // parsing and reading, but experiments didn't show any improvement.
        //

        StringBuffer sb = new StringBuffer();
        InputStreamReader reader;
        reader = new InputStreamReader(inputStream, Charset.forName("UTF-8"));

        BufferedReader br = new BufferedReader(reader);
        boolean done = false;
        while (!done) {
            String aLine = br.readLine();
            if (aLine != null) {
                sb.append(aLine);
                if (isPlainText) {
                    sb.append("\n");
                }
            } else {
                done = true;
            }
        }
        br.close();

        return sb.toString();
    }

    public static boolean showingStatusText() {
        return showStatusText;
    }

    public static void toggleStatusBar() {
        showStatusText = !showStatusText;

        WallClockManager.getInstance().dispatchStatusViewUpdate();

        // refresh the tree
        CodeTimeToolWindow.refresh();
    }

    public static Project getOpenProject() {
        ProjectManager projMgr = ProjectManager.getInstance();
        Project[] projects = projMgr.getOpenProjects();
        if (projects != null && projects.length > 0) {
            return projects[0];
        }
        return null;
    }

    public static void updateStatusBar(final String kpmIcon, final String kpmMsg,
                                        final String tooltip) {
        if ( showStatusText ) {
            lastMsg = kpmMsg;
            lastTooltip = tooltip;
        }

        ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
                ProjectManager pm = ProjectManager.getInstance();
                if (pm != null && pm.getOpenProjects() != null && pm.getOpenProjects().length > 0) {
                    try {
                        Project p = pm.getOpenProjects()[0];
                        final StatusBar statusBar = WindowManager.getInstance().getStatusBar(p);

                        String kpmmsgId = SoftwareCoStatusBarKpmTextWidget.KPM_TEXT_ID + "_kpmmsg";
                        String kpmiconId = SoftwareCoStatusBarKpmIconWidget.KPM_ICON_ID + "_kpmicon";

                        String kpmMsgVal = kpmMsg != null ? kpmMsg : pluginName;

                        String kpmIconVal = kpmIcon;
                        if (!showStatusText) {
                            kpmMsgVal = "";
                            kpmIconVal = "clock-blue.png";
                        }

                        // icon first
                        SoftwareCoStatusBarKpmIconWidget kpmIconWidget = (SoftwareCoStatusBarKpmIconWidget) statusBar.getWidget(kpmiconId);
                        if (kpmIconWidget == null) {
                            kpmIconWidget = buildStatusBarIconWidget(kpmIconVal, tooltip, kpmiconId);
                            statusBar.addWidget(kpmIconWidget, kpmiconId);
                        } else {
                            kpmIconWidget.updateIcon(kpmIconVal);
                            kpmIconWidget.setTooltip(tooltip);
                        }
                        statusBar.updateWidget(kpmiconId);

                        // text next
                        SoftwareCoStatusBarKpmTextWidget kpmMsgWidget = (SoftwareCoStatusBarKpmTextWidget) statusBar.getWidget(kpmmsgId);
                        if (kpmMsgWidget == null) {
                            kpmMsgWidget = buildStatusBarTextWidget(kpmMsgVal, tooltip, kpmmsgId);
                            statusBar.addWidget(kpmMsgWidget, kpmmsgId);
                        } else {
                            kpmMsgWidget.setText(kpmMsgVal);
                            kpmMsgWidget.setTooltip(tooltip);
                        }
                        statusBar.updateWidget(kpmmsgId);

                    } catch(Exception e){
                        //
                    }
                }
            }
        });
    }

    public static SoftwareCoStatusBarKpmTextWidget buildStatusBarTextWidget(String msg, String tooltip, String id) {
        SoftwareCoStatusBarKpmTextWidget textWidget =
                new SoftwareCoStatusBarKpmTextWidget(id);
        textWidget.setText(msg);
        textWidget.setTooltip(tooltip);
        return textWidget;
    }

    public static SoftwareCoStatusBarKpmIconWidget buildStatusBarIconWidget(String iconName, String tooltip, String id) {
        Icon icon = IconLoader.findIcon("/com/softwareco/intellij/plugin/assets/" + iconName);

        SoftwareCoStatusBarKpmIconWidget iconWidget =
                new SoftwareCoStatusBarKpmIconWidget(id);
        iconWidget.setIcon(icon);
        iconWidget.setTooltip(tooltip);
        return iconWidget;
    }

    public static String humanizeMinutes(long minutes) {
        String str = "";
        if (minutes == 60) {
            str = "1 hr";
        } else if (minutes > 60) {
            float hours = (float)minutes / 60;
            try {
                if (hours % 1 == 0) {
                    // don't return a number with 2 decimal place precision
                    str = String.format("%.0f", hours) + " hrs";
                } else {
                    // hours = Math.round(hours * 10) / 10;
                    str = String.format("%.1f", hours) + " hrs";
                }
            } catch (Exception e) {
                str = String.format("%s hrs", String.valueOf(Math.round(hours)));
            }
        } else if (minutes == 1) {
            str = "1 min";
        } else {
            str = minutes + " min";
        }
        return str;
    }

    protected static boolean isItunesRunning() {
        // get running of application "iTunes"
        String[] args = { "osascript", "-e", "get running of application \"iTunes\"" };
        String result = runCommand(args, null);
        return (result != null) ? Boolean.valueOf(result) : false;
    }

    protected static String itunesTrackScript = "tell application \"iTunes\"\n" +
            "set track_artist to artist of current track\n" +
            "set track_album to album of current track\n" +
            "set track_name to name of current track\n" +
            "set track_duration to duration of current track\n" +
            "set track_id to id of current track\n" +
            "set track_genre to genre of current track\n" +
            "set track_state to player state\n" +
            "set json to \"type='itunes';album='\" & track_album & \"';genre='\" & track_genre & \"';artist='\" & track_artist & \"';id='\" & track_id & \"';name='\" & track_name & \"';state='\" & track_state & \"';duration='\" & track_duration & \"'\"\n" +
            "end tell\n" +
            "return json\n";

    protected static String getItunesTrack() {
        String[] args = { "osascript", "-e", itunesTrackScript };
        return runCommand(args, null);
    }

    protected static boolean isSpotifyRunning() {
        String[] args = { "osascript", "-e", "get running of application \"Spotify\"" };
        String result = runCommand(args, null);
        return (result != null) ? Boolean.valueOf(result) : false;
    }

    protected static boolean isSpotifyInstalled() {
        String[] args = { "osascript", "-e", "exists application \"Spotify\"" };
        String result = runCommand(args, null);
        return (result != null) ? Boolean.valueOf(result) : false;
    }

    protected static String spotifyTrackScript = "tell application \"Spotify\"\n" +
            "set track_artist to artist of current track\n" +
            "set track_album to album of current track\n" +
            "set track_name to name of current track\n" +
            "set track_duration to duration of current track\n" +
            "set track_id to id of current track\n" +
            "set track_state to player state\n" +
            "set json to \"type='spotify';album='\" & track_album & \"';genre='';artist='\" & track_artist & \"';id='\" & track_id & \"';name='\" & track_name & \"';state='\" & track_state & \"';duration='\" & track_duration & \"'\"\n" +
            "end tell\n" +
            "return json\n";

    protected static String getSpotifyTrack() {
        String[] args = { "osascript", "-e", spotifyTrackScript };
        return runCommand(args, null);
    }

    protected static String startPlayer(String playerName) {
        String[] args = { "open", "-a", playerName + ".app" };
        return runCommand(args, null);
    }

    protected static String playPlayer(String playerName) {
        String[] args = { "osascript", "-e", "tell application \""+ playerName + "\" to play" };
        return runCommand(args, null);
    }

    protected static String pausePlayer(String playerName) {
        String[] args = { "osascript", "-e", "tell application \""+ playerName + "\" to pause" };
        return runCommand(args, null);
    }

    protected static String previousTrack(String playerName) {
        String[] args = { "osascript", "-e", "tell application \""+ playerName + "\" to play (previous track)" };
        return runCommand(args, null);
    }

    protected static String nextTrack(String playerName) {
        String[] args = { "osascript", "-e", "tell application \""+ playerName + "\" to play (next track)" };
        return runCommand(args, null);
    }

    protected static String stopPlayer(String playerName) {
        // `ps -ef | grep "${appName}" | grep -v grep | awk '{print $2}' | xargs kill`;
        String[] args = { "ps", "-ef", "|", "grep", "\"" + playerName + ".app\"", "|", "grep", "-v", "grep", "|", "awk", "'{print $2}'", "|", "xargs", "kill" };
        return runCommand(args, null);
    }

    /**
     * Execute the args
     * @param args
     * @return
     */
    public static String runCommand(String[] args, String dir) {
        // use process builder as it allows to run the command from a specified dir
        ProcessBuilder builder = new ProcessBuilder();

        try {
            builder.command(args);
            if (dir != null) {
                // change to the directory to run the command
                builder.directory(new File(dir));
            }
            Process process = builder.start();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            InputStream is = process.getInputStream();
            copyLarge(is, baos, new byte[4096]);
            return baos.toString().trim();

        } catch (Exception e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    public static List<String> getCommandResult(List<String> cmdList, String dir) {
        String[] args = Arrays.copyOf(cmdList.toArray(), cmdList.size(), String[].class);
        List<String> results = new ArrayList<>();
        String result = runCommand(args, dir);
        if (result == null || result.trim().length() == 0) {
            return results;
        }
        String[] contentList = result.split("\n");
        results =  Arrays.asList(contentList);
        return results;
    }

    private static long copyLarge(InputStream input, OutputStream output, byte[] buffer) throws IOException {

        long count = 0;
        int n;
        while (EOF != (n = input.read(buffer))) {
            output.write(buffer, 0, n);
            count += n;
        }
        return count;
    }

    // get the git resource config information
    public static JsonObject getResourceInfo(String projectDir) {
        if (fetchingResourceInfo) {
            return null;
        }

        fetchingResourceInfo = true;
        lastResourceInfo = new JsonObject();

        // is the project dir avail?
        if (projectDir != null && !projectDir.equals("")) {
            try {
                String[] branchCmd = { "git", "symbolic-ref", "--short", "HEAD" };
                String branch = runCommand(branchCmd, projectDir);

                String[] identifierCmd = { "git", "config", "--get", "remote.origin.url" };
                String identifier = runCommand(identifierCmd, projectDir);

                String[] emailCmd = { "git", "config", "user.email" };
                String email = runCommand(emailCmd, projectDir);

                String[] tagCmd = { "git", "describe", "--all" };
                String tag = runCommand(tagCmd, projectDir);

                if (StringUtils.isNotBlank(branch) && StringUtils.isNotBlank(identifier)) {
                    lastResourceInfo.addProperty("identifier", identifier);
                    lastResourceInfo.addProperty("branch", branch);
                    lastResourceInfo.addProperty("email", email);
                    lastResourceInfo.addProperty("tag", tag);
                }
            } catch (Exception e) {
                //
            }
        }

        fetchingResourceInfo = false;

        return lastResourceInfo;
    }

    public static void launchSoftwareTopForty() {
        BrowserUtil.browse("http://api.software.com/music/top40");
    }

    public static void submitGitIssue() {
        BrowserUtil.browse("https://github.com/swdotcom/swdc-intellij/issues");
    }

    public static void submitFeedback() {
        BrowserUtil.browse("mailto:cody@software.com");
    }

    public static void buildCodeTimeMetricsDashboard() {
        String summaryInfoFile = SoftwareCoSessionManager.getSummaryInfoFile(true);
        String dashboardFile = SoftwareCoSessionManager.getCodeTimeDashboardFile();

        Writer writer = null;
        String api = "/dashboard?linux=" + SoftwareCoUtils.isLinux() + "&showToday=false";
        String dashboardSummary = SoftwareCoUtils.makeApiCall(api, HttpGet.METHOD_NAME, null).getJsonStr();
        if (dashboardSummary == null || dashboardSummary.trim().isEmpty()) {
            dashboardSummary = SERVICE_NOT_AVAIL;
        }

        // write the summary content
        try {
            writer = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(summaryInfoFile), StandardCharsets.UTF_8));
            writer.write(dashboardSummary);
        } catch (IOException ex) {
            // Report
        } finally {
            try {writer.close();} catch (Exception ex) {/*ignore*/}
        }

        // concat summary info with the dashboard file
        String dashboardContent = "";
        SimpleDateFormat formatDayTime = new SimpleDateFormat("EEE, MMM d h:mma");
        SimpleDateFormat formatDay = new SimpleDateFormat("EEE, MMM d");
        String lastUpdatedStr = formatDayTime.format(new Date());
        dashboardContent += "Code Time          (Last updated on " + lastUpdatedStr + ")";
        dashboardContent += "\n\n";
        String todayStr = formatDay.format(new Date());
        dashboardContent += getSectionHeader("Today (" + todayStr + ")");

        SessionSummary summary = SessionDataManager.fetchSessionSummary(false);
        if (summary != null) {
            long currentDayMinutes = summary.getCurrentDayMinutes();

            long averageDailyMinutes = summary.getAverageDailyMinutes();

            String currentDayTimeStr = SoftwareCoUtils.humanizeMinutes(currentDayMinutes);
            String averageDailyMinutesTimeStr = SoftwareCoUtils.humanizeMinutes(averageDailyMinutes);
            String wcTime = SoftwareCoUtils.humanizeMinutes(WallClockManager.getInstance().getWcTimeInSeconds() / 60);

            dashboardContent += getDashboardRow("Editor time today", wcTime);
            dashboardContent += getDashboardRow("Hours coded today", currentDayTimeStr);
            dashboardContent += getDashboardRow("90-day avg", averageDailyMinutesTimeStr);
            dashboardContent += "\n";
        }

        // append the summary content
        String infoFileContent = FileManager.getFileContent(summaryInfoFile);
        if (infoFileContent != null) {
            dashboardContent += infoFileContent;
        }

        // write the dashboard content to the dashboard file
        FileManager.saveFileContent(dashboardFile, dashboardContent);

    }

    public static void launchCodeTimeMetricsDashboard() {
        if (!SoftwareCoSessionManager.isServerOnline()) {
            SoftwareCoUtils.showOfflinePrompt(false);
        }
        Project p = getOpenProject();
        if (p == null) {
            return;
        }

        buildCodeTimeMetricsDashboard();

        String codeTimeFile = SoftwareCoSessionManager.getCodeTimeDashboardFile();
        File f = new File(codeTimeFile);

        VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(f);
        OpenFileDescriptor descriptor = new OpenFileDescriptor(p, vFile);
        FileEditorManager.getInstance(p).openTextEditor(descriptor, true);
    }

    private static String getSingleLineResult(List<String> cmd, int maxLen) {
        String result = null;
        String[] cmdArgs = Arrays.copyOf(cmd.toArray(), cmd.size(), String[].class);
        String content = SoftwareCoUtils.runCommand(cmdArgs, null);

        // for now just get the 1st one found
        if (content != null) {
            String[] contentList = content.split("\n");
            if (contentList != null && contentList.length > 0) {
                int len = (maxLen != -1) ? Math.min(maxLen, contentList.length) : contentList.length;
                for (int i = 0; i < len; i++) {
                    String line = contentList[i];
                    if (line != null && line.trim().length() > 0) {
                        result = line.trim();
                        break;
                    }
                }
            }
        }
        return result;
    }

    public static String getOsUsername() {
        String username = System.getProperty("user.name");
        if (username == null || username.trim().equals("")) {
            try {
                List<String> cmd = new ArrayList<String>();
                if (SoftwareCoUtils.isWindows()) {
                    cmd.add("cmd");
                    cmd.add("/c");
                    cmd.add("whoami");
                } else {
                    cmd.add("/bin/sh");
                    cmd.add("-c");
                    cmd.add("whoami");
                }
                username = getSingleLineResult(cmd, -1);
            } catch (Exception e) {
                //
            }
        }
        return username;
    }

    public static String getAppJwt(boolean serverIsOnline) {
        if (serverIsOnline) {
            long now = Math.round(System.currentTimeMillis() / 1000);
            String api = "/data/apptoken?token=" + now;
            SoftwareResponse resp = SoftwareCoUtils.makeApiCall(api, HttpGet.METHOD_NAME, null);
            if (resp.isOk()) {
                JsonObject obj = resp.getJsonObj();
                return obj.get("jwt").getAsString();
            }
        }
        return null;
    }

    public static String createAnonymousUser(boolean serverIsOnline) {
        // make sure we've fetched the app jwt
        String appJwt = getAppJwt(serverIsOnline);

        if (serverIsOnline && appJwt != null) {
            String timezone = TimeZone.getDefault().getID();

            JsonObject payload = new JsonObject();
            payload.addProperty("username", getOsUsername());
            payload.addProperty("timezone", timezone);
            payload.addProperty("hostname", getHostname());
            payload.addProperty("creation_annotation", "NO_SESSION_FILE");

            String api = "/data/onboard";
            SoftwareResponse resp = SoftwareCoUtils.makeApiCall(api, HttpPost.METHOD_NAME, payload.toString(), appJwt);
            if (resp.isOk()) {
                // check if we have the data and jwt
                // resp.data.jwt and resp.data.user
                // then update the session.json for the jwt
                JsonObject data = resp.getJsonObj();
                // check if we have any data
                if (data != null && data.has("jwt")) {
                    String dataJwt = data.get("jwt").getAsString();
                    SoftwareCoSessionManager.setItem("jwt", dataJwt);
                    return dataJwt;
                }
            }
        }
        return null;
    }

    private static JsonObject getUser(boolean serverIsOnline) {
        String jwt = SoftwareCoSessionManager.getItem("jwt");
        if (serverIsOnline) {
            String api = "/users/me";
            SoftwareResponse resp = SoftwareCoUtils.makeApiCall(api, HttpGet.METHOD_NAME, null, jwt);
            if (resp.isOk()) {
                // check if we have the data and jwt
                // resp.data.jwt and resp.data.user
                // then update the session.json for the jwt
                JsonObject obj = resp.getJsonObj();
                if (obj != null && obj.has("data")) {
                    return obj.get("data").getAsJsonObject();
                }
            }
        }
        return null;
    }

    private static String regex = "^\\S+@\\S+\\.\\S+$";
    private static Pattern pattern = Pattern.compile(regex);

    private static boolean validateEmail(String email) {
        return pattern.matcher(email).matches();
    }

    private static boolean isLoggedOn(boolean serverIsOnline) {
        String pluginjwt = SoftwareCoSessionManager.getItem("jwt");
        if (serverIsOnline) {
            JsonObject userObj = getUser(serverIsOnline);
            if (userObj != null && userObj.has("email")) {
                // check if the email is valid
                String email = userObj.get("email").getAsString();
                if (validateEmail(email)) {
                    SoftwareCoSessionManager.setItem("jwt", userObj.get("plugin_jwt").getAsString());
                    SoftwareCoSessionManager.setItem("name", email);
                    return true;
                }
            }

            String api = "/users/plugin/state";
            SoftwareResponse resp = SoftwareCoUtils.makeApiCall(api, HttpGet.METHOD_NAME, null, pluginjwt);
            if (resp.isOk()) {
                // check if we have the data and jwt
                // resp.data.jwt and resp.data.user
                // then update the session.json for the jwt
                JsonObject data = resp.getJsonObj();
                String state = (data != null && data.has("state")) ? data.get("state").getAsString() : "UNKNOWN";
                // check if we have any data
                if (state.equals("OK")) {
                    String dataJwt = data.get("jwt").getAsString();
                    SoftwareCoSessionManager.setItem("jwt", dataJwt);
                    String dataEmail = data.get("email").getAsString();
                    if (dataEmail != null) {
                        SoftwareCoSessionManager.setItem("name", dataEmail);
                    }
                    return true;
                } else if (state.equals("NOT_FOUND")) {
                    SoftwareCoSessionManager.setItem("jwt", null);
                }
            }
        }
        SoftwareCoSessionManager.setItem("name", null);
        return false;
    }


    public static synchronized UserStatus getUserStatus() {
        UserStatus currentUserStatus = new UserStatus();

        if (loggedInCacheState) {
            currentUserStatus.loggedIn = loggedInCacheState;
            return currentUserStatus;
        }

        // check if they're logged on
        boolean serverIsOnline = SoftwareCoSessionManager.isServerOnline();

        boolean loggedIn = isLoggedOn(serverIsOnline);

        currentUserStatus.loggedIn = loggedIn;

        loggedInCacheState = loggedIn;
        return currentUserStatus;
    }

    public static void sendHeartbeat(String reason) {
        boolean serverIsOnline = SoftwareCoSessionManager.isServerOnline();
        String jwt = SoftwareCoSessionManager.getItem("jwt");
        if (serverIsOnline && jwt != null) {

            long start = Math.round(System.currentTimeMillis() / 1000);

            JsonObject payload = new JsonObject();
            payload.addProperty("pluginId", pluginId);
            payload.addProperty("os", getOs());
            payload.addProperty("start", start);
            payload.addProperty("version", VERSION);
            payload.addProperty("hostname", getHostname());
            payload.addProperty("trigger_annotation", reason);

            String api = "/data/heartbeat";
            SoftwareResponse resp = SoftwareCoUtils.makeApiCall(api, HttpPost.METHOD_NAME, payload.toString(), jwt);
            if (!resp.isOk()) {
                LOG.log(Level.WARNING, pluginName + ": unable to send heartbeat ping");
            }
        }
    }

    public static void showOfflinePrompt(boolean isTenMinuteReconnect) {
        final String reconnectMsg = (isTenMinuteReconnect) ? "in ten minutes. " : "soon. ";
        ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
                String infoMsg = "Our service is temporarily unavailable. " +
                        "We will try to reconnect again " + reconnectMsg +
                        "Your status bar will not update at this time.";
                // ask to download the PM
                Messages.showInfoMessage(infoMsg, pluginName);
            }
        });
    }

    public static void showMsgPrompt(String infoMsg) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
                Messages.showInfoMessage(infoMsg, pluginName);
            }
        });
    }

    public static Date atStartOfDay(Date date) {
        LocalDateTime localDateTime = dateToLocalDateTime(date);
        LocalDateTime startOfDay = localDateTime.with(LocalTime.MIN);
        return localDateTimeToDate(startOfDay);
    }

    public static Date atEndOfDay(Date date) {
        LocalDateTime localDateTime = dateToLocalDateTime(date);
        LocalDateTime endOfDay = localDateTime.with(LocalTime.MAX);
        return localDateTimeToDate(endOfDay);
    }

    private static LocalDateTime dateToLocalDateTime(Date date) {
        return LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }

    private static Date localDateTimeToDate(LocalDateTime localDateTime) {
        return Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
    }

    // the timestamps are all in seconds
    public static class TimesData {
        public Integer offset = ZonedDateTime.now().getOffset().getTotalSeconds();
        public long now = System.currentTimeMillis() / 1000;
        public long local_now = now + offset;
        public String timezone = TimeZone.getDefault().getID();
        public long local_start_day = atStartOfDay(new Date(local_now * 1000)).toInstant().getEpochSecond();
        public long local_end_day = atEndOfDay(new Date(local_now * 1000)).toInstant().getEpochSecond();
        public long utc_end_day = atEndOfDay(new Date(now * 1000)).toInstant().getEpochSecond();
    }


    public static TimesData getTimesData() {
        TimesData timesData = new TimesData();
        return timesData;
    }

    public static String getTodayInStandardFormat() {
        SoftwareCoUtils.TimesData timesData = SoftwareCoUtils.getTimesData();
        SimpleDateFormat formatDay = new SimpleDateFormat("YYYY-MM-dd");
        String day = formatDay.format(new Date(timesData.local_now * 1000));
        return day;
    }

    public static String getDashboardRow(String label, String value) {
        String content = getDashboardLabel(label) + " : " + getDashboardValue(value) + "\n";
        return content;
    }

    public static String getSectionHeader(String label) {
        String content = label + "\n";
        // add 3 to account for the " : " between the columns
        int dashLen = DASHBOARD_LABEL_WIDTH + DASHBOARD_VALUE_WIDTH + 15;
        for (int i = 0; i < dashLen; i++) {
            content += "-";
        }
        content += "\n";
        return content;
    }

    public static String getDashboardLabel(String label) {
        return getDashboardDataDisplay(DASHBOARD_LABEL_WIDTH, label);
    }

    public static String getDashboardValue(String value) {
        String valueContent = getDashboardDataDisplay(DASHBOARD_VALUE_WIDTH, value);
        String paddedContent = "";
        for (int i = 0; i < 11; i++) {
            paddedContent += " ";
        }
        paddedContent += valueContent;
        return paddedContent;
    }

    public static String getDashboardDataDisplay(int widthLen, String data) {
        int len = widthLen - data.length();
        String content = "";
        for (int i = 0; i < len; i++) {
            content += " ";
        }
        return content + "" + data;
    }

}
