/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.esql.parser;

import org.elasticsearch.test.ESTestCase;
import org.elasticsearch.xpack.esql.plan.logical.EsqlProject;
import org.elasticsearch.xpack.ql.expression.Alias;
import org.elasticsearch.xpack.ql.expression.Expression;
import org.elasticsearch.xpack.ql.expression.Literal;
import org.elasticsearch.xpack.ql.expression.UnresolvedAttribute;
import org.elasticsearch.xpack.ql.expression.UnresolvedStar;
import org.elasticsearch.xpack.ql.expression.function.UnresolvedFunction;
import org.elasticsearch.xpack.ql.expression.predicate.logical.And;
import org.elasticsearch.xpack.ql.expression.predicate.logical.Not;
import org.elasticsearch.xpack.ql.expression.predicate.logical.Or;
import org.elasticsearch.xpack.ql.expression.predicate.operator.arithmetic.Add;
import org.elasticsearch.xpack.ql.expression.predicate.operator.arithmetic.Div;
import org.elasticsearch.xpack.ql.expression.predicate.operator.arithmetic.Mul;
import org.elasticsearch.xpack.ql.expression.predicate.operator.arithmetic.Neg;
import org.elasticsearch.xpack.ql.expression.predicate.operator.arithmetic.Sub;
import org.elasticsearch.xpack.ql.expression.predicate.operator.comparison.Equals;
import org.elasticsearch.xpack.ql.expression.predicate.operator.comparison.GreaterThan;
import org.elasticsearch.xpack.ql.expression.predicate.operator.comparison.GreaterThanOrEqual;
import org.elasticsearch.xpack.ql.expression.predicate.operator.comparison.LessThanOrEqual;
import org.elasticsearch.xpack.ql.plan.logical.Filter;
import org.elasticsearch.xpack.ql.plan.logical.LogicalPlan;
import org.elasticsearch.xpack.ql.type.DataType;

import java.util.ArrayList;
import java.util.List;

import static org.elasticsearch.xpack.ql.expression.function.FunctionResolutionStrategy.DEFAULT;
import static org.elasticsearch.xpack.ql.tree.Source.EMPTY;
import static org.elasticsearch.xpack.ql.type.DataTypes.DOUBLE;
import static org.elasticsearch.xpack.ql.type.DataTypes.INTEGER;
import static org.elasticsearch.xpack.ql.type.DataTypes.KEYWORD;
import static org.elasticsearch.xpack.ql.type.DataTypes.LONG;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.startsWith;

public class ExpressionTests extends ESTestCase {
    private final EsqlParser parser = new EsqlParser();

    public void testBooleanLiterals() {
        assertEquals(Literal.TRUE, whereExpression("true"));
        assertEquals(Literal.FALSE, whereExpression("false"));
        assertEquals(Literal.NULL, whereExpression("null"));
    }

    public void testNumberLiterals() {
        assertEquals(l(123, INTEGER), whereExpression("123"));
        assertEquals(l(123, INTEGER), whereExpression("+123"));
        assertEquals(new Neg(null, l(123, INTEGER)), whereExpression("-123"));
        assertEquals(l(123.123, DOUBLE), whereExpression("123.123"));
        assertEquals(l(123.123, DOUBLE), whereExpression("+123.123"));
        assertEquals(new Neg(null, l(123.123, DOUBLE)), whereExpression("-123.123"));
        assertEquals(l(0.123, DOUBLE), whereExpression(".123"));
        assertEquals(l(0.123, DOUBLE), whereExpression("0.123"));
        assertEquals(l(0.123, DOUBLE), whereExpression("+0.123"));
        assertEquals(new Neg(null, l(0.123, DOUBLE)), whereExpression("-0.123"));
        assertEquals(l(12345678901L, LONG), whereExpression("12345678901"));
        assertEquals(l(12345678901L, LONG), whereExpression("+12345678901"));
        assertEquals(new Neg(null, l(12345678901L, LONG)), whereExpression("-12345678901"));
        assertEquals(l(123e12, DOUBLE), whereExpression("123e12"));
        assertEquals(l(123e-12, DOUBLE), whereExpression("123e-12"));
        assertEquals(l(123E12, DOUBLE), whereExpression("123E12"));
        assertEquals(l(123E-12, DOUBLE), whereExpression("123E-12"));
    }

