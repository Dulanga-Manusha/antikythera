package sa.com.cloudsolutions.antikythera.parser;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import sa.com.cloudsolutions.antikythera.depsolver.DepSolver;
import sa.com.cloudsolutions.antikythera.depsolver.Graph;
import sa.com.cloudsolutions.antikythera.evaluator.ArgumentGenerator;
import sa.com.cloudsolutions.antikythera.evaluator.NullArgumentGenerator;

import java.io.IOException;

public abstract class DepsolvingParser {
    CompilationUnit cu;

    public void start() throws IOException {
        for(TypeDeclaration<?> decl : cu.getTypes()) {
            DepSolver solver = DepSolver.createSolver();
            decl.findAll(MethodDeclaration.class).forEach(md -> {
                if (!md.isPrivate()) {
                    Graph.createGraphNode(md);

                }
            });
            solver.dfs();
        }
    }

    public void start(String method) throws IOException{
        for(TypeDeclaration<?> decl : cu.getTypes()) {
            DepSolver solver = DepSolver.createSolver();
            decl.findAll(MethodDeclaration.class).forEach(md -> {
                if (!md.isPrivate() && md.getNameAsString().equals(method)) {
                    Graph.createGraphNode(md);
                }
            });
            solver.dfs();
        }


    }

}
