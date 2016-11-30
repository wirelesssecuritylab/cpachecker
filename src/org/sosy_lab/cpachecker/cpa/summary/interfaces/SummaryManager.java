/*
 * CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2016  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.summary.interfaces;

import java.util.Collection;
import java.util.function.Function;
import org.sosy_lab.cpachecker.cfa.blocks.Block;
import org.sosy_lab.cpachecker.cfa.blocks.BlockPartitioning;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.interfaces.AbstractDomain;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;

public interface SummaryManager {

  /**
   * Calculate the abstract successors subject to a summary
   * {@code pSummary}
   *
   * @param pState Initial state, associated with a function call.
   * @param pPrecision Analysis precision at the to-state.
   * @param pSummary Summary available for the called function.
   * @param pBlock The block for which the summary was calculated.
   *               Contains information obtained from the dataflow analysis,
   *               which is useful for summary application.
   * @return Set of abstract states resulting from the summary application.
   */
  Collection<? extends AbstractState> getAbstractSuccessorsForSummary(
      AbstractState pState,
      Precision pPrecision,
      Summary pSummary,
      Block pBlock)
      throws CPATransferException, InterruptedException;

  /**
   * Project summary to the function precondition (state at the
   * entry node for the function).
   *
   * @param pSummary Function summary
   * @return Projection of the summary to the function entry point:
   * the summary precondition.
   */
  AbstractState projectToPrecondition(Summary pSummary);

  /**
   * @param pSummary Function summary
   * @return Projection of the summary to the return site:
   * the summary postcondition.
   */
  AbstractState projectToPostcondition(Summary pSummary);

  /**
   * Generate summary from the result of the intraprocedural analysis,
   * represented by the {@link ReachedSet} {@code pReached}.
   * Normally, the implementation would like to get the precondition from the entry
   * state, postcondition from the exit state,
   * and weaken the precondition to exclude irrelevant information,
   * such as global variables which are not read inside the block.
   *
   * @param pEntryNode Entry node for the summarized block.
   * @param pReached Result of intraprocedural analysis.
   * @param pProjection Function for projecting the whole state to
   *                   the state associated with the current CPA.
   * @param pBlock The block for which the summary is generated.
   * @return summary describing subsuming the result.
   */
  Summary generateSummary(
      CFANode pEntryNode,
      AbstractState pEntryState,
      Precision pEntryPrecision,
      ReachedSet pReached,
      Function<? extends AbstractState, ? extends AbstractState> pProjection,
      Block pBlock
  );

  /**
   * Optionally merge two summaries, same interface as
   * {@link org.sosy_lab.cpachecker.core.interfaces.MergeOperator}.
   * The result has to subsume the second argument.
   *
   * <p>Implementation should return the second argument to refuse
   * merging and to keep the states separate instead.
   */
  Summary merge(Summary pSummary1, Summary pSummary2) throws CPAException, InterruptedException;

  /**
   * <p>Coverage relation for summaries: precondition may be weakened,
   * postcondition may be strengthened.
   * There is no point in storing a summary with a stronger precondition
   * and a weaker postcondition of an already existing one.
   *
   * <p>An implementation of this interface might be able to provide a
   * more efficient comparison.
   *
   *
   * @return whether {@code pSummary1} is described by {@code pSummary2}.
   */
  default boolean isDescribedBy(Summary pSummary1,
                                Summary pSummary2,
                                AbstractDomain domain) throws CPAException, InterruptedException {
    return domain.isLessOrEqual(
        projectToPrecondition(pSummary1),
        projectToPrecondition(pSummary2)
    ) && domain.isLessOrEqual(
        projectToPostcondition(pSummary2),
        projectToPostcondition(pSummary1)
    );
  }

  /**
   * Communicate the block partitioning to the configurable
   * program analysis.
   */
  default void setBlockPartitioning(BlockPartitioning pPartitioning) {}
}