    public void testMinusSign() {
        assertEquals(new Neg(null, l(123, INTEGER)), whereExpression("+(-123)"));
        assertEquals(new Neg(null, l(123, INTEGER)), whereExpression("+(+(-123))"));
        // we could do better here. ES SQL is smarter and accounts for the number of minuses
        assertEquals(new Neg(null, new Neg(null, l(123, INTEGER))), whereExpression("-(-123)"));
    }

    public void testStringLiterals() {
        assertEquals(l("abc", KEYWORD), whereExpression("\"abc\""));
        assertEquals(l("123.123", KEYWORD), whereExpression("\"123.123\""));

        assertEquals(l("hello\"world", KEYWORD), whereExpression("\"hello\\\"world\""));
        assertEquals(l("hello'world", KEYWORD), whereExpression("\"hello'world\""));
        assertEquals(l("\"hello\"world\"", KEYWORD), whereExpression("\"\\\"hello\\\"world\\\"\""));
        assertEquals(l("\"hello\nworld\"", KEYWORD), whereExpression("\"\\\"hello\\nworld\\\"\""));
        assertEquals(l("hello\nworld", KEYWORD), whereExpression("\"hello\\nworld\""));
        assertEquals(l("hello\\world", KEYWORD), whereExpression("\"hello\\\\world\""));
        assertEquals(l("hello\rworld", KEYWORD), whereExpression("\"hello\\rworld\""));
        assertEquals(l("hello\tworld", KEYWORD), whereExpression("\"hello\\tworld\""));
        assertEquals(l("C:\\Program Files\\Elastic", KEYWORD), whereExpression("\"C:\\\\Program Files\\\\Elastic\""));

        assertEquals(l("C:\\Program Files\\Elastic", KEYWORD), whereExpression("\"\"\"C:\\Program Files\\Elastic\"\"\""));
        assertEquals(l("\"\"hello world\"\"", KEYWORD), whereExpression("\"\"\"\"\"hello world\"\"\"\"\""));
        assertEquals(l("hello \"\"\" world", KEYWORD), whereExpression("\"hello \\\"\\\"\\\" world\""));
        assertEquals(l("hello\\nworld", KEYWORD), whereExpression("\"\"\"hello\\nworld\"\"\""));
        assertEquals(l("hello\\tworld", KEYWORD), whereExpression("\"\"\"hello\\tworld\"\"\""));
        assertEquals(l("hello world\\", KEYWORD), whereExpression("\"\"\"hello world\\\"\"\""));
        assertEquals(l("hello            world\\", KEYWORD), whereExpression("\"\"\"hello            world\\\"\"\""));
        assertEquals(l("\t \n \r \" \\ ", KEYWORD), whereExpression("\"\\t \\n \\r \\\" \\\\ \""));
    }

    public void testStringLiteralsExceptions() {
        assertParsingException(() -> whereExpression("\"\"\"\"\"\"foo\"\""), "line 1:22: mismatched input 'foo' expecting {<EOF>,");
        assertParsingException(
            () -> whereExpression("\"foo\" == \"\"\"\"\"\"bar\"\"\""),
            "line 1:31: mismatched input 'bar' expecting {<EOF>,"
        );
        assertParsingException(
            () -> whereExpression("\"\"\"\"\"\\\"foo\"\"\"\"\"\" != \"\"\"bar\"\"\""),
            "line 1:31: mismatched input '\" != \"' expecting {<EOF>,"
        );
        assertParsingException(
            () -> whereExpression("\"\"\"\"\"\\\"foo\"\"\\\"\"\"\" == \"\"\"\"\"\\\"bar\\\"\\\"\"\"\"\"\""),
            "line 1:55: token recognition error at: '\"'"
        );
        assertParsingException(
            () -> whereExpression("\"\"\"\"\"\" foo \"\"\"\" == abc"),
            "line 1:23: mismatched input 'foo' expecting {<EOF>,"
        );
    }

    public void testBooleanLiteralsCondition() {
        Expression expression = whereExpression("true and false");
        assertThat(expression, instanceOf(And.class));
        And and = (And) expression;
        assertThat(and.left(), equalTo(Literal.TRUE));
        assertThat(and.right(), equalTo(Literal.FALSE));
    }

