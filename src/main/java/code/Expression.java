/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package code;

import java.util.ArrayList;
import java.util.List;

import cl.MDLConfig;
import parser.Tokenizer;

public class Expression {

    public static final int TRUE = -1;
    public static final int FALSE = 0;

    public static final int EXPRESSION_REGISTER_OR_FLAG = 0;
    public static final int EXPRESSION_INTEGER_CONSTANT = 1;
    public static final int EXPRESSION_STRING_CONSTANT = 2;
    public static final int EXPRESSION_DOUBLE_CONSTANT = 28;
    public static final int EXPRESSION_SYMBOL = 3;
    public static final int EXPRESSION_SIGN_CHANGE = 4;
    public static final int EXPRESSION_PARENTHESIS = 5;
    public static final int EXPRESSION_SUM = 6;
    public static final int EXPRESSION_SUB = 7;
    public static final int EXPRESSION_MUL = 8;
    public static final int EXPRESSION_DIV = 9;
    public static final int EXPRESSION_MOD = 10;
    public static final int EXPRESSION_OR = 11;
    public static final int EXPRESSION_AND = 12;
    public static final int EXPRESSION_EQUAL = 13;
    public static final int EXPRESSION_LOWERTHAN = 14;
    public static final int EXPRESSION_GREATERTHAN = 15;
    public static final int EXPRESSION_LEQTHAN = 16;
    public static final int EXPRESSION_GEQTHAN = 17;
    public static final int EXPRESSION_DIFF = 18;
    public static final int EXPRESSION_TERNARY_IF = 19;
    public static final int EXPRESSION_LSHIFT = 20;
    public static final int EXPRESSION_RSHIFT = 21;
    public static final int EXPRESSION_BITOR = 22;
    public static final int EXPRESSION_BITAND = 23;
    public static final int EXPRESSION_BITNEGATION = 24;
    public static final int EXPRESSION_BITXOR = 25;
    public static final int EXPRESSION_LOGICAL_NEGATION = 26;
    public static final int EXPRESSION_DIALECT_FUNCTION = 27;
    public static final int EXPRESSION_PLUS_SIGN = 29;  // just something like: +1, or +(3-5)
    

    // indexed by the numbers above:
    // Precedences obtained from the ones used by c++: https://en.cppreference.com/w/cpp/language/operator_precedence
    public static final int OPERATOR_PRECEDENCE[] = {
        -1, -1, -1, -1, -1,
        -1, 6, 6, 5, 5,
        5, 13, 11, 10, 9,
        9, 9, 9, 10, 16,
        7, 7, 11, 13, 3,
        12, 3, -1, -1};

    MDLConfig config;
    public int type;
    public int integerConstant;
    public boolean renderAs8bitHex = false; // only applicable to intergerConstant
    public boolean renderAs16bitHex = false; // only applicable to intergerConstant
    public double doubleConstant;
    public String stringConstant;
    public String symbolName;
    public String registerOrFlagName;
    public String parenthesis;  // whether the parenthesis is "(" or "["
    public String dialectFunction;
    public List<Expression> args = null;

    private Expression(int a_type, MDLConfig a_config) {
        type = a_type;
        config = a_config;
    }

    
    public Integer evaluateToInteger(SourceStatement s, CodeBase code, boolean silent) {
        return (Integer)evaluateInternal(s, code, silent, new ArrayList<>());
    }


    public Integer evaluateToIntegerInternal(SourceStatement s, CodeBase code, boolean silent, List<String> variableStack) {
        return (Integer)evaluateInternal(s, code, silent, variableStack);
    }
    

