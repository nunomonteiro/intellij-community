// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.actions

import com.intellij.icons.AllIcons
import com.intellij.idea.ActionsBundle
import com.intellij.internal.statistic.eventLog.whitelist.WhitelistTestGroupStorage
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.ui.LayeredIcon

class CleanupLocalWhitelistAction : AnAction {
  private val recorderId: String?

  constructor() : super(ActionsBundle.message("action.CleanupLocalWhitelistAction.text"),
                        ActionsBundle.message("action.CleanupLocalWhitelistAction.description"),
                        ICON) {
    recorderId = null
  }

  constructor(recorder: String) : super(ActionsBundle.message("action.CleanupLocalWhitelistAction.text"),
                                        ActionsBundle.message("action.CleanupLocalWhitelistAction.description"),
                                        ICON) {
    recorderId = recorder
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return

    ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Removing Local Whitelist", false) {
      override fun run(indicator: ProgressIndicator) {
        if (recorderId == null) {
          WhitelistTestGroupStorage.cleanupAll()
        }
        else {
          WhitelistTestGroupStorage.cleanupAll(listOf(recorderId))
        }
      }
    })
  }

  companion object {
    private val ICON = LayeredIcon(AllIcons.General.Remove, AllIcons.Actions.Scratch)
  }
}