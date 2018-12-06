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
package org.sosy_lab.cpachecker.cpa.hybrid.visitor;

import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.sosy_lab.cpachecker.cfa.types.c.CArrayType;
import org.sosy_lab.cpachecker.cfa.types.c.CBitFieldType;
import org.sosy_lab.cpachecker.cfa.types.c.CCompositeType;
import org.sosy_lab.cpachecker.cfa.types.c.CNumericTypes;
import org.sosy_lab.cpachecker.cfa.types.c.CPointerType;
import org.sosy_lab.cpachecker.cfa.types.c.CSimpleType;
import org.sosy_lab.cpachecker.cfa.types.c.CTypedefType;
import org.sosy_lab.cpachecker.cpa.hybrid.abstraction.HybridValueProvider;
import org.sosy_lab.cpachecker.cpa.hybrid.value.StringValue;
import org.sosy_lab.cpachecker.cpa.value.type.BooleanValue;
import org.sosy_lab.cpachecker.cpa.value.type.NumericValue;
import org.sosy_lab.cpachecker.cpa.value.type.Value;

/**
 * This class provides a random strategy for generating actual values
 * for simple c types
 * 
 * Mind: no support of structs, arrays ...
 */
public class SimpleValueProvider extends HybridValueProvider{

  private final Random random;
  private final int stringMaxLength;
  private final int charNumberUpperBound = 255;

  /**
   * constructs a new instance of this class
   */
  public SimpleValueProvider(int pStringMaxLength) {
    random = new Random();
    stringMaxLength = pStringMaxLength;
  }

  /**
   * Constructs a new instance of this class
   * @param seed The seed to instantiate the random number generator with
   */
  public SimpleValueProvider(int pStringMaxLength, long seed) {
    random = new Random(seed);
    stringMaxLength = pStringMaxLength;
  }


  @Override
  public Value visit(CSimpleType type) {

    switch(type.getType()) {
      case CHAR:
      case INT: return new NumericValue(random.nextInt());
      case FLOAT: return new NumericValue(random.nextFloat());
      case DOUBLE: return new NumericValue(random.nextDouble());
      case BOOL: return BooleanValue.valueOf(random.nextBoolean());
      default: return null;
    }
  }

  @Override
  public Value visit(CPointerType type) {

    // for now, simply handle char pointer
    if(!type.getCanonicalType().equals(CNumericTypes.CHAR)) {
      return null;
    }

    return new StringValue(nextRandomString());
  }

  @Override
  public Value visit(CArrayType type) {

    // evaluate the length
    return null;
  }

  @Override
  public Value visit(CBitFieldType type) {
    return null;
  }

  @Override
  public Value visit(CCompositeType type) {
    return null;
  }

  @Override
  public Value visit(CTypedefType type) {
    return null;
  }

  // creates a random string of random length
  private String nextRandomString() {

    // denoting the length of the string
    final int length = random.nextInt(stringMaxLength);

    final String builder =
        IntStream
            .range(0, length)
            .mapToObj(i -> String.valueOf((char) random.nextInt(charNumberUpperBound)))
            .collect(Collectors.joining());

    return builder;
  }
}