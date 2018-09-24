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
package org.sosy_lab.cpachecker.cpa.hybrid.value;

import org.sosy_lab.cpachecker.cfa.types.c.CType;
import org.sosy_lab.cpachecker.cpa.value.type.NumericValue;
import org.sosy_lab.cpachecker.cpa.value.type.Value;
import org.sosy_lab.cpachecker.cpa.value.type.ValueVisitor;

/** This class wraps a Value with some additional funtionality */
public abstract class HybridValue implements Value {

    private static final long serialVersionUID = 1L;

    protected final Value value;

    public HybridValue(Value value)
    {
        this.value = value;
    }

    @Override
    public boolean isNumericValue() {
        return value.isNumericValue();
    }

    @Override
    public boolean isUnknown() {
        return value.isUnknown();
    }

    @Override
    public boolean isExplicitlyKnown() {
        return value.isExplicitlyKnown();
    }

    @Override
    public NumericValue asNumericValue() {
        return value.asNumericValue();
    }

    @Override
    public Long asLong(CType pType) {
        return value.asLong(pType);
    }

    @Override
    public <T> T accept(ValueVisitor<T> pVisitor) {
        return pVisitor.visit(this);
    }

    public Value getInnerValue()
    {
        return value;
    }

}