/*
 * Author: Santiago Ontañón Villar (Brain Games)
 */
package workers.pattopt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;

import cl.MDLConfig;
import code.CPUOp;
import code.CPUOpDependency;
import code.CodeBase;
import code.Expression;
import code.SourceConstant;
import code.SourceFile;
import code.SourceStatement;
import parser.SourceLine;
import parser.Tokenizer;

/**
 *
 * @author santi
 */
public class Pattern {
    public static class Constraint {
        public String name;
        public String args[];
        
        // If this is != -1, this constraint will be checked as soon as a
        // CPUOpPattern with ID == triggerAfterID is matched
        public int triggerAfterID = -1;
        
        public Constraint(String a_name, String a_args[], int a_triggerAfterID) {
            name = a_name;
            args = a_args;
            triggerAfterID = a_triggerAfterID;
        }
    }
    
    MDLConfig config;

    String name;
    List<CPUOpPattern> pattern = new ArrayList<>();
    List<CPUOpPattern> replacement = new ArrayList<>();
    List<Constraint> constraints = new ArrayList<>();
    
    
    static class DepCheckNode {
        SourceStatement s;
        CPUOpDependency dep;
        List<SourceStatement> callStack;
        
        public DepCheckNode(SourceStatement a_s, CPUOpDependency a_dep, List<SourceStatement> a_cs)
        {
            s = a_s;
            dep = a_dep;
            callStack = a_cs;
        }
        
        
        public boolean match(CPUOpDependency a_dep, List<SourceStatement> a_cs)
        {
            if (!a_dep.equals(dep)) return false;
            if (callStack == null) {
                if (a_cs != null) return false;
            } else {
                if (a_cs == null) return false;
                if (a_cs.size() != callStack.size()) return false;
                for(int i = 0;i<callStack.size();i++) {
                    if (callStack.get(i) != a_cs.get(i)) return false;
                }
            }
            return true;
        }
    }
    

    public Pattern(String patternString, MDLConfig a_config)
    {
        config = a_config;
        int state = 0;  // 0: default, 1: expecting pattern, 2: expecting replacement, 3: expecting constraints
        CodeBase patternCB = new CodeBase(config);

        // parseArgs the pattern:
        String lines[] = patternString.split("\n");
        for(String line:lines) {
            line = line.trim();
            if (line.startsWith("pattern:")) {
                name = line.substring(8).trim();
                state = 1;
            } else if (line.equals("replacement:")) {
                state = 2;
            } else if (line.equals("constraints:")) {
                state = 3;
            } else {
                switch(state) {
                    case 1: // pattern:
                    {
                        CPUOpPattern patt = CPUOpPattern.parse(line, patternCB, config);
                        if (patt != null) {
                            pattern.add(patt);
                        } else {
                            config.error("Cannot parse pattern line: " + line);
                        }
                        break;
                    }

                    case 2: // replacement:
                    {
                        CPUOpPattern patt = CPUOpPattern.parse(line, patternCB, config);
                        if (patt != null) {
                            replacement.add(patt);
                            if (patt.repetitionVariable != null) {
                                config.error("repetition variables will be ignored in replacement pattern lines!");
                            }
                        } else {
                            config.error("Cannot parse replacement line: " + line);
                        }
                        break;
                    }
                    case 3: // constraints:
                    {
                        List<String> tokens = Tokenizer.tokenize(line);
                        String name = tokens.remove(0);
                        int triggerAfterID = -1;
                        List<Expression> expressions = new ArrayList<>();
                        if (!tokens.get(0).equals("(")) throw new RuntimeException("cannot parse constraint: " + line);
                        tokens.remove(0);
                        while(!tokens.get(0).equals(")")) {
                            Expression exp = config.expressionParser.parse(tokens, null, null, patternCB);
                            if (exp == null) throw new RuntimeException("cannot parse constraint: " + line);
                            expressions.add(exp);
                            if (tokens.get(0).equals(",")) tokens.remove(0);
                        }
                        tokens.remove(0);
                        
                        if (!tokens.isEmpty()) {
                            if (tokens.get(0).equals(":")) {
                                tokens.remove(0);
                                if (tokens.isEmpty()) {
                                    throw new RuntimeException("cannot parse constraint: " + line);
                                }
                                triggerAfterID = Integer.parseInt(tokens.remove(0));
                            } else {
                                throw new RuntimeException("cannot parse constraint: " + line);
                            }
                        } 
                        
                        String split[] = new String[expressions.size()];
                        for(int i = 0;i<split.length;i++) {
                            split[i] = expressions.get(i).toString();
                        }
                        constraints.add(new Constraint(name, split, triggerAfterID));
                        break;
                    }
                    default:
                        throw new RuntimeException("Unexpected line parsing a pattern: " + line);
                }
            }
        }
        
        // make sure that at least one of the lines in "pattern" has id 0 (and is not a wildcard):
        boolean found = false;
        for(CPUOpPattern pat:pattern) {
            if (pat.ID == 0 && !pat.isWildcard()) {
                found = true;
            }
        }
        if (!found) {
            config.error("Pattern \""+name+"\" does not contain a non wildcard line with ID 0!");
        }

        // config.trace("parsed pattern: " + name);
    }


    public String getName()
    {
        return name;
    }
        
    
    public String getInstantiatedName(PatternMatch match)
    {
        String tmp = name;
        for(String variable:match.variables.keySet()) {
            tmp = tmp.replace(variable, match.variables.get(variable).toString());
        }
        return tmp;
    }


