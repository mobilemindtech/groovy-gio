package gio.ast

import groovy.transform.TupleConstructor
import org.codehaus.groovy.ast.*
import org.codehaus.groovy.ast.expr.*
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.ExpressionStatement
import org.codehaus.groovy.ast.stmt.ReturnStatement
import org.codehaus.groovy.ast.stmt.Statement
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.transform.ASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation

enum ExpressionType {
    /**
     * guard expression, represented by ClosureExpression:
     * guard { x > 0 }
     */
    Guard,
    /**
     * expression like:
     * x = IO.pure(1)
     */
    Binary,
    /**
     * expression like:
     * IO.puts("")
     */
    Method
}

@TupleConstructor
class ExpressionItem {
    Expression expr
    ExpressionType  type

    boolean isGuard() { type == ExpressionType.Guard }
}

@GroovyASTTransformation(phase = CompilePhase.SEMANTIC_ANALYSIS)
class ForMTransform implements ASTTransformation {

    @Override
    void visit(ASTNode[] nodes, SourceUnit source) {
        if (!nodes || nodes.length < 2) return
        def annotatedNode = nodes[1]

        // Se a anotação foi aplicada diretamente à declaração local
        if (annotatedNode instanceof DeclarationExpression) {
            if (hasEnableForM(annotatedNode)) {
                transformDeclaration((DeclarationExpression) annotatedNode)
            }

        } else if (annotatedNode instanceof FieldNode) {
            // Se a anotação foi aplicada a um campo (Field)
            if (hasEnableForM(annotatedNode)) {
                transformField((FieldNode) annotatedNode)
            }
        }

        // Segurança: se veio algo diferente, nada a fazer
    }
    private boolean hasEnableForM(AnnotatedNode node) {
        node.getAnnotations()?.any { an ->
            an.classNode?.name == ForM.name
        } ?: false
    }

    private void transformField(FieldNode field) {
        def init = field.initialExpression
        if (init) {
            def newInit = tryTransformForMCall(init)
            if (newInit) field.initialValueExpression = newInit
        }
    }

    private void transformDeclaration(DeclarationExpression decl) {
        def rhs = decl.rightExpression
        if (!rhs) return
        def newRhs = tryTransformForMCall(rhs)
        if (newRhs) decl.rightExpression = newRhs
    }

    /**
     * Se a expressão é uma chamada forM { ... } (ou ForM.io { ... }) retorna uma nova
     * MethodCallExpression que chama ForM.io(rewrittenClosure). Senão, retorna null.
     */
    private Expression tryTransformForMCall(Expression expr) {
        if (!(expr instanceof StaticMethodCallExpression)) return null
        StaticMethodCallExpression mcall = (StaticMethodCallExpression) expr

        // detecta forM { ... } (unqualified) ou ClassExpression.forM (ex.: ForM.io)
        def name = mcall.methodAsString

        boolean nameLooksLikeForM = name == 'forM'

        if (!nameLooksLikeForM) return null


        // pega closure argumento (primeiro arg)
        if (!(mcall.arguments instanceof ArgumentListExpression)) {
            println "closure has not args"
            return null
        }
        def args = (ArgumentListExpression) mcall.arguments
        if (args.expressions.isEmpty()) {
            println "args expressions is empty"
            return null
        }
        def first = args.expressions.size() == 1 ? args.expressions[0] : args.expressions[1]
        if (!(first instanceof ClosureExpression)) {
            println "arg is not Closure"
            return null
        }
        ClosureExpression closure = (ClosureExpression) first

        // reescreve a closure (procura @let... yield ...)
        ClosureExpression newClosure = rewriteClosure(closure)
        if (newClosure == null) return null

        // substitui por ForM.io(newClosure) — ForM está no pacote gio.forio
        ClassNode forMClass = ClassHelper.make("gio.syntax.ForM")
        return new MethodCallExpression(
            new ClassExpression(forMClass),
            "forM",
            new ArgumentListExpression(newClosure)
        )
    }

