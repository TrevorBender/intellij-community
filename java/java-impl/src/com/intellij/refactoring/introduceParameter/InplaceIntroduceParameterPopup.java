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
package com.intellij.refactoring.introduceParameter;

import com.intellij.codeInsight.intention.impl.TypeExpression;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.rename.inplace.VariableInplaceRenamer;
import com.intellij.refactoring.ui.TypeSelectorManager;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;
import com.intellij.ui.TitlePanel;
import com.intellij.usageView.UsageInfo;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntProcedure;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * User: anna
 * Date: 2/25/11
 */
class InplaceIntroduceParameterPopup extends IntroduceParameterSettingsUI {

  private final Project myProject;
  private final Editor myEditor;
  private final TypeSelectorManagerImpl myTypeSelectorManager;
  private PsiExpression myExpr;
  private final PsiLocalVariable myLocalVar;
  private final PsiMethod myMethod;
  private final PsiMethod myMethodToSearchFor;
  private final PsiExpression[] myOccurrences;
  private final boolean myMustBeFinal;
  private RangeMarker myExprMarker;
  private List<RangeMarker> myOccurrenceMarkers;

  private final JPanel myWholePanel;
  private int myParameterIndex = -1;
  private String myParameterName;
  private final String myExprText;


  InplaceIntroduceParameterPopup(final Project project,
                                 final Editor editor,
                                 final List<UsageInfo> classMemberRefs,
                                 final TypeSelectorManagerImpl typeSelectorManager,
                                 final PsiExpression expr,
                                 final PsiLocalVariable localVar,
                                 final PsiMethod method,
                                 final PsiMethod methodToSearchFor,
                                 final PsiExpression[] occurrences,
                                 final TIntArrayList parametersToRemove,
                                 final boolean mustBeFinal) {
    super(project, localVar, expr, method, parametersToRemove);
    myProject = project;
    myEditor = editor;
    myTypeSelectorManager = typeSelectorManager;
    myExpr = expr;
    myLocalVar = localVar;
    myMethod = method;
    myMethodToSearchFor = methodToSearchFor;
    myOccurrences = occurrences;
    myMustBeFinal = mustBeFinal;
    myExprMarker = expr != null && expr.isPhysical() ? myEditor.getDocument().createRangeMarker(expr.getTextRange()) : null;
    myExprText = myExpr != null ? myExpr.getText() : null;

    myWholePanel = new JPanel(new GridBagLayout());
    myWholePanel.setBorder(null);
    final GridBagConstraints gc =
      new GridBagConstraints(0, 0, 1, 1, 1, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0);

    final TitlePanel titlePanel = new TitlePanel();
    titlePanel.setBorder(null);
    titlePanel.setText(IntroduceParameterHandler.REFACTORING_NAME);
    myWholePanel.add(titlePanel, gc);

    gc.insets = new Insets(5, 5, 5, 0);
    if (myOccurrences.length > 1 && !myIsInvokedOnDeclaration) {
      gc.gridy++;
      createOccurrencesCb(gc, myWholePanel, myOccurrences.length);
    }
    final JavaRefactoringSettings settings = JavaRefactoringSettings.getInstance();
    createLocalVariablePanel(gc, myWholePanel, settings);
    createRemoveParamsPanel(gc, myWholePanel);
    if (Util.anyFieldsWithGettersPresent(classMemberRefs)) {
      gc.gridy++;
      myWholePanel.add(createReplaceFieldsWithGettersPanel(), gc);
    }
    gc.gridy++;
    createDelegateCb(gc, myWholePanel);
  }


  void inplaceIntroduceParameter() {
    startIntroduceTemplate(false);
  }