    public int getSpaceSaving(PatternMatch match, CodeBase code)
    {
        int patternSize = 0;
        int replacementSize = 0;
        for(CPUOpPattern pat:pattern) {
            if (!pat.isWildcard()) {
                CPUOp ipat = pat.instantiate(match, this, config);
                int ipatSize = ipat.sizeInBytes();
                int n = 1;
                if (pat.repetitionVariable != null) {
                    n = match.variables.get(pat.repetitionVariable).evaluateToInteger(null, code, true);
                }
                patternSize += n * ipatSize;
            }
        }
        for(CPUOpPattern pat:replacement) {
            if (!pat.isWildcard()) {
                CPUOp ipat = pat.instantiate(match, this, config);
                replacementSize += ipat.sizeInBytes();
            }
        }
        int spaceSaving = patternSize - replacementSize;
        return spaceSaving;
    }
    
    
    public int[] getTimeSaving(PatternMatch match, CodeBase code)
    {
        int patternTime[] = {0,0};
        int replacementTime[] = {0,0};
        for(CPUOpPattern pat:pattern) {
            if (!pat.isWildcard()) {
                int tmp[] = pat.instantiate(match, this, config).timing();
                int n = 1;
                if (pat.repetitionVariable != null) {
                    n = match.variables.get(pat.repetitionVariable).evaluateToInteger(null, code, true);
                }
                patternTime[0] += n * tmp[0];
                if (tmp.length>1) {
                    patternTime[1] += n * tmp[1];
                } else {
                    patternTime[1] += n *tmp[0];
                }
            }
        }
        for(CPUOpPattern pat:replacement) {
            if (!pat.isWildcard()) {
                int tmp[] = pat.instantiate(match, this, config).timing();
                replacementTime[0] += tmp[0];
                if (tmp.length>1) {            
                    replacementTime[1] += tmp[1];
                } else {
                    replacementTime[1] += tmp[0];
                }
            }
        }
        return new int[]{patternTime[0] - replacementTime[0],
                         patternTime[1] - replacementTime[1]};
    }
    
    
    public String getTimeSavingString(PatternMatch match, CodeBase code)
    {
        int tmp[] = getTimeSaving(match, code);
        if (tmp[0] == tmp[1]) {
            return ""+tmp[0];
        } else {
            return tmp[0] + "/" + tmp[1];
        }
    }
    
    
    public boolean unifyExpressions(Expression pattern, Expression arg2, boolean expressionRoot, PatternMatch match, SourceStatement s, CodeBase code)
    {
        if (pattern.type == Expression.EXPRESSION_SYMBOL &&
            pattern.symbolName.startsWith("?")) {
            // it's a variable!
            if (pattern.symbolName.startsWith("?reg")) {
                if (arg2.isRegister(code)) {
                    return match.addVariableMatch(pattern.symbolName, arg2);
                } else {
                    return false;
                }
            } else if (pattern.symbolName.startsWith("?const")) {
                // We expluce matches with "parenthesis" expressions, as those might be indirections
                if (arg2.evaluatesToIntegerConstant() &&
                    arg2.type != Expression.EXPRESSION_PARENTHESIS) {
                    return match.addVariableMatch(pattern.symbolName, arg2);
                } else {
                    return false;
                }
            } else if (pattern.symbolName.startsWith("?any")) {
                return match.addVariableMatch(pattern.symbolName, arg2);
            } else {
                throw new RuntimeException("opMatch: unrecognized variable name " + pattern.symbolName);
            }     
        }

        if (pattern.type == Expression.EXPRESSION_INTEGER_CONSTANT) {
            // if the pattern is a numeric constant, and the argument is an expression that
            // evaluates to a number, the evaluateToInteger to check equality:
            // An exception is the "parenthesis" operation, which we assume is for an indirection if we
            // are at the top level of the expression
            if (arg2.evaluatesToIntegerConstant()) {
                if (!expressionRoot || arg2.type != Expression.EXPRESSION_PARENTHESIS) {
                    Integer arg2_val = arg2.evaluateToInteger(s, code, true);
                    if (arg2_val != null && arg2_val == pattern.integerConstant) return true;
                }
            }
        }

        if (pattern.type != arg2.type) return false;
        if (pattern.type == Expression.EXPRESSION_REGISTER_OR_FLAG) {
            return pattern.registerOrFlagName.equals(arg2.registerOrFlagName);
        }
        if (pattern.type == Expression.EXPRESSION_INTEGER_CONSTANT) {
            return pattern.integerConstant == arg2.integerConstant;
        }
        if (pattern.type == Expression.EXPRESSION_STRING_CONSTANT) {
            return pattern.stringConstant.equals(arg2.stringConstant);
        }
        if (pattern.type == Expression.EXPRESSION_SYMBOL) {
            return pattern.symbolName.equals(arg2.symbolName);
        }
        if (pattern.args != null && arg2.args != null && pattern.args.size() == arg2.args.size()) {
            for(int i = 0;i<pattern.args.size();i++) {
                if (!unifyExpressions(pattern.args.get(i), arg2.args.get(i), false, match, s, code)) return false;
            }
            return true;
        }
        return false;
    }


