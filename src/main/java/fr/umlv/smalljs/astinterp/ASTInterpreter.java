package fr.umlv.smalljs.astinterp;

import fr.umlv.smalljs.ast.Expr;
import fr.umlv.smalljs.ast.Expr.Block;
import fr.umlv.smalljs.ast.Expr.FieldAccess;
import fr.umlv.smalljs.ast.Expr.FieldAssignment;
import fr.umlv.smalljs.ast.Expr.Fun;
import fr.umlv.smalljs.ast.Expr.FunCall;
import fr.umlv.smalljs.ast.Expr.If;
import fr.umlv.smalljs.ast.Expr.Literal;
import fr.umlv.smalljs.ast.Expr.LocalVarAccess;
import fr.umlv.smalljs.ast.Expr.LocalVarAssignment;
import fr.umlv.smalljs.ast.Expr.MethodCall;
import fr.umlv.smalljs.ast.Expr.New;
import fr.umlv.smalljs.ast.Expr.Return;
import fr.umlv.smalljs.ast.Script;
import fr.umlv.smalljs.rt.Failure;
import fr.umlv.smalljs.rt.JSObject;
import fr.umlv.smalljs.rt.JSObject.Invoker;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static fr.umlv.smalljs.rt.JSObject.UNDEFINED;
import static java.util.stream.Collectors.joining;

public class ASTInterpreter {
  private static JSObject asJSObject(Object value, int lineNumber) {
    if (!(value instanceof JSObject jsObject)) {
      throw new Failure("at line " + lineNumber + ", type error " + value + " is not a JSObject");
    }
    return jsObject;
  }

  static Object visit(Expr expression, JSObject env) {
    return switch (expression) {
      case Block(List<Expr> instrs, int lineNumber) -> {
        instrs.forEach(expr -> visit(expr, env));
        yield UNDEFINED;
      }
      case Literal<?>(Object value, int lineNumber) -> value;
      case FunCall(Expr qualifier, List<Expr> args, int lineNumber) -> {
        var expectedFunctionName = visit(qualifier, env);
          if (!(expectedFunctionName instanceof JSObject jsObject)) {
            throw new Failure("not a function " + expectedFunctionName + " at line " + lineNumber);
          }
          var interpretedArgs = args.stream()
                  .map(expr -> visit(expr, env))
                  .toArray();
          yield jsObject.invoke(UNDEFINED, interpretedArgs);
      }
      case LocalVarAccess(String name, int lineNumber) -> env.lookup(name);
      case LocalVarAssignment(String name, Expr expr, boolean declaration, int lineNumber) -> {
        var localVarCurrentValue = env.lookup(name);
        if (declaration && localVarCurrentValue != UNDEFINED) {
          throw new Failure("declaration to already defined variable " + name + " at line " + lineNumber);
        }
        if (!declaration && localVarCurrentValue == UNDEFINED) {
          throw new Failure("assignation to undefined variable " + name + " at line " + lineNumber);
        }
        var variableValue = visit(expr, env);
        env.register(name, variableValue);
        yield UNDEFINED;
      }
      case Fun(Optional<String> optName, List<String> parameters, Block body, int lineNumber) -> {
        //Invoker invoker = new Invoker() {
        //  @Override
        //  public Object invoke(JSObject self, Object receiver, Object... args) {
        //    // check the arguments length
        //    // create a new environment
        //    // add this and all the parameters
        //    // visit the body
        //  }
        //};
        var functionName = optName.orElse("lambda");
        Invoker invoker = (self, receiver, args) -> {
          if (args.length != parameters.size()) {
            throw new Failure("expected " + parameters.size() + " arguments, " + args.length + " arguments given to function at line " + lineNumber);
          }
          var localEnv = JSObject.newEnv(env);
          localEnv.register("this", receiver);
          for (int i = 0; i < args.length; i++) {
            localEnv.register(parameters.get(i), args[i]);
          }
          try {
            return visit(body, localEnv);
          } catch (ReturnError returnError) {
            return returnError.getValue();
          }
        };
        var function = JSObject.newFunction(functionName, invoker);
        optName.ifPresent(name -> env.register(name, function));
        yield function;
      }
      case Return(Expr expr, int lineNumber) -> {
        throw new ReturnError(visit(expr, env));
      }
      case If(Expr condition, Block trueBlock, Block falseBlock, int lineNumber) -> {
        var conditionValue = visit(condition, env);
        if (conditionValue instanceof Integer value && value == 0) {
          yield visit(falseBlock, env);
        }
        yield visit(trueBlock, env);
      }
      case New(Map<String, Expr> initMap, int lineNumber) -> {
				throw new UnsupportedOperationException("TODO New");
      }
      case FieldAccess(Expr receiver, String name, int lineNumber) -> {
        throw new UnsupportedOperationException("TODO FieldAccess");
      }
      case FieldAssignment(Expr receiver, String name, Expr expr, int lineNumber) -> {
        throw new UnsupportedOperationException("TODO FieldAssignment");
      }
      case MethodCall(Expr receiver, String name, List<Expr> args, int lineNumber) -> {
        throw new UnsupportedOperationException("TODO MethodCall");
      }
    };
  }

  @SuppressWarnings("unchecked")
  public static void interpret(Script script, PrintStream outStream) {
    JSObject globalEnv = JSObject.newEnv(null);
    Block body = script.body();
    globalEnv.register("global", globalEnv);
    globalEnv.register("print", JSObject.newFunction("print", (self, receiver, args) -> {
      System.err.println("print called with " + Arrays.toString(args));
      outStream.println(Arrays.stream(args).map(Object::toString).collect(joining(" ")));
      return UNDEFINED;
    }));
    globalEnv.register("+", JSObject.newFunction("+", (self, receiver, args) -> (Integer) args[0] + (Integer) args[1]));
    globalEnv.register("-", JSObject.newFunction("-", (self, receiver, args) -> (Integer) args[0] - (Integer) args[1]));
    globalEnv.register("/", JSObject.newFunction("/", (self, receiver, args) -> (Integer) args[0] / (Integer) args[1]));
    globalEnv.register("*", JSObject.newFunction("*", (self, receiver, args) -> (Integer) args[0] * (Integer) args[1]));
    globalEnv.register("%", JSObject.newFunction("%", (self, receiver, args) -> (Integer) args[0] % (Integer) args[1]));

    globalEnv.register("==", JSObject.newFunction("==", (self, receiver, args) -> args[0].equals(args[1]) ? 1 : 0));
    globalEnv.register("!=", JSObject.newFunction("!=", (self, receiver, args) -> !args[0].equals(args[1]) ? 1 : 0));
    globalEnv.register("<", JSObject.newFunction("<", (self, receiver, args) -> (((Comparable<Object>) args[0]).compareTo(args[1]) < 0) ? 1 : 0));
    globalEnv.register("<=", JSObject.newFunction("<=", (self, receiver, args) -> (((Comparable<Object>) args[0]).compareTo(args[1]) <= 0) ? 1 : 0));
    globalEnv.register(">", JSObject.newFunction(">", (self, receiver, args) -> (((Comparable<Object>) args[0]).compareTo(args[1]) > 0) ? 1 : 0));
    globalEnv.register(">=", JSObject.newFunction(">=", (self, receiver, args) -> (((Comparable<Object>) args[0]).compareTo(args[1]) >= 0) ? 1 : 0));
    visit(body, globalEnv);
  }
}

