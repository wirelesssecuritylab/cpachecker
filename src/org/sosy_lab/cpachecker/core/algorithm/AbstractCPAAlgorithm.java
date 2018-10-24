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
package org.sosy_lab.cpachecker.core.algorithm;

import com.google.common.base.Functions;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.common.time.Timer;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.ForcedCovering;
import org.sosy_lab.cpachecker.core.interfaces.MergeOperator;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustment;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustmentResult;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustmentResult.Action;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.interfaces.StatisticsProvider;
import org.sosy_lab.cpachecker.core.interfaces.StopOperator;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;
import org.sosy_lab.cpachecker.core.interfaces.WaitlistElement;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGMergeJoinCPAEnabledAnalysis;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.statistics.AbstractStatValue;
import org.sosy_lab.cpachecker.util.statistics.StatisticsWriter;

public abstract class AbstractCPAAlgorithm implements Algorithm, StatisticsProvider {

  private static class CPAStatistics implements Statistics {

    private Timer totalTimer = new Timer();
    private Timer chooseTimer = new Timer();
    private Timer precisionTimer = new Timer();
    private Timer transferTimer = new Timer();
    private Timer mergeTimer = new Timer();
    private Timer stopTimer = new Timer();
    private Timer addTimer = new Timer();
    private Timer forcedCoveringTimer = new Timer();

    private int countIterations = 0;
    private int maxWaitlistSize = 0;
    private long countWaitlistSize = 0;
    private int countSuccessors = 0;
    private int maxSuccessors = 0;
    private int countMerge = 0;
    private int countStop = 0;
    private int countBreak = 0;

    private Map<String, AbstractStatValue> reachedSetStatistics = new HashMap<>();

    @Override
    public String getName() {
      return "CPA algorithm";
    }

    @Override
    public void printStatistics(PrintStream out, Result pResult, UnmodifiableReachedSet pReached) {
      out.println("Number of iterations:            " + countIterations);
      if (countIterations == 0) {
        // Statistics not relevant, prevent division by zero
        return;
      }

      out.println("Max size of waitlist:            " + maxWaitlistSize);
      out.println("Average size of waitlist:        " + countWaitlistSize / countIterations);
      StatisticsWriter w = StatisticsWriter.writingStatisticsTo(out);
      for (AbstractStatValue c : reachedSetStatistics.values()) {
        w.put(c);
      }
      out.println("Number of computed successors:   " + countSuccessors);
      out.println("Max successors for one state:    " + maxSuccessors);
      out.println("Number of times merged:          " + countMerge);
      out.println("Number of times stopped:         " + countStop);
      out.println("Number of times breaked:         " + countBreak);
      out.println();
      out.println(
          "Total time for CPA algorithm:     "
              + totalTimer
              + " (Max: "
              + totalTimer.getMaxTime().formatAs(TimeUnit.SECONDS)
              + ")");
      out.println("  Time for choose from waitlist:  " + chooseTimer);
      if (forcedCoveringTimer.getNumberOfIntervals() > 0) {
        out.println("  Time for forced covering:       " + forcedCoveringTimer);
      }
      out.println("  Time for precision adjustment:  " + precisionTimer);
      out.println("  Time for transfer relation:     " + transferTimer);
      if (mergeTimer.getNumberOfIntervals() > 0) {
        out.println("  Time for merge operator:        " + mergeTimer);
      }
      out.println("  Time for stop operator:         " + stopTimer);
      out.println("  Time for adding to reached set: " + addTimer);
    }
  }

  private final ForcedCovering forcedCovering;

  private final CPAStatistics stats = new CPAStatistics();

  protected final TransferRelation transferRelation;
  protected final MergeOperator mergeOperator;
  protected final StopOperator stopOperator;
  private final PrecisionAdjustment precisionAdjustment;

  private final LogManager logger;

  private final ShutdownNotifier shutdownNotifier;

  private final AlgorithmStatus status;

  protected AbstractCPAAlgorithm(
      ConfigurableProgramAnalysis cpa,
      LogManager logger,
      ShutdownNotifier pShutdownNotifier,
      ForcedCovering pForcedCovering,
      boolean pIsImprecise) {

    transferRelation = cpa.getTransferRelation();
    mergeOperator = cpa.getMergeOperator();
    stopOperator = cpa.getStopOperator();
    precisionAdjustment = cpa.getPrecisionAdjustment();
    this.logger = logger;
    this.shutdownNotifier = pShutdownNotifier;
    this.forcedCovering = pForcedCovering;
    status = AlgorithmStatus.SOUND_AND_PRECISE.withPrecise(!pIsImprecise);
  }

