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
package com.siyeh.ipp.concatenation;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ConcatenationUtils;
import com.siyeh.ipp.psiutils.ErrorUtil;

class Jdk5StringConcatenationPredicate implements PsiElementPredicate {
  public boolean satisfiedBy(PsiElement element) {
    if (!PsiUtil.isLanguageLevel5OrHigher(element)) {
      return false;
    }
    if (!ConcatenationUtils.isConcatenation(element)) {
      return false;
    }
    if (isInsideAnnotation(element)) {
      return false;
    }
    return !ErrorUtil.containsError(element);
  }

  private static boolean isInsideAnnotation(PsiElement element) {
    return PsiTreeUtil.getParentOfType(element, PsiNameValuePair.class, PsiArrayInitializerMemberValue.class) != null;
  }
}
