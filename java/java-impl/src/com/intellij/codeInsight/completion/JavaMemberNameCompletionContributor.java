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
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.*;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PsiJavaPatterns;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.introduceField.InplaceIntroduceFieldPopup;
import com.intellij.refactoring.introduceVariable.IntroduceVariableBase;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static com.intellij.patterns.PlatformPatterns.psiElement;
import static com.intellij.patterns.PsiJavaPatterns.psiClass;
import static com.intellij.patterns.PsiJavaPatterns.psiField;
import static com.intellij.patterns.StandardPatterns.or;

/**
 * @author peter
 */
public class JavaMemberNameCompletionContributor extends CompletionContributor {
  public static final ElementPattern<PsiElement> INSIDE_TYPE_PARAMS_PATTERN =
    psiElement().afterLeaf(psiElement().withText("?").afterLeaf("<", ","));
  static final int MAX_SCOPE_SIZE_TO_SEARCH_UNRESOLVED = 50000;

  public JavaMemberNameCompletionContributor() {
    extend(
        CompletionType.BASIC,
        psiElement(PsiIdentifier.class).andNot(INSIDE_TYPE_PARAMS_PATTERN).withParent(
            or(psiElement(PsiLocalVariable.class), psiElement(PsiParameter.class))),
        new CompletionProvider<CompletionParameters>() {
          public void addCompletions(@NotNull final CompletionParameters parameters, final ProcessingContext matchingContext, @NotNull final CompletionResultSet result) {
            final Set<LookupElement> lookupSet = new THashSet<LookupElement>();
                completeLocalVariableName(lookupSet, result.getPrefixMatcher(), (PsiVariable)parameters.getPosition().getParent(), parameters.getInvocationCount() >= 1);
            for (final LookupElement item : lookupSet) {
              if (item instanceof LookupItem) {
                ((LookupItem)item).setAutoCompletionPolicy(AutoCompletionPolicy.GIVE_CHANCE_TO_OVERWRITE);
              }
              result.addElement(item);
            }
          }
        });
    extend(
        CompletionType.BASIC,
        psiElement(PsiIdentifier.class).withParent(PsiField.class).andNot(INSIDE_TYPE_PARAMS_PATTERN),
        new CompletionProvider<CompletionParameters>() {
      public void addCompletions(@NotNull final CompletionParameters parameters, final ProcessingContext matchingContext, @NotNull final CompletionResultSet result) {
        final Set<LookupElement> lookupSet = new THashSet<LookupElement>();
        final PsiField variable = (PsiField)parameters.getPosition().getParent();
        completeFieldName(lookupSet, variable, result.getPrefixMatcher(), parameters.getInvocationCount() >= 1);
        completeMethodName(lookupSet, variable, result.getPrefixMatcher());
        for (final LookupElement item : lookupSet) {
          result.addElement(item);
        }
      }
    });
    extend(
        CompletionType.BASIC, PsiJavaPatterns.psiElement().nameIdentifierOf(PsiJavaPatterns.psiMethod().withParent(PsiClass.class)),
        new CompletionProvider<CompletionParameters>() {
          public void addCompletions(@NotNull final CompletionParameters parameters, final ProcessingContext matchingContext, @NotNull final CompletionResultSet result) {
            final Set<LookupElement> lookupSet = new THashSet<LookupElement>();
            completeMethodName(lookupSet, parameters.getPosition().getParent(), result.getPrefixMatcher());
            for (final LookupElement item : lookupSet) {
              result.addElement(item);
            }
          }
        });

  }

  private static void completeLocalVariableName(Set<LookupElement> set, PrefixMatcher matcher, PsiVariable var, boolean includeOverlapped) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed("editing.completion.variable.name");
    final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(var.getProject());
    final VariableKind variableKind = codeStyleManager.getVariableKind(var);

    String propertyName = null;
    if (variableKind == VariableKind.PARAMETER) {
      final PsiMethod method = PsiTreeUtil.getParentOfType(var, PsiMethod.class);
      propertyName = PropertyUtil.getPropertyName(method);
      if (method != null && method.getName().startsWith("with")) {
        propertyName = StringUtil.decapitalize(method.getName().substring(4));
      }
    }

