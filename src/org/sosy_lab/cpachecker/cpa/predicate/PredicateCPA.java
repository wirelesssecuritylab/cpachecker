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
package org.sosy_lab.cpachecker.cpa.predicate;

import com.google.common.collect.ImmutableSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.collect.PathCopyingPersistentTreeMap;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.AnalysisDirection;
import org.sosy_lab.cpachecker.core.Specification;
import org.sosy_lab.cpachecker.core.algorithm.invariants.InvariantSupplier.TrivialInvariantSupplier;
import org.sosy_lab.cpachecker.core.defaults.AutomaticCPAFactory;
import org.sosy_lab.cpachecker.core.defaults.MergeSepOperator;
import org.sosy_lab.cpachecker.core.interfaces.AbstractDomain;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.CPAFactory;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.MergeOperator;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustment;
import org.sosy_lab.cpachecker.core.interfaces.StateSpacePartition;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.interfaces.StatisticsProvider;
import org.sosy_lab.cpachecker.core.interfaces.StopOperator;
import org.sosy_lab.cpachecker.core.interfaces.pcc.ProofChecker;
import org.sosy_lab.cpachecker.core.reachedset.AggregatedReachedSets;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.util.blocking.BlockedCFAReducer;
import org.sosy_lab.cpachecker.util.blocking.interfaces.BlockComputer;
import org.sosy_lab.cpachecker.util.predicates.AbstractionManager;
import org.sosy_lab.cpachecker.util.predicates.BlockOperator;
import org.sosy_lab.cpachecker.util.predicates.bdd.BDDManagerFactory;
import org.sosy_lab.cpachecker.util.predicates.pathformula.CachingPathFormulaManager;
import org.sosy_lab.cpachecker.util.predicates.pathformula.PathFormula;
import org.sosy_lab.cpachecker.util.predicates.pathformula.PathFormulaManager;
import org.sosy_lab.cpachecker.util.predicates.pathformula.PathFormulaManagerImpl;
import org.sosy_lab.cpachecker.util.predicates.regions.RegionManager;
import org.sosy_lab.cpachecker.util.predicates.regions.SymbolicRegionManager;
import org.sosy_lab.cpachecker.util.predicates.smt.FormulaManagerView;
import org.sosy_lab.cpachecker.util.predicates.smt.Solver;
import org.sosy_lab.java_smt.api.SolverException;

/**
 * CPA that defines symbolic predicate abstraction.
 */
