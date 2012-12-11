import org.codehaus.groovy.ast.expr.ConstantExpression

/**
 * A test DSL for a type checking extension which supports the BeanBuilder.
 *
 * Step 1: Basic extension
 * Step 2: Handle the "ref" method of bean builder
 * 
 * @author Cedric Champeau
 */

// tests if a method node is the "beans" method from the "BeanBuilder" class
def isBeanBuilder(method) {
    method?.name == 'beans' && method?.declaringClass?.nameWithoutPackage == 'BeanBuilder'
}

// throws an error if a bean isn't found in the builder context
def checkBeanExists(beanName, source) {
    if (!currentScope.beans[beanName]) {
        addStaticTypeError "Referenced bean ($beanName) does not exist", source
    }
}

onMethodSelection { expr, method ->
    if (isBeanBuilder(method)) {
        newScope {
            beans = [:] // map bean name -> bean type
            secondPassChecks = [] // list of closures to be executed when we exit the beans { ... } method
        }
    }
}

afterMethodCall { mc ->
    if (isBeanBuilder(getTargetMethod(mc))) {
        scopeExit {
            // exiting the "beans { ... }" scope, call the additional checks
            secondPassChecks*.call()
        }
    }
}

unresolvedVariable { var ->
    if (isDynamic(var)) {
        currentScope.secondPassChecks << { checkBeanExists(var.name, var) }
    }
}

methodNotFound { receiver, name, argumentList, argTypes, call ->
    if (firstArgTypesMatches(argTypes, Class)) { // beanName(BeanType)
        def beanType = argTypes[0].genericsTypes[0].type
        currentScope.beans[name] = beanType
        if (argTypeMatches(argTypes, 1, Closure)) { // beanName(BeanType) { ... }
            delegatesTo beanType,Closure.DELEGATE_ONLY
        }

        return newMethod(name, beanType) // return keyword optional
    }
}

// handle the "ref" missing method
methodNotFound { receiver, name, argumentList, argTypes, call ->
    if ('ref'==name && argTypesMatches(argTypes,String) && (getArguments(call)[0] instanceof ConstantExpression)) {
        // ref('foo')
        return newMethod("refTo$name") {
            def returnType = OBJECT_TYPE
            // the return type of the method can be inferred from the LHS of an assignment
            if (enclosingBinaryExpression && isAssignment(enclosingBinaryExpression.operation.type)) {
                returnType = getType(enclosingBinaryExpression.leftExpression)
            }

            returnType
        }
    }
}