    public Object evaluate(SourceStatement s, CodeBase code, boolean silent) {
        return evaluateInternal(s, code, silent, new ArrayList<>());
    }
    
    
    public Object evaluateInternal(SourceStatement s, CodeBase code, boolean silent, List<String> variableStack) {
        switch (type) {
            case EXPRESSION_INTEGER_CONSTANT:
                return integerConstant;

            case EXPRESSION_DOUBLE_CONSTANT:
                return doubleConstant;
                
            case EXPRESSION_STRING_CONSTANT:
                if (stringConstant.length() == 1) {
                    return (int) stringConstant.charAt(0);
                } else {
                    return stringConstant;
                }

            case EXPRESSION_SYMBOL: {
                if (symbolName.equals(CodeBase.CURRENT_ADDRESS)) {
                    if (s != null) {
                        return s.getAddressInternal(code, true, variableStack);
                    } else {
                        return null;
                    }
                }
                Object value = code.getSymbolValueInternal(symbolName, silent, variableStack);
                if (value == null) {
                    if (!silent) {
                        config.error("Undefined symbol " + symbolName);
                    }
                    return null;
                }
                return value;
            }

            case EXPRESSION_SIGN_CHANGE: {
                Object v = args.get(0).evaluateInternal(s, code, silent, variableStack);
                if (v == null) {
                    return null;
                } else if (v instanceof Integer) {
                    return -(Integer)v;
                } else if (v instanceof Double) {
                    return -(Double)v;
                } else {
                    return null;
                }
            }

            case EXPRESSION_PARENTHESIS: {
                Object v = args.get(0).evaluateInternal(s, code, silent, variableStack);
                if (v == null) {
                    return null;
                }
                return v;
            }

            case EXPRESSION_SUM: {
                Number accum = 0;
                boolean turnToDouble = false;
                for (Expression arg : args) {
                    Object v = arg.evaluateInternal(s, code, silent, variableStack);
                    if (v == null) {
                        return null;
                    } else if (v instanceof Double) {
                        turnToDouble = true;
                        accum = accum.doubleValue() + (Double)v;
                    } else if (v instanceof Integer) {
                        if (turnToDouble) {
                            accum = accum.doubleValue() + (Integer)v;
                        } else {
                            accum = accum.intValue() + (Integer)v;
                        }
                    } else {
                        return null;
                    }
                }
                return accum;
            }

            case EXPRESSION_SUB: {
                if (args.size() != 2) {
                    return null;
                }
                // - special case for when these are labels, like: label1-label2,
                // and it is not possible to assign an absolute value to the labels,
                // but it is possible to know their difference:
                if (args.get(0).type == Expression.EXPRESSION_SYMBOL &&
                    args.get(1).type == Expression.EXPRESSION_SYMBOL) {
                    SourceConstant c1 = code.getSymbol(args.get(0).symbolName);
                    SourceConstant c2 = code.getSymbol(args.get(1).symbolName);
                    if (c1 != null && c2 != null && c1.exp != null && c2.exp != null &&
                        c1.exp.type == Expression.EXPRESSION_SYMBOL &&
                        c2.exp.type == Expression.EXPRESSION_SYMBOL &&
                        c1.exp.symbolName.equals(CodeBase.CURRENT_ADDRESS) &&
                        c2.exp.symbolName.equals(CodeBase.CURRENT_ADDRESS)) {
                        SourceStatement d1 = c1.definingStatement;
                        SourceStatement d2 = c2.definingStatement;
                        if (d1 != null && d2 != null && d1.source == d2.source) {
                            int idx1 = d1.source.getStatements().indexOf(d1);
                            int idx2 = d1.source.getStatements().indexOf(d2);
                            if (idx1 >= 0 && idx2 >= 0 && idx1 >= idx2) {
                                Integer diff = 0;
                                for(int i = idx2; i<idx1;i++) {
                                    Integer size = d1.source.getStatements().get(i).sizeInBytesInternal(code, true, true, true, variableStack);
                                    if (size == null) {
                                        diff = null;
                                        break;
                                    }
                                    diff += size;
                                }
                                if (diff != null) {
                                    return diff;
                                }
                            }
                        }
                    }
                }
                
                Object v1 = args.get(0).evaluateInternal(s, code, silent, variableStack);
                Object v2 = args.get(1).evaluateInternal(s, code, silent, variableStack);
                if (v1 == null || v2 == null) {
                    return null;
                }
                if ((v1 instanceof Double)) {
                    if ((v2 instanceof Double)) {
                        return (Double)v1 - (Double)v2;
                    } else if ((v2 instanceof Integer)) {
                        return (Double)v1 - (Integer)v2;
                    }
                } else if ((v1 instanceof Integer)) {
                    if ((v2 instanceof Double)) {
                        return (Integer)v1 - (Double)v2;
                    } else if ((v2 instanceof Integer)) {
                        return (Integer)v1 - (Integer)v2;
                    }
                }
                return null;
            }

            case EXPRESSION_MUL: {
                Number accum = 1;
                boolean turnToDouble = false;
                for (Expression arg : args) {
                    Object v = arg.evaluateInternal(s, code, silent, variableStack);
                    if (v == null) {
                        return null;
                    } else if (v instanceof Double) {
                        turnToDouble = true;
                        accum = accum.doubleValue() * (Double)v;
                    } else if (v instanceof Integer) {
                        if (turnToDouble) {
                            accum = accum.doubleValue() * (Integer)v;
                        } else {
                            accum = accum.intValue() * (Integer)v;
                        }
                    } else {
                        return null;
                    }
                }
                return accum;
            }

            case EXPRESSION_DIV: {
                if (args.size() != 2) {
                    return null;
                }
                Object v1 = args.get(0).evaluateInternal(s, code, silent, variableStack);
                Object v2 = args.get(1).evaluateInternal(s, code, silent, variableStack);
                if (v1 == null || v2 == null) {
                    return null;
                }
                if ((v1 instanceof Double)) {
                    if ((v2 instanceof Double)) {
                        return (Double)v1 / (Double)v2;
                    } else if ((v2 instanceof Integer)) {
                        return (Double)v1 / (Integer)v2;
                    }
                } else if ((v1 instanceof Integer)) {
                    if ((v2 instanceof Double)) {
                        return (Integer)v1 / (Double)v2;
                    } else if ((v2 instanceof Integer)) {
                        return (Integer)v1 / (Integer)v2;
                    }
                }
            }

            case EXPRESSION_MOD: {
                if (args.size() != 2) {
                    return null;
                }
                Object v1 = args.get(0).evaluateInternal(s, code, silent, variableStack);
                Object v2 = args.get(1).evaluateInternal(s, code, silent, variableStack);
                if (v1 == null || v2 == null) {
                    return null;
                }
                if ((v1 instanceof Double)) {
                    if ((v2 instanceof Double)) {
                        return (Double)v1 % (Double)v2;
                    } else if ((v2 instanceof Integer)) {
                        return (Double)v1 % (Integer)v2;
                    }
                } else if ((v1 instanceof Integer)) {
                    if ((v2 instanceof Double)) {
                        return (Integer)v1 % (Double)v2;
                    } else if ((v2 instanceof Integer)) {
                        return (Integer)v1 % (Integer)v2;
                    }
                }
            }

            case EXPRESSION_OR: {
                if (args.size() != 2) {
                    return null;
                }
                Integer v1 = args.get(0).evaluateToInteger(s, code, silent);
                Integer v2 = args.get(1).evaluateToInteger(s, code, silent);
                if (v1 == null || v2 == null) {
                    return null;
                }
                boolean b1 = v1 != 0;
                boolean b2 = v2 != 0;
                return b1 || b2 ? TRUE : FALSE;
            }

            case EXPRESSION_AND: {
                if (args.size() != 2) {
                    return null;
                }
                Integer v1 = args.get(0).evaluateToInteger(s, code, silent);
                Integer v2 = args.get(1).evaluateToInteger(s, code, silent);
                if (v1 == null || v2 == null) {
                    return null;
                }
                boolean b1 = v1 != 0;
                boolean b2 = v2 != 0;
                return b1 && b2 ? TRUE : FALSE;
            }

            case EXPRESSION_EQUAL: {
                if (args.size() != 2) {
                    return null;
                }
                Object v1 = args.get(0).evaluateInternal(s, code, silent, variableStack);
                Object v2 = args.get(1).evaluateInternal(s, code, silent, variableStack);
                if (v1 == null || v2 == null) {
                    return null;
                }
                if (v1.equals(v2)) {
                    return TRUE;
                }
                return FALSE;
            }

            case EXPRESSION_LOWERTHAN: {
                if (args.size() != 2) {
                    return null;
                }
                Object v1 = args.get(0).evaluateInternal(s, code, silent, variableStack);
                Object v2 = args.get(1).evaluateInternal(s, code, silent, variableStack);
                if (v1 == null || v2 == null) {
                    return null;
                }
                if ((v1 instanceof Double)) {
                    if ((v2 instanceof Double)) {
                        return (Double)v1 < (Double)v2 ? TRUE:FALSE;
                    } else if ((v2 instanceof Integer)) {
                        return (Double)v1 < (Integer)v2 ? TRUE:FALSE;
                    }
                } else if ((v1 instanceof Integer)) {
                    if ((v2 instanceof Double)) {
                        return (Integer)v1 < (Double)v2 ? TRUE:FALSE;
                    } else if ((v2 instanceof Integer)) {
                        return (Integer)v1 < (Integer)v2 ? TRUE:FALSE;
                    }
                }
            }

            case EXPRESSION_GREATERTHAN: {
                if (args.size() != 2) {
                    return null;
                }
                Object v1 = args.get(0).evaluateInternal(s, code, silent, variableStack);
                Object v2 = args.get(1).evaluateInternal(s, code, silent, variableStack);
                if (v1 == null || v2 == null) {
                    return null;
                }
                if ((v1 instanceof Double)) {
                    if ((v2 instanceof Double)) {
                        return (Double)v1 > (Double)v2 ? TRUE:FALSE;
                    } else if ((v2 instanceof Integer)) {
                        return (Double)v1 > (Integer)v2 ? TRUE:FALSE;
                    }
                } else if ((v1 instanceof Integer)) {
                    if ((v2 instanceof Double)) {
                        return (Integer)v1 > (Double)v2 ? TRUE:FALSE;
                    } else if ((v2 instanceof Integer)) {
                        return (Integer)v1 > (Integer)v2 ? TRUE:FALSE;
                    }
                }            }

            case EXPRESSION_LEQTHAN: {
                if (args.size() != 2) {
                    return null;
                }
                Object v1 = args.get(0).evaluateInternal(s, code, silent, variableStack);
                Object v2 = args.get(1).evaluateInternal(s, code, silent, variableStack);
                if (v1 == null || v2 == null) {
                    return null;
                }
                if ((v1 instanceof Double)) {
                    if ((v2 instanceof Double)) {
                        return (Double)v1 <= (Double)v2 ? TRUE:FALSE;
                    } else if ((v2 instanceof Integer)) {
                        return (Double)v1 <= (Integer)v2 ? TRUE:FALSE;
                    }
                } else if ((v1 instanceof Integer)) {
                    if ((v2 instanceof Double)) {
                        return (Integer)v1 <= (Double)v2 ? TRUE:FALSE;
                    } else if ((v2 instanceof Integer)) {
                        return (Integer)v1 <= (Integer)v2 ? TRUE:FALSE;
                    }
                }            
            }

            case EXPRESSION_GEQTHAN: {
                if (args.size() != 2) {
                    return null;
                }
                Object v1 = args.get(0).evaluateInternal(s, code, silent, variableStack);
                Object v2 = args.get(1).evaluateInternal(s, code, silent, variableStack);
                if (v1 == null || v2 == null) {
                    return null;
                }
                if ((v1 instanceof Double)) {
                    if ((v2 instanceof Double)) {
                        return (Double)v1 >= (Double)v2 ? TRUE:FALSE;
                    } else if ((v2 instanceof Integer)) {
                        return (Double)v1 >= (Integer)v2 ? TRUE:FALSE;
                    }
                } else if ((v1 instanceof Integer)) {
                    if ((v2 instanceof Double)) {
                        return (Integer)v1 >= (Double)v2 ? TRUE:FALSE;
                    } else if ((v2 instanceof Integer)) {
                        return (Integer)v1 >= (Integer)v2 ? TRUE:FALSE;
                    }
                }            
            }

            case EXPRESSION_DIFF: {
                if (args.size() != 2) {
                    return null;
                }
                Object v1 = args.get(0).evaluateInternal(s, code, silent, variableStack);
                Object v2 = args.get(1).evaluateInternal(s, code, silent, variableStack);
                if (v1 == null || v2 == null) {
                    return null;
                }
                if (v1.equals(v2)) {
                    return FALSE;
                }
                return TRUE;
            }

            case EXPRESSION_TERNARY_IF: {
                Integer cond = args.get(0).evaluateToInteger(s, code, silent);
                if (cond == null) {
                    return null;
                }
                if (cond != FALSE) {
                    return args.get(1).evaluateToInteger(s, code, silent);
                } else {
                    return args.get(2).evaluateToInteger(s, code, silent);
                }
            }

            case EXPRESSION_LSHIFT: {
                if (args.size() != 2) {
                    return null;
                }
                Integer v1 = args.get(0).evaluateToInteger(s, code, silent);
                Integer v2 = args.get(1).evaluateToInteger(s, code, silent);
                if (v1 == null || v2 == null) {
                    return null;
                }
                return v1 << v2;
            }

            case EXPRESSION_RSHIFT: {
                if (args.size() != 2) {
                    return null;
                }
                Integer v1 = args.get(0).evaluateToInteger(s, code, silent);
                Integer v2 = args.get(1).evaluateToInteger(s, code, silent);
                if (v1 == null || v2 == null) {
                    return null;
                }
                return v1 >> v2;
            }

            case EXPRESSION_BITOR: {
                if (args.size() != 2) {
                    return null;
                }
                Integer v1 = args.get(0).evaluateToInteger(s, code, silent);
                Integer v2 = args.get(1).evaluateToInteger(s, code, silent);
                if (v1 == null || v2 == null) {
                    return null;
                }
                return v1 | v2;
            }

            case EXPRESSION_BITAND: {
                if (args.size() != 2) {
                    return null;
                }
                Integer v1 = args.get(0).evaluateToInteger(s, code, silent);
                Integer v2 = args.get(1).evaluateToInteger(s, code, silent);
                if (v1 == null || v2 == null) {
                    return null;
                }
                return v1 & v2;
            }
            case EXPRESSION_BITNEGATION: {
                Integer v = args.get(0).evaluateToInteger(s, code, silent);
                if (v == null) {
                    return null;
                }
                return ~v;
            }
            case EXPRESSION_BITXOR: {
                if (args.size() != 2) {
                    return null;
                }
                Integer v1 = args.get(0).evaluateToInteger(s, code, silent);
                Integer v2 = args.get(1).evaluateToInteger(s, code, silent);
                if (v1 == null || v2 == null) {
                    return null;
                }
                return v1 ^ v2;
            }
            case EXPRESSION_LOGICAL_NEGATION: {
                Integer v = args.get(0).evaluateToInteger(s, code, silent);
                if (v == null) {
                    return null;
                }
                return v == FALSE ? TRUE : FALSE;
            }
            case EXPRESSION_DIALECT_FUNCTION: {
                return config.dialectParser.evaluateExpression(dialectFunction, args, s, code, silent);
            }
            case EXPRESSION_PLUS_SIGN:
                return args.get(0).evaluateInternal(s, code, silent, variableStack);

        }

        return null;
    }

    
    @Override
    public String toString() {
        return toStringInternal(false);
    }    
    