    public boolean opMatch(CPUOpPattern pat1, CPUOp op2, SourceStatement s, CodeBase code, PatternMatch match)
    {
        if (pat1.opName.startsWith("?op")) {
            if (!match.addVariableMatch(pat1.opName, Expression.symbolExpressionInternal(op2.spec.opName, s, code, false, config))) {
                return false;
            }
        } else {
            if (!pat1.opName.equals(op2.spec.opName)) return false;
        }
        if (pat1.args.size() != op2.args.size()) return false;

        for(int i = 0;i<pat1.args.size();i++) {
            Expression arg1 = pat1.args.get(i);
            Expression arg2 = op2.args.get(i);                        
            if (!unifyExpressions(arg1, arg2, true, match, s, code)) return false;
        }

        // config.trace("opMatch: "+pat1+" with "+op2+" ("+match.variables+")");
        return true;
    }

    
    public List<String> applyBindingsToTokens(List<String> tokens, PatternMatch match)
    {
        // apply bindings:
        List<String> tokens2 = new ArrayList<>();
        for(int i = 0;i<tokens.size();i++) {
            if (tokens.get(i).equals("?") && match.variables.containsKey("?" + tokens.get(i+1))) {                            
                List<String> tokensTmp = Tokenizer.tokenize(match.variables.get("?" + tokens.get(i+1)).toString());
                tokens2.addAll(tokensTmp);
                i++;    // we skip the second token we used
            } else {
                tokens2.add(tokens.get(i));
            }
        }
        return tokens2;
    }
    
    
    public void maybeLogOptimization(PatternMatch match, PatternBasedOptimizer pbo, SourceLine sl)
    {
        if (pbo.logPotentialOptimizations) {
            if (pbo.onlyOnePotentialOptimizationPerLine && pbo.alreadyShownAPotentialOptimization) return;
            String name2 = getInstantiatedName(match);
            config.info("Potential optimization ("+name2+") in " + sl);
            pbo.alreadyShownAPotentialOptimization = true;
        }
    }
    