  private void startIntroduceTemplate(final boolean replaceAllOccurrences) {
    CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
      public void run() {
        myTypeSelectorManager.setAllOccurences(replaceAllOccurrences);
        final PsiType defaultType = myTypeSelectorManager.getTypeSelector().getSelectedType();
        final String propName = myLocalVar != null ? JavaCodeStyleManager
          .getInstance(myProject).variableNameToPropertyName(myLocalVar.getName(), VariableKind.LOCAL_VARIABLE) : null;
        final String[] names = IntroduceParameterHandler.createNameSuggestionGenerator(myExpr, propName, myProject)
          .getSuggestedNameInfo(defaultType).names;
        final PsiParameter parameter = createParameterToStartTemplateOn(names, defaultType);
        if (parameter != null) {
          myParameterIndex = myMethod.getParameterList().getParameterIndex(parameter);
          myEditor.getCaretModel().moveToOffset(parameter.getTextOffset());
          myEditor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
          final LinkedHashSet<String> nameSuggestions = new LinkedHashSet<String>();
          nameSuggestions.add(parameter.getName());
          nameSuggestions.addAll(Arrays.asList(names));
          final VariableInplaceRenamer renamer = new ParameterInplaceIntroducer(parameter);
          renamer.performInplaceRename(false, nameSuggestions);
        }
      }
    }, IntroduceParameterHandler.REFACTORING_NAME, null);
  }

  @Override
  protected TypeSelectorManager getTypeSelectionManager() {
    return myTypeSelectorManager;
  }

  private PsiParameter getParameter() {
    return myMethod.getParameterList().getParameters()[myParameterIndex];
  }

  public List<RangeMarker> getOccurrenceMarkers() {
    if (myOccurrenceMarkers == null) {
      myOccurrenceMarkers = new ArrayList<RangeMarker>();
      for (PsiExpression occurrence : myOccurrences) {
        myOccurrenceMarkers.add(myEditor.getDocument().createRangeMarker(occurrence.getTextRange()));
      }
    }
    return myOccurrenceMarkers;
  }

  private class ParameterInplaceIntroducer extends AbstractInplaceIntroducer {

    private SmartTypePointer myParameterTypePointer;
    private SmartTypePointer myDefaultParameterTypePointer;

    private boolean myFinal;

    public ParameterInplaceIntroducer(PsiParameter parameter) {
      super(myProject, new TypeExpression(myProject, myTypeSelectorManager.getTypesForAll()),
            myEditor, parameter, myMustBeFinal,
            myTypeSelectorManager.getTypesForAll().length > 1, myExprMarker, InplaceIntroduceParameterPopup.this.getOccurrenceMarkers());
      myDefaultParameterTypePointer = SmartTypePointerManager.getInstance(myProject).createSmartTypePointer(parameter.getType());
    }

    @Override
    protected JComponent getComponent() {
      final JPanel panel = new JPanel(new BorderLayout());
      panel.add(myWholePanel, BorderLayout.CENTER);

      final JPanel wrapper = new JPanel(new BorderLayout());
      wrapper.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
      if (myCanBeFinal != null) {
        wrapper.add(myCanBeFinal, BorderLayout.NORTH);
      }
      panel.add(wrapper, BorderLayout.SOUTH);

      return panel;
    }

    @Override
    protected boolean isReplaceAllOccurrences() {
      return InplaceIntroduceParameterPopup.this.isReplaceAllOccurences();
    }

    @Override
    protected PsiExpression getExpr() {
      return myExpr != null && myExpr.isValid() && myExpr.isPhysical() ? myExpr : null;
    }

    @Override
    protected PsiExpression[] getOccurrences() {
      return myOccurrences;
    }

    @Override
    protected List<RangeMarker> getOccurrenceMarkers() {
      return InplaceIntroduceParameterPopup.this.getOccurrenceMarkers();
    }

    @Override
    protected PsiVariable getVariable() {
      return getParameter();
    }


    @Override
    protected void saveSettings(PsiVariable psiVariable) {
      final JavaRefactoringSettings settings = JavaRefactoringSettings.getInstance();
      InplaceIntroduceParameterPopup.super.saveSettings(settings);
      settings.INTRODUCE_PARAMETER_CREATE_FINALS = psiVariable.hasModifierProperty(PsiModifier.FINAL);
      TypeSelectorManagerImpl.typeSelected(psiVariable.getType(), myDefaultParameterTypePointer.getType());
    }

    @Override
    protected void moveOffsetAfter(boolean success) {
      if (success) {
        boolean isDeleteLocalVariable = false;

        PsiExpression parameterInitializer = myExpr;
        if (myLocalVar != null) {
          if (isUseInitializer()) {
            parameterInitializer = myLocalVar.getInitializer();
          }
          isDeleteLocalVariable = isDeleteLocalVariable();
        }
        final TIntArrayList parametersToRemove = getParametersToRemove();

        final IntroduceParameterProcessor processor =
              new IntroduceParameterProcessor(myProject, myMethod,
                                              myMethodToSearchFor, parameterInitializer, myExpr,
                                              myLocalVar, isDeleteLocalVariable, myParameterName,
                                              isReplaceAllOccurences(),
                                              getReplaceFieldsWithGetters(), myMustBeFinal || myFinal, isGenerateDelegate(),
                                              myParameterTypePointer.getType(),
                                              parametersToRemove);
        final Runnable runnable = new Runnable() {
          public void run() {
            ApplicationManager.getApplication().invokeLater(new Runnable() {
              public void run() {
                final boolean [] conflictsFound = new boolean[] {true};
                processor.setPrepareSuccessfulSwingThreadCallback(new Runnable() {
                  @Override
                  public void run() {
                    conflictsFound[0] = processor.hasConflicts();
                  }
                });
                processor.run();
                normalizeParameterIdxAccordingToRemovedParams(parametersToRemove);
                ParameterInplaceIntroducer.super.moveOffsetAfter(!conflictsFound[0]);
              }
            });
          }
        };
        CommandProcessor.getInstance().executeCommand(myProject, runnable, IntroduceParameterHandler.REFACTORING_NAME, null);
      } else {
        super.moveOffsetAfter(success);
      }
    }

    private void normalizeParameterIdxAccordingToRemovedParams(TIntArrayList parametersToRemove) {
      parametersToRemove.forEach(new TIntProcedure() {
        @Override
        public boolean execute(int value) {
          if (myParameterIndex > value) {
            myParameterIndex--;
          }
          return true;
        }
      });
    }


    @Override
    public void finish() {
      super.finish();
      final PsiParameter psiParameter = (PsiParameter)getVariable();
      LOG.assertTrue(psiParameter != null);
      myFinal = psiParameter.hasModifierProperty(PsiModifier.FINAL);
      myParameterTypePointer = SmartTypePointerManager.getInstance(myProject).createSmartTypePointer(psiParameter.getType());
      PsiDocumentManager.getInstance(myProject).commitAllDocuments();
      myParameterName = psiParameter.getName();
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          final PsiFile containingFile = myMethod.getContainingFile();
          final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(myProject);
          if (myExprMarker != null) {
            myExpr = restoreExpression(containingFile, psiParameter, elementFactory, myExprMarker, myExprText);
            if (myExpr != null) {
              myExprMarker = myEditor.getDocument().createRangeMarker(myExpr.getTextRange());
            }
          }
          final List<RangeMarker> occurrenceMarkers = getOccurrenceMarkers();
          for (int i = 0, occurrenceMarkersSize = occurrenceMarkers.size(); i < occurrenceMarkersSize; i++) {
            RangeMarker marker = occurrenceMarkers.get(i);
            if (myExprMarker != null && marker.getStartOffset() == myExprMarker.getStartOffset()) {
              myOccurrences[i] = myExpr;
              continue;
            }
            final PsiExpression psiExpression = restoreExpression(containingFile, psiParameter, elementFactory, marker, myExprText);
            if (psiExpression != null) {
              myOccurrences[i] = psiExpression;
            }
          }
          myOccurrenceMarkers = null;
          if (psiParameter.isValid()) {
            psiParameter.delete();
          }
        }
      });
    }


    public boolean createFinals() {
      return hasFinalModifier();
    }
  }

  private boolean hasFinalModifier() {
    final Boolean createFinals = JavaRefactoringSettings.getInstance().INTRODUCE_PARAMETER_CREATE_FINALS;
    return createFinals == null ? CodeStyleSettingsManager.getSettings(myProject).GENERATE_FINAL_PARAMETERS : createFinals.booleanValue();
  }

  @Override
  protected void updateControls(JCheckBox[] removeParamsCb) {
    super.updateControls(removeParamsCb);
    if (myParameterIndex < 0) return;
    final TemplateState templateState = TemplateManagerImpl.getTemplateState(myEditor);
    if (templateState != null) {
      templateState.gotoEnd(true);
      startIntroduceTemplate(isReplaceAllOccurences());
    }
  }


  private PsiParameter createParameterToStartTemplateOn(final String[] names,
                                                        final PsiType defaultType) {
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(myMethod.getProject());
    return ApplicationManager.getApplication().runWriteAction(new Computable<PsiParameter>() {
      @Override
      public PsiParameter compute() {
        final String name = myParameterName != null ? myParameterName : names[0];
        final PsiParameter anchor = JavaIntroduceParameterMethodUsagesProcessor.getAnchorParameter(myMethod);
        final PsiParameter psiParameter = (PsiParameter)myMethod.getParameterList()
          .addAfter(elementFactory.createParameter(name, defaultType), anchor);
        PsiUtil.setModifierProperty(psiParameter, PsiModifier.FINAL, hasFinalModifier());
        return psiParameter;
      }
    });
  }

}