package com.softwareco.intellij.plugin.models;

public class SessionSummary {
    private long currentDayMinutes = 0L;
    private long currentDayKeystrokes = 0L;
    private long currentDayKpm = 0L;
    private long currentDayLinesAdded = 0L;
    private long currentDayLinesRemoved = 0L;
    private long averageDailyMinutes = 0L;
    private long averageDailyKeystrokes = 0L;
    private long averageDailyKpm = 0L;
    private long averageLinesAdded = 0L;
    private long averageLinesRemoved = 0L;
    private long globalAverageSeconds = 0L;
    private long globalAverageDailyMinutes = 0L;
    private long globalAverageDailyKeystrokes = 0L;
    private long globalAverageLinesAdded = 0L;
    private long globalAverageLinesRemoved = 0L;

    public void clone(SessionSummary in) {
        this.currentDayMinutes = Math.max(in.getCurrentDayMinutes(), this.currentDayMinutes);
        this.currentDayKeystrokes = Math.max(in.getCurrentDayKeystrokes(), this.currentDayKeystrokes);
        this.currentDayKpm = Math.max(in.getCurrentDayKpm(), this.currentDayKpm);
        this.currentDayLinesAdded = Math.max(in.getCurrentDayLinesAdded(), this.currentDayLinesAdded);
        this.currentDayLinesRemoved = Math.max(in.getCurrentDayLinesRemoved(), this.currentDayLinesRemoved);
        this.cloneNonCurrentMetrics(in);
    }

    public void cloneNonCurrentMetrics(SessionSummary in) {
        this.averageDailyMinutes = in.getAverageDailyMinutes();
        this.averageDailyKeystrokes = in.getAverageDailyKeystrokes();
        this.averageDailyKpm = in.getAverageDailyKpm();
        this.averageLinesAdded = in.getAverageLinesAdded();
        this.averageLinesRemoved = in.getAverageLinesAdded();
        this.globalAverageSeconds = in.getGlobalAverageSeconds();
        this.globalAverageDailyMinutes = in.getGlobalAverageDailyMinutes();
        this.globalAverageDailyKeystrokes = in.getGlobalAverageDailyKeystrokes();
        this.globalAverageLinesAdded = in.getGlobalAverageLinesAdded();
        this.globalAverageLinesRemoved = in.getGlobalAverageLinesRemoved();
    }

    public long getCurrentDayMinutes() {
        return currentDayMinutes;
    }

    public void setCurrentDayMinutes(long currentDayMinutes) {
        this.currentDayMinutes = currentDayMinutes;
    }

    public long getCurrentDayKeystrokes() {
        return currentDayKeystrokes;
    }

    public void setCurrentDayKeystrokes(long currentDayKeystrokes) {
        this.currentDayKeystrokes = currentDayKeystrokes;
    }

    public long getCurrentDayKpm() {
        return currentDayKpm;
    }

    public void setCurrentDayKpm(long currentDayKpm) {
        this.currentDayKpm = currentDayKpm;
    }

    public long getCurrentDayLinesAdded() {
        return currentDayLinesAdded;
    }

    public void setCurrentDayLinesAdded(long currentDayLinesAdded) {
        this.currentDayLinesAdded = currentDayLinesAdded;
    }

    public long getCurrentDayLinesRemoved() {
        return currentDayLinesRemoved;
    }

    public void setCurrentDayLinesRemoved(long currentDayLinesRemoved) {
        this.currentDayLinesRemoved = currentDayLinesRemoved;
    }

    public long getAverageDailyMinutes() {
        return averageDailyMinutes;
    }

    public void setAverageDailyMinutes(long averageDailyMinutes) {
        this.averageDailyMinutes = averageDailyMinutes;
    }

    public long getAverageDailyKeystrokes() {
        return averageDailyKeystrokes;
    }

    public void setAverageDailyKeystrokes(long averageDailyKeystrokes) {
        this.averageDailyKeystrokes = averageDailyKeystrokes;
    }

    public long getAverageDailyKpm() {
        return averageDailyKpm;
    }

    public void setAverageDailyKpm(long averageDailyKpm) {
        this.averageDailyKpm = averageDailyKpm;
    }

    public long getAverageLinesAdded() {
        return averageLinesAdded;
    }

    public void setAverageLinesAdded(long averageLinesAdded) {
        this.averageLinesAdded = averageLinesAdded;
    }

    public long getAverageLinesRemoved() {
        return averageLinesRemoved;
    }

    public void setAverageLinesRemoved(long averageLinesRemoved) {
        this.averageLinesRemoved = averageLinesRemoved;
    }

    public long getGlobalAverageSeconds() {
        return globalAverageSeconds;
    }

    public void setGlobalAverageSeconds(long globalAverageSeconds) {
        this.globalAverageSeconds = globalAverageSeconds;
    }

    public long getGlobalAverageDailyMinutes() {
        return globalAverageDailyMinutes;
    }

    public void setGlobalAverageDailyMinutes(long globalAverageDailyMinutes) {
        this.globalAverageDailyMinutes = globalAverageDailyMinutes;
    }

    public long getGlobalAverageDailyKeystrokes() {
        return globalAverageDailyKeystrokes;
    }

    public void setGlobalAverageDailyKeystrokes(long globalAverageDailyKeystrokes) {
        this.globalAverageDailyKeystrokes = globalAverageDailyKeystrokes;
    }

    public long getGlobalAverageLinesAdded() {
        return globalAverageLinesAdded;
    }

    public void setGlobalAverageLinesAdded(long globalAverageLinesAdded) {
        this.globalAverageLinesAdded = globalAverageLinesAdded;
    }

    public long getGlobalAverageLinesRemoved() {
        return globalAverageLinesRemoved;
    }

    public void setGlobalAverageLinesRemoved(long globalAverageLinesRemoved) {
        this.globalAverageLinesRemoved = globalAverageLinesRemoved;
    }
}
