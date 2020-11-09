package com.softwareco.intellij.plugin.tree;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.softwareco.intellij.plugin.SoftwareCoUtils;
import com.softwareco.intellij.plugin.managers.SessionDataManager;
import org.jetbrains.annotations.NotNull;


public class CodeTimeToolWindowFactory implements ToolWindowFactory {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        CodeTimeToolWindow ctWindow = new CodeTimeToolWindow(toolWindow);
        ContentFactory contentFactory = ContentFactory.SERVICE.getInstance();
        Content content = contentFactory.createContent(ctWindow.getContent(), "", false);
        toolWindow.getContentManager().addContent(content);
    }

    public static void openToolWindow() {
        Project project = SoftwareCoUtils.getFirstActiveProject();
        if (project != null) {
            ToolWindow tw = ToolWindowManager.getInstance(project).getToolWindow("Code Time");
            if (tw != null) {
                tw.show(null);

                SessionDataManager.treeDataUpdateCheck();
            }
        }
    }

    public static boolean isToolWindowVisible() {
        Project project = SoftwareCoUtils.getFirstActiveProject();
        if (project != null) {
            ToolWindow tw = ToolWindowManager.getInstance(project).getToolWindow("Code Time");
            if (tw != null) {
                return tw.isVisible();
            }
        }
        return false;
    }
}
