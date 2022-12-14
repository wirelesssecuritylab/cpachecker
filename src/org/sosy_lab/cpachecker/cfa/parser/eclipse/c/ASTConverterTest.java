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
package org.sosy_lab.cpachecker.cfa.parser.eclipse.c;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import org.junit.Ignore;
import org.junit.Test;
import org.sosy_lab.cpachecker.cfa.ast.FileLocation;
import org.sosy_lab.cpachecker.cfa.ast.c.CFloatLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CIntegerLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CLiteralExpression;
import org.sosy_lab.cpachecker.cfa.types.MachineModel;
import org.sosy_lab.cpachecker.cfa.types.c.CNumericTypes;
import org.sosy_lab.cpachecker.cfa.types.c.CType;
import org.sosy_lab.cpachecker.util.Triple;

public class ASTConverterTest {

  private final ASTLiteralConverter converter32 =
      new ASTLiteralConverter(MachineModel.LINUX32, ParseContext.dummy());
  private final ASTLiteralConverter converter64 =
      new ASTLiteralConverter(MachineModel.LINUX64, ParseContext.dummy());

  @Test
  public final void testCharacterExpression() {
    assertEquals('\000',   converter32.parseCharacterLiteral("'\\000'", null));
    assertEquals('\077',   converter32.parseCharacterLiteral("'\\077'", null));
    assertEquals('\u00FF', converter32.parseCharacterLiteral("'\\xFF'", null));
    assertEquals('\u00BC', converter32.parseCharacterLiteral("'\\xBC'", null));
    assertEquals('\u0080', converter32.parseCharacterLiteral("'\\x80'", null));
    assertEquals('\u007F', converter32.parseCharacterLiteral("'\\X7F'", null));
    assertEquals('\\', converter32.parseCharacterLiteral("'\\\\'", null));
    assertEquals('\'', converter32.parseCharacterLiteral("'\\''", null));
    assertEquals('"',  converter32.parseCharacterLiteral("'\\\"'", null));
    assertEquals('\0', converter32.parseCharacterLiteral("'\\0'", null));
    assertEquals(7,    converter32.parseCharacterLiteral("'\\a'", null));
    assertEquals('\b', converter32.parseCharacterLiteral("'\\b'", null));
    assertEquals('\f', converter32.parseCharacterLiteral("'\\f'", null));
    assertEquals('\n', converter32.parseCharacterLiteral("'\\n'", null));
    assertEquals('\r', converter32.parseCharacterLiteral("'\\r'", null));
    assertEquals('\t', converter32.parseCharacterLiteral("'\\t'", null));
    assertEquals(11,   converter32.parseCharacterLiteral("'\\v'", null));
    assertEquals('a', converter32.parseCharacterLiteral("'a'", null));
    assertEquals(' ', converter32.parseCharacterLiteral("' '", null));
    assertEquals('9', converter32.parseCharacterLiteral("'9'", null));
    assertEquals('??', converter32.parseCharacterLiteral("'??'", null));
    assertEquals('??', converter32.parseCharacterLiteral("'??'", null));
  }

  @Test(expected=CFAGenerationRuntimeException.class)
  public final void testInvalidCharacterExpression1() {
    converter32.parseCharacterLiteral("", null);
  }

  @Test(expected=CFAGenerationRuntimeException.class)
  public final void testInvalidCharacterExpression2() {
    converter32.parseCharacterLiteral("'\\'", null);
  }

  @Test(expected=CFAGenerationRuntimeException.class)
  public final void testInvalidCharacterExpression3() {
    converter32.parseCharacterLiteral("'aa'", null);
  }

  @Test(expected=CFAGenerationRuntimeException.class)
  public final void testInvalidCharacterExpression4() {
    converter32.parseCharacterLiteral("'\\777'", null);
  }

  @Test(expected=CFAGenerationRuntimeException.class)
  public final void testInvalidCharacterExpression5() {
    converter32.parseCharacterLiteral("'\\xFFF'", null);
  }

  @Test(expected=CFAGenerationRuntimeException.class)
  public final void testInvalidCharacterExpression6() {
    converter32.parseCharacterLiteral("'\\z'", null);
  }

  @Test(expected=CFAGenerationRuntimeException.class)
  public final void testInvalidCharacterExpression7() {
    converter32.parseCharacterLiteral("'\\0777'", null);
  }

  @Test(expected=CFAGenerationRuntimeException.class)
  public final void testInvalidCharacterExpression8() {
    converter32.parseCharacterLiteral("'\\088'", null);
  }

  @Test(expected=CFAGenerationRuntimeException.class)
  public final void testInvalidCharacterExpression9() {
    converter32.parseCharacterLiteral("'\\xGG'", null);
  }

  @Test
  public final void testInvalidIntegerExpressions() {
    ImmutableList<ASTLiteralConverter> converters = ImmutableList.of(converter32, converter64);
    ImmutableList<String> invalidValues =
        ImmutableList.<String>of(
            "18446744073709551617u",
            "36893488147419103232",
            "36893488147419103232u",
            "100020003000400050006000700080009000u");

    for (ASTLiteralConverter c : converters) {
      for (String s : invalidValues) {
        try {
          c.parseIntegerLiteral(FileLocation.DUMMY, s, null);
          fail();
        } catch (CFAGenerationRuntimeException e) {
          assertThat(e.getMessage())
              .contains(
                  "Integer value is too large to be represented by the highest possible type (unsigned long long int):");
        }
      }
    }
  }