    public String toStringInternal(boolean splitSpecialCharactersInStrings) {
        switch (type) {
            case EXPRESSION_REGISTER_OR_FLAG:
                if (config.output_opsInLowerCase) {
                    return registerOrFlagName.toLowerCase();
                } else if (config.output_opsInUpperCase) {
                    return registerOrFlagName.toUpperCase();
                } else {
                    return registerOrFlagName;
                }
            case EXPRESSION_INTEGER_CONSTANT:
                if (renderAs16bitHex && integerConstant >= 0 && integerConstant <= 0xffff) {
                    return Tokenizer.toHexWord(integerConstant, config.hexStyle);
                } else if (renderAs8bitHex && integerConstant >= 0 && integerConstant <= 0xff) {
                    return Tokenizer.toHexByte(integerConstant, config.hexStyle);
                } else {
                    return "" + integerConstant;
                }
            case EXPRESSION_DOUBLE_CONSTANT:
                return "" + doubleConstant;
            case EXPRESSION_STRING_CONSTANT:
            {
                if (splitSpecialCharactersInStrings) {
                    String tmp = "";
                    boolean first = true;
                    boolean insideQuotes = false;
                    for(int i = 0;i<stringConstant.length();i++) {
                        int c = stringConstant.charAt(i);
                        if (c<32 || c=='\\' || c=='\"') {
                            if (insideQuotes) {
                                tmp += "\"";
                                insideQuotes = false;
                            }
                            tmp += (first ? "":", ") + c;
                        } else {
                            if (insideQuotes) {
                                tmp += stringConstant.substring(i,i+1);
                            } else {
                                tmp += (first ? "":", ") + "\"" + stringConstant.substring(i,i+1);
                                insideQuotes = true;
                            }
                        }
                        first = false;
                    }
                    if (insideQuotes) tmp += "\"";
                    return tmp;
                } else {
//                    String tmp = stringConstant.replace("\n", "\\n");
//                    tmp = tmp.replace("\\", "\\\\");
//                    tmp = tmp.replace("\r", "\\r");
//                    tmp = tmp.replace("\t", "\\t");
//                    tmp = tmp.replace("\"", "\\\"");
                    return "\"" + stringConstant + "\"";                    
                }
            }
            case EXPRESSION_SYMBOL:
                if (config.output_replaceLabelDotsByUnderscores) {
                    return symbolName.replace(".", "_");
                } else {
                    return symbolName;
                }
            case EXPRESSION_SIGN_CHANGE:
                if (args.get(0).type == EXPRESSION_REGISTER_OR_FLAG
                        || args.get(0).type == EXPRESSION_INTEGER_CONSTANT
                        || args.get(0).type == EXPRESSION_STRING_CONSTANT
                        || args.get(0).type == EXPRESSION_PARENTHESIS
                        || args.get(0).type == EXPRESSION_SYMBOL) {
                    return "-" + args.get(0).toString();
                } else {
                    return "-(" + args.get(0).toString() + ")";
                }
            case EXPRESSION_PARENTHESIS:
                return "(" + args.get(0).toString() + ")";
            case EXPRESSION_SUM: {
                String str = null;
                for (Expression arg : args) {
                    if (str == null) {
                        str = arg.toString();
                    } else {
                        str += " + " + arg.toString();
                    }
                }
                return str;
            }
            case EXPRESSION_SUB: {
                String str = null;
                for (Expression arg : args) {
                    if (str == null) {
                        str = arg.toString();
                    } else {
                        str += " - " + arg.toString();
                    }
                }
                return str;
            }
            case EXPRESSION_MUL: {
                String str = null;
                for (Expression arg : args) {
                    if (str == null) {
                        str = arg.toString();
                    } else {
                        str += " * " + arg.toString();
                    }
                }
                return str;
            }
            case EXPRESSION_DIV: {
                String str = null;
                for (Expression arg : args) {
                    if (str == null) {
                        str = arg.toString();
                    } else {
                        str += " / " + arg.toString();
                    }
                }
                return str;
            }
            case EXPRESSION_MOD: {
                String str = null;
                for (Expression arg : args) {
                    if (str == null) {
                        str = arg.toString();
                    } else {
                        str += " % " + arg.toString();
                    }
                }
                return str;
            }
            case EXPRESSION_OR: {
                String str = null;
                for (Expression arg : args) {
                    if (str == null) {
                        str = arg.toString();
                    } else {
                        str += " || " + arg.toString();
                    }
                }
                return str;
            }
            case EXPRESSION_AND: {
                String str = null;
                for (Expression arg : args) {
                    if (str == null) {
                        str = arg.toString();
                    } else {
                        str += " && " + arg.toString();
                    }
                }
                return str;
            }
            case EXPRESSION_EQUAL: {
                String str = null;
                for (Expression arg : args) {
                    if (str == null) {
                        str = arg.toString();
                    } else {
                        str += " = " + arg.toString();
                    }
                }
                return str;
            }
            case EXPRESSION_LOWERTHAN: {
                String str = null;
                for (Expression arg : args) {
                    if (str == null) {
                        str = arg.toString();
                    } else {
                        str += " < " + arg.toString();
                    }
                }
                return str;
            }
            case EXPRESSION_GREATERTHAN: {
                String str = null;
                for (Expression arg : args) {
                    if (str == null) {
                        str = arg.toString();
                    } else {
                        str += " > " + arg.toString();
                    }
                }
                return str;
            }
            case EXPRESSION_LEQTHAN: {
                String str = null;
                for (Expression arg : args) {
                    if (str == null) {
                        str = arg.toString();
                    } else {
                        str += " <= " + arg.toString();
                    }
                }
                return str;
            }
            case EXPRESSION_GEQTHAN: {
                String str = null;
                for (Expression arg : args) {
                    if (str == null) {
                        str = arg.toString();
                    } else {
                        str += " >= " + arg.toString();
                    }
                }
                return str;
            }

            case EXPRESSION_DIFF: {
                String str = null;
                for (Expression arg : args) {
                    if (str == null) {
                        str = arg.toString();
                    } else {
                        str += " != " + arg.toString();
                    }
                }
                return str;
            }

            case EXPRESSION_TERNARY_IF: {
                return args.get(0).toString() + " ? "
                        + args.get(1).toString() + " : "
                        + args.get(2).toString();
            }

            case EXPRESSION_LSHIFT: {
                String str = null;
                for (Expression arg : args) {
                    if (str == null) {
                        str = arg.toString();
                    } else {
                        str += " << " + arg.toString();
                    }
                }
                return str;
            }

            case EXPRESSION_RSHIFT: {
                String str = null;
                for (Expression arg : args) {
                    if (str == null) {
                        str = arg.toString();
                    } else {
                        str += " >> " + arg.toString();
                    }
                }
                return str;
            }

            case EXPRESSION_BITOR: {
                String str = null;
                for (Expression arg : args) {
                    if (str == null) {
                        str = arg.toString();
                    } else {
                        str += " | " + arg.toString();
                    }
                }
                return str;
            }

            case EXPRESSION_BITAND: {
                String str = null;
                for (Expression arg : args) {
                    if (str == null) {
                        str = arg.toString();
                    } else {
                        str += " & " + arg.toString();
                    }
                }
                return str;
            }
            case EXPRESSION_BITNEGATION:
                if (args.get(0).type == EXPRESSION_REGISTER_OR_FLAG
                        || args.get(0).type == EXPRESSION_INTEGER_CONSTANT
                        || args.get(0).type == EXPRESSION_STRING_CONSTANT
                        || args.get(0).type == EXPRESSION_PARENTHESIS
                        || args.get(0).type == EXPRESSION_SYMBOL) {
                    return "~" + args.get(0).toString();
                } else {
                    return "~(" + args.get(0).toString() + ")";
                }
            case EXPRESSION_BITXOR: {
                String str = null;
                for (Expression arg : args) {
                    if (str == null) {
                        str = arg.toString();
                    } else {
                        str += " ^ " + arg.toString();
                    }
                }
                return str;
            }
            case EXPRESSION_LOGICAL_NEGATION:
                if (args.get(0).type == EXPRESSION_REGISTER_OR_FLAG
                        || args.get(0).type == EXPRESSION_INTEGER_CONSTANT
                        || args.get(0).type == EXPRESSION_STRING_CONSTANT
                        || args.get(0).type == EXPRESSION_PARENTHESIS
                        || args.get(0).type == EXPRESSION_SYMBOL) {
                    return "!" + args.get(0).toString();
                } else {
                    return "!(" + args.get(0).toString() + ")";
                }
            case EXPRESSION_DIALECT_FUNCTION:
            {
                String str = dialectFunction + "(";
                for(int i = 0;i<args.size();i++) {
                    if (i == 0) {
                        str += args.get(i).toString();
                    } else {
                        str += ", " + args.get(i).toString();
                    }
                }
                return str += ")";
            }
            case EXPRESSION_PLUS_SIGN:
            {
                return "+" + args.get(0).toString();
            }
            default:
                return "<UNSUPPORTED TYPE " + type + ">";
        }
    }