    final PsiType type = var.getType();
    SuggestedNameInfo suggestedNameInfo = codeStyleManager.suggestVariableName(variableKind, propertyName, null, type, StringUtil.isEmpty(matcher.getPrefix()));
    suggestedNameInfo = codeStyleManager.suggestUniqueVariableName(suggestedNameInfo, var, false);
    final String[] suggestedNames = suggestedNameInfo.names;
    addLookupItems(set, suggestedNameInfo, matcher, suggestedNames);
    if (set.isEmpty()) {
      if (type.equalsToText(CommonClassNames.JAVA_LANG_OBJECT) && matcher.prefixMatches("object")) {
        set.add(LookupElementBuilder.create("object"));
      }
      if (type.equalsToText(CommonClassNames.JAVA_LANG_STRING) && matcher.prefixMatches("string")) {
        set.add(LookupElementBuilder.create("string"));
      }
    }

    if (set.isEmpty() && includeOverlapped) {
      addLookupItems(set, null, matcher, getOverlappedNameVersions(matcher.getPrefix(), suggestedNames, ""));
    }
    PsiElement parent = PsiTreeUtil.getParentOfType(var, PsiCodeBlock.class);
    if(parent == null) parent = PsiTreeUtil.getParentOfType(var, PsiMethod.class);
    addLookupItems(set, suggestedNameInfo, matcher, getUnresolvedReferences(parent, false));

