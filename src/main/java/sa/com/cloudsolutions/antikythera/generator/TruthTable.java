package sa.com.cloudsolutions.antikythera.generator;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.DoubleLiteralExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.utils.Pair;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Generates and print truth tables for given conditionals
 *
 * Comparisions involving Object.equals() are tricky. The range of values to assign to the variable
 * depends on the argument to the equals method. Using null may not be possible because it will
 * lead to Null Pointer Exceptions.
 *
 * The values assigned may have it's domain in Strings, Boolean or any other objects. This
 * implementation will only consider Numeric, Boolean and String expressions.
 */
public class TruthTable {
    public static final NameExpr RESULT = new NameExpr("Result");
    /**
     * The condition that this truth table is for
     */
    private final Expression condition;
    /**
     * Collection of variables involved in the condition.
     * the key will be the expression representing the variable and the value will be a Pair
     * representing the lower and upper bounds for the expresion
     */
    private final HashMap<Expression, Pair<Object, Object>> variables;

    /**
     * All the sub conditions that make up the condition.
     */
    private final Set<Expression> conditions;

    /**
     * The matrix of values for the variables and the result of the condition
     */
    private List<Map<Expression, Object>> table;

    /**
     * Create a new truth table for the given condition represented as a string
     * @param conditionCode the condition as string
     */
    public TruthTable(String conditionCode) {
        this(StaticJavaParser.parseExpression(conditionCode));

    }

    /**
     * Create a new truth table for the given condition.
     * @param condition Expression
     */
    public TruthTable(Expression condition) {
        this.condition = condition;
        this.variables = new HashMap<>();
        this.conditions = new HashSet<>();

        /*
         * A single IF statement may have multiple binary expressions attached to it, first step
         * is to identify all of them.
         */
        this.condition.accept(new ConditionCollector(), conditions);
        this.condition.accept(new VariableCollector(), variables);

        generateTruthTable();
    }

    private static boolean isInequality(BinaryExpr binaryExpr) {
        return binaryExpr.getOperator() == BinaryExpr.Operator.LESS
                || binaryExpr.getOperator() == BinaryExpr.Operator.GREATER
                || binaryExpr.getOperator() == BinaryExpr.Operator.LESS_EQUALS
                || binaryExpr.getOperator() == BinaryExpr.Operator.GREATER_EQUALS;
    }

    /**
     * Main method to test the truth table generation and printing with different conditions.
     *
     * @param args Command line arguments.
     */
    public static void main(String[] args) {
        String[] conditions = {
                "a > b",
                "a == b",
                "a.equals(\"null\")",
                "a.equals(b)",
                "a.equals(\"b\")",
                "a > b && c == d",
                "a != null && b != null",
                "a == null",
                "a == null || b == null",
                "a && b || !c",
                "x || y && !z",
                "a > b && b < c",
                "a > b && b > c"
        };

        for (String condition : conditions) {
            TruthTable generator = new TruthTable(condition);
            generator.printTruthTable();
            generator.printValues(true);
            generator.printValues(false);
            System.out.println("\n");
        }
    }

    /**
     * Generates a truth table for the given condition.
     */
    private void generateTruthTable() {
        Expression[] variableList = variables.keySet().toArray(new Expression[0]);
        int numRows = (int) Math.pow(2, variableList.length);

        table = new ArrayList<>();

        for (int i = 0; i < numRows; i++) {
            Map<Expression, Object> truthValues = new HashMap<>();
            for (int j = 0; j < variableList.length; j++) {
                Expression variable = variableList[j];
                Pair<Object, Object> bounds = variables.get(variable);

                boolean value = (i & (1 << j)) != 0;
                if (value) {
                    if (bounds.a != null) {
                        truthValues.put(variable, bounds.a);
                    } else {
                        truthValues.put(variable, true);
                    }
                }
                else {
                    if (bounds.a != null) {
                        truthValues.put(variable, bounds.b);
                    } else {
                        truthValues.put(variable, false);
                    }
                }

            }
            boolean result = evaluateCondition(condition, truthValues);
            truthValues.put(RESULT, result);
            table.add(truthValues);
        }
    }

    /**
     * Prints the truth table for the given condition.
     *
     */
    public void printTruthTable() {
        writeTruthTable(System.out);
    }