    public boolean isRegister(CodeBase code) {
        if (type != EXPRESSION_REGISTER_OR_FLAG) {
            return false;
        }
        return code.isRegister(registerOrFlagName);
    }

    public boolean isConstant() {
        return (type == EXPRESSION_INTEGER_CONSTANT)
                || (type == EXPRESSION_STRING_CONSTANT);
    }

    public boolean evaluatesToNumericConstant() {
        if (type == EXPRESSION_INTEGER_CONSTANT) {
            return true;
        }
        if (type == EXPRESSION_DOUBLE_CONSTANT) {
            return true;
        }
        if (type == EXPRESSION_SYMBOL) {
            return true;
        }
        if (type == EXPRESSION_STRING_CONSTANT
                && stringConstant.length() == 1) {
            return true;
        }
        if (type == EXPRESSION_SIGN_CHANGE
                || type == EXPRESSION_PARENTHESIS
                || type == EXPRESSION_SUM
                || type == EXPRESSION_SUB
                || type == EXPRESSION_MUL
                || type == EXPRESSION_DIV
                || type == EXPRESSION_MOD
                || type == EXPRESSION_OR
                || type == EXPRESSION_AND
                || type == EXPRESSION_EQUAL
                || type == EXPRESSION_LOWERTHAN
                || type == EXPRESSION_GREATERTHAN
                || type == EXPRESSION_LEQTHAN
                || type == EXPRESSION_GEQTHAN
                || type == EXPRESSION_DIFF
                || type == EXPRESSION_LSHIFT
                || type == EXPRESSION_RSHIFT
                || type == EXPRESSION_BITOR
                || type == EXPRESSION_BITAND
                || type == EXPRESSION_BITNEGATION
                || type == EXPRESSION_BITXOR
                || type == EXPRESSION_LOGICAL_NEGATION
                || type == EXPRESSION_PLUS_SIGN) {
            for (Expression arg : args) {
                if (!arg.evaluatesToNumericConstant()) {
                    return false;
                }
            }
            return true;
        }
        if (type == EXPRESSION_TERNARY_IF) {
            return args.get(1).evaluatesToIntegerConstant() && args.get(2).evaluatesToIntegerConstant();
        }
        if (type == EXPRESSION_DIALECT_FUNCTION) {
            return config.dialectParser.expressionEvaluatesToIntegerConstant(dialectFunction);
        }
        return false;
    }    
    
    
    public boolean evaluatesToIntegerConstant() {
        if (type == EXPRESSION_INTEGER_CONSTANT) {
            return true;
        }
        if (type == EXPRESSION_SYMBOL) {
            return true;
        }
        if (type == EXPRESSION_STRING_CONSTANT
                && stringConstant.length() == 1) {
            return true;
        }
        if (type == EXPRESSION_SIGN_CHANGE
                || type == EXPRESSION_PARENTHESIS
                || type == EXPRESSION_SUM
                || type == EXPRESSION_SUB
                || type == EXPRESSION_MUL
                || type == EXPRESSION_DIV
                || type == EXPRESSION_MOD
                || type == EXPRESSION_OR
                || type == EXPRESSION_AND
                || type == EXPRESSION_EQUAL
                || type == EXPRESSION_LOWERTHAN
                || type == EXPRESSION_GREATERTHAN
                || type == EXPRESSION_LEQTHAN
                || type == EXPRESSION_GEQTHAN
                || type == EXPRESSION_DIFF
                || type == EXPRESSION_LSHIFT
                || type == EXPRESSION_RSHIFT
                || type == EXPRESSION_BITOR
                || type == EXPRESSION_BITAND
                || type == EXPRESSION_BITNEGATION
                || type == EXPRESSION_BITXOR
                || type == EXPRESSION_LOGICAL_NEGATION
                || type == EXPRESSION_PLUS_SIGN) {
            for (Expression arg : args) {
                if (!arg.evaluatesToIntegerConstant()) {
                    return false;
                }
            }
            return true;
        }
        if (type == EXPRESSION_TERNARY_IF) {
            return args.get(1).evaluatesToIntegerConstant() && args.get(2).evaluatesToIntegerConstant();
        }
        if (type == EXPRESSION_DIALECT_FUNCTION) {
            return config.dialectParser.expressionEvaluatesToIntegerConstant(dialectFunction);
        }
        return false;
    }

