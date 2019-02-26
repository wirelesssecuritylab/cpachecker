/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2019  Dirk Beyer
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
 */
package org.sosy_lab.cpachecker.util;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.ConfigurationBuilder;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.ast.AExpression;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.FunctionReturnEdge;
import org.sosy_lab.cpachecker.core.CPABuilder;
import org.sosy_lab.cpachecker.core.Specification;
import org.sosy_lab.cpachecker.core.algorithm.CPAAlgorithm;
import org.sosy_lab.cpachecker.core.algorithm.bmc.candidateinvariants.CandidateInvariant;
import org.sosy_lab.cpachecker.core.algorithm.bmc.candidateinvariants.ExpressionTreeLocationInvariant;
import org.sosy_lab.cpachecker.core.algorithm.bmc.candidateinvariants.ExpressionTreeLocationInvariant.ManagerKey;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.StateSpacePartition;
import org.sosy_lab.cpachecker.core.reachedset.AggregatedReachedSets;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSetFactory;
import org.sosy_lab.cpachecker.cpa.automaton.AutomatonState;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.util.expressions.And;
import org.sosy_lab.cpachecker.util.expressions.ExpressionTree;
import org.sosy_lab.cpachecker.util.expressions.ExpressionTrees;
import org.sosy_lab.cpachecker.util.expressions.ToFormulaVisitor;

/** Utility class to extract candidates from witness file */
public class CandidatesFromWitness {

  private static Configuration generateLocalConfiguration(Configuration pConfig)
      throws InvalidConfigurationException {
    ConfigurationBuilder configBuilder =
        Configuration.builder()
            .loadFromResource(CandidatesFromWitness.class, "witness-analysis.properties");
    List<String> copyOptions =
        Arrays.asList(
            "analysis.machineModel",
            "analysis.programNames",
            "cpa.callstack.skipRecursion",
            "cpa.callstack.skipVoidRecursion",
            "cpa.callstack.skipFunctionPointerRecursion",
            "witness.strictChecking",
            "witness.checkProgramHash");
    for (String copyOption : copyOptions) {
      configBuilder.copyOptionFromIfPresent(pConfig, copyOption);
    }
    return configBuilder.build();
  }

  public static ReachedSet analyzeWitness(
      Configuration pConfig,
      Specification pSpecification,
      LogManager pLogger,
      CFA pCFA,
      final ShutdownNotifier shutdownNotifier,
      Path pathToInvariantsAutomatonFile)
      throws InvalidConfigurationException, CPAException {
    Configuration config = generateLocalConfiguration(pConfig);
    ReachedSetFactory reachedSetFactory = new ReachedSetFactory(config, pLogger);
    ReachedSet reachedSet = reachedSetFactory.create();
    CPABuilder builder = new CPABuilder(config, pLogger, shutdownNotifier, reachedSetFactory);
    Specification automatonAsSpec =
        Specification.fromFiles(
            pSpecification.getProperties(),
            ImmutableList.of(pathToInvariantsAutomatonFile),
            pCFA,
            config,
            pLogger);
    ConfigurableProgramAnalysis cpa =
        builder.buildCPAs(pCFA, automatonAsSpec, new AggregatedReachedSets());
    CPAAlgorithm algorithm = CPAAlgorithm.create(cpa, pLogger, config, shutdownNotifier);
    CFANode rootNode = pCFA.getMainFunction();
    StateSpacePartition partition = StateSpacePartition.getDefaultPartition();

    try {
      reachedSet.add(
          cpa.getInitialState(rootNode, partition), cpa.getInitialPrecision(rootNode, partition));
      algorithm.run(reachedSet);
    } catch (InterruptedException e) {
      // Candidate collection was interrupted,
      // but instead of throwing the exception here,
      // let it be thrown by the invariant generator.
    }
    return reachedSet;
  }

  public static void extractCandidatesFromReachedSet(
      final ShutdownNotifier pShutdownNotifier,
      final Set<CandidateInvariant> candidates,
      final Multimap<String, CFANode> candidateGroupLocations,
      ReachedSet reachedSet) {
    Set<ExpressionTreeLocationInvariant> expressionTreeLocationInvariants = Sets.newHashSet();
    Map<String, ExpressionTree<AExpression>> expressionTrees = Maps.newHashMap();
    Set<CFANode> visited = Sets.newHashSet();
    Multimap<CFANode, ExpressionTreeLocationInvariant> potentialAdditionalCandidates =
        HashMultimap.create();
    Map<ManagerKey, ToFormulaVisitor> toCodeVisitorCache = Maps.newConcurrentMap();
    for (AbstractState abstractState : reachedSet) {
      if (pShutdownNotifier.shouldShutdown()) {
        return;
      }
      Iterable<CFANode> locations = AbstractStates.extractLocations(abstractState);
      Iterables.addAll(visited, locations);
      for (AutomatonState automatonState :
          AbstractStates.asIterable(abstractState).filter(AutomatonState.class)) {
        ExpressionTree<AExpression> candidate = automatonState.getCandidateInvariants();
        String groupId = automatonState.getInternalStateName();
        candidateGroupLocations.putAll(groupId, locations);
        if (!candidate.equals(ExpressionTrees.getTrue())) {
          ExpressionTree<AExpression> previous = expressionTrees.get(groupId);
          if (previous == null) {
            previous = ExpressionTrees.getTrue();
          }
          expressionTrees.put(groupId, And.of(previous, candidate));
          for (CFANode location : locations) {
            potentialAdditionalCandidates.removeAll(location);
            ExpressionTreeLocationInvariant candidateInvariant =
                new ExpressionTreeLocationInvariant(
                    groupId, location, candidate, toCodeVisitorCache);
            expressionTreeLocationInvariants.add(candidateInvariant);
            // Check if there are any leaving return edges:
            // The predecessors are also potential matches for the invariant
            for (FunctionReturnEdge returnEdge :
                CFAUtils.leavingEdges(location).filter(FunctionReturnEdge.class)) {
              CFANode successor = returnEdge.getSuccessor();
              if (!candidateGroupLocations.containsEntry(groupId, successor)
                  && !visited.contains(successor)) {
                potentialAdditionalCandidates.put(
                    successor,
                    new ExpressionTreeLocationInvariant(
                        groupId, successor, candidate, toCodeVisitorCache));
              }
            }
          }
        }
      }
    }
    for (Map.Entry<CFANode, Collection<ExpressionTreeLocationInvariant>> potentialCandidates :
        potentialAdditionalCandidates.asMap().entrySet()) {
      if (!visited.contains(potentialCandidates.getKey())) {
        for (ExpressionTreeLocationInvariant candidateInvariant : potentialCandidates.getValue()) {
          candidateGroupLocations.put(
              candidateInvariant.getGroupId(), potentialCandidates.getKey());
          expressionTreeLocationInvariants.add(candidateInvariant);
        }
      }
    }
    for (ExpressionTreeLocationInvariant expressionTreeLocationInvariant :
        expressionTreeLocationInvariants) {
      for (CFANode location :
          candidateGroupLocations.get(expressionTreeLocationInvariant.getGroupId())) {
        candidates.add(
            new ExpressionTreeLocationInvariant(
                expressionTreeLocationInvariant.getGroupId(),
                location,
                expressionTrees.get(expressionTreeLocationInvariant.getGroupId()),
                toCodeVisitorCache));
      }
    }
  }
}