    public PatternMatch match(int a_index, SourceFile f, CodeBase code,
                              PatternBasedOptimizer pbo)
    {
        int index = a_index;
        int index_to_display_message_on = -1;
        List<SourceStatement> l = f.getStatements();
        if (l.get(index).type != SourceStatement.STATEMENT_CPUOP) return null;
        PatternMatch match = new PatternMatch(this, f);
        
        // Match the CPU ops:
        for(int i = 0;i<pattern.size();i++) {
            CPUOpPattern patt = pattern.get(i);
            if (patt.isWildcard()) {
                if (i == pattern.size() - 1) {
                    // wildcard cannot be the last thing in a pattern!
                    return null;
                }
                CPUOpPattern nextPatt = pattern.get(i+1);
                List<SourceStatement> wildcardMatches = new ArrayList<>();

                while(true) {
                    if (index >= l.size()) return null;
                    SourceStatement s = l.get(index);
                    if (i!=0 && s.label != null) return null;
                    if (s.comment != null && s.comment.contains(config.PRAGMA_NO_OPTIMIZATION)) return null;
                    if (s.type == SourceStatement.STATEMENT_CPUOP) {
                        PatternMatch matchTmp = new PatternMatch(match);
                        if (opMatch(nextPatt, s.op, s, code, matchTmp)) {
                            // we are done!
                            if (patt.ID == 0) index_to_display_message_on = index;
                            break;
                        } else {
                            // make sure it's not statement involving jumps (ret/call/jp/jr/djnz/reti/retn/...):
                            if (s.op.mightJump()) {
                                return null;
                            }
                            wildcardMatches.add(s);
                        }
                    } else if (!s.isEmptyAllowingComments()) {
                        return null;
                    }
                    index++;
                }

                match.map.put(patt.ID, wildcardMatches);
            } else if (patt.repetitionVariable != null) {
                // it's a potentially repeated line:
                List<SourceStatement> statementsMatched = new ArrayList<>();
                int count = 0;
                while(true) {
                    if (index >= l.size()) return null;
                    SourceStatement s = l.get(index);
                    if (i!=0 && s.label != null) return null;
                    if (i==0 && s.label != null && count>0) break;
                    if (s.comment != null && s.comment.contains(config.PRAGMA_NO_OPTIMIZATION)) return null;
                    if (s.type == SourceStatement.STATEMENT_CPUOP) {
                        if (opMatch(patt, l.get(index).op, l.get(index), code, match)) {
                            count += 1;
                        } else {
                            if (count == 0) return null;
                            break;
                        }
                    } else if (!s.isEmptyAllowingComments()) {
                        return null;
                    }
                    if (count > 0) {
                        // matching started!
                        statementsMatched.add(l.get(index));
                    }
                    index++;
                }
                                
                // add matching to count:
                if (!match.addVariableMatch(patt.repetitionVariable, Expression.constantExpression(count, config))) {
                    return null;
                }
                match.map.put(patt.ID, statementsMatched);
                index++;
                
            } else {
                // not a wildcard, not a repetition:
                while(true) {
                    if (index >= l.size()) return null;
                    SourceStatement s = l.get(index);
                    if (i!=0 && s.label != null) return null;
                    if (s.comment != null && s.comment.contains(config.PRAGMA_NO_OPTIMIZATION)) return null;
                    if (s.type == SourceStatement.STATEMENT_CPUOP) break;
                    if (!s.isEmptyAllowingComments()) return null;
                    index++;
                }
                if (!opMatch(patt, l.get(index).op, l.get(index), code, match)) return null;
                List<SourceStatement> tmp = new ArrayList<>();
                tmp.add(l.get(index));
                match.map.put(patt.ID, tmp);
                index++;
            }
            
            // check if we need to check any constraints at this point:
            for(Constraint constraint:constraints) {
                if (constraint.triggerAfterID == patt.ID) {
                    if (!checkConstraint(constraint, match, f, code, pbo,
                                         index_to_display_message_on)) return null;
                }
            }
            
        }
        
        if (index_to_display_message_on == -1) index_to_display_message_on = a_index;

        // potential match! check constraints:
        for(Constraint constraint:constraints) {
            if (!checkConstraint(constraint, match, f, code, pbo,
                                 index_to_display_message_on)) return null;
        }

        return match;
    }

    
    public boolean checkConstraint(Constraint raw_constraint, PatternMatch match,
                                   SourceFile f, CodeBase code, PatternBasedOptimizer pbo,
                                   int index_to_display_message_on) {
        Constraint constraint = new Constraint(raw_constraint.name, 
                new String[raw_constraint.args.length], raw_constraint.triggerAfterID);
        for(int i = 0;i<raw_constraint.args.length;i++) {
            if (match.variables.containsKey(raw_constraint.args[i])) {
                constraint.args[i] = match.variables.get(raw_constraint.args[i]).toString();
            } else {
                constraint.args[i] = raw_constraint.args[i];
            }
        }

        switch(constraint.name) {
            case "regsNotUsedAfter":
            {
                int idx = Integer.parseInt(constraint.args[0]);
                if (!match.map.containsKey(idx)) return false;
                for(int i = 1;i<constraint.args.length;i++) {
                    String reg = constraint.args[i];
                    Boolean result = regNotUsedAfter(match.map.get(idx).get(match.map.get(idx).size()-1), reg, f, code);
                    if (result == null) {
                        maybeLogOptimization(match, pbo, f.getStatements().get(index_to_display_message_on).sl);
                        return false;
                    } else {
                        if (!result) return false;
                    }
                }
                break;
            }
            case "flagsNotUsedAfter":
            {
                int idx = Integer.parseInt(constraint.args[0]);
                if (!match.map.containsKey(idx)) return false;
                for(int i = 1;i<constraint.args.length;i++) {
                    String flag = constraint.args[i].replace(" ", "");   // this is because the P/V flag, otherwise, it's generated as "P / V" and there is no match

                    Boolean result = flagNotUsedAfter(match.map.get(idx).get(match.map.get(idx).size()-1), flag, f, code);
                    if (result == null) {
                        maybeLogOptimization(match, pbo, f.getStatements().get(index_to_display_message_on).sl);
                        return false;
                    } else {
                        if (!result) return false;
                    }
                }
                break;
            }
            case "equal":
            {                    
                String v1_str = constraint.args[0];
                String v2_str = constraint.args[1];
                List<String> v1_tokens = applyBindingsToTokens(Tokenizer.tokenize(v1_str), match);
                List<String> v2_tokens = applyBindingsToTokens(Tokenizer.tokenize(v2_str), match);

                Expression exp1 = config.expressionParser.parse(v1_tokens, null, null, code);
                Expression exp2 = config.expressionParser.parse(v2_tokens, null, null, code);

                if (exp1.evaluatesToIntegerConstant() != exp2.evaluatesToIntegerConstant()) {
                    return false;
                }
                if (exp1.evaluatesToIntegerConstant()) {
                    // If the expressions are numeric, we evaluateToInteger them:
                    Integer v1 = exp1.evaluateToInteger(null, code, true);
                    Integer v2 = exp2.evaluateToInteger(null, code, true);
                    if (v1 == null || v2 == null) {
                        return false;
                    }
                    if ((int)v1 != (int)v2) {
                        return false;
                    }

                    match.newEqualities.add(new EqualityConstraint(exp1, null, exp2, null, false));
                } else {
                    // If they are not, then there is no need to evaluateToInteger, as they should just string match:
                    if (!v1_str.equalsIgnoreCase(v2_str)) {
                        return false;
                    }
                }
                break;
            }
            case "notEqual":
            {
                String v1_str = constraint.args[0];
                String v2_str = constraint.args[1];
                List<String> v1_tokens = applyBindingsToTokens(Tokenizer.tokenize(v1_str), match);
                List<String> v2_tokens = applyBindingsToTokens(Tokenizer.tokenize(v2_str), match);

                Expression exp1 = config.expressionParser.parse(v1_tokens, null, null, code);
                Expression exp2 = config.expressionParser.parse(v2_tokens, null, null, code);

                if (exp1.evaluatesToIntegerConstant() != exp2.evaluatesToIntegerConstant()) break;
                if (exp1.evaluatesToIntegerConstant()) {
                    // If the expressions are numeric, we evaluateToInteger them:
                    Integer v1 = exp1.evaluateToInteger(null, code, true);
                    Integer v2 = exp2.evaluateToInteger(null, code, true);
                    if (v1 == null || v2 == null) {
                        return false;
                    }
                    if (exp1.evaluateToInteger(null, code, true).equals(exp2.evaluateToInteger(null, code, true))) {
                        return false;
                    }

                    match.newEqualities.add(new EqualityConstraint(exp1, null, exp2, null, true));
                } else {
                    // If they are not, then there is no need to evaluateToInteger, as they should just string match:
                    if (v1_str.equalsIgnoreCase(v2_str)) {
                        return false;
                    }
                }
                break;
            }                
            case "in":
            {
                boolean found = false;
                for(int i = 1;i<constraint.args.length;i++) {
                    if (constraint.args[0].equalsIgnoreCase(constraint.args[i])) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    return false;
                }
                break;
            }
            case "notIn":
            {
                boolean found = false;
                for(int i = 1;i<constraint.args.length;i++) {
                    if (constraint.args[0].equalsIgnoreCase(constraint.args[i])) {
                        found = true;
                        break;
                    }
                }
                if (found) {
                    return false;
                }
                break;
            }
            case "regpair":
            {
                String expected1 = null;
                String expected2 = null;
                String expected3 = null;
                if (!constraint.args[0].startsWith("?")) {
                    // we need to construct the value from the second part:
                    if (constraint.args[0].equalsIgnoreCase("bc")) {
                        expected1 = "bc"; expected2 = "b"; expected3 = "c";
                    }
                    if (constraint.args[0].equalsIgnoreCase("de")) {
                        expected1 = "de"; expected2 = "d"; expected3 = "e";
                    }
                    if (constraint.args[0].equalsIgnoreCase("hl")) {
                        expected1 = "hl"; expected2 = "h"; expected3 = "l";
                    }
                    if (constraint.args[0].equalsIgnoreCase("ix")) {
                        expected1 = "ix"; expected2 = "ixh"; expected3 = "ixl";
                    }
                    if (constraint.args[0].equalsIgnoreCase("iy")) {
                        expected1 = "iy"; expected2 = "iyh"; expected3 = "iyl";
                    }
                }
                if (!constraint.args[1].startsWith("?")) {
                    // we need to construct the value from the second part:
                    if (constraint.args[1].equalsIgnoreCase("b")) {
                        expected1 = "bc"; expected2 = "b"; expected3 = "c";
                    }
                    if (constraint.args[1].equalsIgnoreCase("d")) {
                        expected1 = "de"; expected2 = "d"; expected3 = "e";
                    }
                    if (constraint.args[1].equalsIgnoreCase("h")) {
                        expected1 = "hl"; expected2 = "h"; expected3 = "l";
                    }
                    if (constraint.args[1].equalsIgnoreCase("ixh")) {
                        expected1 = "ix"; expected2 = "ixh"; expected3 = "ixl";
                    }
                    if (constraint.args[1].equalsIgnoreCase("iyh")) {
                        expected1 = "iy"; expected2 = "iyh"; expected3 = "iyl";
                    }
                }
                if (!constraint.args[2].startsWith("?")) {
                    // we need to construct the value from the second part:
                    if (constraint.args[2].equalsIgnoreCase("c")) {
                        expected1 = "bc"; expected2 = "b"; expected3 = "c";
                    }
                    if (constraint.args[2].equalsIgnoreCase("e")) {
                        expected1 = "de"; expected2 = "d"; expected3 = "e";
                    }
                    if (constraint.args[2].equalsIgnoreCase("l")) {
                        expected1 = "hl"; expected2 = "h"; expected3 = "l";
                    }
                    if (constraint.args[2].equalsIgnoreCase("ixl")) {
                        expected1 = "ix"; expected2 = "ixh"; expected3 = "ixl";
                    }
                    if (constraint.args[2].equalsIgnoreCase("iyl")) {
                        expected1 = "iy"; expected2 = "iyh"; expected3 = "iyl";
                    }
                }
                if (expected1 == null || expected2 == null || expected3 == null) {
                    return false;
                }
                if (constraint.args[0].startsWith("?")) {
                    if (!match.addVariableMatch(constraint.args[0], Expression.symbolExpression(expected1, null, code, config))) {
                        return false;
                    }
                } else {
                    if (!constraint.args[0].equalsIgnoreCase(expected1)) {
                        return false;
                    }
                }
                if (constraint.args[1].startsWith("?")) {
                    if (!match.addVariableMatch(constraint.args[1], Expression.symbolExpression(expected2, null, code, config))) {
                        return false;
                    }
                } else {
                    if (!constraint.args[1].equalsIgnoreCase(expected2)) {
                        return false;
                    }
                }
                if (constraint.args[2].startsWith("?")) {
                    if (!match.addVariableMatch(constraint.args[2], Expression.symbolExpression(expected3, null, code, config))) {
                        return false;
                    }
                } else {
                    if (!constraint.args[2].equalsIgnoreCase(expected3)) {
                        return false;
                    }
                }
                break;
            }
            case "reachableByJr":
            {
                SourceStatement start = match.map.get(Integer.parseInt(constraint.args[0])).get(0);
                Integer startAddress = start.getAddress(code);
                if (startAddress == null) {
                    return false;
                }
                SourceConstant sc = code.getSymbol(constraint.args[1]);
                if (sc == null) {
                    return false;
                }
                Object tmp = sc.getValue(code, false);
                if (tmp == null && tmp instanceof Integer) {
                    return false;
                }
                Integer endAddress = (Integer)tmp;
                int diff = endAddress - startAddress;
                if (diff < -126 || diff > 130) return false;
                break;
            }
            case "regsNotModified":
            {
                int idx = Integer.parseInt(constraint.args[0]);
                List<SourceStatement> statements = new ArrayList<>();
                if (match.map.containsKey(idx)) {
                    statements.addAll(match.map.get(idx));
                } else {
                    return false;
                }
                for(int i = 1;i<constraint.args.length;i++) {
                    String reg = constraint.args[i];
                    for(SourceStatement s:statements) {
                        if (!regNotModified(s, reg, f, code)) {
                            return false;
                        }
                    }
                    // config.debug("regsNotModified " + reg + " satisfied in: " + statements);
                    // config.debug("    mapping was: " + match.variables);
                }
                break;
            }
            case "flagsNotModified":
            {
                int idx = Integer.parseInt(constraint.args[0]);
                List<SourceStatement> statements = new ArrayList<>();
                if (match.map.containsKey(idx)) {
                    statements.addAll(match.map.get(idx));
                } else {
                    return false;
                }
                for(int i = 1;i<constraint.args.length;i++) {
                    String flag = constraint.args[i];
                    for(SourceStatement s:statements) {
                        if (!flagNotModified(s, flag, f, code)) {
                            return false;
                        }
                    }
                    // config.debug("flagsNotModified " + flag + " satisfied in: " + statements);
                    // config.debug("    mapping was: " + match.variables);
                }
                break;
            }
            case "regsNotUsed":
            {
                int idx = Integer.parseInt(constraint.args[0]);
                List<SourceStatement> statements = new ArrayList<>();
                if (match.map.containsKey(idx)) {
                    statements.addAll(match.map.get(idx));
                } else {
                    return false;
                }
                for(int i = 1;i<constraint.args.length;i++) {
                    String reg = constraint.args[i];
                    for(SourceStatement s:statements) {
                        if (!regNotUsed(s, reg, f, code)) {
                            return false;
                        }
                    }
                    // config.debug("regsNotModified " + reg + " satisfied in: " + statements);
                    // config.debug("    mapping was: " + match.variables);
                }
                break;
            }
            case "flagsNotUsed":
            {
                int idx = Integer.parseInt(constraint.args[0]);
                List<SourceStatement> statements = new ArrayList<>();
                if (match.map.containsKey(idx)) {
                    statements.addAll(match.map.get(idx));
                } else {
                    return false;
                }
                for(int i = 1;i<constraint.args.length;i++) {
                    String flag = constraint.args[i];
                    for(SourceStatement s:statements) {
                        if (!flagNotUsed(s, flag, f, code)) {
                            return false;
                        }
                    }
                    // config.debug("flagsNotUsed " + flag + " satisfied in: " + statements);
                    // config.debug("    mapping was: " + match.variables);
                }
                break;
            }
            case "evenPushPops":
            {
                int idx = Integer.parseInt(constraint.args[0]);
                List<SourceStatement> statements = new ArrayList<>();
                if (match.map.containsKey(idx)) {
                    statements.addAll(match.map.get(idx));
                } else {
                    return false;
                }
                int stackMovements = 0;
                for(SourceStatement s:statements) {
                    if (s.type == SourceStatement.STATEMENT_CPUOP) {
                        if (s.op.spec.opName.equalsIgnoreCase("push")) {
                            stackMovements -= 2;
                        } else if (s.op.spec.opName.equalsIgnoreCase("pop")) {
                            stackMovements += 2;
                        } else if (s.op.spec.opName.equalsIgnoreCase("inc") &&
                                   s.op.args.get(0).type == Expression.EXPRESSION_REGISTER_OR_FLAG &&
                                   s.op.args.get(0).registerOrFlagName.equalsIgnoreCase("sp")) {
                            stackMovements ++;
                        } else if (s.op.spec.opName.equalsIgnoreCase("dec") &&
                                   s.op.args.get(0).type == Expression.EXPRESSION_REGISTER_OR_FLAG &&
                                   s.op.args.get(0).registerOrFlagName.equalsIgnoreCase("sp")) {
                            stackMovements --;
                        } else if (!s.op.args.isEmpty()) {
                            // check if the 1st operand is SP in any form:
                            Expression arg = s.op.args.get(0);
                            if (arg.type == Expression.EXPRESSION_REGISTER_OR_FLAG && 
                                arg.registerOrFlagName.equalsIgnoreCase("sp")) {
                                maybeLogOptimization(match, pbo, f.getStatements().get(index_to_display_message_on).sl);
                                return false;
                            }
                            if (arg.type == Expression.EXPRESSION_PARENTHESIS && 
                                arg.args.get(0).type == Expression.EXPRESSION_REGISTER_OR_FLAG &&
                                arg.args.get(0).registerOrFlagName.equalsIgnoreCase("sp")) {
                                maybeLogOptimization(match, pbo, f.getStatements().get(index_to_display_message_on).sl);
                                return false;
                            }
                        }
                    }
                }
                if (stackMovements != 0) return false;
                break;
            }

            case "atLeastOneCPUOp":
            {
                int idx = Integer.parseInt(constraint.args[0]);
                List<SourceStatement> statements = new ArrayList<>();
                if (match.map.containsKey(idx)) {
                    statements.addAll(match.map.get(idx));
                } else {
                    return false;
                }
                boolean found = false;
                for(SourceStatement s:statements) {
                    if (s.type == SourceStatement.STATEMENT_CPUOP) {
                        found = true;
                        break;
                    }
                }                    
                if (!found) return false;
                break;
            }

            default:
                throw new UnsupportedOperationException("Unknown pattern constraint " + constraint.name);
        }        
        return true;
    }
    