    public int sizeInBytes(int granularity) {
        if (type == EXPRESSION_STRING_CONSTANT) {
            return stringConstant.length();
        } else {
            return granularity;
        }
    }
    
    public boolean resolveLocalLabels(String labelPrefix, SourceStatement s, CodeBase code)
    {
        if (type == EXPRESSION_SYMBOL) {
            if (symbolName.equals(CodeBase.CURRENT_ADDRESS)) return true;
            SourceConstant sc = code.getSymbol(labelPrefix + symbolName);
            if (sc != null) {
                symbolName = sc.name;
                return true;
            } else if (!labelPrefix.isEmpty()) {
                int idx = labelPrefix.substring(0,labelPrefix.length()-1).lastIndexOf(".");
                if (idx >= 0) {
                    return resolveLocalLabels(labelPrefix.substring(0, idx+1), s, code);
                }
            }
            return false;
        } else if (args != null) {
            boolean allResolved = true;
            for(Expression exp:args) {
                if (!exp.resolveLocalLabels(labelPrefix, s, code)) allResolved = false;
            }
            return allResolved;
        }
        return true;
    }
    
    
    public Expression resolveEagerSymbols(CodeBase code)
    {
        switch(type) {
            case EXPRESSION_SYMBOL:
                {
                    SourceConstant c = code.getSymbol(symbolName);
                    if (c.resolveEagerly && c.exp != null) {
                        Object value = c.exp.evaluate(c.definingStatement, code, true);
                        if (value != null) {
                            if (value instanceof Integer) {
                                return Expression.constantExpression((Integer)value, config);
                            } else if (value instanceof Double) {
                                return Expression.constantExpression((Double)value, config);
                            } else if (value instanceof String) {
                                return Expression.constantExpression((String)value, config);
                            }
                        }
                    }
                }
                return this;
            default:
                if (args != null) {
                    for(int i = 0;i<args.size();i++) {
                        args.set(i, args.get(i).resolveEagerSymbols(code));
                    }
                }
                return this;
        }
    }
    