    /**
     * Reescreve a closure transformando declarações anotadas com @let (DeclarationExpression)
     * em binds aninhados e o yield final em pure(...)
     */
    private ClosureExpression rewriteClosure(ClosureExpression mainClosureExpr) {

        if (!(mainClosureExpr.code instanceof BlockStatement)) return null
        BlockStatement block = (BlockStatement) mainClosureExpr.code

        List<ExpressionItem> binds = []
        ClosureExpression yieldExpr = null
        List<DeclarationExpression> declarations = []

        // 1) Identifica declarações anotadas com RHS GIO.pure e yield final
        block.statements.each { Statement s ->
            if (!(s instanceof ExpressionStatement)) return
            Expression e = ((ExpressionStatement) s).expression

            // DeclarationExpression que deve virar bind/flatMap
            if (e instanceof BinaryExpression) {
                Expression rhs = e.rightExpression
                if (rhs instanceof MethodCallExpression){
                    //rhs.objectExpression?.text == "gio.TIO") {  // GIO.pure(...) ou GIO.println(...)
                    binds << new ExpressionItem(e, ExpressionType.Binary)
                } else if (e instanceof DeclarationExpression){
                    declarations << e
                }
            }  else if (e instanceof MethodCallExpression && e.methodAsString == 'yield') {
                def args = e.arguments
                if (args instanceof ArgumentListExpression && !args.expressions.isEmpty()) {
                    yieldExpr = args.expressions[0] as ClosureExpression
                }
            } else if (e instanceof MethodCallExpression && e.methodAsString == 'guard') {
                def args = e.arguments
                if (args instanceof ArgumentListExpression && !args.expressions.isEmpty()) {
                    binds << new ExpressionItem(args.expressions[0] as ClosureExpression, ExpressionType.Guard)
                }
            } else if (e instanceof  MethodCallExpression){
                //&& e.objectExpression?.text == "gio.TIO"){
                binds << new ExpressionItem(e, ExpressionType.Method)
            }
        }

        if (!yieldExpr || binds.isEmpty()) return null

        // 2) Cria expressão inicial: pure(yieldExpr) ou map sobre o último bind
        //Expression nested = yieldExpr
        mainClosureExpr.variableScope = mainClosureExpr.variableScope ?: new VariableScope()

        // trata declaração de variáveis (não IO) dentro da closure
        def expressions = declarations
            .collect { new ExpressionStatement(it) }


        def variables = expressions
            .collect {
                (it.expression as DeclarationExpression).variableExpression.accessedVariable
            }
        def nested = creteNested(mainClosureExpr, binds, yieldExpr, variables, [])

        // 4) Substitui corpo da closure

        mainClosureExpr.code = new BlockStatement(
            [*expressions, new ExpressionStatement(nested)],
            mainClosureExpr.variableScope
        )


        //printAst mainClosureExpr

        mainClosureExpr

   }

    Expression creteNested(ClosureExpression mainClosureExpr,
                           List<ExpressionItem> itr,
                           ClosureExpression yieldExpr,
                           List<Variable> variables,
                           List<Parameter> params){

        def exprItem = itr.head()
        List<ExpressionItem> guards = []

        for(def it in itr.tail()){
            if(it.guard) guards << it
            else break
        }

        def tail = itr.tail().dropWhile { it.guard}


        def decl = exprItem.expr
        String varName = ""
        MethodCallExpression rhs = null

        switch(exprItem.type){
            case ExpressionType.Method:
                varName = "tmp_${params.size()}"
                rhs = decl as MethodCallExpression
                break
            case ExpressionType.Binary:
                def binaryExpr = decl as BinaryExpression
                varName = (binaryExpr.leftExpression as VariableExpression).name
                rhs = binaryExpr.rightExpression as MethodCallExpression
                break
            default:
                throw new Exception("unexpected type $exprItem.type")
        }

        // Tipo da closure
        ClassNode type = rhs?.getType() ?: ClassHelper.OBJECT_TYPE
        Parameter p = new Parameter(type, varName)
        p.setClosureSharedVariable(true)

        VariableScope childScope = new VariableScope(mainClosureExpr.variableScope)

        def hasNext = !tail.empty

        copyVariablesToChildScope(childScope, mainClosureExpr, variables, params)

        BlockStatement closureBlock

        if (hasNext) {
            def nested = creteNested(mainClosureExpr, tail, yieldExpr, variables, params + p)
            closureBlock = new BlockStatement([new ExpressionStatement(nested)], childScope)
        } else {
            // no has next, create yield expression
            closureBlock = new BlockStatement((yieldExpr.code as BlockStatement).statements, childScope)
        }

        // Closure interna { varName -> nested }
        ClosureExpression innerClosure = new ClosureExpression(
            [p] as Parameter[],
            closureBlock
        )

        innerClosure.variableScope = childScope

        // Cria MethodCallExpression usando tmpVar como alvo
        MethodCallExpression chainedCall = new MethodCallExpression(
            rewriteVariables(mainClosureExpr, rhs, [:], variables, params),
            hasNext ? "flatMap" : "map",
            new ArgumentListExpression(innerClosure)
        )


        if(guards){
            // cria todos os guards subsequentes (até que encontre um IO) de uma só vez
            for(def guard in guards) {
                def guardExpr = guard.expr as ClosureExpression
                def guardChildScope = new VariableScope(mainClosureExpr.variableScope)
                copyVariablesToChildScope(guardChildScope, mainClosureExpr, variables, params)
                def guardClosureExpr = new ClosureExpression(
                    [] as Parameter[],
                    new BlockStatement((guardExpr.code as BlockStatement).statements, guardChildScope)
                )
                chainedCall = new MethodCallExpression(
                    chainedCall,
                    "filter",
                    new ArgumentListExpression(guardClosureExpr)
                )
                guardClosureExpr.variableScope = guardChildScope
            }
        }

        // Atualiza nested para a próxima iteração (sempre uma Expression!)
        chainedCall
    }

