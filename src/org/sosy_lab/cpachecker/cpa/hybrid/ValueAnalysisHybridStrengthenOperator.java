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
package org.sosy_lab.cpachecker.cpa.hybrid;

import com.google.common.collect.Sets;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.ast.c.CArraySubscriptExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CSimpleDeclaration;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.cpa.hybrid.abstraction.HybridStrengthenOperator;
import org.sosy_lab.cpachecker.cpa.hybrid.exception.InvalidAssumptionException;
import org.sosy_lab.cpachecker.cpa.hybrid.util.ExpressionUtils;
import org.sosy_lab.cpachecker.cpa.value.ValueAnalysisState;
import org.sosy_lab.cpachecker.cpa.value.ValueAnalysisState.ValueAndType;
import org.sosy_lab.cpachecker.util.states.MemoryLocation;

/**
 * This class provides the strengthening for a HybridAnalysisState via a ValueAnalysisState
 */
public class ValueAnalysisHybridStrengthenOperator implements HybridStrengthenOperator {

  private final AssumptionGenerator assumptionGenerator;
  private final LogManager logger;

  public ValueAnalysisHybridStrengthenOperator(
      AssumptionGenerator pAssumptionGenerator,
      LogManager pLogger) {
    this.assumptionGenerator = pAssumptionGenerator;
    this.logger = pLogger;
  }

  @Override
  public HybridAnalysisState strengthen(
      HybridAnalysisState pStateToStrengthen,
      AbstractState pStrengtheningState,
      CFAEdge pEdge) {

    // operator only excepts ValueAnalysisStates
    assert pStrengtheningState instanceof ValueAnalysisState;

    ValueAnalysisState strengtheningState = (ValueAnalysisState) pStrengtheningState;

    // check for assumptions containing a variable that is also tracked by the ValueAnalysis and remove them
    Set<CBinaryExpression> assumptions = pStateToStrengthen.getExplicitAssumptions();

    // used to collect all binary expressions, that are already tracked by the value analysis and thus can be removed
    Set<CBinaryExpression> removableAssumptions = Sets.newHashSet();

    Set<MemoryLocation> unknownValues = retrieveUnknownValues(
      strengtheningState.getTrackedMemoryLocations(), 
      strengtheningState);

    Set<MemoryLocation> trackedVariables = Sets.newHashSet(strengtheningState.getTrackedMemoryLocations());
    trackedVariables.removeAll(unknownValues); // we can safely remove those memory locations, because later on we create hybrid values for them

    for(CBinaryExpression binaryExpression : assumptions) {

      boolean keepOffset = binaryExpression.getOperand1() instanceof CArraySubscriptExpression;

      @Nullable final String variableName = ExpressionUtils.extractVariableIdentifier(binaryExpression);

      for(MemoryLocation memoryLocation : trackedVariables) {

        if(compareNames(variableName, memoryLocation, keepOffset)) {
          removableAssumptions.add(binaryExpression);
          // the assumption was added anyway
          break;
        }

      }
    }

    // remove unnecessary assumptions
    assumptions.removeAll(removableAssumptions);

    // build new assumptions for unknown values
    try {
      assumptions.addAll(createAssumptionsForUnknownValues(
          unknownValues,
          pStateToStrengthen));
    } catch (InvalidAssumptionException iae) {
      logger.log(
          Level.WARNING,
          "An error occurred while trying to generate assumptions for the unknown values.",
          unknownValues);
    }

    // build collection with variable identifiers for the internal var cache
    Set<CExpression> variableIdentifiers = assumptions
      .stream()
      .map(assumption -> assumption.getOperand1())
      .collect(Collectors.toSet());
    
    return new HybridAnalysisState(
        assumptions,
        variableIdentifiers,
        pStateToStrengthen.getDeclarations());
  }

  private boolean compareNames(
      @Nullable String pVariableName,
      MemoryLocation pMemoryLocation,
      boolean keepOffset) {

    if(pVariableName == null) {
      return false;
    }

    StringBuilder nameBuilder = new StringBuilder();

    if(pVariableName.contains("::")) {
      nameBuilder.append(pMemoryLocation.getFunctionName()).append("::");
    }

    nameBuilder.append(pMemoryLocation.getIdentifier());

    if(keepOffset) {
      nameBuilder.append("/").append(pMemoryLocation.getOffset());
    }

    return pVariableName.equals(nameBuilder.toString());
  }

  /*
   * checks for every MemoryLocation 
   */
  private Set<MemoryLocation> retrieveUnknownValues(
    final Set<MemoryLocation> pMemoryLocations,
    final ValueAnalysisState pValueAnalysisState) {
    
    Set<MemoryLocation> unknownValues = Sets.newHashSet();

    for(MemoryLocation memLoc : pMemoryLocations) {
      ValueAndType valueAndType = pValueAnalysisState.getValueAndTypeFor(memLoc);
      if(valueAndType.getValue().isUnknown()) {
        unknownValues.add(memLoc);
      }
    }

    return unknownValues;
  }

  private Set<CBinaryExpression> createAssumptionsForUnknownValues(
    Set<MemoryLocation> pMemoryLocations,
    HybridAnalysisState pState) throws InvalidAssumptionException {

      Set<CBinaryExpression> createdAssumptions =  Sets.newHashSet();

      for(MemoryLocation memoryLocation : pMemoryLocations) {
        // TODO check building of names
        final String variableName = memoryLocation.isOnFunctionStack()
            ? String.format("%s::%s", memoryLocation.getFunctionName(), memoryLocation.getIdentifier())
            : memoryLocation.getIdentifier();

        Optional<CSimpleDeclaration> declarationOpt = pState.getDeclarationForName(variableName);

        // we found a declaration for the name and can now build a hybrid value (assumption)
        if(declarationOpt.isPresent()) {
          createdAssumptions.add(assumptionGenerator.generateAssumption(declarationOpt.get()));
        }
      }

      return createdAssumptions;
  }
}