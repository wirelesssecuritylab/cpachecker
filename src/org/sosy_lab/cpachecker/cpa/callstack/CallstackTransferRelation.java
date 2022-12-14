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
package org.sosy_lab.cpachecker.cpa.callstack;

import static org.sosy_lab.cpachecker.util.CFAUtils.leavingEdges;

import java.util.Collection;
import java.util.Collections;
import java.util.logging.Level;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.common.log.LogManagerWithoutDuplicates;
import org.sosy_lab.cpachecker.cfa.ast.AExpression;
import org.sosy_lab.cpachecker.cfa.ast.AFunctionCall;
import org.sosy_lab.cpachecker.cfa.ast.AFunctionCallStatement;
import org.sosy_lab.cpachecker.cfa.ast.AIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CThreadOperationStatement.CThreadCreateStatement;
import org.sosy_lab.cpachecker.cfa.model.AStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdgeType;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.FunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.FunctionEntryNode;
import org.sosy_lab.cpachecker.cfa.model.FunctionSummaryEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionSummaryStatementEdge;
import org.sosy_lab.cpachecker.cfa.postprocessing.global.CFACloner;
import org.sosy_lab.cpachecker.core.AnalysisDirection;
import org.sosy_lab.cpachecker.core.defaults.SingleEdgeTransferRelation;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.exceptions.UnsupportedCodeException;
import org.sosy_lab.cpachecker.util.CFAUtils;

public class CallstackTransferRelation extends SingleEdgeTransferRelation {

  /**
   * This flag might be set by external CPAs (e.g. BAM) to indicate
   * a recursive context that might not be recognized by the CallstackCPA.
   * (In case of BAM the operator Reduce splits an indirect recursive call f-g-f
   * into two calls f-g and g-f, which are both non-recursive.)
   * A function-call in a recursive context will be skipped,
   * if the Option 'skipRecursion' is enabled.
   */
  private boolean isRecursiveContext = false;

  protected final CallstackOptions options;
  protected final LogManagerWithoutDuplicates logger;

  public CallstackTransferRelation(CallstackOptions pOptions, LogManager pLogger) {
    options = pOptions;
    logger = new LogManagerWithoutDuplicates(pLogger);
  }

  @Override
  public Collection<? extends AbstractState> getAbstractSuccessorsForEdge(
      AbstractState pElement, Precision pPrecision, CFAEdge pEdge)
      throws CPATransferException {

    final CallstackState e = (CallstackState) pElement;
    final CFANode pred = pEdge.getPredecessor();
    final CFANode succ = pEdge.getSuccessor();
    final String predFunction = pred.getFunctionName();
    final String succFunction = succ.getFunctionName();

    switch (pEdge.getEdgeType()) {
    case StatementEdge: {
      AStatementEdge edge = (AStatementEdge)pEdge;
      if (edge.getStatement() instanceof AFunctionCall) {
        AExpression functionNameExp = ((AFunctionCall)edge.getStatement()).getFunctionCallExpression().getFunctionNameExpression();
        if (functionNameExp instanceof AIdExpression) {
          String functionName = ((AIdExpression)functionNameExp).getName();
              if (options
                  .getUnsupportedFunctions()
                  .contains(CFACloner.extractFunctionName(functionName))) {
            throw new UnsupportedCodeException(functionName, edge, edge.getStatement());
          }
        }
      }

      if (pEdge instanceof CFunctionSummaryStatementEdge) {
        if (!shouldGoByFunctionSummaryStatement(e, (CFunctionSummaryStatementEdge) pEdge)) {
          // should go by function call and skip the current edge
          return Collections.emptySet();
        }
        // otherwise use this edge just like a normal edge
      }
      break;
    }

    case FunctionCallEdge: {
        final String calledFunction = succ.getFunctionName();
        final CFANode callerNode = pred;

          if (options
              .getUnsupportedFunctions()
              .contains(CFACloner.extractFunctionName(calledFunction))) {
            throw new UnsupportedCodeException(calledFunction, pEdge);
          }

        if (hasRecursion(e, calledFunction)) {
          if (skipRecursiveFunctionCall(e, (FunctionCallEdge)pEdge)) {
            // skip recursion, don't enter function
            logger.logOnce(Level.WARNING, "Skipping recursive function call from",
                pred.getFunctionName(), "to", calledFunction);
            return Collections.emptySet();
          } else {
            // recursion is unsupported
            logger.log(Level.INFO, "Recursion detected, aborting. To ignore recursion, add -skipRecursion to the command line.");
            throw new UnsupportedCodeException("recursion", pEdge);
          }
        } else {
          // regular function call:
          //    add the called function to the current stack

          return Collections.singleton(
              new CallstackState(e, calledFunction, callerNode));
        }
      }

    case FunctionReturnEdge: {
        final String calledFunction = predFunction;
        final String callerFunction = succFunction;
        final CFANode callNode = succ.getEnteringSummaryEdge().getPredecessor();
        final CallstackState returnElement;

          assert calledFunction.equals(e.getCurrentFunction())
              || isWildcardState(e, AnalysisDirection.FORWARD);

          if (isWildcardState(e, AnalysisDirection.FORWARD)) {
            returnElement = new CallstackState(null, callerFunction, e.getCallNode());

        } else {
          if (!callNode.equals(e.getCallNode())) {
            // this is not the right return edge
            return Collections.emptySet();
          }

          // we are in a function return:
          //    remove the current function from the stack;
          //    the new abstract state is the predecessor state in the stack
          returnElement = e.getPreviousState();

            assert callerFunction.equals(returnElement.getCurrentFunction())
                || isWildcardState(returnElement, AnalysisDirection.FORWARD);
        }

        return Collections.singleton(returnElement);
      }

    default:
      break;
    }

    return Collections.singleton(pElement);
  }

