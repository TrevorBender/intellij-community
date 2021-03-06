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
package git4idea.push;

import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.TypeSafeDataProvider;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowser;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import git4idea.GitBranch;
import git4idea.GitUtil;
import git4idea.history.browser.GitCommit;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.*;
import java.awt.*;
import java.io.File;
import java.util.*;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * The list of commits from multiple repositories and branches, with diff panel at the right.
 *
 * @author Kirill Likhodedov
 */
class GitPushLog extends JPanel implements TypeSafeDataProvider {

  private final Project myProject;
  private final Collection<GitRepository> myAllRepositories;
  private final ChangesBrowser myChangesBrowser;
  private final CheckboxTree myTree;
  private final DefaultTreeModel myTreeModel;
  private final CheckedTreeNode myRootNode;
  private final ReentrantReadWriteLock TREE_CONSTRUCTION_LOCK = new ReentrantReadWriteLock();
  private final MyTreeCellRenderer myTreeCellRenderer;

  GitPushLog(@NotNull Project project, @NotNull Collection<GitRepository> repositories, @NotNull final Consumer<Boolean> checkboxListener) {
    myProject = project;
    myAllRepositories = repositories;

    myRootNode = new CheckedTreeNode(null);
    myRootNode.add(new DefaultMutableTreeNode(new FakeCommit()));

    myTreeModel = new DefaultTreeModel(myRootNode);
    myTreeCellRenderer = new MyTreeCellRenderer();
    myTree = new CheckboxTree(myTreeCellRenderer, myRootNode) {
      @Override
      protected void onNodeStateChanged(CheckedTreeNode node) {
        Object userObject = node.getUserObject();
        if (userObject instanceof GitRepository) {
          checkboxListener.consume(node.isChecked());
        }
      }

      @Override
      public boolean getScrollableTracksViewportWidth() {
        return false;
      }
    };
    myTree.setRootVisible(false);
    TreeUtil.expandAll(myTree);

    myTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    myTree.addTreeSelectionListener(new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent e) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) myTree.getLastSelectedPathComponent();
        if (node != null) {
          Object nodeInfo = node.getUserObject();
          if (nodeInfo instanceof GitCommit) {
            myChangesBrowser.getViewer().setEmptyText("No differences");
            myChangesBrowser.setChangesToDisplay(((GitCommit)nodeInfo).getChanges());
            return;
          }
        }
        setDefaultEmptyText();
        myChangesBrowser.setChangesToDisplay(Collections.<Change>emptyList());
      }
    });
    

    myChangesBrowser = new ChangesBrowser(project, null, Collections.<Change>emptyList(), null, false, true, null, ChangesBrowser.MyUseCase.LOCAL_CHANGES, null);
    myChangesBrowser.getDiffAction().registerCustomShortcutSet(CommonShortcuts.getDiff(), myTree);
    setDefaultEmptyText();

    Splitter splitter = new Splitter(false, 0.7f);
    splitter.setFirstComponent(ScrollPaneFactory.createScrollPane(myTree));
    splitter.setSecondComponent(myChangesBrowser);
    
    setLayout(new BorderLayout());
    add(splitter);
  }

  private void setDefaultEmptyText() {
    myChangesBrowser.getViewer().setEmptyText("No commits selected");
  }

  // Make changes available for diff action
  @Override
  public void calcData(DataKey key, DataSink sink) {
    if (VcsDataKeys.CHANGES.equals(key)) {
      DefaultMutableTreeNode[] selectedNodes = myTree.getSelectedNodes(DefaultMutableTreeNode.class, null);
      if (selectedNodes.length == 0) {
        return;
      }
      Object object = selectedNodes[0].getUserObject();
      if (object instanceof GitCommit) {
        sink.put(key, ArrayUtil.toObjectArray(((GitCommit)object).getChanges(), Change.class));
      }
    }
  }

  @NotNull
  public JComponent getPreferredFocusComponent() {
    return myTree;
  }

  void setCommits(@NotNull GitCommitsByRepoAndBranch commits) {
    try {
      TREE_CONSTRUCTION_LOCK.writeLock().lock();
      myRootNode.removeAllChildren();
      createNodes(commits);
      myTreeModel.nodeStructureChanged(myRootNode);
      myTree.setModel(myTreeModel);  // TODO: why doesn't it repaint otherwise?
      myTreeCellRenderer.recalculateWidth(commits.getAllCommits());
      TreeUtil.expandAll(myTree);
      selectFirstCommit();
    }
    finally {
      TREE_CONSTRUCTION_LOCK.writeLock().unlock();
    }
  }

  private void selectFirstCommit() {
    DefaultMutableTreeNode firstLeaf = myRootNode.getFirstLeaf();
    if (firstLeaf == null) {
      return;
    }

    Enumeration enumeration = myRootNode.depthFirstEnumeration();
    DefaultMutableTreeNode node = null;
    while (enumeration.hasMoreElements()) {
      node = (DefaultMutableTreeNode) enumeration.nextElement();
      if (node.isLeaf() && node.getUserObject() instanceof GitCommit) {
        break;
      }
    }
    if (node == null) {
      node = firstLeaf;
    }
    myTree.setSelectionPath(new TreePath(node.getPath()));
  }

  private void createNodes(@NotNull GitCommitsByRepoAndBranch commits) {
    for (GitRepository repository : GitUtil.sortRepositories(commits.getRepositories())) {
      GitCommitsByBranch commitsByBranch = commits.get(repository);
      createRepoNode(repository, commitsByBranch, myRootNode);
    }
  }

  /**
   * Creates the node with subnodes for a repository and adds it to the rootNode.
   * If there is only one repo in the project, doesn't create a node for the repository, and adds subnodes directly to the rootNode.
   */
  private void createRepoNode(@NotNull GitRepository repository, @NotNull GitCommitsByBranch commitsByBranch, @NotNull DefaultMutableTreeNode rootNode) {
    DefaultMutableTreeNode parentNode;
    if (GitUtil.justOneGitRepository(myProject)) {
      parentNode = rootNode;
    } else {
      parentNode = new CheckedTreeNode(repository);
      rootNode.add(parentNode);
    }

    for (GitBranch branch : sortBranches(commitsByBranch.getBranches())) {
      DefaultMutableTreeNode branchNode = createBranchNode(branch, commitsByBranch.get(branch));
      parentNode.add(branchNode);
    }
  }

  private static List<GitBranch> sortBranches(@NotNull Collection<GitBranch> branches) {
    List<GitBranch> sortedBranches = new ArrayList<GitBranch>(branches);
    Collections.sort(sortedBranches, new Comparator<GitBranch>() {
      @Override public int compare(GitBranch o1, GitBranch o2) {
        return o1.getName().compareTo(o2.getName());
      }
    });
    return sortedBranches;
  }

  private static DefaultMutableTreeNode createBranchNode(@NotNull GitBranch branch, @NotNull GitPushBranchInfo branchInfo) {
    DefaultMutableTreeNode branchNode = new DefaultMutableTreeNode(branchInfo);
    for (GitCommit commit : branchInfo.getCommits()) {
      branchNode.add(new DefaultMutableTreeNode(commit));
    }
    if (branchInfo.isNewBranchCreated()) {
      branchNode.add(new DefaultMutableTreeNode(new MoreCommitsToShow()));
    }
    return branchNode;
  }

  void displayError(String message) {
    DefaultMutableTreeNode titleNode = new DefaultMutableTreeNode("Error: couldn't collect commits to be pushed");
    DefaultMutableTreeNode detailNode = new DefaultMutableTreeNode(message);
    myRootNode.add(titleNode);
    myRootNode.add(detailNode);
    myTreeModel.reload(myRootNode);
    TreeUtil.expandAll(myTree);
    repaint();
  }

  /**
   * @return repositories selected (via checkboxes) to be pushed.
   */
  Collection<GitRepository> getSelectedRepositories() {
    if (myAllRepositories.size() == 1) {
      return myAllRepositories;
    }

    try {
      TREE_CONSTRUCTION_LOCK.readLock().lock();  // wait for tree to be constructed
      Collection<GitRepository> selectedRepositories = new ArrayList<GitRepository>(myAllRepositories.size());
      if (myRootNode.getChildCount() == 0) {  // the method is requested before tree construction began => returning all repos.
        return myAllRepositories;
      }

      for (int i = 0; i < myRootNode.getChildCount(); i++) {
        TreeNode child = myRootNode.getChildAt(i);
        if (child instanceof CheckedTreeNode) {
          CheckedTreeNode node = (CheckedTreeNode)child;
          if (node.isChecked()) {
            if (node.getUserObject() instanceof GitRepository) {
              selectedRepositories.add((GitRepository)node.getUserObject());
            }
          }
        }
      }
      return selectedRepositories;
    }
    finally {
      TREE_CONSTRUCTION_LOCK.readLock().unlock();
    }
  }

  private static class MyTreeCellRenderer extends CheckboxTree.CheckboxTreeCellRenderer {
    
    private int myDateMaxWidth;
    
    void recalculateWidth(@NotNull Collection<GitCommit> commits) {
      for (GitCommit commit : commits) {
        int len = getDateString(commit).length();
        if (len > myDateMaxWidth) {
          myDateMaxWidth = len;
        }
      }
    }

    private static String getDateString(GitCommit commit) {
      return DateFormatUtil.formatPrettyDateTime(commit.getAuthorTime());
    }

    @Override
    public void customizeRenderer(final JTree tree, final Object value, final boolean selected, final boolean expanded, final boolean leaf, final int row, final boolean hasFocus) {
      Object userObject;
      if (value instanceof CheckedTreeNode) {
        userObject = ((CheckedTreeNode)value).getUserObject();
      } else if (value instanceof DefaultMutableTreeNode) {
        userObject = ((DefaultMutableTreeNode)value).getUserObject();
      } else {
        return;
      }

      ColoredTreeCellRenderer renderer = getTextRenderer();
      Font font = EditorColorsManager.getInstance().getGlobalScheme().getFont(EditorFontType.PLAIN); // using probable monospace font to emulate table
      renderer.setFont(font);

      SimpleTextAttributes smallGrey = new SimpleTextAttributes(SimpleTextAttributes.STYLE_SMALLER, UIUtil.getInactiveTextColor());
      if (userObject instanceof GitCommit) {
        GitCommit commit = (GitCommit)userObject;
        SimpleTextAttributes small = new SimpleTextAttributes(SimpleTextAttributes.STYLE_SMALLER, renderer.getForeground());
        renderer.append(commit.getShortHash().toString(), smallGrey);
        renderer.append(String.format(" %" + myDateMaxWidth + "s  ", getDateString(commit)), smallGrey);
        renderer.append(commit.getSubject(), small);
      }
      else if (userObject instanceof GitRepository) {
        String repositoryPath = calcRootPath((GitRepository)userObject);
        renderer.append(repositoryPath, SimpleTextAttributes.GRAY_ATTRIBUTES);
      }
      else if (userObject instanceof GitPushBranchInfo) {
        GitPushBranchInfo branchInfo = (GitPushBranchInfo) userObject;
        GitBranch fromBranch = branchInfo.getSourceBranch();
        GitBranch dest = branchInfo.getDestBranch();

        GitPushBranchInfo.Type type = branchInfo.getType();
        final String showingRecentCommits = ", showing " + GitPusher.RECENT_COMMITS_NUMBER + " recent commits";
        String text = fromBranch.getName();
        SimpleTextAttributes attrs = SimpleTextAttributes.REGULAR_ATTRIBUTES;
        String additionalText = "";
        switch (type) {
          case STANDARD:
            text += " -> " + dest.getName();
            if (branchInfo.getCommits().isEmpty()) {
              additionalText = " nothing to push";
            }
            break;
          case NEW_BRANCH:
            text += " -> +" + dest.getName();
            attrs = SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES;
            additionalText = " new branch will be created" + showingRecentCommits;
            break;
          case NO_TRACKED_OR_TARGET:
            attrs = SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES;
            additionalText = " no tracked branch. Use checkbox below to push branch to manually specified" + showingRecentCommits;
            break;
        }
        renderer.append(text, attrs);
        renderer.append(additionalText, smallGrey);
      }
      else if (userObject instanceof FakeCommit) {
        int spaces = 6 + 15 + 3 + 30;
        String s = String.format("%" + spaces + "s", " ");
        renderer.append(s, new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, renderer.getBackground()));
      }
      else if (userObject instanceof MoreCommitsToShow) {
        renderer.append("...");
      }
      else {
        renderer.append(userObject == null ? "" : userObject.toString());
      }
    }

    @NotNull
    private static String calcRootPath(@NotNull GitRepository repository) {
      VirtualFile projectDir = repository.getProject().getBaseDir();

      String repositoryPath = repository.getPresentableUrl();
      if (projectDir != null) {
        String relativePath = VfsUtilCore.getRelativePath(repository.getRoot(), projectDir, File.separatorChar);
        if (relativePath != null) {
          repositoryPath = relativePath;
        }
      }

      return repositoryPath.isEmpty() ? "<Project>" : "." + File.separator + repositoryPath;
    }
  }
  
  private static class FakeCommit {
  }

  private static class MoreCommitsToShow {
  }
}
