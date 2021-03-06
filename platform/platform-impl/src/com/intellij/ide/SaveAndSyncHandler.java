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
package com.intellij.ide;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.openapi.vfs.newvfs.RefreshSession;
import org.jetbrains.annotations.NotNull;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public class SaveAndSyncHandler implements ApplicationComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.SaveAndSyncHandler");
  private final Runnable myIdleListener;
  private final PropertyChangeListener myGeneralSettingsListener;
  private final ProgressManager myProgressManager;
  
  private final AtomicInteger myBlockSaveOnFrameDeactivationCount = new AtomicInteger();
  private final AtomicInteger myBlockSyncOnFrameActivationCount = new AtomicInteger();

  public static SaveAndSyncHandler getInstance(){
    return ApplicationManager.getApplication().getComponent(SaveAndSyncHandler.class);
  }

  public SaveAndSyncHandler(final FrameStateManager frameStateManager,
                            final FileDocumentManager fileDocumentManager,
                            final GeneralSettings generalSettings,
                            final ProgressManager progressManager) {
    myProgressManager = progressManager;

    myIdleListener = new Runnable() {
      public void run() {
        if (generalSettings.isAutoSaveIfInactive() && canSyncOrSave()) {
          fileDocumentManager.saveAllDocuments();
        }
      }
    };


    IdeEventQueue.getInstance().addIdleListener(
      myIdleListener,
      generalSettings.getInactiveTimeout() * 1000
    );

    myGeneralSettingsListener = new PropertyChangeListener() {
      public void propertyChange(PropertyChangeEvent e) {
        if (GeneralSettings.PROP_INACTIVE_TIMEOUT.equals(e.getPropertyName())) {
          IdeEventQueue eventQueue = IdeEventQueue.getInstance();
          eventQueue.removeIdleListener(myIdleListener);
          Integer timeout = (Integer)e.getNewValue();
          eventQueue.addIdleListener(myIdleListener, timeout.intValue() * 1000);
        }
      }
    };
    generalSettings.addPropertyChangeListener(myGeneralSettingsListener);

    frameStateManager.addListener(new FrameStateListener() {
      public void onFrameDeactivated() {
        if (canSyncOrSave()) {
          saveProjectsAndDocuments();
        }
      }

      public void onFrameActivated() {
        refreshFiles();
      }
    });

  }

  @NotNull
  public String getComponentName() {
    return "SaveAndSyncHandler";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
    GeneralSettings.getInstance().removePropertyChangeListener(myGeneralSettingsListener);
    IdeEventQueue.getInstance().removeIdleListener(myIdleListener);
  }

  private boolean canSyncOrSave() {
    return !LaterInvocator.isInModalContext() && !myProgressManager.hasModalProgressIndicator();
  }

  // made public for tests
  public void saveProjectsAndDocuments() {
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: save()");
    }
    if (ApplicationManager.getApplication().isDisposed()) return;
    
    if (myBlockSaveOnFrameDeactivationCount.get() == 0 && GeneralSettings.getInstance().isSaveOnFrameDeactivation()) {
      FileDocumentManager.getInstance().saveAllDocuments();

      Project[] openProjects = ProjectManagerEx.getInstanceEx().getOpenProjects();
      for (Project project : openProjects) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("save project: " + project);
        }
        project.save();
      }
      if (LOG.isDebugEnabled()) {
        LOG.debug("save application settings");
      }
      ApplicationManagerEx.getApplicationEx().saveSettings();
      if (LOG.isDebugEnabled()) {
        LOG.debug("exit: save()");
      }
    }
  }

  private void refreshFiles() {
    if (ApplicationManager.getApplication().isDisposed()) return;
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: synchronize()");
    }


    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        if (canSyncOrSave()) {
          refreshOpenFiles();
        }

        maybeRefresh(ModalityState.NON_MODAL);
      }
    }, ModalityState.NON_MODAL);

    if (LOG.isDebugEnabled()) {
      LOG.debug("exit: synchronize()");
    }
  }

  public void maybeRefresh(ModalityState modalityState) {
    if (myBlockSyncOnFrameActivationCount.get() == 0 && GeneralSettings.getInstance().isSyncOnFrameActivation()) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("refresh VFS");
      }
      RefreshQueue.getInstance().refreshLocalRoots(true, null, modalityState);
    }
  }

  public static void refreshOpenFiles() {
    // Refresh open files synchronously so it doesn't wait for potentially longish refresh request in the queue to finish
    final RefreshSession session = RefreshQueue.getInstance().createSession(false, false, null);

    for (Project project : ProjectManagerEx.getInstanceEx().getOpenProjects()) {
      VirtualFile[] files = FileEditorManager.getInstance(project).getSelectedFiles();
      for (VirtualFile file : files) {
        if (file instanceof NewVirtualFile) {
          session.addFile(file);
        }
      }
    }

    session.launch();
  }
  
  public void blockSaveOnFrameDeactivation() {
    myBlockSaveOnFrameDeactivationCount.incrementAndGet();
  }

  public void unblockSaveOnFrameDeactivation() {
    myBlockSaveOnFrameDeactivationCount.decrementAndGet();
  }

  public void blockSyncOnFrameActivation() {
    myBlockSyncOnFrameActivationCount.incrementAndGet();
  }
  
  public void unblockSyncOnFrameActivation() {
    myBlockSyncOnFrameActivationCount.decrementAndGet();
  }
}