/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.continuation;

import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Created by IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 8/19/11
 * Time: 12:14 PM
 */
public abstract class ModalityIgnorantBackgroundableTask extends Task.Backgroundable {
  public ModalityIgnorantBackgroundableTask(@Nullable Project project,
                                            @NotNull String title,
                                            boolean canBeCancelled,
                                            @Nullable PerformInBackgroundOption backgroundOption) {
    super(project, title, canBeCancelled, backgroundOption);
  }

  public ModalityIgnorantBackgroundableTask(@Nullable Project project, @NotNull String title, boolean canBeCancelled) {
    super(project, title, canBeCancelled);
  }

  public ModalityIgnorantBackgroundableTask(@Nullable Project project, @NotNull String title) {
    super(project, title);
  }

  protected abstract void doInAwtIfFail(final Exception e);
  protected abstract void doInAwtIfCancel();
  protected abstract void doInAwtIfSuccess();
  protected abstract void runImpl(@NotNull ProgressIndicator indicator);

  @Override
  public void run(@NotNull final ProgressIndicator indicator) {
    try {
      runImpl(indicator);
    } catch (final Exception e) {
      SwingUtilities.invokeLater(new Runnable() {
        @Override
        public void run() {
          doInAwtIfFail(e);
        }
      });
      return;
    }

    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        if (indicator.isCanceled()) {
          doInAwtIfCancel();
        } else {
          doInAwtIfSuccess();
        }
      }
    });
  }
}