    public void writeTruthTable(PrintStream out) {
        out.println("Truth Table for condition: " + condition);

        if (!table.isEmpty()) {
            Map<Expression, Object> firstRow = table.get(0);
            final String FORMAT = "%-11s";
            for (Expression key : firstRow.keySet()) {
                if (!key.equals(RESULT)) {
                    out.printf(FORMAT, key.toString());
                }
            }
            out.printf(FORMAT, RESULT);
            out.println();

            for (Map<Expression, Object> row : table) {
                for (var entry : row.entrySet()) {
                    if (!entry.getKey().equals(RESULT)) {
                        out.printf(FORMAT, entry.getValue());
                    }
                }
                out.printf(FORMAT, row.get(RESULT));
                out.println();
            }
        } else {
            out.println("No data to display.");
        }
    }

    /**
     * Prints the values that make the condition true.
     */
    public void printValues(boolean desiredState) {
        String state = desiredState ? "true" : "false";
        System.out.println("\nValues to make the condition " + state + " for: " + condition);

        List<Map<Expression, Object>> values = findValuesForCondition(desiredState);

        values.stream().findFirst().ifPresentOrElse(
                row -> {
                    row.entrySet().forEach(var ->System.out.printf("%-10s", var));
                    System.out.println();
                },
                () -> System.out.println("No combination of values makes the condition " + state + ".")
        );
    }

    /**
     * Find the values that make the condition true or false.
     * Often there will be more than one combination of values.
     * @param desiredState either true or false
     * @return a list of maps containing the values that make the condition true or false
     */
    public List<Map<Expression, Object>> findValuesForCondition(boolean desiredState) {
        List<Map<Expression, Object>> result = new ArrayList<>();

        for (Map<Expression, Object> row : table) {
            if ((boolean) row.get(RESULT) == desiredState) {
                Map<Expression, Object> copy = new HashMap<>();
                for (Map.Entry<Expression, Object> entry : row.entrySet()) {
                    if (!entry.getKey().equals(RESULT)) {
                        copy.put(entry.getKey(), entry.getValue());
                    }
                }
                result.add(copy);
            }
        }

        return result;
    }

    /**
     * Evaluates the given condition with the provided truth values.
     *
     * @param condition   The condition to evaluate.
     * @param truthValues The truth values for the variables.
     * @return The result of the evaluation.
     */
    private Boolean evaluateCondition(Expression condition, Map<Expression, Object> truthValues) {
        if (condition.isBinaryExpr()) {
            var binaryExpr = condition.asBinaryExpr();
            var leftExpr = binaryExpr.getLeft();
            var rightExpr = binaryExpr.getRight();

            if (isInequality(binaryExpr)) {
                int left = (int) getValue(leftExpr, truthValues);
                int right = (int) getValue(rightExpr, truthValues);

                truthValues.put(leftExpr, left);
                truthValues.put(rightExpr, right);

                return switch (binaryExpr.getOperator()) {
                    case LESS -> left < right;
                    case GREATER -> left > right;
                    case LESS_EQUALS -> left <= right;
                    case GREATER_EQUALS -> left >= right;
                    default -> throw new UnsupportedOperationException("Unsupported operator: " + binaryExpr.getOperator());
                };
            } else {
                Boolean left = evaluateCondition(leftExpr, truthValues);
                Boolean right = evaluateCondition(rightExpr, truthValues);
                return switch (binaryExpr.getOperator()) {
                    case AND -> left && right;
                    case OR -> left || right;
                    case EQUALS -> (left == null || right == null) ? left == right : left.equals(right);
                    case NOT_EQUALS -> (left == null || right == null) ? left != right : !left.equals(right);
                    default -> throw new UnsupportedOperationException("Unsupported operator: " + binaryExpr.getOperator());
                };
            }
        } else if (condition.isUnaryExpr()) {
            var unaryExpr = condition.asUnaryExpr();
            boolean value = evaluateCondition(unaryExpr.getExpression(), truthValues);
            return switch (unaryExpr.getOperator()) {
                case LOGICAL_COMPLEMENT -> !value;
                default -> throw new UnsupportedOperationException("Unsupported operator: " + unaryExpr.getOperator());
            };
        } else if (condition.isNameExpr()) {
            return (Boolean) truthValues.get(condition);
        } else if (condition.isBooleanLiteralExpr()) {
            return condition.asBooleanLiteralExpr().getValue();
        } else if (condition.isStringLiteralExpr() || condition.isFieldAccessExpr()) {
            return (Boolean) getValue(condition, truthValues);
        }
        else if (condition.isMethodCallExpr() ) {
            if (condition.toString().contains("equals")) {
                MethodCallExpr mce = condition.asMethodCallExpr();
                return truthValues.get(condition).equals(truthValues.get(mce.getArgument(0)));
            }
            return (Boolean) getValue(condition, truthValues);
        } else if (condition.isNullLiteralExpr()) {
            return null;
        }
        throw new UnsupportedOperationException("Unsupported expression: " + condition);
    }

