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
package org.jetbrains.idea.svn17.dialogs;

import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.MultiLineLabelUI;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn17.SvnBundle;
import org.jetbrains.idea.svn17.SvnConfiguration17;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;

import javax.swing.*;
import java.awt.*;
import java.io.File;

public class UpgradeFormatDialog extends DialogWrapper  {
  private JRadioButton myUpgradeNoneButton;
  private JRadioButton myUpgradeAutoButton;
  private JRadioButton myUpgradeAuto15Button;
  private JRadioButton myUpgradeAuto16Button;
  private JRadioButton myUpgradeAuto17Button;

  protected File myPath;

  public UpgradeFormatDialog(Project project, File path, boolean canBeParent) {
    this(project, path, canBeParent, true);
  }

  protected UpgradeFormatDialog(Project project, File path, boolean canBeParent, final boolean initHere) {
    super(project, canBeParent);
    myPath = path;
    setResizable(false);
    setTitle(SvnBundle.message("dialog.upgrade.wcopy.format.title"));

    if (initHere) {
      init();
    }
  }

  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction()};
  }

  @NonNls
  protected String getDimensionServiceKey() {
    return "svn.upgradeDialog";
  }

  public void setData(final boolean display13format, final String selectedFormat) {
    if (SvnConfiguration17.UPGRADE_AUTO_17.equals(selectedFormat)) {
      myUpgradeAuto17Button.setSelected(true);
    } else if (SvnConfiguration17.UPGRADE_AUTO_16.equals(selectedFormat)) {
      myUpgradeAuto16Button.setSelected(true);
    } else if (SvnConfiguration17.UPGRADE_AUTO.equals(selectedFormat)) {
      myUpgradeAutoButton.setSelected(true);
    } else if (SvnConfiguration17.UPGRADE_AUTO_15.equals(selectedFormat)) {
      myUpgradeAuto15Button.setSelected(true);
    } else {
      myUpgradeNoneButton.setSelected(true);
    }
    myUpgradeNoneButton.setVisible(display13format);
    if (myUpgradeNoneButton.isSelected() && (! display13format)) {
      myUpgradeAutoButton.setSelected(true);
    }
  }

  protected String getTopMessage(final String label) {
    return SvnBundle.message(new StringBuilder().append("label.configure.").append(label).append(".label").toString(),
                             ApplicationNamesInfo.getInstance().getFullProductName());
  }

  @Nullable
  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel();
    panel.setLayout(new GridBagLayout());

    GridBagConstraints gb = new GridBagConstraints();


    // top label.
    gb.insets = new Insets(2, 2, 2, 2);
    gb.weightx = 1;
    gb.weighty = 0;
    gb.gridwidth = 2;
    gb.gridheight = 1;
    gb.gridx = 0;
    gb.gridy = 0;
    gb.anchor = GridBagConstraints.WEST;
    gb.fill = GridBagConstraints.HORIZONTAL;

    File adminPath = new File(myPath, SVNFileUtil.getAdminDirectoryName());
    final boolean adminPathIsDirectory = adminPath.isDirectory();
    final String label = getMiddlePartOfResourceKey(adminPathIsDirectory);

    JLabel topLabel = new JLabel(getTopMessage(label));
    topLabel.setUI(new MultiLineLabelUI());
    panel.add(topLabel, gb);
    gb.gridy += 1;

    myUpgradeNoneButton = new JRadioButton(SvnBundle.message(new StringBuilder().append("radio.configure.").append(label).append(".none").toString()));
    myUpgradeAutoButton = new JRadioButton(SvnBundle.message(new StringBuilder().append("radio.configure.").append(label).append(".auto").toString()));
    myUpgradeAuto15Button = new JRadioButton(SvnBundle.message(new StringBuilder().append("radio.configure.").append(label).append(".auto.15format").toString()));
    myUpgradeAuto16Button = new JRadioButton(SvnBundle.message(new StringBuilder().append("radio.configure.").append(label).append(".auto.16format").toString()));
    myUpgradeAuto17Button = new JRadioButton(SvnBundle.message(new StringBuilder().append("radio.configure.").append(label).append(".auto.17format").toString()));

    ButtonGroup group = new ButtonGroup();
    group.add(myUpgradeNoneButton);
    group.add(myUpgradeAutoButton);
    group.add(myUpgradeAuto15Button);
    group.add(myUpgradeAuto16Button);
    group.add(myUpgradeAuto17Button);
    panel.add(myUpgradeNoneButton, gb);
    gb.gridy += 1;
    panel.add(myUpgradeAutoButton, gb);
    gb.gridy += 1;
    panel.add(myUpgradeAuto15Button, gb);
    gb.gridy += 1;
    panel.add(myUpgradeAuto16Button, gb);
    gb.gridy += 1;
    panel.add(myUpgradeAuto17Button, gb);
    gb.gridy += 1;

    myUpgradeNoneButton.setSelected(true);

    final JPanel auxiliaryPanel = getBottomAuxiliaryPanel();
    if (auxiliaryPanel != null) {
      panel.add(auxiliaryPanel, gb);
      gb.gridy += 1;
    }

    return panel;
  }

  @Nullable
  protected JPanel getBottomAuxiliaryPanel() {
    return null;
  }

  protected String getMiddlePartOfResourceKey(final boolean adminPathIsDirectory) {
    return ! adminPathIsDirectory ? "create" : "upgrade";
  }

  protected boolean showHints() {
    return true;
  }

  @Nullable
  public String getUpgradeMode() {
    if (myUpgradeAuto17Button.isSelected()) {
      return SvnConfiguration17.UPGRADE_AUTO_17;
    } else if (myUpgradeNoneButton.isSelected()) {
      return SvnConfiguration17.UPGRADE_NONE;
    } else if (myUpgradeAutoButton.isSelected()) {
      return SvnConfiguration17.UPGRADE_AUTO;
    } else if (myUpgradeAuto15Button.isSelected()) {
      return SvnConfiguration17.UPGRADE_AUTO_15;
    } else if (myUpgradeAuto16Button.isSelected()) {
      return SvnConfiguration17.UPGRADE_AUTO_16;
    }
    return null;
  }

}
