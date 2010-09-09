package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyParenthesizedExpression;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PyParenthesizedExpressionImpl extends PyElementImpl implements PyParenthesizedExpression {
  public PyParenthesizedExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyParenthesizedExpression(this);
  }

  public PyExpression getContainedExpression() {
    return PsiTreeUtil.getChildOfType(this, PyExpression.class);
  }

  public PyType getType(@NotNull TypeEvalContext context) {
    final PyExpression expr = getContainedExpression();
    return expr != null ? context.getType(expr) : null;
  }
}
