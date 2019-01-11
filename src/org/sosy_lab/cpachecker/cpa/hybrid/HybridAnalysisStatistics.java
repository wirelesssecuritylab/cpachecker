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

import java.io.IOException;
import java.io.PrintStream;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Set;
import java.util.logging.Level;

import javax.annotation.Nullable;

import com.google.common.collect.Sets;

import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.io.IO;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.FileOption;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.statistics.StatCounter;
import org.sosy_lab.cpachecker.util.statistics.StatisticsWriter;

@Options(prefix = "cpa.hybrid.statistics")
public class HybridAnalysisStatistics implements Statistics {

  @Option(secure = true,
          name = "testCaseFile",
          description = "The test-case file to export the testcases to")
  @FileOption(FileOption.Type.OUTPUT_FILE)
  private Path testCaseFile = null;

  private StatCounter generatedForNondet;

  private StatCounter unableGeneration;

  private StatCounter assumptionFound;

  private StatCounter feasibleAssumptionFound;

  private StatCounter solverGenerated;

  private StatCounter removedOnAssumption;

  private StatCounter removedOnAssignment;

  private StatCounter transferWithoutValueGeneration;

  private final LogManager logger;

  public HybridAnalysisStatistics(
      Configuration pConfig,
      LogManager pLogger) 
        throws InvalidConfigurationException {
    pConfig.inject(this);
    logger = pLogger;
    generatedForNondet =           
        new StatCounter("Values generated for non-deterministic function assignemnt calls");
    unableGeneration =             
        new StatCounter("Unhandled non-deterministic function call assignments.          ");
    assumptionFound =              
        new StatCounter("Examined uncovered paths overall                                ");
    feasibleAssumptionFound =      
        new StatCounter("Feasible uncovered paths                                        ");
    solverGenerated =               
        new StatCounter("Values generated by the solver environment                      ");
    removedOnAssumption =            
        new StatCounter("Tracked values removed on assumption transition                 ");
    removedOnAssignment =            
        new StatCounter("Tracked values removed on concrete variable assignments         ");
    transferWithoutValueGeneration = 
        new StatCounter("Program transitions without value generation                    ");
  }

  @Override
  public void printStatistics(PrintStream pOut, Result pResult, UnmodifiableReachedSet pReached) {

    if(testCaseFile != null) {

        final String testCases = createTestCases(pReached);

        try (Writer writer = IO.openOutputFile(testCaseFile, Charset.defaultCharset())) {
            writer.write(testCases);
        } catch (IOException e) {
        logger.logUserException(Level.WARNING, e, "Could not write value-analysis precision to file");
        }
    }

    StatisticsWriter writer = StatisticsWriter.writingStatisticsTo(pOut);
    writer.put(generatedForNondet)
        .put(assumptionFound)
        .put(feasibleAssumptionFound)
        .put(unableGeneration)
        .put(solverGenerated)
        .put(removedOnAssumption)
        .put(removedOnAssignment)
        .put(transferWithoutValueGeneration)
        .put("Overall examined branches to new explored branches ratio", 
             "               " + uncoveredToFeasiblePathsRatio() + "%");
  }

  @Override
  @Nullable
  public String getName() {
    return HybridAnalysisState.class.getSimpleName();
  }

  public void incrementGeneratedNondet() {
      generatedForNondet.inc();
  }

  public void incrementUnableGeneration() {
    unableGeneration.inc();
  }

  public void incrementAssumptionFound() {
    assumptionFound.inc();
  }

  public void incrementFeasiblePathFound() {
    feasibleAssumptionFound.inc();
  }

  public void incrementSolverGenerated() {
      solverGenerated.inc();
  }

  public void incrementRemovedOnAssumption() {
      removedOnAssumption.inc();
  }

  public void incrementRemovedOnAssignment() {
      removedOnAssignment.inc();
  }

  public void incrementEmptyTransfer() {
      transferWithoutValueGeneration.inc();
  }

  private String createTestCases(UnmodifiableReachedSet pReachedSet) {
    StringBuilder builder = new StringBuilder();
    Set<String> caseCache = Sets.newHashSet();

    for(AbstractState state : pReachedSet) {
        HybridAnalysisState hybridAnalysisState = AbstractStates.extractStateByType(state, HybridAnalysisState.class);
        final String rep = hybridAnalysisState.toString();
        if(rep.equals("") || caseCache.contains(rep)) continue;

        builder
            .append(hybridAnalysisState)
            .append(System.lineSeparator()).append(System.lineSeparator())
            .append("-----------------------------")
            .append(System.lineSeparator()).append(System.lineSeparator());

        caseCache.add(hybridAnalysisState.toString());
    }

    return builder.toString();
  }

  private double uncoveredToFeasiblePathsRatio() {
      return Math.round((feasibleAssumptionFound.getValue() * 100) / assumptionFound.getValue());
  }

}