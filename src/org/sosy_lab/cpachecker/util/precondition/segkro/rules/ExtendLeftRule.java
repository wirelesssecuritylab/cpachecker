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
package org.sosy_lab.cpachecker.util.precondition.segkro.rules;

import static org.sosy_lab.cpachecker.util.predicates.matching.SmtAstPatternBuilder.*;

import java.util.Collection;
import java.util.Map;

import org.sosy_lab.cpachecker.exceptions.SolverException;
import org.sosy_lab.cpachecker.util.precondition.segkro.rules.GenericPatterns.PropositionType;
import org.sosy_lab.cpachecker.util.predicates.Solver;
import org.sosy_lab.cpachecker.util.predicates.interfaces.BooleanFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.Formula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.NumeralFormula.IntegerFormula;
import org.sosy_lab.cpachecker.util.predicates.matching.SmtAstMatcher;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;


public class ExtendLeftRule extends PatternBasedRule {

  public ExtendLeftRule(Solver pSolver, SmtAstMatcher pMatcher) {
    super(pSolver, pMatcher);
  }

  @Override
  protected void setupPatterns() {
    premises.add(new PatternBasedPremise(
      or(
        matchExistsQuantBind("exists",
            and(
              GenericPatterns.array_at_index_matcher("f", quantified("var"), PropositionType.ALL),
              match(">=",
                  matchAnyWithAnyArgsBind(quantified("x")),
                  matchAnyWithAnyArgsBind("i")),
              match("<=",
                  matchAnyWithAnyArgsBind(quantified("x")),
                  matchAnyWithAnyArgsBind("j")))),

        matchForallQuantBind("forall",
            and(
              GenericPatterns.array_at_index_matcher("f", quantified("var"), PropositionType.ALL),
              match(">=",
                  matchAnyWithAnyArgsBind(quantified("x")),
                  matchAnyWithAnyArgsBind("i")),
              match("<=",
                  matchAnyWithAnyArgsBind(quantified("x")),
                  matchAnyWithAnyArgsBind("j"))))
    )));

    premises.add(new PatternBasedPremise(
      or(
        match("<",
            matchNumeralExpressionBind("k"),
            matchNumeralExpressionBind("i")),
        match("=",
            matchNumeralExpressionBind("k"),
            matchNumeralExpressionBind("i")),
        match("<=",
            matchNumeralExpressionBind("k"),
            matchNumeralExpressionBind("i"))
        )
    ));
  }

  @Override
  protected boolean satisfiesConstraints(Map<String, Formula> pAssignment) throws SolverException, InterruptedException {
//    final IntegerFormula i = (IntegerFormula) Preconditions.checkNotNull(pAssignment.get("i"));
//    final IntegerFormula equalToI = (IntegerFormula) Preconditions.checkNotNull(pAssignment.get("=i"));
//
//    return solver.isUnsat(bfm.not(ifm.equal(i, equalToI)));
    return true;
  }

  @Override
  protected Collection<BooleanFormula> deriveConclusion(Map<String, Formula> pAssignment) {
    final IntegerFormula j = (IntegerFormula) Preconditions.checkNotNull(pAssignment.get("j"));
    final IntegerFormula k = (IntegerFormula) Preconditions.checkNotNull(pAssignment.get("k"));
    final BooleanFormula f = (BooleanFormula) Preconditions.checkNotNull(pAssignment.get("f"));

    final IntegerFormula xBound = (IntegerFormula) pAssignment.get(quantified("var"));
    final Formula xBoundParent = pAssignment.get(parentOf(quantified("var")));
    final IntegerFormula xNew = ifm.makeVariable("x");
    final BooleanFormula fNew = (BooleanFormula) substituteInParent(xBoundParent, xBound, xNew, f);

    final BooleanFormula xConstraint =  bfm.and(
        ifm.greaterOrEquals(xNew, k),
        ifm.lessOrEquals(xNew, j));

    if (pAssignment.containsKey("forall")) {
      return Lists.newArrayList(qfm.forall(Lists.newArrayList(xNew), bfm.and(fNew, xConstraint)));
    } else {
      return Lists.newArrayList(qfm.exists(Lists.newArrayList(xNew), bfm.and(fNew, xConstraint)));
    }
  }
}