    void copyVariablesToChildScope(VariableScope childScope, ClosureExpression mainClosure, List<Variable> variables, List<Parameter> params){
        // copias as referencias de variáveis capturadas pela closure
        mainClosure
            .variableScope
            .referencedLocalVariablesIterator
            .each {
                childScope.putReferencedLocalVariable(it)
            }

        params.each {
            childScope.putReferencedLocalVariable(it)
        }

        variables.each {
            it.setClosureSharedVariable(true)
            childScope.putReferencedLocalVariable(it)
        }
    }

    Expression rewriteVariables(ClosureExpression mainClosureExpr,
                                Expression expr,
                                Map<String, VariableExpression> env,
                                List<Variable> variables,
                                List<Parameter> params) {
        if (expr instanceof VariableExpression) {
            //if (env.containsKey(expr.name)) {
            //    return env[expr.name]
            //}
            return expr
        }

        if (expr instanceof BinaryExpression) {
            return new BinaryExpression(
                rewriteVariables(mainClosureExpr, expr.leftExpression, env, variables, params),
                expr.operation,
                rewriteVariables(mainClosureExpr, expr.rightExpression, env, variables, params)
            )
        }

        if (expr instanceof MethodCallExpression) {
            return new MethodCallExpression(
                rewriteVariables(mainClosureExpr, expr.objectExpression, env, variables, params),
                expr.method,
                rewriteVariables(mainClosureExpr, expr.arguments, env, variables, params)
            )
        }

        if (expr instanceof ArgumentListExpression) {
            return new ArgumentListExpression(
                expr.expressions.collect { rewriteVariables(mainClosureExpr, it, env, variables, params) }
            )
        }

        if (expr instanceof ClosureExpression) {
            // percorre o body da closure
            def childScope = new VariableScope(mainClosureExpr.variableScope)
            expr.variableScope = childScope
            copyVariablesToChildScope(childScope, mainClosureExpr, variables, params)
            BlockStatement body = expr.code as BlockStatement
            body.statements.each { stmt ->
                if (stmt instanceof ExpressionStatement) {
                    stmt.expression = rewriteVariables(mainClosureExpr, stmt.expression, env, variables, params)
                }
            }
            return expr
        }

        if (expr instanceof TernaryExpression) {
            return new TernaryExpression(
                rewriteVariables(mainClosureExpr, expr.booleanExpression, env, variables, params) as BooleanExpression,
                rewriteVariables(mainClosureExpr, expr.trueExpression, env, variables, params),
                rewriteVariables(mainClosureExpr, expr.falseExpression, env, variables, params)
            )
        }

        // outros tipos de Expression que você espera...
        return expr
    }

    void printAst(ASTNode node, int level = 0) {
        if (node == null) return
        String indent = "  " * level
        String type = node.getClass().simpleName

        switch (node) {
            case MethodCallExpression:
                def m = node as MethodCallExpression
                println "${indent}MethodCall: ${m.methodAsString}"
                println "${indent}  Target:"
                printAst(m.objectExpression, level + 2)
                printAst(m.arguments, level + 1)
                break

            case StaticMethodCallExpression:
                def m = node as StaticMethodCallExpression
                println "${indent}StaticCall: ${m.ownerType.name}.${m.method}"
                printAst(m.arguments, level + 1)
                break

            case ClosureExpression:
                def c = node as ClosureExpression
                println "${indent}Closure"
                println "${indent }Params: ${c.parameters.collect { it }.join(", ")}"
                printAst(c.code, level + 1)
                break

            case BlockStatement:
                def b = node as BlockStatement
                println "${indent}Block"
                b.statements.each { printAst(it, level + 1) }
                break

            case ExpressionStatement:
                def e = node as ExpressionStatement
                println "${indent}ExprStmt"
                printAst(e.expression, level + 1)
                break

            case DeclarationExpression:
                def d = node as DeclarationExpression
                println "${indent}Decl: ${d.leftExpression}"
                printAst(d.rightExpression, level + 1)
                break

            case BinaryExpression:
                def b = node as BinaryExpression
                println "${indent}Binary: ${b.leftExpression} ${b.operation.text} ${b.rightExpression}"
                break

            case ConstantExpression:
                println "${indent}Const: ${(node as ConstantExpression).text}"
                break

            case VariableExpression:
                println "${indent}Var: ${(node as VariableExpression).name}"
                break

            case ArgumentListExpression:
                def a = node as ArgumentListExpression
                println "${indent}Args"
                a.expressions.each { printAst(it, level + 1) }
                break

            case ReturnStatement:
                def r = node as ReturnStatement
                println "${indent}Return"
                printAst(r.expression, level + 1)
                break

            default:
                println "${indent}${type}: $node"
        }
    }

}
