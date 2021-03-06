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
package org.jetbrains.plugins.groovy.actions.generate;

import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.codeInsight.generation.PsiGenerationInfo;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.GrReferenceAdjuster;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

/**
 * @author peter
 */
public class GroovyGenerationInfo<T extends PsiMember> extends PsiGenerationInfo<T>{
  public GroovyGenerationInfo(@NotNull T member, boolean mergeIfExists) {
    super(member, mergeIfExists);
  }

  public GroovyGenerationInfo(@NotNull T member) {
    super(member);
  }

  @Override
  public void insert(PsiClass aClass, PsiElement anchor, boolean before) throws IncorrectOperationException {
    super.insert(aClass, anchor, before);
    final T member = getPsiMember();
    if (member != null) {
      assert member instanceof GroovyPsiElement;
      GrReferenceAdjuster.shortenReferences(member);
    }
  }

  @Override
  public PsiElement findInsertionAnchor(@NotNull PsiClass aClass, @NotNull PsiElement leaf) {
    PsiElement element = leaf;
    if (element.getParent() != aClass) {
      while (element.getParent().getParent() != aClass) {
        element = element.getParent();
      }
    }

    final GrTypeDefinition typeDefinition = (GrTypeDefinition)aClass;
    PsiElement lBrace = typeDefinition.getLBrace();
    if (lBrace == null) {
      return null;
    }
    else {
      PsiElement rBrace = typeDefinition.getRBrace();
      if (!GenerateMembersUtil.isChildInRange(element, lBrace.getNextSibling(), rBrace)) {
        return null;
      }
    }

    while (element != null &&
           element.getPrevSibling() != null &&
           TokenSets.WHITE_SPACES_SET.contains(element.getPrevSibling().getNode().getElementType())) {
      element = element.getPrevSibling();
    }

    return element;
  }
}