    public void testArithmeticOperationCondition() {
        Expression expression = whereExpression("-a-b*c == 123");
        assertThat(expression, instanceOf(Equals.class));
        Equals eq = (Equals) expression;
        assertThat(eq.right(), instanceOf(Literal.class));
        assertThat(((Literal) eq.right()).value(), equalTo(123));
        assertThat(eq.left(), instanceOf(Sub.class));
        Sub sub = (Sub) eq.left();
        assertThat(sub.left(), instanceOf(Neg.class));
        Neg subLeftNeg = (Neg) sub.left();
        assertThat(subLeftNeg.field(), instanceOf(UnresolvedAttribute.class));
        assertThat(((UnresolvedAttribute) subLeftNeg.field()).name(), equalTo("a"));
        Mul mul = (Mul) sub.right();
        assertThat(mul.left(), instanceOf(UnresolvedAttribute.class));
        assertThat(mul.right(), instanceOf(UnresolvedAttribute.class));
        assertThat(((UnresolvedAttribute) mul.left()).name(), equalTo("b"));
        assertThat(((UnresolvedAttribute) mul.right()).name(), equalTo("c"));
    }

    public void testConjunctionDisjunctionCondition() {
        Expression expression = whereExpression("not aaa and b or c");
        assertThat(expression, instanceOf(Or.class));
        Or or = (Or) expression;
        assertThat(or.right(), instanceOf(UnresolvedAttribute.class));
        assertThat(((UnresolvedAttribute) or.right()).name(), equalTo("c"));
        assertThat(or.left(), instanceOf(And.class));
        And and = (And) or.left();
        assertThat(and.right(), instanceOf(UnresolvedAttribute.class));
        assertThat(((UnresolvedAttribute) and.right()).name(), equalTo("b"));
        assertThat(and.left(), instanceOf(Not.class));
        Not not = (Not) and.left();
        assertThat(not.children().size(), equalTo(1));
        assertThat(not.children().get(0), instanceOf(UnresolvedAttribute.class));
        assertThat(((UnresolvedAttribute) not.children().get(0)).name(), equalTo("aaa"));
    }

    public void testParenthesizedExpression() {
        Expression expression = whereExpression("((a and ((b and c))) or (((x or y))))");
        assertThat(expression, instanceOf(Or.class));
        Or or = (Or) expression;

        assertThat(or.right(), instanceOf(Or.class));
        Or orRight = (Or) or.right();
        assertThat(orRight.right(), instanceOf(UnresolvedAttribute.class));
        assertThat(((UnresolvedAttribute) orRight.right()).name(), equalTo("y"));
        assertThat(orRight.left(), instanceOf(UnresolvedAttribute.class));
        assertThat(orRight.left(), instanceOf(UnresolvedAttribute.class));
        assertThat(((UnresolvedAttribute) orRight.left()).name(), equalTo("x"));

        assertThat(or.left(), instanceOf(And.class));
        And and = (And) or.left();
        assertThat(and.right(), instanceOf(And.class));
        And andRight = (And) and.right();
        assertThat(andRight.right(), instanceOf(UnresolvedAttribute.class));
        assertThat(((UnresolvedAttribute) andRight.right()).name(), equalTo("c"));
        assertThat(andRight.left(), instanceOf(UnresolvedAttribute.class));
        assertThat(((UnresolvedAttribute) andRight.left()).name(), equalTo("b"));

        assertThat(and.left(), instanceOf(UnresolvedAttribute.class));
        assertThat(((UnresolvedAttribute) and.left()).name(), equalTo("a"));
    }

    public void testCommandNamesAsIdentifiers() {
        Expression expr = whereExpression("from and where");
        assertThat(expr, instanceOf(And.class));
        And and = (And) expr;

        assertThat(and.left(), instanceOf(UnresolvedAttribute.class));
        assertThat(((UnresolvedAttribute) and.left()).name(), equalTo("from"));

        assertThat(and.right(), instanceOf(UnresolvedAttribute.class));
        assertThat(((UnresolvedAttribute) and.right()).name(), equalTo("where"));
    }

    public void testIdentifiersCaseSensitive() {
        Expression expr = whereExpression("hElLo");

        assertThat(expr, instanceOf(UnresolvedAttribute.class));
        assertThat(((UnresolvedAttribute) expr).name(), equalTo("hElLo"));
    }