  private String parseIntegerExpression32(String s) {
    return parseIntegerExpression(s, converter32);
  }

  private String parseIntegerExpression64(String s) {
    return parseIntegerExpression(s, converter64);
  }

  private String parseIntegerExpression(String pExpression, ASTLiteralConverter pConverter) {
    CLiteralExpression exp = pConverter.parseIntegerLiteral(FileLocation.DUMMY, pExpression, null);
    return ((CIntegerLiteralExpression) exp).getValue().toString();
  }

  @Test
  public final void testIntegerExpression() {
    check("0","0");
    check("1", "1");
    check("2", "2");
    check("3", "3u");
    check("4", "4");
    check("5", "5u");
    check("63", "077");

    check("2147483647", "2147483647");
    check("2147483647", "0x7FFFFFFF");
    check("2147483648", "2147483648");
    check("2147483648", "0x80000000");
    check("4294967295", "4294967295");
    check("4294967295", "0xFFFFFFFF");
    check("4294967296", "4294967296");
    check("4000000000", "4000000000");
    check("4000000000", "4000000000u");

    check("18446744073709551600", "0xfffffffffffffff0u");
    check("18446744073709551600", "0xfffffffffffffff0");

    check("9223372036854775807", "9223372036854775807");
    check("9223372036854775807", "0x7FFFFFFFFFFFFFFF");
    check("9223372036854775808", "9223372036854775808");
    check("9223372036854775808", "0x8000000000000000");
    check("9223372036854775808", "9223372036854775808u");
    check("9223372036854775808", "0x8000000000000000u");
    check("18446744073709551615", "18446744073709551615u");
    check("18446744073709551615", "0xFFFFFFFFFFFFFFFFu");
    check("18446711088360718336", "0xffffe20000000000U");
    check("18446604435732824064", "0xffff810000000000U");

    check("18446744073709551615", "0xFFFFFFFFFFFFFFFF");
  }

  private void check(String expected, String input) {
    // constant integers are always extended to the smallest matching type.
    // thus the result is always identical for L and LL
    for (String postfix : ImmutableList.of("", "l", "ll", "L", "LL", "lL", "Ll")) {
      assertEquals(expected, parseIntegerExpression32(input + postfix));
      assertEquals(expected, parseIntegerExpression64(input + postfix));
    }
  }

  @Test
  public final void testValidFloatExpressions() {
    ImmutableList<ASTLiteralConverter> converters = ImmutableList.of(converter32, converter64);

    ImmutableList<Triple<String, String, CType>> input_output =
        ImmutableList.of(
            // Triples consist of: input value, expected output, input type for CLiteralExpression
            Triple.of("0", "0.0", CNumericTypes.DOUBLE),
            Triple.of("-0", "0.0", CNumericTypes.DOUBLE),
            Triple.of("0xf", "15.0", CNumericTypes.DOUBLE),
            Triple.of("5e2f", "500.0", CNumericTypes.FLOAT),
            Triple.of("5e+2f", "500.0", CNumericTypes.FLOAT),
            Triple.of("0x5e2f", "24111.0", CNumericTypes.FLOAT),
            Triple.of("0x5e-2f", "94.0", CNumericTypes.FLOAT),
            Triple.of(
                "3.41E+38", "341000000000000000445911848520865808384.0", CNumericTypes.DOUBLE));

    for (ASTLiteralConverter converter : converters) {
      for (Triple<String, String, CType> triple : input_output) {
        String inputValue = triple.getFirst();
        String expectedValue = triple.getSecond();
        CType inputType = triple.getThird();

        CFloatLiteralExpression literal =
            (CFloatLiteralExpression)
                converter.parseFloatLiteral(FileLocation.DUMMY, inputType, inputValue, null);

        assertEquals(expectedValue, literal.getValue().toString());
        assertTrue(inputType == literal.getExpressionType());
      }
    }
  }

  // Enable this test once BigDecimals are replaced by CFloats in CFloatLiteralExpression-class
  // (and subsequently, when the ASTLiteralConverter#adjustPrecision() got removed)
  @Ignore
  public final void testInvalidFloatExpressions() {
    ImmutableList<ASTLiteralConverter> converters = ImmutableList.of(converter32, converter64);

    ImmutableList<String> values =
        ImmutableList.of(
            "3.41e+38f", "-4.2e+38f", "1.8e+308", "-2.3e+308", "1.2e+4932l", "-1.2e+4932l");

    for (ASTLiteralConverter converter : converters) {
      for (String value : values) {
        try {
          converter.parseFloatLiteral(FileLocation.DUMMY, null, value, null);
          fail("Failed because of value: " + value);
        } catch (CFAGenerationRuntimeException e) {
          assertThat(e.getMessage())
              .isAnyOf(
                  "unable to parse floating point literal (inf)",
                  "unable to parse floating point literal (-inf)");
        }
      }
    }
  }
}
