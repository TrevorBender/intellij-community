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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.codeInsight.generation.PsiMethodMember;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.ide.util.MemberChooser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;


/**
 * @author ven
 */
public class CreateConstructorMatchingSuperFix extends BaseIntentionAction {
  private static final Logger LOG = Logger.getInstance("com.intellij.codeInsight.daemon.impl.quickfix.CreateConstructorMatchingSuperFix");

  private final PsiClass myClass;

  public CreateConstructorMatchingSuperFix(PsiClass aClass) {
    myClass = aClass;
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("create.constructor.matching.super");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!myClass.isValid() || !myClass.getManager().isInProject(myClass)) return false;
    setText(QuickFixBundle.message("create.constructor.matching.super"));
    return true;
  }

  @Override
  public void invoke(@NotNull final Project project, final Editor editor, PsiFile file) {
    if (!CodeInsightUtilBase.prepareFileForWrite(myClass.getContainingFile())) return;
    PsiClass baseClass = myClass.getSuperClass();
    LOG.assertTrue(baseClass != null);
    PsiSubstitutor substitutor = TypeConversionUtil.getSuperClassSubstitutor(baseClass, myClass, PsiSubstitutor.EMPTY);
    List<PsiMethodMember> baseConstructors = new ArrayList<PsiMethodMember>();
    PsiMethod[] baseConstrs = baseClass.getConstructors();
    for (PsiMethod baseConstr : baseConstrs) {
      if (PsiUtil.isAccessible(baseConstr, myClass, myClass)) baseConstructors.add(new PsiMethodMember(baseConstr, substitutor));
    }

    chooseConstructor2Delegate(project, editor, substitutor, baseConstructors, baseConstrs, myClass);
  }

  public static void chooseConstructor2Delegate(final Project project,
                                                final Editor editor,
                                                PsiSubstitutor substitutor,
                                                List<PsiMethodMember> baseConstructors,
                                                PsiMethod[] baseConstrs,
                                                final PsiClass targetClass) {
    PsiMethodMember[] constructors = baseConstructors.toArray(new PsiMethodMember[baseConstructors.size()]);
    if (constructors.length == 0) {
      constructors = new PsiMethodMember[baseConstrs.length];
      for (int i = 0; i < baseConstrs.length; i++) {
        constructors[i] = new PsiMethodMember(baseConstrs[i], substitutor);
      }
    }

    LOG.assertTrue(constructors.length >=1); // Otherwise we won't have been messing with all this stuff
    boolean isCopyJavadoc = true;
    if (constructors.length > 1) {
      MemberChooser<PsiMethodMember> chooser = new MemberChooser<PsiMethodMember>(constructors, false, true, project);
      chooser.setTitle(QuickFixBundle.message("super.class.constructors.chooser.title"));
      chooser.show();
      if (chooser.getExitCode() != MemberChooser.OK_EXIT_CODE) return;
      constructors = chooser.getSelectedElements(new PsiMethodMember[0]);
      isCopyJavadoc = chooser.isCopyJavadoc();
    }

    final PsiMethodMember[] constructors1 = constructors;
    final boolean isCopyJavadoc1 = isCopyJavadoc;
    ApplicationManager.getApplication().runWriteAction (
      new Runnable() {
        @Override
        public void run() {
          try {
            PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
            CodeStyleManager reformatter = CodeStyleManager.getInstance(project);
            PsiMethod derived = null;
            for (PsiMethodMember candidate : constructors1) {
              PsiMethod base = candidate.getElement();
              derived = GenerateMembersUtil.substituteGenericMethod(base, candidate.getSubstitutor());

              if (!isCopyJavadoc1) {
                final PsiDocComment docComment = derived.getDocComment();
                if (docComment != null) {
                  docComment.delete();
                }
              }

              derived.getNameIdentifier().replace(targetClass.getNameIdentifier());
              @NonNls StringBuffer buffer = new StringBuffer();
              buffer.append("void foo () {\nsuper(");

              PsiParameter[] params = derived.getParameterList().getParameters();
              for (int j = 0; j < params.length; j++) {
                PsiParameter param = params[j];
                buffer.append(param.getName());
                if (j < params.length - 1) buffer.append(",");
              }
              buffer.append(");\n}");
              PsiMethod stub = factory.createMethodFromText(buffer.toString(), targetClass);

              derived.getBody().replace(stub.getBody());
              derived = (PsiMethod)reformatter.reformat(derived);
              derived = (PsiMethod)targetClass.add(derived);
            }
            if (derived != null) {
              editor.getCaretModel().moveToOffset(derived.getTextRange().getStartOffset());
              editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
            }
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }

          UndoUtil.markPsiFileForUndo(targetClass.getContainingFile());
        }
      }
    );
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