@Options(prefix = "cpa.predicate")
public class PredicateCPA
    implements ConfigurableProgramAnalysis, StatisticsProvider, ProofChecker, AutoCloseable {

  public static CPAFactory factory() {
    return AutomaticCPAFactory.forType(PredicateCPA.class).withOptions(BlockOperator.class);
  }

  @Option(secure=true, name="abstraction.type", toUppercase=true, values={"BDD", "FORMULA"},
      description="What to use for storing abstractions")
  private String abstractionType = "BDD";

  @Option(secure=true, name="blk.useCache", description="use caching of path formulas")
  private boolean useCache = true;

  @Option(secure=true, name="enableBlockreducer", description="Enable the possibility to precompute explicit abstraction locations.")
  private boolean enableBlockreducer = false;

  @Option(secure=true, name="merge", values={"SEP", "ABE"}, toUppercase=true,
      description="which merge operator to use for predicate cpa (usually ABE should be used)")
  private String mergeType = "ABE";

  @Option(secure=true, name="stop", values={"SEP", "SEPPCC"}, toUppercase=true,
      description="which stop operator to use for predicate cpa (usually SEP should be used in analysis)")
  private String stopType = "SEP";

  @Option(secure=true, description="Direction of the analysis?")
  private AnalysisDirection direction = AnalysisDirection.FORWARD;

  @Option(
      secure = true,
      description =
          "whether to include the symbolic path formula in the "
              + "coverage checks or do only the fast abstract checks")
  private boolean symbolicCoverageCheck = false;

  protected final Configuration config;
  protected final LogManager logger;
  protected final ShutdownNotifier shutdownNotifier;

  private final PredicatePrecision initialPrecision;
  private final PathFormulaManager pathFormulaManager;
  private final Solver solver;
  private final PredicateAbstractionManager predicateManager;
  private final PredicateCPAStatistics stats;
  private final PredicatePrecisionBootstrapper precisionBootstraper;
  private final CFA cfa;
  private final AbstractionManager abstractionManager;
  private final PredicateCPAInvariantsManager invariantsManager;
  private final BlockOperator blk;
  private final PredicateStatistics statistics;
  private final PredicateProvider predicateProvider;
  private final FormulaManagerView formulaManager;
  private final PredicateCpaOptions options;

  // path formulas for PCC
  private final Map<PredicateAbstractState, PathFormula> computedPathFormulaePcc = new HashMap<>();

  protected PredicateCPA(
      Configuration config,
      LogManager logger,
      BlockOperator pBlk,
      CFA pCfa,
      ShutdownNotifier pShutdownNotifier,
      Specification specification,
      AggregatedReachedSets pAggregatedReachedSets)
      throws InvalidConfigurationException, CPAException {
    config.inject(this, PredicateCPA.class);

    this.config = config;
    this.logger = logger;
    this.shutdownNotifier = pShutdownNotifier;

    cfa = pCfa;
    blk = pBlk;

    if (enableBlockreducer) {
      BlockComputer blockComputer = new BlockedCFAReducer(config, logger);
      blk.setExplicitAbstractionNodes(blockComputer.computeAbstractionNodes(cfa));
    }
    blk.setCFA(cfa);

    solver = Solver.create(config, logger, pShutdownNotifier);
    formulaManager = solver.getFormulaManager();
    String libraries = solver.getVersion();

    PathFormulaManager pfMgr = new PathFormulaManagerImpl(formulaManager, config, logger, shutdownNotifier, cfa, direction);
    if (useCache) {
      pfMgr = new CachingPathFormulaManager(pfMgr);
    }
    pathFormulaManager = pfMgr;

    RegionManager regionManager;
    if (abstractionType.equals("FORMULA") || blk.alwaysReturnsFalse()) {
      // No need to load BDD library if we never abstract (might use lots of memory)
      regionManager = new SymbolicRegionManager(solver);
    } else {
      assert abstractionType.equals("BDD");
      regionManager = new BDDManagerFactory(config, logger).createRegionManager();
      libraries += " and " + regionManager.getVersion();
    }
    logger.log(Level.INFO, "Using predicate analysis with", libraries + ".");

    abstractionManager = new AbstractionManager(regionManager, config, logger, solver);

    invariantsManager =
        new PredicateCPAInvariantsManager(
            config, logger, pShutdownNotifier, pCfa, specification, pAggregatedReachedSets);

    predicateManager =
        new PredicateAbstractionManager(
            abstractionManager,
            pathFormulaManager,
            solver,
            config,
            logger,
            pShutdownNotifier,
            invariantsManager.appendToAbstractionFormula()
                ? invariantsManager
                : TrivialInvariantSupplier.INSTANCE);

    statistics = new PredicateStatistics();
    options = new PredicateCpaOptions(config);
    precisionBootstraper = new PredicatePrecisionBootstrapper(config, logger, cfa, abstractionManager, formulaManager);
    initialPrecision = precisionBootstraper.prepareInitialPredicates();
    logger.log(Level.FINEST, "Initial precision is", initialPrecision);

    predicateProvider =
        new PredicateProvider(config, pCfa, logger, formulaManager, predicateManager);

    stats =
        new PredicateCPAStatistics(
            config,
            logger,
            pCfa,
            solver,
            pathFormulaManager,
            blk,
            regionManager,
            abstractionManager,
            predicateManager,
            statistics);
  }

  @Override
  public AbstractDomain getAbstractDomain() {
    return new PredicateAbstractDomain(predicateManager, symbolicCoverageCheck, statistics);
  }

  @Override
  public PredicateTransferRelation getTransferRelation() {
    return new PredicateTransferRelation(
        logger,
        direction,
        formulaManager,
        pathFormulaManager,
        blk,
        predicateManager,
        statistics,
        options);
  }

  @Override
  public MergeOperator getMergeOperator() {
    switch (mergeType) {
      case "SEP":
        return MergeSepOperator.getInstance();
      case "ABE":
        return new PredicateMergeOperator(logger, pathFormulaManager, statistics);
      default:
        throw new InternalError("Update list of allowed merge operators");
    }
  }

  @Override
  public StopOperator getStopOperator() {
    switch (stopType) {
      case "SEP":
        return new PredicateStopOperator(getAbstractDomain());
      case "SEPPCC":
        return new PredicatePCCStopOperator(pathFormulaManager, predicateManager);
      default:
        throw new InternalError("Update list of allowed stop operators");
    }
  }

  public PredicateAbstractionManager getPredicateManager() {
    return predicateManager;
  }

  public PathFormulaManager getPathFormulaManager() {
    return pathFormulaManager;
  }

  public Solver getSolver() {
    return solver;
  }

  public Configuration getConfiguration() {
    return config;
  }

  LogManager getLogger() {
    return logger;
  }

  public ShutdownNotifier getShutdownNotifier() {
    return shutdownNotifier;
  }

  @Override
  public AbstractState getInitialState(CFANode node, StateSpacePartition pPartition) {
    return PredicateAbstractState.mkAbstractionState(
        pathFormulaManager.makeEmptyPathFormula(),
        predicateManager.makeTrueAbstractionFormula(null),
        PathCopyingPersistentTreeMap.of());
  }

  @Override
  public Precision getInitialPrecision(CFANode pNode, StateSpacePartition pPartition) {
    return initialPrecision;
  }

  @Override
  public PrecisionAdjustment getPrecisionAdjustment() {
    return new PredicatePrecisionAdjustment(
        logger,
        formulaManager,
        pathFormulaManager,
        blk,
        predicateManager,
        invariantsManager,
        predicateProvider,
        statistics);
  }

  @Override
  public void collectStatistics(Collection<Statistics> pStatsCollection) {
    pStatsCollection.add(stats);
    precisionBootstraper.collectStatistics(pStatsCollection);
    invariantsManager.collectStatistics(pStatsCollection);
  }

  @Override
  public void close() {
    solver.close();
  }

  @Override
  public boolean areAbstractSuccessors(AbstractState pElement, CFAEdge pCfaEdge, Collection<? extends AbstractState> pSuccessors) throws CPATransferException, InterruptedException {
    try {
      return getTransferRelation()
          .areAbstractSuccessors(pElement, pCfaEdge, pSuccessors, computedPathFormulaePcc);
    } catch (SolverException e) {
      throw new CPATransferException("Solver failed during abstract-successor check", e);
    }
  }

  @Override
  public boolean isCoveredBy(AbstractState pElement, AbstractState pOtherElement) throws CPAException, InterruptedException {
    // isLessOrEqual for proof checking; formula based; elements can be trusted (i.e., invariants do not have to be checked)

    PredicateAbstractState e1 = (PredicateAbstractState) pElement;
    PredicateAbstractState e2 = (PredicateAbstractState) pOtherElement;

    if (e1.isAbstractionState() && e2.isAbstractionState()) {
      try {
        return predicateManager.checkCoverage(
            e1.getAbstractionFormula(),
            pathFormulaManager.makeEmptyPathFormula(e1.getPathFormula()),
            e2.getAbstractionFormula()
        );
      } catch (SolverException e) {
        throw new CPAException("Solver Failure", e);
      }
    } else {
      return false;
    }
  }

  public CFA getCfa() {
    return cfa;
  }

  public AbstractionManager getAbstractionManager() {
    return abstractionManager;
  }

  public PredicateCPAInvariantsManager getInvariantsManager() {
    return invariantsManager;
  }

  public void changeExplicitAbstractionNodes(final ImmutableSet<CFANode> explicitlyAbstractAt) {
    blk.setExplicitAbstractionNodes(explicitlyAbstractAt);
  }
}