    public boolean apply(SourceFile f, PatternMatch match, 
                         CodeBase code,
                         List<EqualityConstraint> equalitiesToMaintain)
    {
        // undo record:
        List<Pair<Integer, SourceStatement>> undo = new ArrayList<>();
        
        List<SourceStatement> l = f.getStatements();
        List<Integer> replacementIndexes = new ArrayList<>();
        int insertionPoint = -1;
        SourceStatement lastRemoved = null;
                
        for(CPUOpPattern p:replacement) {
            replacementIndexes.add(p.ID);
        }
        for(int i = 0;i<pattern.size();i++) {
            int key = pattern.get(i).ID;
            if (pattern.get(i).isWildcard()) {
                // It's a wildcard:
                boolean found = false;
                for(int j = 0;j<replacement.size();j++) {
                    if (replacement.get(j).ID == pattern.get(i).ID) {
                        replacementIndexes.remove((Integer)replacement.get(j).ID);
                        if (!replacement.get(j).isWildcard()) {
                            config.error("Replacing instructions matched with a wildcard is not yet supported!");
                            return false;
                        } else {
                            found = true;
                            insertionPoint = -1;
                            break;
                        }
                    }
                }
                if (!found) {
                    config.error("Removing instructions matched with a wildcard is not yet supported!");
                    return false;
                }                
            } else {
                // It is a regular op (not a wildcard):
                SourceStatement removedLabel = null;
                for(SourceStatement s:match.map.get(key)) {
                    insertionPoint = l.indexOf(s);
                    lastRemoved = l.remove(insertionPoint);
                    match.removed.add(lastRemoved);
                    if (lastRemoved.label != null) {
                        if (removedLabel != null) {
                            config.error("There were more than one label in the matched instructions of a pattern, which should not have happened!");
                            return false;
                        } else {
                            removedLabel = lastRemoved;
                        }
                    }
                    undo.add(Pair.of(insertionPoint, lastRemoved));
                }
                if (lastRemoved == null) {
                    config.error("optimization pattern line matched with one of its lines mathcing to zero ops. This should not have happened!");
                    return false;
                }
                boolean replaced = false;
                for(int j = 0;j<replacement.size();j++) {
                    if (replacement.get(j).ID == pattern.get(i).ID) {
                        replacementIndexes.remove((Integer)replacement.get(j).ID);
                        SourceStatement s = new SourceStatement(SourceStatement.STATEMENT_CPUOP, lastRemoved.sl, lastRemoved.source, config);
                        // if the original statement had a label, we need to keep it!
                        if (removedLabel != null) {
                            s.label = removedLabel.label;
                            s.label.definingStatement = s;
                            removedLabel = null;
                        }
                        s.op = new CPUOp(replacement.get(j).instantiate(match, this, config));
                        if (s.op == null) {
                            config.error("Problem applying optimization to replace: " + lastRemoved);
                            config.error("The replacement was: " + replacement.get(j));
                            return false;
                        }
                        l.add(insertionPoint, s);
                        match.added.add(s);
                        insertionPoint++;
                        replaced = true;
                        undo.add(Pair.of(null, s));
                        break;
                    }
                }
                if (!replaced && removedLabel != null) {
                    // We were losing a label. Insert a dummy statement with the label we lost:
                    SourceStatement s = new SourceStatement(SourceStatement.STATEMENT_NONE, removedLabel.sl, removedLabel.source, config);
                    s.label = removedLabel.label;
                    s.label.definingStatement = s;
                    l.add(insertionPoint, s);
                    match.added.add(s);
                    insertionPoint++;
                    undo.add(Pair.of(null, s));
                }                
            }
        }
        // add the missing replacements:
        for(int idx:replacementIndexes) {
            if (insertionPoint == -1) {
                config.error("Could not determine the insertion point in an additional replacement for: " + replacement.get(idx));
                return false;
            }
            if (lastRemoved == null) {
                config.error("Could not determine the source for an additional replacement for: " + replacement.get(idx));
                return false;
            }
            SourceStatement s = new SourceStatement(SourceStatement.STATEMENT_CPUOP, lastRemoved.sl, lastRemoved.source, config);
            s.op = new CPUOp(replacement.get(idx).instantiate(match, this, config));
            if (s.op == null) {
                config.error("The replacement was: " + replacement.get(idx));
                return false;
            }
            l.add(insertionPoint, s);
            match.added.add(s);
            insertionPoint++;
            undo.add(Pair.of(null, s));
        }
        
        // Add new equality constraints (we add them first, in case the optimization itself breaks them):
        int previousLength = equalitiesToMaintain.size();
        equalitiesToMaintain.addAll(match.newEqualities);
        
        code.resetAddresses();
        
        // Check the equalities:
        // config.debug("Checking " + equalitiesToMaintain.size() + " equalities!");
        for(EqualityConstraint eq:equalitiesToMaintain) {
            if (!eq.check(code, config)) {                
                // undo the optimization:
                for(int i = undo.size()-1; i>=0; i--) {
                    if (undo.get(i).getLeft() == null) {
                        // remove:
                        l.remove(undo.get(i).getRight());
                    } else {
                        // add:
                        l.add(undo.get(i).getLeft(), undo.get(i).getRight());
                    }
                }
                 while(equalitiesToMaintain.size() > previousLength) equalitiesToMaintain.remove(equalitiesToMaintain.size()-1);
                config.info("Optimization undone, as it was breaking the equality constraint: " + eq.exp1 + " == " + eq.exp2);
                code.resetAddresses();
                return false;
            }
        }
                
        return true;
    }