    /*
     * a > 1 and b > 1 + 2 => (a > 1) and (b > (1 + 2))
     */
    public void testOperatorsPrecedenceWithConjunction() {
        Expression expression = whereExpression("a > 1 and b > 1 + 2");
        assertThat(expression, instanceOf(And.class));
        And and = (And) expression;

        assertThat(and.left(), instanceOf(GreaterThan.class));
        GreaterThan gt = (GreaterThan) and.left();
        assertThat(gt.left(), instanceOf(UnresolvedAttribute.class));
        assertThat(((UnresolvedAttribute) gt.left()).name(), equalTo("a"));
        assertThat(gt.right(), instanceOf(Literal.class));
        assertThat(((Literal) gt.right()).value(), equalTo(1));

        assertThat(and.right(), instanceOf(GreaterThan.class));
        gt = (GreaterThan) and.right();
        assertThat(gt.left(), instanceOf(UnresolvedAttribute.class));
        assertThat(((UnresolvedAttribute) gt.left()).name(), equalTo("b"));
        assertThat(gt.right(), instanceOf(Add.class));
        Add add = (Add) gt.right();
        assertThat(((Literal) add.right()).value(), equalTo(2));
        assertThat(((Literal) add.left()).value(), equalTo(1));
    }

    /*
     * a <= 1 or b >= 5 / 2 and c != 5 => (a <= 1) or (b >= (5 / 2) and not(c == 5))
     */
    public void testOperatorsPrecedenceWithDisjunction() {
        Expression expression = whereExpression("a <= 1 or b >= 5 / 2 and c != 5");
        assertThat(expression, instanceOf(Or.class));
        Or or = (Or) expression;

        assertThat(or.left(), instanceOf(LessThanOrEqual.class));
        LessThanOrEqual lte = (LessThanOrEqual) or.left();
        assertThat(lte.left(), instanceOf(UnresolvedAttribute.class));
        assertThat(((UnresolvedAttribute) lte.left()).name(), equalTo("a"));
        assertThat(lte.right(), instanceOf(Literal.class));
        assertThat(((Literal) lte.right()).value(), equalTo(1));

        assertThat(or.right(), instanceOf(And.class));
        And and = (And) or.right();
        assertThat(and.left(), instanceOf(GreaterThanOrEqual.class));
        GreaterThanOrEqual gte = (GreaterThanOrEqual) and.left();
        assertThat(gte.left(), instanceOf(UnresolvedAttribute.class));
        assertThat(((UnresolvedAttribute) gte.left()).name(), equalTo("b"));
        assertThat(gte.right(), instanceOf(Div.class));
        Div div = (Div) gte.right();
        assertThat(div.right(), instanceOf(Literal.class));
        assertThat(((Literal) div.right()).value(), equalTo(2));
        assertThat(div.left(), instanceOf(Literal.class));
        assertThat(((Literal) div.left()).value(), equalTo(5));

        assertThat(and.right(), instanceOf(Not.class));
        assertThat(((Not) and.right()).field(), instanceOf(Equals.class));
        Equals e = (Equals) ((Not) and.right()).field();
        assertThat(e.left(), instanceOf(UnresolvedAttribute.class));
        assertThat(((UnresolvedAttribute) e.left()).name(), equalTo("c"));
        assertThat(e.right(), instanceOf(Literal.class));
        assertThat(((Literal) e.right()).value(), equalTo(5));
    }