    public static Expression constantExpression(int v, MDLConfig config) {
        Expression exp = new Expression(EXPRESSION_INTEGER_CONSTANT, config);
        exp.integerConstant = v;
        return exp;
    }

    public static Expression constantExpression(int v, boolean renderAs8bitHex, boolean renderAs16bitHex, MDLConfig config) {
        Expression exp = new Expression(EXPRESSION_INTEGER_CONSTANT, config);
        exp.integerConstant = v;
        exp.renderAs8bitHex = renderAs8bitHex;
        exp.renderAs16bitHex = renderAs16bitHex;
        return exp;
    }
    
    public static Expression constantExpression(double v, MDLConfig config) {
        Expression exp = new Expression(EXPRESSION_DOUBLE_CONSTANT, config);
        exp.doubleConstant = v;
        return exp;
    }

    public static Expression constantExpression(String v, MDLConfig config) {
        Expression exp = new Expression(EXPRESSION_STRING_CONSTANT, config);
        exp.stringConstant = v;
        return exp;
    }

    
    public static Expression symbolExpression(String symbol, SourceStatement s, CodeBase code, MDLConfig config) {
        return symbolExpressionInternal(symbol, s, code, true, config);
    }    
    
    
    public static Expression symbolExpressionInternal(String symbol, SourceStatement s, CodeBase code, boolean evaluateEagerSymbols, MDLConfig config) {
        if (code.isRegister(symbol) || code.isCondition(symbol)) {
            Expression exp = new Expression(EXPRESSION_REGISTER_OR_FLAG, config);
            exp.registerOrFlagName = symbol;
            return exp;
        } else {
            Expression exp = new Expression(EXPRESSION_SYMBOL, config);
            exp.symbolName = symbol;
            
            // check if it's a variable that needs to be evaluated eagerly:            
            SourceConstant c = code.getSymbol(exp.symbolName);
            if (c != null && c.resolveEagerly && evaluateEagerSymbols) {
                Object value = c.getValue(code, false);
                if (value == null) {
                    config.error("Cannot resolve eager variable " + symbol + "!");
                    return null;
                } 
                if (value instanceof Integer) {
                    exp = new Expression(EXPRESSION_INTEGER_CONSTANT, config);
                    exp.integerConstant = (Integer)value;
                } else if (value instanceof Double) {
                    exp = new Expression(EXPRESSION_DOUBLE_CONSTANT, config);
                    exp.doubleConstant = (Double)value;
                } else if (value instanceof String) {
                    exp = new Expression(EXPRESSION_STRING_CONSTANT, config);
                    exp.stringConstant = (String)value;
                } else {
                    return null;
                }
            }            
            return exp;
        }
    }