    public boolean regNotModified(SourceStatement s, String reg, SourceFile f, CodeBase code)
    {
        CPUOpDependency dep = new CPUOpDependency(reg.toUpperCase(), null, null, null, null);        
        if (s.type == SourceStatement.STATEMENT_CPUOP) {
            CPUOp op = s.op;            
            if (op.isRet()) {
                // It's hard to tell where is this instruction going to jump,
                // so we act conservatively, and block the optimization:
                // config.trace("    ret!");
                return false;
            }
            
            CPUOpDependency dep2 = op.checkOutputDependency(dep);
            return dep.equals(dep2);
        } else {
            return true;
        }
    }
    
    
    public boolean flagNotModified(SourceStatement s, String flag, SourceFile f, CodeBase code)
    {
        CPUOpDependency dep = new CPUOpDependency(null, flag.toUpperCase(), null, null, null);        
        if (s.type == SourceStatement.STATEMENT_CPUOP) {
            CPUOp op = s.op;            
            if (op.isRet()) {
                // It's hard to tell where is this instruction going to jump,
                // so we act conservatively, and block the optimization:
                // config.trace("    ret!");
                return false;
            }
            
            CPUOpDependency dep2 = op.checkOutputDependency(dep);
            return dep.equals(dep2);
        } else {
            return true;
        }
    }    
    

