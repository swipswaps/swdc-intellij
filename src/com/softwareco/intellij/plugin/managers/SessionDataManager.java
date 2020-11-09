package com.softwareco.intellij.plugin.managers;


import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.softwareco.intellij.plugin.SoftwareCo;
import com.softwareco.intellij.plugin.SoftwareCoSessionManager;
import com.softwareco.intellij.plugin.SoftwareCoUtils;
import com.softwareco.intellij.plugin.SoftwareResponse;
import com.softwareco.intellij.plugin.models.ElapsedTime;
import com.softwareco.intellij.plugin.models.KeystrokeAggregate;
import com.softwareco.intellij.plugin.models.SessionSummary;
import org.apache.http.client.methods.HttpGet;

import java.lang.reflect.Type;

public class SessionDataManager {

    public static String getSessionDataSummaryFile() {
        String file = SoftwareCoSessionManager.getSoftwareDir(true);
        if (SoftwareCoUtils.isWindows()) {
            file += "\\sessionSummary.json";
        } else {
            file += "/sessionSummary.json";
        }
        return file;
    }

    public static void clearSessionSummaryData() {
        SessionSummary summary = new SessionSummary();
        FileManager.writeData(getSessionDataSummaryFile(), summary);
    }

    public static void treeDataUpdateCheck() {
        String day = SoftwareCoUtils.getTodayInStandardFormat();
        String currentDay = FileManager.getItem("updatedTreeDate", "");
        SessionSummary existingSummary = SessionDataManager.getSessionSummaryData();
        if (!currentDay.equals(day) || existingSummary.getGlobalAverageDailyMinutes() == 0) {
            updateSessionSummaryFromServer();
            FileManager.setItem("updatedTreeDate", day);
        }
    }

    private static void updateSessionSummaryFromServer() {
        SessionSummary summary = SessionDataManager.getSessionSummaryData();

        String jwt = FileManager.getItem("jwt");
        String api = "/sessions/summary?refresh=true";
        SoftwareResponse resp = SoftwareCoUtils.makeApiCall(api, HttpGet.METHOD_NAME, null, jwt);
        if (resp.isOk()) {
            JsonObject jsonObj = resp.getJsonObj();

            Type type = new TypeToken<SessionSummary>() {}.getType();
            SessionSummary fetchedSummary = SoftwareCo.gson.fromJson(jsonObj, type);

            // clone all
            summary.clone(fetchedSummary);

            TimeDataManager.updateSessionFromSummaryApi(fetchedSummary.getCurrentDayMinutes());

            // save the file
            FileManager.writeData(SessionDataManager.getSessionDataSummaryFile(), summary);
        }

        WallClockManager.getInstance().dispatchStatusViewUpdate();
    }

    public static SessionSummary getSessionSummaryData() {
        JsonObject jsonObj = FileManager.getFileContentAsJson(getSessionDataSummaryFile());
        if (jsonObj == null) {
            clearSessionSummaryData();
            jsonObj = FileManager.getFileContentAsJson(getSessionDataSummaryFile());
        }
        JsonElement lastUpdatedToday = jsonObj.get("lastUpdatedToday");
        if (lastUpdatedToday != null) {
            // make sure it's a boolean and not a number
            if (!lastUpdatedToday.getAsJsonPrimitive().isBoolean()) {
                // set it to boolean
                boolean newVal = lastUpdatedToday.getAsInt() != 0;
                jsonObj.addProperty("lastUpdatedToday", newVal);
            }
        }
        JsonElement inFlow = jsonObj.get("inFlow");
        if (inFlow != null) {
            // make sure it's a boolean and not a number
            if (!inFlow.getAsJsonPrimitive().isBoolean()) {
                // set it to boolean
                boolean newVal = inFlow.getAsInt() != 0;
                jsonObj.addProperty("inFlow", newVal);
            }
        }
        Type type = new TypeToken<SessionSummary>() {}.getType();
        SessionSummary summary = SoftwareCo.gson.fromJson(jsonObj, type);
        return summary;
    }

    public static void incrementSessionSummary(KeystrokeAggregate aggregate, long sessionSeconds) {
        SessionSummary summary = getSessionSummaryData();

        long sessionMinutes = sessionSeconds / 60;
        summary.setCurrentDayMinutes(summary.getCurrentDayMinutes() + sessionMinutes);

        summary.setCurrentDayKeystrokes(summary.getCurrentDayKeystrokes() + aggregate.keystrokes);
        summary.setCurrentDayLinesAdded(summary.getCurrentDayLinesAdded() + aggregate.linesAdded);
        summary.setCurrentDayLinesRemoved(summary.getCurrentDayLinesRemoved() + aggregate.linesRemoved);

        // save the file
        FileManager.writeData(getSessionDataSummaryFile(), summary);
    }

    public static ElapsedTime getTimeBetweenLastPayload() {
        ElapsedTime eTime = new ElapsedTime();

        // default of 1 minute
        long sessionSeconds = 60;
        long elapsedSeconds = 60;

        long lastPayloadEnd = FileManager.getNumericItem("latestPayloadTimestampEndUtc", 0L);
        if (lastPayloadEnd > 0) {
            SoftwareCoUtils.TimesData timesData = SoftwareCoUtils.getTimesData();
            elapsedSeconds = Math.max(60, timesData.now - lastPayloadEnd);
            long sessionThresholdSeconds = 60 * 15;
            if (elapsedSeconds > 0 && elapsedSeconds <= sessionThresholdSeconds) {
                sessionSeconds = elapsedSeconds;
            }
            sessionSeconds = Math.max(60, sessionSeconds);
        }

        eTime.sessionSeconds = sessionSeconds;
        eTime.elapsedSeconds = elapsedSeconds;

        return eTime;
    }
}