    public static Expression signChangeExpression(Expression arg, MDLConfig config) {
        Expression exp = new Expression(EXPRESSION_SIGN_CHANGE, config);
        exp.args = new ArrayList<>();
        exp.args.add(arg);
        return exp;
    }

    public static Expression bitNegationExpression(Expression arg, MDLConfig config) {
        Expression exp = new Expression(EXPRESSION_BITNEGATION, config);
        exp.args = new ArrayList<>();
        exp.args.add(arg);
        return exp;
    }

    public static Expression parenthesisExpression(Expression arg, String parenthesis, MDLConfig config) {
        Expression exp = new Expression(EXPRESSION_PARENTHESIS, config);
        exp.args = new ArrayList<>();
        exp.args.add(arg);
        exp.parenthesis = parenthesis;
        return exp;
    }

    public static Expression operatorExpression(int operator, Expression arg, MDLConfig config) {
        Expression exp = new Expression(operator, config);
        exp.args = new ArrayList<>();
        exp.args.add(arg);
        return exp;
    }

    public static Expression operatorExpression(int operator, Expression arg1, Expression arg2, MDLConfig config) {
        // look at operator precedence:
        if (OPERATOR_PRECEDENCE[operator] < 0) {
            config.error("Precedence for operator " + operator + " is undefined!");
            return null;
        }
        if (OPERATOR_PRECEDENCE[arg1.type] >= 0
                && OPERATOR_PRECEDENCE[operator] < OPERATOR_PRECEDENCE[arg1.type]) {
            // operator has higher precedence than the one in arg1, we need to reorder!
            if (OPERATOR_PRECEDENCE[arg2.type] >= 0
                    && OPERATOR_PRECEDENCE[operator] < OPERATOR_PRECEDENCE[arg2.type]) {
                if (OPERATOR_PRECEDENCE[arg1.type] < OPERATOR_PRECEDENCE[arg2.type]) {
                    // (1 arg1 (2 operator 3)) arg2 4
                    Expression exp = new Expression(operator, config);
                    exp.args = new ArrayList<>();
                    exp.args.add(arg1.args.get(arg1.args.size() - 1));
                    exp.args.add(arg2.args.get(0));
                    arg1.args.set(arg1.args.size() - 1, exp);
                    arg2.args.set(0, arg1);
                    return arg2;
                } else {
                    // 1 arg1 ((2 operator 3) arg2 4)
                    Expression exp = new Expression(operator, config);
                    exp.args = new ArrayList<>();
                    exp.args.add(arg1.args.get(arg1.args.size() - 1));
                    exp.args.add(arg2.args.get(0));
                    arg2.args.set(0, exp);
                    arg1.args.set(arg1.args.size() - 1, arg2);
                    return arg1;
                }
            } else {
                // 1 arg1 (2 operator arg2)
                Expression exp = new Expression(operator, config);
                exp.args = new ArrayList<>();
                exp.args.add(arg1.args.get(arg1.args.size() - 1));
                exp.args.add(arg2);
                arg1.args.set(arg1.args.size() - 1, exp);
                return arg1;
            }
        } else if (OPERATOR_PRECEDENCE[arg2.type] >= 0
                && OPERATOR_PRECEDENCE[operator] < OPERATOR_PRECEDENCE[arg2.type]) {
            // operator has higher precedence than the one in arg2, we need to reorder!
            // (arg1 operator 3) arg2 4
            Expression exp = new Expression(operator, config);
            exp.args = new ArrayList<>();
            exp.args.add(arg1);
            exp.args.add(arg2.args.get(0));
            arg2.args.set(0, exp);
            return arg2;
        }

        Expression exp = new Expression(operator, config);
        exp.args = new ArrayList<>();
        exp.args.add(arg1);
        exp.args.add(arg2);
        return exp;
    }

    
    public static Expression operatorTernaryExpression(int operator, Expression arg1, Expression arg2, Expression arg3, MDLConfig config) {
        Expression exp = new Expression(operator, config);
        exp.args = new ArrayList<>();
        exp.args.add(arg1);
        exp.args.add(arg2);
        exp.args.add(arg3);
        return exp;
    }

    
    public static Expression dialectFunctionExpression(String functionName, List<Expression> a_args, MDLConfig config) {
        Expression exp = new Expression(EXPRESSION_DIALECT_FUNCTION, config);
        exp.dialectFunction = functionName;
        exp.args = new ArrayList<>();
        exp.args.addAll(a_args);
        return exp;
    }

}
