package io.substrait.isthmus;

import static org.apache.calcite.sql.fun.SqlStdOperatorTable.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.substrait.expression.Expression;
import io.substrait.expression.ExpressionCreator;
import io.substrait.function.ImmutableSimpleExtension;
import io.substrait.function.SimpleExtension;
import io.substrait.isthmus.expression.ExpressionRexConverter;
import io.substrait.isthmus.expression.RexExpressionConverter;
import io.substrait.isthmus.expression.ScalarFunctionConverter;
import io.substrait.type.Type;
import java.io.IOException;
import java.util.function.Consumer;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.type.SqlTypeName;
import org.junit.jupiter.api.Test;

public class CalciteCallTest extends CalciteObjs {
  static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(CalciteCallTest.class);

  private static final SimpleExtension.ExtensionCollection EXTENSION_COLLECTION;
  private final ScalarFunctionConverter functionConverter =
      new ScalarFunctionConverter(EXTENSION_COLLECTION.scalarFunctions(), type);
  private final RexExpressionConverter rexExpressionConverter =
      new RexExpressionConverter(functionConverter);

  private final ExpressionRexConverter expressionRexConverter =
      new ExpressionRexConverter(type, functionConverter, null);

  static {
    SimpleExtension.ExtensionCollection defaults =
        ImmutableSimpleExtension.ExtensionCollection.builder().build();
    try {
      defaults = SimpleExtension.loadDefaults();
    } catch (IOException e) {
      throw new RuntimeException("Failure while loading defaults.", e);
    }

    EXTENSION_COLLECTION = defaults;
  }

  @Test
  public void coerceNumericOp() {
    test(
        "add:opt_i64_i64",
        rex.makeCall(PLUS, c(20, SqlTypeName.INTEGER), c(4, SqlTypeName.BIGINT)),
        func -> {
          // check that there is a cast for the incorrect argument type.
          assertEquals(
              ExpressionCreator.cast(Type.REQUIRED.I64, ExpressionCreator.i32(false, 20)),
              func.arguments().get(0));
        },
        false); // TODO: implicit calcite cast
  }

  @Test
  public void directMatchPlus() {
    test(
        "add:opt_i64_i64",
        rex.makeCall(PLUS, c(4, SqlTypeName.BIGINT), c(4, SqlTypeName.BIGINT)),
        func -> {

          // ensure both literals are included directly.
          assertTrue(func.arguments().get(0) instanceof Expression.I64Literal);
          assertTrue(func.arguments().get(1) instanceof Expression.I64Literal);
        },
        true);
  }

  @Test
  public void directMatchAnd() {
    test("and:bool", rex.makeCall(AND, c(true, SqlTypeName.BOOLEAN), c(true, SqlTypeName.BOOLEAN)));
  }

  @Test
  public void directMatchOr() {
    test("or:bool", rex.makeCall(OR, c(false, SqlTypeName.BOOLEAN), c(true, SqlTypeName.BOOLEAN)));
  }

  @Test
  public void not() {
    test("not:bool", rex.makeCall(NOT, c(false, SqlTypeName.BOOLEAN)));
  }

  private void test(String expectedName, RexNode call) {
    test(expectedName, call, c -> {}, true);
  }

  private void test(
      String expectedName,
      RexNode call,
      Consumer<Expression.ScalarFunctionInvocation> consumer,
      boolean bidirectional) {
    var expression = call.accept(rexExpressionConverter);
    assertTrue(expression instanceof Expression.ScalarFunctionInvocation);
    Expression.ScalarFunctionInvocation func = (Expression.ScalarFunctionInvocation) expression;
    assertEquals(expectedName, func.declaration().key());
    consumer.accept(func);

    if (bidirectional) {
      RexNode convertedCall = expression.accept(expressionRexConverter);
      assertEquals(call, convertedCall);
    }
  }
}
