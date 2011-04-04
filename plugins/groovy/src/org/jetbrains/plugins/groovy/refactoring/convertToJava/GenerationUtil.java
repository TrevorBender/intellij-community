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
package org.jetbrains.plugins.groovy.refactoring.convertToJava;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.refactoring.DefaultGroovyVariableNameValidator;
import org.jetbrains.plugins.groovy.refactoring.GroovyNameSuggestionUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Maxim.Medvedev
 */
public class GenerationUtil {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.refactoring.convertToJava.GenerationUtil");

  private GenerationUtil() {
  }

  public static void writeType(StringBuilder builder, PsiType type) {
    builder.append(type.getCanonicalText()); //todo make smarter.
  }

  private static void writeTypeParameters(StringBuilder builder, PsiType[] parameters) {
    if (parameters.length == 0) return;

    builder.append("<");
    for (PsiType parameter : parameters) {
      writeType(builder, parameter);
      builder.append(", ");
    }
    builder.replace(builder.length() - 2, builder.length(), ">");
  }

  static String suggestVarName(PsiType type, GroovyPsiElement context, ExpressionContext expressionContext) {
    final DefaultGroovyVariableNameValidator nameValidator =
      new DefaultGroovyVariableNameValidator(context, expressionContext.myUsedVarNames, true);
    final String[] varNames = GroovyNameSuggestionUtil.suggestVariableNameByType(type, nameValidator);

    LOG.assertTrue(varNames.length > 0);
    expressionContext.myUsedVarNames.add(varNames[0]);
    return varNames[0];
  }

  public static void writeCodeReferenceElement(StringBuilder builder, GrCodeReferenceElement referenceElement) {
    //todo
    throw new UnsupportedOperationException();
  }

  public static void invokeMethodByName(GrExpression caller,
                                        String methodName,
                                        GrExpression[] exprs,
                                        GrNamedArgument[] namedArgs,
                                        GrClosableBlock[] closureArgs,
                                        ExpressionGenerator expressionGenerator,
                                        GroovyPsiElement psiContext) {
    final GroovyResolveResult call;

    final PsiType type = caller.getType();
    if (type == null) {
      call = GroovyResolveResult.EMPTY_RESULT;
    }
    else {
      final PsiType[] argumentTypes = PsiUtil.getArgumentTypes(namedArgs, exprs, closureArgs, false, null);
      final GroovyResolveResult[] candidates = ResolveUtil.getMethodCandidates(type, methodName, psiContext, argumentTypes);
      call = PsiImplUtil.extractUniqueResult(candidates);
    }
    invokeMethodByResolveResult(caller, call, methodName, exprs, namedArgs, closureArgs, expressionGenerator, psiContext);
  }

  public static void invokeMethodByResolveResult(GrExpression caller,
                                                 GroovyResolveResult resolveResult,
                                                 String methodName,
                                                 GrExpression[] exprs,
                                                 GrNamedArgument[] namedArgs,
                                                 GrClosableBlock[] closureArgs,
                                                 ExpressionGenerator expressionGenerator,
                                                 GroovyPsiElement psiContext) {
    final PsiElement resolved = resolveResult.getElement();
    if (resolved instanceof PsiMethod) {
      final PsiSubstitutor substitutor = resolveResult.getSubstitutor();
      expressionGenerator.invokeMethodOn(((PsiMethod)resolved), caller, exprs, namedArgs, closureArgs, substitutor, psiContext);
      return;
    }
    //other case
    final StringBuilder builder = expressionGenerator.getBuilder();
    final ExpressionContext expressionContext = expressionGenerator.getContext();

    caller.accept(expressionGenerator);
    builder.append(".").append(methodName);
    final ArgumentListGenerator argumentListGenerator = new ArgumentListGenerator(builder, expressionContext);
    argumentListGenerator.generate(null, exprs, namedArgs, closureArgs, psiContext);
  }

  public static void writeModifierList(StringBuilder builder, GrModifierList modifierList) {
    final PsiElement[] modifiers = modifierList.getModifiers();

  }

  public static boolean writeModifiers(StringBuffer text, PsiModifierList modifierList, String[] modifiers) {
    boolean wasAddedModifiers = false;
    for (String modifierType : modifiers) {
      if (modifierList.hasModifierProperty(modifierType)) {
        text.append(modifierType);
        text.append(" ");
        wasAddedModifiers = true;
      }
    }
    return wasAddedModifiers;
  }

  public static void writeClassModifiers(StringBuffer text,
                                          @Nullable PsiModifierList modifierList,
                                          boolean isInterface,
                                          boolean toplevel) {
    if (modifierList == null) {
      text.append("public ");
      return;
    }

    List<String> allowedModifiers = new ArrayList<String>();
    allowedModifiers.add(PsiModifier.PUBLIC);
    allowedModifiers.add(PsiModifier.FINAL);
    if (!toplevel) {
      allowedModifiers.addAll(Arrays.asList(PsiModifier.PROTECTED, PsiModifier.PRIVATE, PsiModifier.STATIC));
    }
    if (!isInterface) {
      allowedModifiers.add(PsiModifier.ABSTRACT);
    }

    writeModifiers(text, modifierList, allowedModifiers.toArray(new String[allowedModifiers.size()]));
  }
}