  @Override
  public AlgorithmStatus run(final ReachedSet reachedSet)
      throws CPAException, InterruptedException {
    stats.totalTimer.start();
    try {
      return run0(reachedSet);
    } finally {
      stats.totalTimer.stopIfRunning();
      stats.chooseTimer.stopIfRunning();
      stats.precisionTimer.stopIfRunning();
      stats.transferTimer.stopIfRunning();
      stats.mergeTimer.stopIfRunning();
      stats.stopTimer.stopIfRunning();
      stats.addTimer.stopIfRunning();
      stats.forcedCoveringTimer.stopIfRunning();

      /*Map<String, ? extends AbstractStatValue> reachedSetStats = null;
      if (reachedSet instanceof PartitionedReachedSet) {
        reachedSetStats = ((PartitionedReachedSet) reachedSet).getStatistics();
      } else if (reachedSet instanceof PseudoPartitionedReachedSet) {
        reachedSetStats = ((PseudoPartitionedReachedSet) reachedSet).getStatistics();
      } else {
        reachedSetStats = null;
      }

      if (reachedSetStats != null) {
        for (Entry<String, ? extends AbstractStatValue> e : reachedSetStats.entrySet()) {
          String key = e.getKey();
          AbstractStatValue val = e.getValue();
          if (!stats.reachedSetStatistics.containsKey(key)) {
            stats.reachedSetStatistics.put(key, val);
          } else {
            AbstractStatValue newVal = stats.reachedSetStatistics.get(key);

            if (newVal instanceof StatCounter) {
              assert val instanceof StatCounter;
              for (int i = 0; i < ((StatCounter) val).getValue(); i++) {
                ((StatCounter) newVal).inc();
              }
            } else if (newVal instanceof StatInt) {
              assert val instanceof StatInt;
              ((StatInt) newVal).add((StatInt) val);
            } else {
              assert false : "Can't handle " + val.getClass().getSimpleName();
            }
          }
        }
      }*/
    }
  }

  private AlgorithmStatus run0(final ReachedSet reachedSet)
      throws CPAException, InterruptedException {

    if (reachedSet.hasStatesToAdd()) {
      for (Pair<AbstractState, Precision> pair : reachedSet.getStatesToAdd()) {
        frontier(reachedSet, pair.getFirst(), pair.getSecond());
      }
    }

    while (reachedSet.hasWaitingState()) {
      shutdownNotifier.shutdownIfNecessary();

      stats.countIterations++;

      // Pick next state using strategy
      // BFS, DFS or top sort according to the configuration
      int size = reachedSet.getWaitlistSize();
      if (size >= stats.maxWaitlistSize) {
        stats.maxWaitlistSize = size;
      }
      stats.countWaitlistSize += size;

      stats.chooseTimer.start();
      final WaitlistElement element = reachedSet.popFromWaitlist();
      stats.chooseTimer.stop();

      logger.log(Level.FINER, "Retrieved state from waitlist");
      try {
        if (handleTransition(element, reachedSet)) {
          // Prec operator requested break
          return status;
        }
      } catch (Exception e) {
        // re-add the old state to the waitlist, there might be unhandled successors left
        // that otherwise would be forgotten (which would be unsound)
        reachedSet.addToWaitlist(element);
        throw e;
      }

    }

    return status;
  }

