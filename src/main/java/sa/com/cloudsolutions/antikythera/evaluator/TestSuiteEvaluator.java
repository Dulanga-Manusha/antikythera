package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import sa.com.cloudsolutions.antikythera.evaluator.mock.MockingRegistry;
import sa.com.cloudsolutions.antikythera.parser.Callable;

import java.lang.reflect.Method;

public class TestSuiteEvaluator extends Evaluator {
    private boolean when;
    private Callable callable;

    public TestSuiteEvaluator(CompilationUnit cu, String className) {
        super();
        this.className = className;
        this.cu = cu;
    }

    @Override
    public Variable evaluateMethodCall(ScopeChain.Scope scope) throws ReflectiveOperationException {
        MethodCallExpr methodCall = scope.getScopedMethodCall();
        if (methodCall.getNameAsString().equals("when")) {
            when = true;
            Expression arg = methodCall.getArgument(0);
            return evaluateMethodCall((MethodCallExpr) arg);
        }
        if (methodCall.getNameAsString().equals("thenReturn")) {
            Expression arg = methodCall.getArgument(0);
            Variable v = evaluateExpression(arg);
            when = false;
            MockingRegistry.when(callable.getMethod().getDeclaringClass().getName(), callable, v.getValue());
            return v;
        }
        return super.evaluateMethodCall(scope);
    }

    @Override
    Variable evaluateScopedMethodCall(ScopeChain chain) throws ReflectiveOperationException {
        MethodCallExpr methodCall = chain.getExpression().asMethodCallExpr();
        Variable variable = evaluateScopeChain(chain);
        ScopeChain.Scope scope = chain.getChain().getLast();
        scope.setScopedMethodCall(methodCall);
        scope.setVariable(variable);
        return evaluateMethodCall(scope);
    }

    @Override
    Variable reflectiveMethodCall(Variable v, ReflectionArguments reflectionArguments) throws ReflectiveOperationException {
        if (when) {
            Method method = Reflect.findAccessibleMethod(v.getClazz(), reflectionArguments);
            callable = new Callable(method);
            return new Variable(this);
        }
        return super.reflectiveMethodCall(v, reflectionArguments);
    }
}
