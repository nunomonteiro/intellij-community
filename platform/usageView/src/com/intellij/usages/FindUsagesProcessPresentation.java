// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

public class FindUsagesProcessPresentation {
  private final UsageViewPresentation myUsageViewPresentation;

  private boolean myShowPanelIfOnlyOneUsage;
  private boolean myShowNotFoundMessage;
  private Factory<ProgressIndicator> myProgressIndicatorFactory;
  private Collection<VirtualFile> myLargeFiles;
  private boolean myShowFindOptionsPrompt = true;
  private volatile Runnable mySearchWithProjectFiles;
  private volatile boolean myCanceled;

  public FindUsagesProcessPresentation(@NotNull UsageViewPresentation presentation) {
    myUsageViewPresentation = presentation;
  }

  public boolean isShowNotFoundMessage() {
    return myShowNotFoundMessage;
  }

  public void setShowNotFoundMessage(final boolean showNotFoundMessage) {
    myShowNotFoundMessage = showNotFoundMessage;
  }

  public boolean isShowPanelIfOnlyOneUsage() {
    return myShowPanelIfOnlyOneUsage;
  }

  public void setShowPanelIfOnlyOneUsage(final boolean showPanelIfOnlyOneUsage) {
    myShowPanelIfOnlyOneUsage = showPanelIfOnlyOneUsage;
  }

  public Factory<ProgressIndicator> getProgressIndicatorFactory() {
    return myProgressIndicatorFactory;
  }

  public void setProgressIndicatorFactory(@NotNull Factory<ProgressIndicator> progressIndicatorFactory) {
    myProgressIndicatorFactory = progressIndicatorFactory;
  }

  @Nullable
  public Runnable searchIncludingProjectFileUsages() {
    return mySearchWithProjectFiles;
  }

  public void projectFileUsagesFound(@NotNull Runnable searchWithProjectFiles) {
    mySearchWithProjectFiles = searchWithProjectFiles;
  }

  public void setLargeFilesWereNotScanned(@NotNull Collection<VirtualFile> largeFiles) {
    myLargeFiles = largeFiles;
  }

  @NotNull
  public Collection<VirtualFile> getLargeFiles() {
    return myLargeFiles == null ? Collections.emptyList() : myLargeFiles;
  }

  public boolean isShowFindOptionsPrompt() {
    return myShowFindOptionsPrompt;
  }

  @NotNull
  public UsageViewPresentation getUsageViewPresentation() {
    return myUsageViewPresentation;
  }

  public void setShowFindOptionsPrompt(boolean showFindOptionsPrompt) {
    myShowFindOptionsPrompt = showFindOptionsPrompt;
  }


  public void setCanceled(boolean canceled) {
    myCanceled = canceled;
  }

  public boolean isCanceled() {
    return myCanceled;
  }
}


