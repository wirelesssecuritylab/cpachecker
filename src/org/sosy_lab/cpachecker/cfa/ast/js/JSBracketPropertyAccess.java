/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2018  Dirk Beyer
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
package org.sosy_lab.cpachecker.cfa.ast.js;

import java.util.Objects;
import org.sosy_lab.cpachecker.cfa.ast.AbstractLeftHandSide;
import org.sosy_lab.cpachecker.cfa.ast.FileLocation;
import org.sosy_lab.cpachecker.cfa.types.js.JSAnyType;
import org.sosy_lab.cpachecker.cfa.types.js.JSType;

/**
 * Access of an object property using bracket notation: <code>
 * objectExpression[propertyNameExpression]</code>
 *
 * @see <a href="https://www.ecma-international.org/ecma-262/5.1/#sec-11.2.1">Property Accessors</a>
 */
public class JSBracketPropertyAccess extends AbstractLeftHandSide implements JSLeftHandSide {

  private static final long serialVersionUID = -8454075385785972330L;
  private final JSExpression objectExpression;
  private final JSExpression propertyNameExpression;

  public JSBracketPropertyAccess(
      final FileLocation pFileLocation,
      final JSExpression pObjectExpression,
      final JSExpression pPropertyNameExpression) {
    super(pFileLocation, JSAnyType.ANY);
    objectExpression = pObjectExpression;
    propertyNameExpression = pPropertyNameExpression;
  }

  public JSExpression getObjectExpression() {
    return objectExpression;
  }

  public JSExpression getPropertyNameExpression() {
    return propertyNameExpression;
  }

  @Override
  public <R, X extends Exception> R accept(final JSLeftHandSideVisitor<R, X> v) throws X {
    return v.visit(this);
  }

  @Override
  public JSType getExpressionType() {
    return (JSType) super.getExpressionType();
  }

  @Override
  public String toASTString(final boolean pQualified) {
    return objectExpression.toASTString(pQualified) + "[" + propertyNameExpression + "]";
  }

  @Override
  public boolean equals(final Object other) {
    if (this == other) {
      return true;
    }
    if (other == null || getClass() != other.getClass()) {
      return false;
    }
    if (!super.equals(other)) {
      return false;
    }
    final JSBracketPropertyAccess that = (JSBracketPropertyAccess) other;
    return Objects.equals(objectExpression, that.objectExpression)
        && Objects.equals(propertyNameExpression, that.propertyNameExpression);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), objectExpression, propertyNameExpression);
  }
}