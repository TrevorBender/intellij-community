/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * User: anna
 * Date: 26-Mar-2008
 */
package com.intellij.packageDependencies.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.packageDependencies.DependenciesBuilder;
import com.intellij.packageDependencies.FindDependencyUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.UsageInfoToUsageConverter;
import com.intellij.util.Consumer;

import javax.swing.*;
import java.util.List;
import java.util.Set;

public class DependenciesUsagesPanel extends UsagesPanel {
  private final List<DependenciesBuilder> myBuilders;

  public DependenciesUsagesPanel(Project project, final List<DependenciesBuilder> builders) {
    super(project);
    myBuilders = builders;
    setToInitialPosition();
  }

  public String getInitialPositionText() {
    return myBuilders.get(0).getInitialUsagesPosition();
  }


  public String getCodeUsagesString() {
    return myBuilders.get(0).getRootNodeNameInUsageView();
  }

  public void findUsages(final Set<PsiFile> searchIn, final Set<PsiFile> searchFor) {
    cancelCurrentFindRequest();

    myAlarm.cancelAllRequests();
    myAlarm.addRequest(new Runnable() {
      public void run() {
        ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
          public void run() {
            final ProgressIndicator progress = new PanelProgressIndicator(new Consumer<JComponent>() {
              public void consume(final JComponent component) {
                setToComponent(component);
              }
            });
            myCurrentProgress = progress;
            ProgressManager.getInstance().runProcess(new Runnable() {
              public void run() {
                ApplicationManager.getApplication().runReadAction(new Runnable() {
                  public void run() {
                    UsageInfo[] usages = new UsageInfo[0];
                    Set<PsiFile> elementsToSearch = null;

                    try {
                      if (myBuilders.get(0).isBackward()){
                        elementsToSearch = searchIn;
                        usages = FindDependencyUtil.findBackwardDependencies(myBuilders, searchFor, searchIn);
                      }
                      else {
                        elementsToSearch = searchFor;
                        usages = FindDependencyUtil.findDependencies(myBuilders, searchIn, searchFor);
                      }
                    }
                    catch (ProcessCanceledException e) {
                    }
                    catch (Exception e) {
                      LOG.error(e);
                    }

                    if (!progress.isCanceled()) {
                      final UsageInfo[] finalUsages = usages;
                      final PsiElement[] _elementsToSearch =
                        elementsToSearch != null ? PsiUtilBase.toPsiElementArray(elementsToSearch) : PsiElement.EMPTY_ARRAY;
                      ApplicationManager.getApplication().invokeLater(new Runnable() {
                        public void run() {
                          showUsages(new UsageInfoToUsageConverter.TargetElementsDescriptor(_elementsToSearch), finalUsages);
                        }
                      }, ModalityState.stateForComponent(DependenciesUsagesPanel.this));
                    }
                  }
                });
                myCurrentProgress = null;
              }
            }, progress);
          }
        });
      }
    }, 300);
  }

  public void addBuilder(DependenciesBuilder builder) {
    myBuilders.add(builder);
  }
}
