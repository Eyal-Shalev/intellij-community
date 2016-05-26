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

/*
 * User: anna
 * Date: 02-Oct-2008
 */
package com.intellij.refactoring.inlineSuperClass.usageInfo;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiImportStaticStatement;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;

public class ReplaceStaticImportUsageInfo extends FixableUsageInfo {
  private final PsiImportStaticStatement myStaticImportStatement;
  private final PsiClass[] myTargetClasses;

  public ReplaceStaticImportUsageInfo(final PsiImportStaticStatement staticImportStatement, final PsiClass[] targetClass) {
    super(staticImportStatement);
    myStaticImportStatement = staticImportStatement;
    myTargetClasses = targetClass;
  }

  public void fixUsage() throws IncorrectOperationException {
    final String memberName = myStaticImportStatement.getReferenceName();
    myStaticImportStatement.replace(JavaPsiFacade.getInstance(myStaticImportStatement.getProject()).getElementFactory().createImportStaticStatement(myTargetClasses[0],
                                                                                                                                                    memberName != null ? memberName : "*"));
  }

  @Override
  public String getConflictMessage() {
    if (myTargetClasses.length != 1) {
      return "Static import can be replaced with any of " + StringUtil.join(myTargetClasses, psiClass -> {
        return psiClass.getQualifiedName();
      }, ", ");
    }
    return super.getConflictMessage();
  }
}