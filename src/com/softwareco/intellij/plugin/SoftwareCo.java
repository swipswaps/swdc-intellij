/**
 * Copyright (c) 2018 by Software.com
 * All rights reserved
 */
package com.softwareco.intellij.plugin;


import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.MessageBusConnection;
import com.softwareco.intellij.plugin.managers.EventTrackerManager;
import com.softwareco.intellij.plugin.managers.FileManager;
import com.softwareco.intellij.plugin.managers.WallClockManager;
import com.swdc.snowplow.tracker.events.UIInteractionType;
import org.apache.commons.lang.StringUtils;

import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Logger;


/**
 * Intellij Plugin Application
 */
public class SoftwareCo implements ApplicationComponent {

    public static final Logger log = Logger.getLogger("SoftwareCo");
    public static final Gson gson = new GsonBuilder().create();

    public static MessageBusConnection connection;

    private final AsyncManager asyncManager = AsyncManager.getInstance();

    public SoftwareCo() {
        // constructor
    }

    public void initComponent() {
        String jwt = FileManager.getItem("jwt");
        if (StringUtils.isBlank(jwt)) {
            jwt = SoftwareCoUtils.createAnonymousUser(false);
            if (StringUtils.isBlank(jwt)) {
                boolean serverIsOnline = SoftwareCoSessionManager.isServerOnline();
                if (!serverIsOnline) {
                    SoftwareCoUtils.showOfflinePrompt(true);
                }
            } else {
                initializePlugin(true);
            }
        }
        initializePlugin(false);
    }

    protected void initializePlugin(boolean initializedUser) {
        String plugName = SoftwareCoUtils.getPluginName();

        log.info(plugName + ": Loaded v" + SoftwareCoUtils.getVersion());

        initializeUserInfoWhenProjectsReady(initializedUser);

        log.info(plugName + ": Finished initializing SoftwareCo plugin");
    }

    /**
     * This logic waits until the user has selected a project.
     * Once that happens we can continue initializing the plugin.
     * @param initializedUser
     */
    private void initializeUserInfoWhenProjectsReady(boolean initializedUser) {
        Project p = SoftwareCoUtils.getOpenProject();
        if (p == null) {
            // try again in 5 seconds
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    initializeUserInfoWhenProjectsReady(initializedUser);
                }
            }, 5000);
        } else {
            // init user info
            initializeUserInfo(initializedUser);

            // setup the doc listeners
            setupFileEditorEventListeners(p);
        }
    }

    // The app is ready and has a selected project
    private void initializeUserInfo(boolean initializedUser) {

        // initialize the tracker
        EventTrackerManager.getInstance().init();

        // send the activate event
        EventTrackerManager.getInstance().trackEditorAction("editor", "activate");

        String readmeDisplayed = FileManager.getItem("intellij_CtReadme");
        if (readmeDisplayed == null || Boolean.valueOf(readmeDisplayed) == false) {
            // send an initial plugin payload
            FileManager.openReadmeFile(UIInteractionType.keyboard);
            FileManager.setItem("intellij_CtReadme", "true");
        }

        // setup the doc listeners
        setupEventListeners();
    }

    // add the document change event listener
    private void setupEventListeners() {
        ApplicationManager.getApplication().invokeLater(() -> {
            // edit document
            EditorFactory.getInstance().getEventMulticaster().addDocumentListener(
                    new SoftwareCoDocumentListener(), this::disposeComponent);
        });
    }

    // add the file selection change event listener
    private void setupFileEditorEventListeners(Project p) {
        ApplicationManager.getApplication().invokeLater(() -> {
            // file open,close,selection listener
            p.getMessageBus().connect().subscribe(
                    FileEditorManagerListener.FILE_EDITOR_MANAGER, new SoftwareCoFileEditorListener());

            WallClockManager.getInstance();
        });
    }

    public void disposeComponent() {
        // store the activate event
        EventTrackerManager.getInstance().trackEditorAction("editor", "deactivate");

        try {
            if (connection != null) {
                connection.disconnect();
            }
        } catch(Exception e) {
            log.info("Error disconnecting the software.com plugin, reason: " + e.toString());
        }

        asyncManager.destroyServices();

        // process one last time
        // this will ensure we process the latest keystroke updates
        KeystrokeManager keystrokeManager = KeystrokeManager.getInstance();
        if (keystrokeManager.getKeystrokeCount() != null) {
            keystrokeManager.getKeystrokeCount().processKeystrokes();
        }
    }
}