    /**
     * FInd the appropriate value for the given expression
     * @param expr the conditional expression to find the value for
     * @param truthValues the table containing the values to use
     * @return the value will typically be true/false in some cases it maybe 0/1 and when the
     *      condition has a null in it, we may return null
     */
    private Object getValue(Expression expr, Map<Expression, Object> truthValues) {
        if (expr.isNameExpr()) {
            Object value = truthValues.get(expr);
            if (value instanceof Boolean) {
                return (boolean) value ? 1 : 0;
            } else if (value instanceof Number n) {
                return n.intValue();
            }
        } else if (expr.isLiteralExpr()) {
            return switch (expr) {
                case IntegerLiteralExpr integerLiteralExpr -> Integer.valueOf(integerLiteralExpr.getValue());
                case DoubleLiteralExpr doubleLiteralExpr -> Double.valueOf(doubleLiteralExpr.getValue());
                case StringLiteralExpr stringLiteralExpr -> stringLiteralExpr.getValue();
                case NullLiteralExpr nullLiteralExpr -> null;
                default -> throw new UnsupportedOperationException("Unsupported literal expression: " + expr);
            };
        }

        return truthValues.get(expr);
    }


    /**
     * Collects variable names from the condition expression.
     */
    private class VariableCollector extends VoidVisitorAdapter<HashMap<Expression, Pair<Object, Object>>> {
        /**
         * Identify name expressions.
         * We will get a lot of false positives here where the name expression is part of a component
         * that is being captured else where. So we have to carefully filter them out.
         *
         * @param n
         * @param collector
         */
        @Override
        public void visit(NameExpr n, HashMap<Expression, Pair<Object, Object>> collector) {
            if (n.getParentNode().isEmpty()) {
                collector.put(n, new Pair<>(true, false));
            } else {
                Node parent = n.getParentNode().get();
                if (parent instanceof MethodCallExpr mce && mce.getNameAsString().equals("equals")) {
                    Expression arg = mce.getArgument(0);
                    if (arg.equals(n)) {
                        collector.put(n, new Pair<>(true, false));
                    }
                } else if (!(parent instanceof FieldAccessExpr)) {
                    if(isInequalityPresent()) {
                        collector.put(n, new Pair<>(0, 1));
                    }
                    else {
                        collector.put(n, new Pair<>(true, false));
                    }
                }
            }
            super.visit(n, collector);
        }

        @Override
        public void visit(MethodCallExpr m, HashMap<Expression, Pair<Object, Object>> collector) {
            collector.put(m, new Pair<>(true, false));
            super.visit(m, collector);
        }

        @Override
        public void visit(FieldAccessExpr f, HashMap<Expression, Pair<Object, Object>> collector) {
            if(isInequalityPresent()) {
                collector.put(f, new Pair<>(0, 1));
            }
            else {
                collector.put(f, new Pair<>(true, false));
            }
            super.visit(f, collector);
        }

        /*
         * Does this condition have an inequality as a sub expression
         */
        private boolean isInequalityPresent() {
            for(Expression expr : conditions) {
                BinaryExpr bin = expr.asBinaryExpr();
                if (bin.getOperator().equals(BinaryExpr.Operator.LESS) ||
                        bin.getOperator().equals(BinaryExpr.Operator.GREATER) ||
                        bin.getOperator().equals(BinaryExpr.Operator.LESS_EQUALS) ||
                        bin.getOperator().equals(BinaryExpr.Operator.GREATER_EQUALS)) {
                    return true;
                }
            }
            return false;
        }
    }

    private static class ConditionCollector extends VoidVisitorAdapter<Set<Expression>> {
        @Override
        public void visit(BinaryExpr b, Set<Expression> collector) {
            collector.add(b);
            super.visit(b, collector);
        }

        /**
         * In this scenario a method call expression will always be a .equals call or a method returning boolean
         * @param m the method call expression
         * @param collector for all the conditional expressions encountered.
         *
         */
        @Override
        public void visit(MethodCallExpr m, Set<Expression> collector) {
            collector.add(m);
            super.visit(m, collector);
        }
    }
}
