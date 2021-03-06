/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.migration;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class StringBufferReplaceableByStringBuilderInspection extends BaseInspection {

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @NotNull
  public String getID() {
    return "StringBufferMayBeStringBuilder";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("string.buffer.replaceable.by.string.builder.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("string.buffer.replaceable.by.string.builder.problem.descriptor");
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new StringBufferMayBeStringBuilderFix();
  }

  private static class StringBufferMayBeStringBuilderFix
    extends InspectionGadgetsFix {

    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message(
        "string.buffer.replaceable.by.string.builder.replace.quickfix");
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiElement element = descriptor.getPsiElement();
      final PsiElement parent = element.getParent();
      final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
      final PsiClass stringBuilderClass = psiFacade.findClass(CommonClassNames.JAVA_LANG_STRING_BUILDER, element.getResolveScope());
      if (stringBuilderClass == null) {
        return;
      }
      final PsiElementFactory factory = psiFacade.getElementFactory();
      final PsiJavaCodeReferenceElement stringBuilderClassReference = factory.createClassReferenceElement(stringBuilderClass);
      final PsiClassType stringBuilderType = factory.createType(stringBuilderClass);
      final PsiTypeElement stringBuilderTypeElement = factory.createTypeElement(stringBuilderType);
      final PsiElement grandParent = parent.getParent();
      final PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement)grandParent;
      final PsiElement[] declaredElements = declarationStatement.getDeclaredElements();
      for (PsiElement declaredElement : declaredElements) {
        if (!(declaredElement instanceof PsiVariable)) {
          continue;
        }
        replaceWithStringBuilder(stringBuilderClassReference, stringBuilderTypeElement, (PsiVariable)declaredElement);
      }
    }

    private static void replaceWithStringBuilder(PsiJavaCodeReferenceElement newClassReference,
                                                 PsiTypeElement newTypeElement,
                                                 PsiVariable variable) {
      final PsiExpression initializer = variable.getInitializer();
      if (!(initializer instanceof PsiNewExpression)) {
        return;
      }
      final PsiNewExpression newExpression = (PsiNewExpression)initializer;
      final PsiJavaCodeReferenceElement classReference = newExpression.getClassReference(); // no anonymous classes because StringBuffer is final
      if (classReference == null) {
        return;
      }
      final PsiTypeElement typeElement = variable.getTypeElement();
      if (typeElement != null && typeElement.getParent() == variable) {
        typeElement.replace(newTypeElement);
      }
      classReference.replace(newClassReference);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new StringBufferReplaceableByStringBuilderVisitor();
  }

  private static class StringBufferReplaceableByStringBuilderVisitor extends BaseInspectionVisitor {

    private static final Set<String> excludes = new HashSet(Arrays.asList(CommonClassNames.JAVA_LANG_STRING_BUILDER,
                                                                          CommonClassNames.JAVA_LANG_STRING_BUFFER));

    @Override
    public void visitDeclarationStatement(PsiDeclarationStatement statement) {
      if (!PsiUtil.isLanguageLevel5OrHigher(statement)) {
        return;
      }
      super.visitDeclarationStatement(statement);
      final PsiElement[] declaredElements = statement.getDeclaredElements();
      if (declaredElements.length == 0) {
        return;
      }
      for (PsiElement declaredElement : declaredElements) {
        if (!(declaredElement instanceof PsiLocalVariable)) {
          return;
        }
        final PsiLocalVariable variable = (PsiLocalVariable)declaredElement;
        final PsiElement context = PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class, true, PsiClass.class);
        if (!isReplaceableStringBuffer(variable, context)) {
          return;
        }
      }
      final PsiLocalVariable firstVariable = (PsiLocalVariable)declaredElements[0];
      registerVariableError(firstVariable);
    }

    private static boolean isReplaceableStringBuffer(PsiVariable variable, PsiElement context) {
      if (context == null) {
        return false;
      }
      final PsiType type = variable.getType();
      if (!TypeUtils.typeEquals(CommonClassNames.JAVA_LANG_STRING_BUFFER, type)) {
        return false;
      }
      final PsiExpression initializer = variable.getInitializer();
      if (initializer == null) {
        return false;
      }
      if (!isNewStringBuffer(initializer)) {
        return false;
      }
      if (VariableAccessUtils.variableIsAssigned(variable, context)) {
        return false;
      }
      if (VariableAccessUtils.variableIsAssignedFrom(variable, context)) {
        return false;
      }
      if (VariableAccessUtils.variableIsReturned(variable, context)) {
        return false;
      }
      if (VariableAccessUtils.variableIsPassedAsMethodArgument(variable, excludes, context)) {
        return false;
      }
      if (VariableAccessUtils.variableIsUsedInInnerClass(variable, context)) {
        return false;
      }
      return true;
    }

    private static boolean isNewStringBuffer(PsiExpression expression) {
      if (expression == null) {
        return false;
      }
      else if (expression instanceof PsiNewExpression) {
        return true;
      }
      else if (expression instanceof PsiMethodCallExpression) {
        final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)expression;
        final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
        @NonNls final String methodName = methodExpression.getReferenceName();
        if (!"append".equals(methodName)) {
          return false;
        }
        final PsiExpression qualifier = methodExpression.getQualifierExpression();
        return isNewStringBuffer(qualifier);
      }
      return false;
    }
  }
}