    PsiExpression initializer = var.getInitializer();
    if (initializer != null) {
      SuggestedNameInfo initializerSuggestions = IntroduceVariableBase.getSuggestedName(type, initializer);
      addLookupItems(set, initializerSuggestions, matcher, initializerSuggestions.names);
    }
  }

  private static String[] getOverlappedNameVersions(final String prefix, final String[] suggestedNames, String suffix) {
    final List<String> newSuggestions = new ArrayList<String>();
    int longestOverlap = 0;

    for (String suggestedName : suggestedNames) {
      if (suggestedName.toUpperCase().startsWith(prefix.toUpperCase())) {
        newSuggestions.add(suggestedName);
        longestOverlap = prefix.length();
      }

      suggestedName = String.valueOf(Character.toUpperCase(suggestedName.charAt(0))) + suggestedName.substring(1);
      final int overlap = getOverlap(suggestedName, prefix);

      if (overlap < longestOverlap) continue;

      if (overlap > longestOverlap) {
        newSuggestions.clear();
        longestOverlap = overlap;
      }

      String suggestion = prefix.substring(0, prefix.length() - overlap) + suggestedName;

      final int lastIndexOfSuffix = suggestion.lastIndexOf(suffix);
      if (lastIndexOfSuffix >= 0 && suffix.length() < suggestion.length() - lastIndexOfSuffix) {
        suggestion = suggestion.substring(0, lastIndexOfSuffix) + suffix;
      }

      if (!newSuggestions.contains(suggestion)) {
        newSuggestions.add(suggestion);
      }
    }
    return ArrayUtil.toStringArray(newSuggestions);
  }

  private static int getOverlap(final String propertyName, final String prefix) {
    int overlap = 0;
    int propertyNameLen = propertyName.length();
    int prefixLen = prefix.length();
    for (int j = 1; j < prefixLen && j < propertyNameLen; j++) {
      if (prefix.substring(prefixLen - j).equals(propertyName.substring(0, j))) {
        overlap = j;
      }
    }
    return overlap;
  }

  private static String[] getUnresolvedReferences(final PsiElement parentOfType, final boolean referenceOnMethod) {
    if (parentOfType != null && parentOfType.getTextLength() > MAX_SCOPE_SIZE_TO_SEARCH_UNRESOLVED) return ArrayUtil.EMPTY_STRING_ARRAY;
    final Set<String> unresolvedRefs = new LinkedHashSet<String>();

    if (parentOfType != null) {
      parentOfType.accept(new JavaRecursiveElementWalkingVisitor() {
        @Override public void visitReferenceExpression(PsiReferenceExpression reference) {
          final PsiElement parent = reference.getParent();
          if (parent instanceof PsiReference) return;
          if (referenceOnMethod && parent instanceof PsiMethodCallExpression &&
              reference == ((PsiMethodCallExpression)parent).getMethodExpression()) {
            if (reference.resolve() == null) {
              ContainerUtil.addIfNotNull(unresolvedRefs, reference.getReferenceName());
            }
          }
          else if (!referenceOnMethod && !(parent instanceof PsiMethodCallExpression) &&reference.resolve() == null) {
            ContainerUtil.addIfNotNull(unresolvedRefs, reference.getReferenceName());
          }
        }
      });
    }
    return ArrayUtil.toStringArray(unresolvedRefs);
  }

  private static void completeFieldName(Set<LookupElement> set, PsiField var, final PrefixMatcher matcher, boolean includeOverlapped) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed("editing.completion.variable.name");

    JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(var.getProject());
    final VariableKind variableKind = JavaCodeStyleManager.getInstance(var.getProject()).getVariableKind(var);

    final String prefix = matcher.getPrefix();
    if (PsiType.VOID.equals(var.getType()) || psiField().inClass(psiClass().isInterface()).accepts(var)) {
      completeVariableNameForRefactoring(var.getProject(), set, matcher, var.getType(), variableKind, includeOverlapped, true);
      return;
    }

    SuggestedNameInfo suggestedNameInfo = codeStyleManager.suggestVariableName(variableKind, null, null, var.getType());
    final String[] suggestedNames = suggestedNameInfo.names;
    addLookupItems(set, suggestedNameInfo, matcher, suggestedNames);

    if (set.isEmpty() && includeOverlapped) {
      // use suggested names as suffixes
      final String requiredSuffix = codeStyleManager.getSuffixByVariableKind(variableKind);
      if(variableKind != VariableKind.STATIC_FINAL_FIELD){
        for (int i = 0; i < suggestedNames.length; i++)
          suggestedNames[i] = codeStyleManager.variableNameToPropertyName(suggestedNames[i], variableKind);
      }


      addLookupItems(set, null, matcher, getOverlappedNameVersions(prefix, suggestedNames, requiredSuffix));
    }

    addLookupItems(set, suggestedNameInfo, matcher, getUnresolvedReferences(var.getParent(), false));

    PsiExpression initializer = var.getInitializer();
    PsiClass containingClass = var.getContainingClass();
    if (initializer != null && containingClass != null) {
      SuggestedNameInfo initializerSuggestions = InplaceIntroduceFieldPopup.
        suggestFieldName(var.getType(), null, initializer, var.hasModifierProperty(PsiModifier.STATIC), containingClass);
      addLookupItems(set, initializerSuggestions, matcher, initializerSuggestions.names);
    }
  }

  public static void completeVariableNameForRefactoring(Project project,
                                                        Set<LookupElement> set,
                                                        PrefixMatcher matcher,
                                                        PsiType varType,
                                                        VariableKind varKind, final boolean includeOverlapped, final boolean methodPrefix) {
    JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
    SuggestedNameInfo suggestedNameInfo = codeStyleManager.suggestVariableName(varKind, null, null, varType);
    final String[] strings = completeVariableNameForRefactoring(codeStyleManager, matcher, varType, varKind, suggestedNameInfo,
                                                                includeOverlapped, methodPrefix);
    addLookupItems(set, suggestedNameInfo, matcher, strings);
  }

  public static String[] completeVariableNameForRefactoring(JavaCodeStyleManager codeStyleManager,
                                                            final PrefixMatcher matcher,
                                                            @Nullable final PsiType varType,
                                                            final VariableKind varKind,
                                                            SuggestedNameInfo suggestedNameInfo,
                                                            final boolean includeOverlapped, final boolean methodPrefix) {
    Set<String> result = new LinkedHashSet<String>();
    final String[] suggestedNames = suggestedNameInfo.names;
    for (final String suggestedName : suggestedNames) {
      if (matcher.prefixMatches(suggestedName)) {
        result.add(suggestedName);
      }
    }

    if (result.isEmpty() && PsiType.VOID != varType && includeOverlapped) {
      // use suggested names as suffixes
      final String requiredSuffix = codeStyleManager.getSuffixByVariableKind(varKind);
      final String prefix = matcher.getPrefix();
      if (varKind != VariableKind.STATIC_FINAL_FIELD || methodPrefix) {
        for (int i = 0; i < suggestedNames.length; i++) {
          suggestedNames[i] = codeStyleManager.variableNameToPropertyName(suggestedNames[i], varKind);
        }
      }

      ContainerUtil.addAll(result, getOverlappedNameVersions(prefix, suggestedNames, requiredSuffix));


    }
    return ArrayUtil.toStringArray(result);
  }

  private static void completeMethodName(Set<LookupElement> set, PsiElement element, final PrefixMatcher matcher){
    if(element instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)element;
      if (method.isConstructor()) {
        final PsiClass containingClass = method.getContainingClass();
        if (containingClass != null) {
          final String name = containingClass.getName();
          if (StringUtil.isNotEmpty(name)) {
            addLookupItems(set, null, matcher, name);
          }
        }
        return;
      }
    }

    PsiClass ourClassParent = PsiTreeUtil.getParentOfType(element, PsiClass.class);
    if (ourClassParent == null) return;
    addLookupItems(set, null, matcher, getUnresolvedReferences(ourClassParent, true));

    addLookupItems(set, null, matcher, getPropertiesHandlersNames(
      ourClassParent,
      ((PsiModifierListOwner)element).hasModifierProperty(PsiModifier.STATIC),
      PsiUtil.getTypeByPsiElement(element), element));
  }

  private static String[] getPropertiesHandlersNames(final PsiClass psiClass,
                                                    final boolean staticContext,
                                                    final PsiType varType,
                                                    final PsiElement element) {
    class Change implements Runnable {
      private String[] result;

      public void run() {
        final List<String> propertyHandlers = new ArrayList<String>();
        final PsiField[] fields = psiClass.getFields();

        for (final PsiField field : fields) {
          if (field == element) continue;
          final PsiModifierList modifierList = field.getModifierList();
          if (staticContext && (modifierList != null && !modifierList.hasModifierProperty(PsiModifier.STATIC))) continue;

          if (field.getType().equals(varType)) {
            final String getterName = PropertyUtil.suggestGetterName(field.getProject(), field);
            if ((psiClass.findMethodsByName(getterName, true).length == 0 ||
                 psiClass.findMethodBySignature(PropertyUtil.generateGetterPrototype(field), true) == null)) {
              propertyHandlers.add(getterName);
            }
          }

          if (PsiType.VOID.equals(varType)) {
            final String setterName = PropertyUtil.suggestSetterName(field.getProject(), field);
            if ((psiClass.findMethodsByName(setterName, true).length == 0 ||
                 psiClass.findMethodBySignature(PropertyUtil.generateSetterPrototype(field), true) == null)) {
              propertyHandlers.add(setterName);
            }
          }
        }
        result = ArrayUtil.toStringArray(propertyHandlers);
      }
    }
    final Change result = new Change();
    CodeStyleManager.getInstance(element.getProject()).performActionWithFormatterDisabled(result);
    return result.result;
  }

  private static void addLookupItems(Set<LookupElement> lookupElements, @Nullable final SuggestedNameInfo callback, PrefixMatcher matcher, String... strings) {
    outer:
    for (int i = 0; i < strings.length; i++) {
      String name = strings[i];
      if (!matcher.prefixMatches(name)) {
        continue;
      }

      for (LookupElement lookupElement : lookupElements) {
        if (lookupElement.getAllLookupStrings().contains(name)) {
          continue outer;
        }
      }
      
      LookupElement element = PrioritizedLookupElement.withPriority(LookupElementBuilder.create(name).withAutoCompletionPolicy(AutoCompletionPolicy.GIVE_CHANCE_TO_OVERWRITE), -i);
      if (callback != null) {
        element = LookupElementDecorator.withInsertHandler(element, new InsertHandler<LookupElementDecorator<LookupElement>>() {
          @Override
          public void handleInsert(InsertionContext context, LookupElementDecorator<LookupElement> item) {
            callback.nameChoosen(item.getLookupString());
          }
        });
      }
      lookupElements.add(element);
    }
  }
}
