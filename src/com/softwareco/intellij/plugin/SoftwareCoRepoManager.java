package com.softwareco.intellij.plugin;

import com.google.common.collect.Maps;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.intellij.openapi.diagnostic.Logger;

import java.net.URLEncoder;
import java.util.*;

public class SoftwareCoRepoManager {

    public static final Logger log = Logger.getInstance("SoftwareCoRepoManager");

    private static SoftwareCoRepoManager instance = null;

    private Map<String, JsonObject> latestCommitMap = new HashMap<>();

    private JsonObject currentTrack = new JsonObject();

    public static SoftwareCoRepoManager getInstance() {
        if (instance == null) {
            instance = new SoftwareCoRepoManager();
        }
        return instance;
    }

    private String buildRepoKey(String identifier, String branch, String tag) {
        return identifier + "_" + branch + "_" + tag;
    }

    public void getLatestCommit(String projectDir) {
        JsonObject resource = SoftwareCoUtils.getResourceInfo(projectDir);
        if (resource.has("identifier")) {
            String identifier = resource.get("identifier").getAsString();
            String tag = (resource.has("tag")) ? resource.get("tag").getAsString() : "";
            String branch = (resource.has("branch")) ? resource.get("branch").getAsString() : "";

            String repoKey = this.buildRepoKey(identifier, branch, tag);

            if (!latestCommitMap.containsKey(repoKey) || latestCommitMap.get(repoKey) == null) {
                try {
                    String encodedIdentifier = URLEncoder.encode(identifier, "UTF-8");
                    String encodedTag = URLEncoder.encode(tag, "UTF-8");
                    String encodedBranch = URLEncoder.encode(branch, "UTF-8");

                    String qryString = "identifier=" + encodedIdentifier;
                    qryString += "&tag=" + encodedTag;
                    qryString += "&branch=" + encodedBranch;

                    SoftwareCoUtils.HttpResponseInfo responseData = SoftwareCoUtils.getResponseInfo(
                            SoftwareCoSessionManager.makeApiCall("/commits/latest?" + qryString, false, null));
                    if (responseData != null && responseData.isOk) {
                        JsonObject payload = responseData.jsonObj;
                        // will get a single commit object back with the following attributes
                        // commitId, message, changes, timestamp
                        JsonObject latestCommit = payload.get("commit").getAsJsonObject();
                        latestCommitMap.put(repoKey, latestCommit);
                    } else {
                        log.debug("Software.com: Unable to fetch latest commit info");
                    }
                } catch (Exception e) {
                    //
                }
            }
        }
    }