  /**
   * Handle one state from the waitlist, i.e., produce successors etc.
   *
   * @param state The abstract state that was taken out of the waitlist
   * @param precision The precision for this abstract state.
   * @param reachedSet The reached set.
   * @return true if analysis should terminate, false if analysis should continue with next state
   */
  private boolean handleTransition(WaitlistElement element, final ReachedSet reachedSet)
      throws CPAException, InterruptedException {

    // TODO
    /*
    logger.log(Level.ALL, "Current state is", state, "with precision", precision);

    if (forcedCovering != null) {
      stats.forcedCoveringTimer.start();
      try {
        boolean stop = forcedCovering.tryForcedCovering(state, precision, reachedSet);

        if (stop) {
          // TODO: remove state from reached set?
          return false;
        }
      } finally {
        stats.forcedCoveringTimer.stop();
      }
    }*/

    stats.transferTimer.start();
    Collection<Pair<? extends AbstractState, ? extends Precision>> successors;
    try {
      successors = getAbstractSuccessors(element, reachedSet);
    } finally {
      stats.transferTimer.stop();
    }

    // TODO When we have a nice way to mark the analysis result as incomplete,
    // we could continue analysis on a CPATransferException with the next state from waitlist.

    int numSuccessors = successors.size();
    logger.log(Level.FINER, "Current state has", numSuccessors, "successors");
    stats.countSuccessors += numSuccessors;
    stats.maxSuccessors = Math.max(numSuccessors, stats.maxSuccessors);

    for (Iterator<Pair<? extends AbstractState, ? extends Precision>> it = successors.iterator(); it.hasNext();) {

      Pair<? extends AbstractState, ? extends Precision> pair = it.next();
      AbstractState successor = pair.getFirst();
      Precision precision = pair.getSecond();
      shutdownNotifier.shutdownIfNecessary();
      logger.log(Level.FINER, "Considering successor of current state");
      logger.log(Level.ALL, "Successor of", element, "\nis", successor);

      stats.precisionTimer.start();
      PrecisionAdjustmentResult precAdjustmentResult;
      try {
        Optional<PrecisionAdjustmentResult> precAdjustmentOptional =
            precisionAdjustment.prec(
                successor, precision, reachedSet, Functions.<AbstractState>identity(), successor);
        if (!precAdjustmentOptional.isPresent()) {
          continue;
        }
        precAdjustmentResult = precAdjustmentOptional.get();
      } finally {
        stats.precisionTimer.stop();
      }

      successor = precAdjustmentResult.abstractState();
      Precision successorPrecision = precAdjustmentResult.precision();
      Action action = precAdjustmentResult.action();

      if (action == Action.BREAK) {
        stats.stopTimer.start();
        boolean stop;
        try {
          stop = stop(successor, reachedSet.getReached(successor), successorPrecision);
        } finally {
          stats.stopTimer.stop();
        }

        if (AbstractStates.isTargetState(successor) && stop) {
          // don't signal BREAK for covered states
          // no need to call merge and stop either, so just ignore this state
          // and handle next successor
          stats.countStop++;
          logger.log(Level.FINER, "Break was signalled but ignored because the state is covered.");
          continue;

        } else {
          stats.countBreak++;
          logger.log(Level.FINER, "Break signalled, CPAAlgorithm will stop.");

          // add the new state
          reachedSet.addToReachedSet(successor, successorPrecision);

          if (it.hasNext()) {
            // re-add the old state to the waitlist, there are unhandled
            // successors left that otherwise would be forgotten
            reachedSet.addToWaitlist(element);
          }

          return true;
        }
      }
      assert action == Action.CONTINUE : "Enum Action has unhandled values!";

      Collection<AbstractState> reached = reachedSet.getReached(successor);

      // An optimization, we don't bother merging if we know that the
      // merge operator won't do anything (i.e., it is merge-sep).
      if (mergeIsNotSep(successor) && !reached.isEmpty()) {
        stats.mergeTimer.start();
        try {
          List<AbstractState> toRemove = new ArrayList<>();
          List<Pair<AbstractState, Precision>> toAdd = new ArrayList<>();
          try {
            logger.log(
                Level.FINER, "Considering", reached.size(), "states from reached set for merge");
            for (AbstractState reachedState : reached) {
              shutdownNotifier.shutdownIfNecessary();
              AbstractState mergedState =
                  merge(successor, reachedState, successorPrecision);

              if (!mergedState.equals(reachedState)) {
                logger.log(Level.FINER, "Successor was merged with state from reached set");
                logger.log(
                    Level.ALL, "Merged", successor, "\nand", reachedState, "\n-->", mergedState);
                stats.countMerge++;

                toRemove.add(reachedState);
                toAdd.add(Pair.of(mergedState, successorPrecision));
              }
            }
          } finally {
            // If we terminate, we should still update the reachedSet if necessary
            // because ARGCPA doesn't like states in toRemove to be in the reachedSet.
            //TODO update timer
            if (!toRemove.isEmpty()) {
              update(reachedSet, toRemove, toAdd);
            }
          }

          if (mergeOperator instanceof ARGMergeJoinCPAEnabledAnalysis) {
            ((ARGMergeJoinCPAEnabledAnalysis) mergeOperator).cleanUp(reachedSet);
          }

        } finally {
          stats.mergeTimer.stop();
        }
      }
      reached = reachedSet.getReached(successor);
      stats.stopTimer.start();
      boolean stop;
      try {
        stop = stop(successor, reached, successorPrecision);
      } finally {
        stats.stopTimer.stop();
      }

      if (stop) {
        logger.log(Level.FINER, "Successor is covered or unreachable, not adding to waitlist");
        stats.countStop++;

      } else {
        logger.log(Level.FINER, "No need to stop, adding successor to waitlist");

        stats.addTimer.start();
        frontier(reachedSet, successor, successorPrecision);
        stats.addTimer.stop();
      }
    }

    return false;
  }

  protected abstract boolean stop(AbstractState pSuccessor,
      Collection<AbstractState> pReached, Precision pSuccessorPrecision) throws CPAException, InterruptedException;

  protected abstract AbstractState merge(AbstractState pSuccessor,
      AbstractState pReachedState, Precision pSuccessorPrecision) throws CPAException, InterruptedException;

  protected abstract void frontier(ReachedSet pReached, AbstractState pSuccessor,
      Precision pPrecision);

  protected abstract void update(ReachedSet pReachedSet, List<AbstractState> pToRemove,
      List<Pair<AbstractState, Precision>> pToAdd);

  protected abstract Collection<Pair<? extends AbstractState, ? extends Precision>> getAbstractSuccessors(
      WaitlistElement element, ReachedSet rset)
      throws CPATransferException, InterruptedException;

  protected abstract boolean mergeIsNotSep(AbstractState pState);

  @Override
  public void collectStatistics(
      Collection<Statistics> pStatsCollection) {
    if (forcedCovering instanceof StatisticsProvider) {
      ((StatisticsProvider) forcedCovering).collectStatistics(pStatsCollection);
    }
    pStatsCollection.add(stats);
  }
}