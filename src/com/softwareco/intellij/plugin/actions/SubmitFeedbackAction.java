package com.softwareco.intellij.plugin.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.softwareco.intellij.plugin.SoftwareCoUtils;
import com.swdc.snowplow.tracker.events.UIInteractionType;
import org.jetbrains.annotations.NotNull;

public class SubmitFeedbackAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent anActionEvent) { SoftwareCoUtils.submitFeedback(UIInteractionType.keyboard); }

    @Override
    public void update(AnActionEvent event) {
        Editor editor = event.getData(CommonDataKeys.EDITOR);
        event.getPresentation().setVisible(true);
        event.getPresentation().setEnabled(true);
    }
}