    public void getHistoricalCommits(String projectDir) {
        JsonObject resource = SoftwareCoUtils.getResourceInfo(projectDir);
        if (resource.has("identifier")) {
            String identifier = resource.get("identifier").getAsString();
            String tag = (resource.has("tag")) ? resource.get("tag").getAsString() : "";
            String branch = (resource.has("branch")) ? resource.get("branch").getAsString() : "";
            String email = (resource.has("email")) ? resource.get("email").getAsString() : "";
            String repoKey = this.buildRepoKey(identifier, branch, tag);

            getLatestCommit(projectDir);

            String sinceOption = null;
            JsonObject latestCommit = latestCommitMap.get(repoKey);
            if (latestCommit != null && latestCommit.has("timestamp")) {
                long unixTs = latestCommit.get("timestamp").getAsLong();
                sinceOption = "--since=" + unixTs;
            }

            String authorOption = "--author=" + email;
            List<String> cmdList = new ArrayList<String>();
            cmdList.add("git");
            cmdList.add("log");
            cmdList.add("--stat");
            cmdList.add("--pretty=COMMIT:%H,%ct,%cI,%s");
            cmdList.add(authorOption);
            if (sinceOption != null) {
                cmdList.add(sinceOption);
            }

            // String[] commitHistoryCmd = {"git", "log", "--stat", "--pretty=COMMIT:%H,%ct,%cI,%s", authorOption};

            String[] commitHistoryCmd = Arrays.copyOf(cmdList.toArray(), cmdList.size(), String[].class);
            String historyContent = SoftwareCoUtils.runCommand(commitHistoryCmd, projectDir);

            if (historyContent == null || historyContent.isEmpty()) {
                return;
            }

            String latestCommitId = (latestCommit != null && latestCommit.has("commitId")) ?
                    latestCommit.get("commitId").getAsString() : null;

            // split the content
            JsonArray commits = new JsonArray();
            JsonObject commit = null;
            String[] historyContentList = historyContent.split("\n");
            if (historyContentList != null && historyContentList.length > 0) {
                for (String line : historyContentList) {
                    line = line.trim();
                    if (line.indexOf("COMMIT:") == 0) {
                        line = line.substring("COMMIT:".length());
                        if (commit != null) {
                            commits.add(commit);
                        }
                        // split by comma
                        String[] commitInfos = line.split(",");
                        if (commitInfos != null && commitInfos.length > 3) {
                            String commitId = commitInfos[0].trim();
                            if (latestCommitId != null && commitId.equals(latestCommitId)) {
                                commit = null;
                                // go to the next one
                                continue;
                            }
                            long timestamp = Long.valueOf(commitInfos[1].trim());
                            String date = commitInfos[2].trim();
                            String message = commitInfos[3].trim();
                            commit = new JsonObject();
                            commit.addProperty("commitId", commitId);
                            commit.addProperty("timestamp", timestamp);
                            commit.addProperty("date", date);
                            commit.addProperty("message", message);
                            JsonObject sftwTotalsObj = new JsonObject();
                            sftwTotalsObj.addProperty("insertions", 0);
                            sftwTotalsObj.addProperty("deletions", 0);
                            JsonObject changesObj = new JsonObject();
                            changesObj.add("__sftwTotal__", sftwTotalsObj);
                            commit.add("changes", changesObj);
                        }
                    } else if (commit != null && line.indexOf("|") != -1) {
                        line = line.replaceAll("\\s+"," ");
                        String[] fileInfos = line.split(" ");
                        if (fileInfos != null && fileInfos.length > 3) {
                            String file = fileInfos[0].trim();
                            String addAndDeletes = fileInfos[3].trim();
                            // count the number of plus signs and negative signs to find
                            // out how many additions and deletions per file
                            int len = addAndDeletes.length();
                            int lastPlusIdx = addAndDeletes.lastIndexOf("+");
                            int insertions = 0;
                            int deletions = 0;
                            if (lastPlusIdx != -1) {
                                insertions = lastPlusIdx + 1;
                                deletions = len - insertions;
                            } else if (len > 0) {
                                // all deletions
                                deletions = len;
                            }
                            JsonObject fileChanges = new JsonObject();
                            fileChanges.addProperty("insertions", insertions);
                            fileChanges.addProperty("deletions", deletions);
                            JsonObject changesObj = commit.get("changes").getAsJsonObject();
                            changesObj.add(file, fileChanges);

                            JsonObject swftTotalsObj = changesObj.get("__sftwTotal__").getAsJsonObject();
                            int insertionTotal = swftTotalsObj.get("insertions").getAsInt() + insertions;
                            int deletionsTotal = swftTotalsObj.get("deletions").getAsInt() + deletions;
                            swftTotalsObj.addProperty("insertions", insertionTotal);
                            swftTotalsObj.addProperty("deletions", deletionsTotal);
                        }
                    }
                }

                if (commit != null) {
                    commits.add(commit);
                }

                if (commits != null && commits.size() > 0) {
                    // send it in batches of 100
                    JsonArray batch = new JsonArray();
                    for (int i = 0; i < commits.size(); i++) {
                        batch.add(commits.get(i));
                        if (i > 0 && i % 100 == 0) {
                            this.processCommits(batch, identifier, tag, branch);
                            batch = new JsonArray();
                        }
                    }
                    if (batch.size() > 0) {
                        this.processCommits(batch, identifier, tag, branch);
                    }
                }
            }
        }
    }