    /*
     * not a == 1 or not b >= 5 and c == 5 => (not (a == 1)) or ((not (b >= 5)) and c == 5)
     */
    public void testOperatorsPrecedenceWithNegation() {
        Expression expression = whereExpression("not a == 1 or not b >= 5 and c == 5");
        assertThat(expression, instanceOf(Or.class));
        Or or = (Or) expression;

        assertThat(or.left(), instanceOf(Not.class));
        assertThat(((Not) or.left()).field(), instanceOf(Equals.class));
        Equals e = (Equals) ((Not) or.left()).field();
        assertThat(e.left(), instanceOf(UnresolvedAttribute.class));
        assertThat(((UnresolvedAttribute) e.left()).name(), equalTo("a"));
        assertThat(e.right(), instanceOf(Literal.class));
        assertThat(((Literal) e.right()).value(), equalTo(1));

        assertThat(or.right(), instanceOf(And.class));
        And and = (And) or.right();
        assertThat(and.left(), instanceOf(Not.class));
        assertThat(((Not) and.left()).field(), instanceOf(GreaterThanOrEqual.class));
        GreaterThanOrEqual gte = (GreaterThanOrEqual) ((Not) and.left()).field();
        assertThat(gte.right(), instanceOf(Literal.class));
        assertThat(((Literal) gte.right()).value(), equalTo(5));

        assertThat(and.right(), instanceOf(Equals.class));
        e = (Equals) and.right();
        assertThat(e.left(), instanceOf(UnresolvedAttribute.class));
        assertThat(((UnresolvedAttribute) e.left()).name(), equalTo("c"));
        assertThat(e.right(), instanceOf(Literal.class));
        assertThat(((Literal) e.right()).value(), equalTo(5));
    }

    public void testOperatorsPrecedenceExpressionsEquality() {
        assertThat(whereExpression("a-1>2 or b>=5 and c-1>=5"), equalTo(whereExpression("((a-1)>2 or (b>=5 and (c-1)>=5))")));
        assertThat(
            whereExpression("a*5==25 and b>5 and c%4>=1 or true or false"),
            equalTo(whereExpression("(((((a*5)==25) and (b>5) and ((c%4)>=1)) or true) or false)"))
        );
        assertThat(
            whereExpression("a*4-b*5<100 and b/2+c*6>=50 or c%5+x>=5"),
            equalTo(whereExpression("((((a*4)-(b*5))<100) and (((b/2)+(c*6))>=50)) or (((c%5)+x)>=5)"))
        );
        assertThat(
            whereExpression("true and false or true and c/12+x*5-y%2>=50"),
            equalTo(whereExpression("((true and false) or (true and (((c/12)+(x*5)-(y%2))>=50)))"))
        );
    }

    public void testFunctionExpressions() {
        assertEquals(new UnresolvedFunction(EMPTY, "fn", DEFAULT, new ArrayList<>()), whereExpression("fn()"));
        assertEquals(
            new UnresolvedFunction(
                EMPTY,
                "invoke",
                DEFAULT,
                new ArrayList<>(
                    List.of(
                        new UnresolvedAttribute(EMPTY, "a"),
                        new Add(EMPTY, new UnresolvedAttribute(EMPTY, "b"), new UnresolvedAttribute(EMPTY, "c"))
                    )
                )
            ),
            whereExpression("invoke(a, b + c)")
        );
        assertEquals(whereExpression("(invoke((a + b)))"), whereExpression("invoke(a+b)"));
        assertEquals(whereExpression("((fn()) + fn(fn()))"), whereExpression("fn() + fn(fn())"));
    }

    public void testWildcardProjectKeepPatterns() {
        String[] exp = new String[] {
            "a*",
            "*a",
            "a.*",
            "a.a.*.*.a",
            "*.a.a.a.*",
            "*abc.*",
            "a*b*c",
            "*a*",
            "*a*b",
            "a*b*",
            "*a*b*c*",
            "a*b*c*",
            "*a*b*c",
            "a*b*c*a.b*",
            "a*b*c*a.b.*",
            "*a.b.c*b*c*a.b.*" };
        List<?> projections;
        EsqlProject p;
        for (String e : exp) {
            p = projectExpression(e);
            projections = p.projections();
            assertThat(projections.size(), equalTo(1));
            assertThat(p.removals().size(), equalTo(0));
            assertThat("Projection [" + e + "] has an unexpected type", projections.get(0), instanceOf(UnresolvedAttribute.class));
            UnresolvedAttribute ua = (UnresolvedAttribute) projections.get(0);
            assertThat(ua.name(), equalTo(e));
            assertThat(ua.unresolvedMessage(), equalTo("Unknown column [" + e + "]"));
        }
    }

    public void testWildcardProjectKeep() {
        EsqlProject p = projectExpression("*");
        List<?> projections = p.projections();
        assertThat(projections.size(), equalTo(1));
        assertThat(p.removals().size(), equalTo(0));
        assertThat(projections.get(0), instanceOf(UnresolvedStar.class));
        UnresolvedStar us = (UnresolvedStar) projections.get(0);
        assertThat(us.qualifier(), equalTo(null));
        assertThat(us.unresolvedMessage(), equalTo("Cannot determine columns for [*]"));
    }