    public boolean regNotUsed(SourceStatement s, String reg, SourceFile f, CodeBase code)
    {
        CPUOpDependency dep = new CPUOpDependency(reg.toUpperCase(), null, null, null, null);        
        if (s.type == SourceStatement.STATEMENT_CPUOP) {
            CPUOp op = s.op;            
            if (op.isRet()) {
                // It's hard to tell where is this instruction going to jump,
                // so we act conservatively, and block the optimization:
                // config.trace("    ret!");
                return false;
            }
            
            return !op.checkInputDependency(dep);
        } else {
            return true;
        }
    }
    
    
    public boolean flagNotUsed(SourceStatement s, String flag, SourceFile f, CodeBase code)
    {
        CPUOpDependency dep = new CPUOpDependency(null, flag.toUpperCase(), null, null, null);        
        if (s.type == SourceStatement.STATEMENT_CPUOP) {
            CPUOp op = s.op;            
            if (op.isRet()) {
                // It's hard to tell where is this instruction going to jump,
                // so we act conservatively, and block the optimization:
                // config.trace("    ret!");
                return false;
            }
            
            return !op.checkInputDependency(dep);
        } else {
            return true;
        }
    }    
    
    
    public Boolean regNotUsedAfter(SourceStatement s, String reg, SourceFile f, CodeBase code)
    {
        CPUOpDependency dep = new CPUOpDependency(reg.toUpperCase(), null, null, null, null);
        return depNotUsedAfter(s, dep, f, code);
    }