    private void processCommits(JsonArray commits, String identifier, String tag, String branch) {
        try {

            // send the commits
            JsonObject commitData = new JsonObject();
            commitData.add("commits", commits);
            commitData.addProperty("identifier", identifier);
            commitData.addProperty("tag", tag);
            commitData.addProperty("branch", branch);
            String commitDataStr = commitData.toString();

            SoftwareCoUtils.HttpResponseInfo responseData = SoftwareCoUtils.getResponseInfo(
                    SoftwareCoSessionManager.makeApiCall(
                            "/commits", true, commitDataStr));

            if (responseData != null) {

                // {"status":"success","message":"Updated commits"}
                // {"status":"failed","data":"Unable to process commits data"}
                JsonObject responseObj = responseData.jsonObj;
                String message = "";
                if (responseObj.has("data")) {
                    JsonObject data = responseObj.get("data").getAsJsonObject();
                    message = data.get("message").getAsString();
                } else if (responseObj.has("message")) {
                    message = responseObj.get("message").getAsString();
                }

                log.debug("Software.com: completed commits update - " + message);
            } else {
                log.debug("Software.com: Unable to process repo commits");
            }
        } catch (Exception e) {
            log.debug("Software.com: Unable to process repo commits, error: " + e.getMessage());
        }
    }

    public void processRepoMembersInfo(final String projectDir) {
        JsonObject resource = SoftwareCoUtils.getResourceInfo(projectDir);
        if (resource.has("identifier")) {
            String identifier = resource.get("identifier").getAsString();
            String tag = (resource.has("tag")) ? resource.get("tag").getAsString() : "";
            String branch = (resource.has("branch")) ? resource.get("branch").getAsString(): "";

            String[] identifierCmd = { "git", "log", "--pretty=%an,%ae" };
            String devOutput = SoftwareCoUtils.runCommand(identifierCmd, projectDir);

            // split the output
            String[] devList = devOutput.split("\n");
            JsonArray members = new JsonArray();
            Map<String, String> memberMap = Maps.newHashMap();
            if (devList != null && devList.length > 0) {
                for (String line : devList) {
                    line = line.trim();
                    String[] parts = line.split(",");
                    if (parts != null && parts.length > 1) {
                        String name = parts[0].trim();
                        String email = parts[1].trim();
                        if (!memberMap.containsKey(email)) {
                            memberMap.put(email, name);
                            JsonObject json = new JsonObject();
                            json.addProperty("email", email);
                            json.addProperty("name", name);
                            members.add(json);
                        }
                    }
                }
            }

            if (members.size() > 0) {
                // send the members
                try {
                    JsonObject repoData = new JsonObject();
                    repoData.add("members", members);
                    repoData.addProperty("identifier", identifier);
                    repoData.addProperty("tag", tag);
                    repoData.addProperty("branch", branch);
                    String repoDataStr = repoData.toString();
                    JsonObject responseData = SoftwareCoUtils.getResponseInfo(
                            SoftwareCoSessionManager.makeApiCall(
                                    "/repo/members", true, repoDataStr)).jsonObj;

                    // {"status":"success","message":"Updated repo members"}
                    // {"status":"failed","data":"Unable to process repo information"}
                    if (responseData != null && responseData.has("message")) {
                        log.debug("Software.com: " + responseData.get("message").getAsString());
                    } else if (responseData != null && responseData.has("data")) {
                        log.debug("Software.com: " + responseData.get("data").getAsString());
                    } else {
                        log.debug("Software.com: Unable to process repo member metrics");
                    }
                } catch (Exception e) {
                    log.debug("Software.com: Unable to process repo member metrics, error: " + e.getMessage());
                }
            }
        }
    }
}