    public void testWildcardProjectAwayPatterns() {
        String[] exp = new String[] {
            "-a*",
            "-*a",
            "-a.*",
            "-a.a.*.*.a",
            "-*.a.a.a.*",
            "-*abc.*",
            "-a*b*c",
            "-*a*",
            "-*a*b",
            "-a*b*",
            "-*a*b*c*",
            "-a*b*c*",
            "-*a*b*c",
            "-a*b*c*a.b*",
            "-a*b*c*a.b.*",
            "-*a.b.c*b*c*a.b.*" };
        List<?> removals;
        for (String e : exp) {
            EsqlProject p = projectExpression(e);
            removals = p.removals();
            assertThat(removals.size(), equalTo(1));
            assertThat(p.projections().size(), equalTo(0));
            assertThat("Projection [" + e + "] has an unexpected type", removals.get(0), instanceOf(UnresolvedAttribute.class));
            UnresolvedAttribute ursa = (UnresolvedAttribute) removals.get(0);
            assertThat(ursa.name(), equalTo(e));
            assertThat(ursa.unresolvedMessage(), equalTo("Unknown column [" + e + "]"));
        }
    }

    public void testForbidWildcardProjectAway() {
        assertParsingException(() -> projectExpression("-*"), "line 1:19: Removing all fields is not allowed [-*]");
    }

    public void testProjectKeepPatterns() {
        String[] exp = new String[] { "abc", "abc.xyz", "a.b.c.d.e" };
        List<?> projections;
        EsqlProject p;
        for (String e : exp) {
            p = projectExpression(e);
            projections = p.projections();
            assertThat(projections.size(), equalTo(1));
            assertThat(p.removals().size(), equalTo(0));
            assertThat(projections.get(0), instanceOf(UnresolvedAttribute.class));
            assertThat(((UnresolvedAttribute) projections.get(0)).name(), equalTo(e));
        }
    }

    public void testProjectAwayPatterns() {
        String[] exp = new String[] { "-abc", "-abc.xyz", "-a.b.c.d.e" };
        List<?> removals;
        for (String e : exp) {
            EsqlProject p = projectExpression(e);
            removals = p.removals();
            assertThat(removals.size(), equalTo(1));
            assertThat(p.projections().size(), equalTo(0));
            assertThat(removals.get(0), instanceOf(UnresolvedAttribute.class));
            assertThat(((UnresolvedAttribute) removals.get(0)).name(), equalTo(e));
        }
    }

    public void testProjectRename() {
        String[] newName = new String[] { "a", "a.b", "a", "x.y" };
        String[] oldName = new String[] { "b", "a.c", "x.y", "a" };
        List<?> projections;
        for (int i = 0; i < newName.length; i++) {
            EsqlProject p = projectExpression(newName[i] + "=" + oldName[i]);
            projections = p.projections();
            assertThat(projections.size(), equalTo(1));
            assertThat(p.removals().size(), equalTo(0));
            assertThat(projections.get(0), instanceOf(Alias.class));
            Alias a = (Alias) projections.get(0);
            assertThat(a.child(), instanceOf(UnresolvedAttribute.class));
            UnresolvedAttribute ua = (UnresolvedAttribute) a.child();
            assertThat(a.name(), equalTo(newName[i]));
            assertThat(ua.name(), equalTo(oldName[i]));
        }
    }

    public void testForbidWildcardProjectRename() {
        assertParsingException(
            () -> projectExpression("a*=b*"),
            "line 1:19: Using wildcards (*) in renaming projections is not allowed [a*=b*]"
        );
    }

    private Expression whereExpression(String e) {
        LogicalPlan plan = parser.createStatement("from a | where " + e);
        return ((Filter) plan).condition();
    }

    private EsqlProject projectExpression(String e) {
        return (EsqlProject) parser.createStatement("from a | project " + e);
    }

    private Literal l(Object value, DataType type) {
        return new Literal(null, value, type);
    }

    private void assertParsingException(ThrowingRunnable expression, String expectedError) {
        ParsingException e = expectThrows(ParsingException.class, "Expected syntax error", expression);
        assertThat(e.getMessage(), startsWith(expectedError));
    }
}