    public Boolean flagNotUsedAfter(SourceStatement s, String flag, SourceFile f, CodeBase code)
    {
        CPUOpDependency dep = new CPUOpDependency(null, flag.toUpperCase(), null, null, null);
        return depNotUsedAfter(s, dep, f, code);
    }


    // - returns true/false if we know for sure the dependency is or not used
    // - returns null when it's unclear
    public Boolean depNotUsedAfter(SourceStatement s, CPUOpDependency a_dep, SourceFile f, CodeBase code)
    {
        List<DepCheckNode> open = new ArrayList<>();
        HashMap<SourceStatement,List<DepCheckNode>> closed = new HashMap<>();
        List<Pair<SourceStatement, List<SourceStatement>>> tmp = f.nextExecutionStatements(s, true, new ArrayList<>(), code);
        if (tmp == null) {
            // It's hard to tell where is this instruction going to jump,
            // so we act conservatively, and block the optimization:
            // config.trace("    unclear next statement after " + s);
            return false;
        }
        for(Pair<SourceStatement, List<SourceStatement>> pair:tmp) {
            DepCheckNode node = new DepCheckNode(pair.getLeft(), a_dep, pair.getRight());
            open.add(node);
            List<DepCheckNode> l = new ArrayList<>();
            l.add(node);
            closed.put(pair.getLeft(), l);
        }
        while(!open.isEmpty()) {
            DepCheckNode node = open.remove(0);
            SourceStatement next = node.s;
            CPUOpDependency dep = node.dep;
            List<SourceStatement> callStack = node.callStack;
            // config.trace("    "+next.sl.lineNumber+": "+next);

            if (next.type == SourceStatement.STATEMENT_CPUOP) {
                CPUOp op = next.op;
//                if (op.isRet()) {
//                    // It's hard to tell where is this instruction going to jump,
//                    // so we act conservatively, and block the optimization:
//                    // config.trace("    ret!");
//                    return null;
//                }
                if (op.checkInputDependency(dep)) {
                    // dependency is actually used!
                    // config.trace("    dependency found!");
                    return false;
                }
                dep = op.checkOutputDependency(dep);
//                if (dep == null) {
//                    // config.trace("    dependency broken!");
//                }
            }
            
            if (dep != null) {
                // add successors:
                List<Pair<SourceStatement, List<SourceStatement>>> nextNext_l = next.source.nextExecutionStatements(next, true, callStack, code);
                if (nextNext_l == null) {
                    // It's hard to tell where is this instruction going to jump,
                    // so we act conservatively, and block the optimization:
                    // config.trace("    unclear next statement after: "+next);
                    return null;
                }
                for(Pair<SourceStatement, List<SourceStatement>> nextNext_pair: nextNext_l) {
                    SourceStatement nextNext = nextNext_pair.getLeft();
                    List<SourceStatement> netNext_stack = nextNext_pair.getRight();
                    if (!closed.containsKey(nextNext)) {
                        DepCheckNode nextNode = new DepCheckNode(nextNext, dep, netNext_stack);
                        open.add(nextNode);
                        List<DepCheckNode> l = new ArrayList<>();
                        l.add(nextNode);
                        closed.put(nextNext, l);
                    } else {
                        List<DepCheckNode> l = closed.get(nextNext);
                        boolean found = false;
                        for(DepCheckNode n:l) {
                            if (n.match(dep, netNext_stack)) {
                                found = true;
                                break;
                            }
                        }
                        if (!found) {
                            DepCheckNode nextNode = new DepCheckNode(nextNext, dep, netNext_stack);
                            l.add(nextNode);
                            open.add(nextNode);
                        }
                    }
                }
            }
        }

        return true;
    }
}