  /**
   * Checks if the given callstack state should be treated as a wildcard state.
   *
   * @param pState the state to check.
   * @param direction direction of the analysis
   *
   * @return {@code true} if the given state should be treated as a wildcard,
   * {@code false} otherwise.
   */
  protected boolean isWildcardState(final CallstackState pState, AnalysisDirection direction) {
    // TODO: Maybe it would be better to have designated wildcard states (without a call node)
    // instead of this heuristic.
    String function = pState.getCurrentFunction();
    CFANode callNode = pState.getCallNode();

    // main function "call" case
    if (callNode instanceof FunctionEntryNode
        && callNode.getFunctionName().equals(function)) {
      return false;
    }

    // Normal function call case
    for (FunctionEntryNode node : CFAUtils.successorsOf(pState.getCallNode()).filter(FunctionEntryNode.class)) {
      if (node.getFunctionName().equals(pState.getCurrentFunction())) {
        return false;
      }
    }

    // Not a function call node -> wildcard state
    // Info: a backward-analysis causes an callstack-state with a non-function-call-node,
    // build from the target state on getInitialState.
    return direction == AnalysisDirection.FORWARD;
  }

  protected boolean skipRecursiveFunctionCall(final CallstackState element,
      final FunctionCallEdge callEdge) {
    // Cannot skip if there is no edge for skipping
    // (this would just terminate the path here -> unsound).
    if (leavingEdges(callEdge.getPredecessor()).filter(CFunctionSummaryStatementEdge.class).isEmpty()) {
      return false;
    }

    if (options.skipRecursion()) {
      return true;
    }
    if (options.skipFunctionPointerRecursion() && hasFunctionPointerRecursion(element, callEdge)) {
      return true;
    }
    if (options.skipVoidRecursion() && hasVoidRecursion(element, callEdge)) {
      return true;
    }
    return false;
  }

  /** check, if the current function-call has already appeared in the call-stack. */
  protected boolean hasRecursion(final CallstackState pCurrentState, final String pCalledFunction) {
    if (isRecursiveContext) { // external CPA has seen recursion
      return true;
    }
    // iterate through the current stack and search for an equal name
    CallstackState e = pCurrentState;
    int counter = 0;
    while (e != null) {
      if (e.getCurrentFunction().equals(pCalledFunction)) {
        counter++;
        if (counter > options.getRecursionBoundDepth()) {
          return true;
        }
      }
      e = e.getPreviousState();
    }
    return false;
  }

  protected boolean hasFunctionPointerRecursion(final CallstackState element,
      final FunctionCallEdge pCallEdge) {
    if (pCallEdge.getRawStatement().startsWith("pointer call(")) { // Hack, see CFunctionPointerResolver
      return true;
    }

    final String functionName = pCallEdge.getSuccessor().getFunctionName();
    CallstackState e = element;
    while (e != null) {
      if (e.getCurrentFunction().equals(functionName)) {
        // reached the previous stack frame of the same function,
        // and no function pointer so far
        return false;
      }

      if (e.getPreviousState() == null) {
        // reached beginning of program or current BAM-block, abort
        return false;
      }

      FunctionCallEdge callEdge = findOutgoingCallEdge(e.getCallNode());
      if (callEdge.getRawStatement().startsWith("pointer call(")) {
        return true;
      }

      e = e.getPreviousState();
    }
    throw new AssertionError();
  }

  protected boolean hasVoidRecursion(final CallstackState element,
      final FunctionCallEdge pCallEdge) {
    if (pCallEdge.getSummaryEdge().getExpression() instanceof AFunctionCallStatement) {
      return true;
    }

    final String functionName = pCallEdge.getSuccessor().getFunctionName();
    CallstackState e = element;
    while (e != null) {
      if (e.getCurrentFunction().equals(functionName)) {
        // reached the previous stack frame of the same function,
        // and no function pointer so far
        return false;
      }

      if (e.getPreviousState() == null) {
        // reached beginning of program or current BAM-block, abort
        return false;
      }

      FunctionSummaryEdge summaryEdge = e.getCallNode().getLeavingSummaryEdge();
      if (summaryEdge.getExpression() instanceof AFunctionCallStatement) {
        return true;
      }

      e = e.getPreviousState();
    }
    throw new AssertionError();
  }

  protected boolean shouldGoByFunctionSummaryStatement(CallstackState element, CFunctionSummaryStatementEdge sumEdge) {
    String functionName = sumEdge.getFunctionName();
    FunctionCallEdge callEdge = findOutgoingCallEdge(sumEdge.getPredecessor());
    if (sumEdge.getFunctionCall() instanceof CThreadCreateStatement) {
      //Thread operations should be handled twice, so, go by the summary edge
      return true;
    }
    assert functionName.equals(callEdge.getSuccessor().getFunctionName());
    return hasRecursion(element, functionName) && skipRecursiveFunctionCall(element, callEdge);
  }

  protected FunctionCallEdge findOutgoingCallEdge(CFANode predNode) {
    for (CFAEdge edge : leavingEdges(predNode)) {
      if (edge.getEdgeType() == CFAEdgeType.FunctionCallEdge) {
        return (FunctionCallEdge)edge;
      }
    }
    throw new AssertionError("Missing function call edge for function call summary edge after node " + predNode);
  }

  public void enableRecursiveContext() {
    isRecursiveContext = true;
  }

  public void disableRecursiveContext() {
    isRecursiveContext = false;
  }
}
