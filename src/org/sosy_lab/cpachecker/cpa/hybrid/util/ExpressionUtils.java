/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2014  Dirk Beyer
 *  All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 *  CPAchecker web page:
 *    http://cpachecker.sosy-lab.org
 */
package org.sosy_lab.cpachecker.cpa.hybrid.util;

import com.google.common.collect.Sets;
import java.math.BigInteger;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;
import org.sosy_lab.cpachecker.cfa.ast.c.CArraySubscriptExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CCharLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression.BinaryOperator;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpressionAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CIntegerLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CLeftHandSide;
import org.sosy_lab.cpachecker.cfa.ast.c.CSimpleDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CStatement;
import org.sosy_lab.cpachecker.cfa.model.c.CAssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CStatementEdge;

/**
 * This class provides CExpression related functionality
 */
public final class ExpressionUtils {

  // utility class only contains static members
  private ExpressionUtils() {}

  /**
   * Calculate the Expression including the truthAssumption
   * @param pCfaEdge The respective AssumptionEdge of the cfa
   * @return the possibly inverted Expression
   */
  public static CBinaryExpression invertOnTruthAssumption(CAssumeEdge pCfaEdge) {

    CExpression assumption = pCfaEdge.getExpression();

    if(!(assumption instanceof CBinaryExpression)) {
      throw new AssertionError("Assumption must be of type CBinaryExpression.");
    }

    CBinaryExpression binaryAssumption = (CBinaryExpression) assumption;

    if(pCfaEdge.getTruthAssumption()) {

        return binaryAssumption;
    }

    // operator inversion is needed
    return invertExpression(binaryAssumption);
  }

  public static CBinaryExpression invertExpression(CBinaryExpression pExpression) {
    BinaryOperator newOperator = pExpression.getOperator().getOppositLogicalOperator();

    return new CBinaryExpression(
        pExpression.getFileLocation(),
        pExpression.getExpressionType(),
        pExpression.getCalculationType(),
        pExpression.getOperand1(),
        pExpression.getOperand2(),
        newOperator);
  }

  public static boolean checkForVariableIdentifier(CExpression pCExpression) {
    return pCExpression instanceof CIdExpression
        || pCExpression instanceof CArraySubscriptExpression;
  }

  @Nullable
  public static String extractVariableIdentifier(CExpression pExpression) {

    if(pExpression == null ) return null;

    if (pExpression instanceof CIdExpression) {

      return ((CIdExpression) pExpression).getDeclaration().getQualifiedName();
    } else if (pExpression instanceof CArraySubscriptExpression) {

      CArraySubscriptExpression arraySubscriptExpression = (CArraySubscriptExpression) pExpression;
      CExpression arrayIdentifierExpression = arraySubscriptExpression.getArrayExpression();
      return extractVariableIdentifier(arrayIdentifierExpression); // should be CIdExpression

    } else if(pExpression instanceof CBinaryExpression) {
      // try to extract for the first operand
      return extractVariableIdentifier(((CBinaryExpression)pExpression).getOperand1());
    }

    return null;
  }

  /**
   * Inspects the Expression and collects all variable identifiers.
   * Recursively inspects both operands of BinaryExpressions such that all identifiers can be found.
   * No duplicates due to set semantics.
   * @param pExpression The respective expression from which to retrieve the variale identifiers
   * @return A Set of CIdExpressions containing all variable identifiers that could be found - may be empty
   */
  public static Set<CIdExpression> extractAllVariableIdentifiers(CExpression pExpression) {

    Set<CIdExpression> variables = Sets.newHashSet();
    collectVariableIdentifiers(pExpression, variables);
    return variables;
  }

  private static void collectVariableIdentifiers(CExpression pExpression, Set<CIdExpression> pCollector) {

    // base case
    if(pExpression instanceof  CIdExpression) {

      pCollector.add((CIdExpression) pExpression);

    } else if(pExpression instanceof CArraySubscriptExpression) {

      collectVariableIdentifiers(((CArraySubscriptExpression)pExpression).getArrayExpression(), pCollector);

    } else if(pExpression instanceof CBinaryExpression) {

      CBinaryExpression binaryExpression = (CBinaryExpression)pExpression;
      collectVariableIdentifiers(binaryExpression.getOperand1(), pCollector);
      collectVariableIdentifiers(binaryExpression.getOperand2(), pCollector);
    }
  }

  public static boolean haveTheSameVariable(CExpression first, CExpression second) {

    @Nullable String nameFirst = ExpressionUtils.extractVariableIdentifier(first);
    @Nullable String nameSecond = ExpressionUtils.extractVariableIdentifier(second);

    return Objects.equals(nameFirst, nameSecond); // avoid possible null pointer
  }

  public static boolean isVerifierNondet(CFunctionCallExpression pFunctionCallExpression) {

    final String verifierName = pFunctionCallExpression.getFunctionNameExpression().toASTString();

    return verifierName.startsWith("__VERIFIER_nondet");
  }

  public static boolean assignmentContainsVariable(
      CStatementEdge pStatementEdge,
      Set<CIdExpression> pVariables) {

    CStatement statement = pStatementEdge.getStatement();
    CLeftHandSide leftHandSide = null;

    if(statement instanceof CExpressionAssignmentStatement) {
      leftHandSide = ((CExpressionAssignmentStatement)statement).getLeftHandSide();
    } else if(statement instanceof CFunctionCallAssignmentStatement) {
      leftHandSide = ((CFunctionCallAssignmentStatement)statement).getLeftHandSide();
    }

    if(leftHandSide == null) {
      return false;
    }

    boolean result = false;

    for(CExpression variableExpression : pVariables) {

      result |= haveTheSameVariable(leftHandSide, variableExpression);
    }

    return result;
  }

  public static CIntegerLiteralExpression charToIntLiteral(CCharLiteralExpression pCharLiteral) {
    return new CIntegerLiteralExpression(
                pCharLiteral.getFileLocation(), 
                pCharLiteral.getExpressionType(), 
                BigInteger.valueOf(pCharLiteral.getCharacter()));
  }

  @Nullable
  public static CSimpleDeclaration extractDeclaration(CBinaryExpression pExpression) {

    @Nullable
    CIdExpression idExpression = extractIdExpression(pExpression.getOperand1());
    if(idExpression == null) {
      return null;
    }

    return idExpression.getDeclaration();
  }

  @Nullable
  public static CIdExpression extractIdExpression(CExpression pExpression) {
    if(pExpression instanceof CIdExpression) {
      return (CIdExpression) pExpression;
    }
    if(pExpression instanceof CArraySubscriptExpression) {
      return extractIdExpression(((CArraySubscriptExpression)pExpression).getArrayExpression());
    }
    if(pExpression instanceof CBinaryExpression) {
      return extractIdExpression(((CBinaryExpression)pExpression).getOperand1());
    }

    return null;
  }
} 