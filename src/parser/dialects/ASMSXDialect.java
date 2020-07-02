/*
 * Author: Santiago Ontañón Villar (Brain Games)
 */
package parser.dialects;

import cl.MDLConfig;
import code.CodeBase;
import code.Expression;
import code.SourceFile;
import code.SourceStatement;
import java.util.List;
import parser.SourceMacro;
import parser.Tokenizer;

/**
 *
 * @author santi
 */
public class ASMSXDialect implements Dialect {
    MDLConfig config;
    
    String lastAbsoluteLabel = null;
    
    
    public ASMSXDialect(MDLConfig a_config)
    {
        config = a_config;
    }
    
    
    @Override
    public void init(MDLConfig config)
    {
        config.lineParser.KEYWORD_ORG = ".org";
        config.lineParser.KEYWORD_INCLUDE = ".include";
        config.lineParser.KEYWORD_INCBIN = ".incbin";
        config.lineParser.addKeywordSynonym(".equ", config.lineParser.KEYWORD_EQU);
        
        config.lineParser.addKeywordSynonym(".db", config.lineParser.KEYWORD_DB);
        config.lineParser.addKeywordSynonym("defb", config.lineParser.KEYWORD_DB);
        config.lineParser.addKeywordSynonym(".defb", config.lineParser.KEYWORD_DB);
        config.lineParser.addKeywordSynonym("dt", config.lineParser.KEYWORD_DB);
        config.lineParser.addKeywordSynonym(".dt", config.lineParser.KEYWORD_DB);
        config.lineParser.addKeywordSynonym("deft", config.lineParser.KEYWORD_DB);
        config.lineParser.addKeywordSynonym(".deft", config.lineParser.KEYWORD_DB);

        config.lineParser.addKeywordSynonym(".dw", config.lineParser.KEYWORD_DW);
        config.lineParser.addKeywordSynonym("defw", config.lineParser.KEYWORD_DW);
        config.lineParser.addKeywordSynonym(".defw", config.lineParser.KEYWORD_DW);

        config.lineParser.addKeywordSynonym(".ds", config.lineParser.KEYWORD_DS);
        config.lineParser.addKeywordSynonym("defs", config.lineParser.KEYWORD_DS);
        config.lineParser.addKeywordSynonym(".defs", config.lineParser.KEYWORD_DS);
    }
    
    
    @Override
    public boolean recognizeIdiom(List<String> tokens) {
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase(".bios")) return true;
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase(".size")) return true;
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase(".page")) return true;
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase(".printtext")) return true;
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase(".printdec")) return true;
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase(".printhex")) return true;
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase(".byte")) return true;
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase(".word")) return true;
        return false;
    }

    
    @Override
    public String newSymbolName(String name, Expression value) {
        // TODO(santi@): complete this list (or maybe have a better way than an if-then rule list!
        if (name.equalsIgnoreCase(".bios") ||
            name.equalsIgnoreCase(".size") ||
            name.equalsIgnoreCase(".page") ||
            name.equalsIgnoreCase(".printtext") ||
            name.equalsIgnoreCase(".printdec") ||
            name.equalsIgnoreCase(".printhex") ||
            name.equalsIgnoreCase(".byte") ||
            name.equalsIgnoreCase(".word")) {
            return null;
        }
        if (name.startsWith("@@")) {
            return lastAbsoluteLabel + "." + name.substring(2);
        } else if (name.startsWith(".")) {
            return lastAbsoluteLabel + "." + name.substring(1);
        } else {
            if (value.type == Expression.EXPRESSION_SYMBOL && 
                value.symbolName.equalsIgnoreCase(CodeBase.CURRENT_ADDRESS)) {
                lastAbsoluteLabel = name;
            }
        }
        return name;
    }    
    
    
    @Override
    public String symbolName(String name) {
        if (name.startsWith("@@")) {
            return lastAbsoluteLabel + "." + name.substring(2);
        } else if (name.startsWith(".")) {
            return lastAbsoluteLabel + "." + name.substring(1);
        } else {
            return name;
        }
    }    
    
    
    @Override
    public boolean parseLine(List<String> tokens, String line, int lineNumber, SourceStatement s, SourceFile source, CodeBase code) throws Exception {
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase(".bios")) {
            tokens.remove(0);
            // just ignore this line, as it seems it does nothing ...
            return config.lineParser.parseRestofTheLine(tokens, line, lineNumber, s, source);
        }
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase(".size")) {
            tokens.remove(0);
            Expression size_exp = config.expressionParser.parse(tokens, code);
            if (size_exp == null) {
                config.error("Cannot parse .size parameter in " + source.fileName + ", " + lineNumber + ": " + line);
                return false;
            }
            // Since we are not producing compiled output, we ignore this directive
            return config.lineParser.parseRestofTheLine(tokens, line, lineNumber, s, source);
        }
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase(".page")) {
            tokens.remove(0);
            Expression page_exp = config.expressionParser.parse(tokens, code);
            if (page_exp == null) {
                config.error("Cannot parse .page parameter in " + source.fileName + ", " + lineNumber + ": " + line);
                return false;
            }
            s.type = SourceStatement.STATEMENT_ORG;
            s.org = page_exp;
            Expression.operatorExpression(Expression.EXPRESSION_MUL, 
                    Expression.parenthesisExpression(page_exp), 
                    Expression.constantExpression(16*1024), config);
            // Since we are not producing compiled output, we ignore this directive
            return config.lineParser.parseRestofTheLine(tokens, line, lineNumber, s, source);
        }
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase(".printtext")) {
            config.info(tokens.get(1));
            tokens.remove(0);
            tokens.remove(0);
            return config.lineParser.parseRestofTheLine(tokens, line, lineNumber, s, source);
        }
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase(".printdec")) {
            tokens.remove(0);
            Expression exp = config.expressionParser.parse(tokens, code);
            Integer value = exp.evaluate(s, code, true);
            if (value == null) {
                config.error("Cannot evaluate expression in " + source.fileName + ", " + lineNumber + ": " + line);
                return false;                
            }
            config.info("" + value);
            return config.lineParser.parseRestofTheLine(tokens, line, lineNumber, s, source);
        }        
        if (tokens.size()>=2 && tokens.get(0).equalsIgnoreCase(".printhex")) {
            tokens.remove(0);
            Expression exp = config.expressionParser.parse(tokens, code);
            Integer value = exp.evaluate(s, code, true);
            if (value == null) {
                config.error("Cannot evaluate expression in " + source.fileName + ", " + lineNumber + ": " + line);
                return false;                
            }
            config.info(Tokenizer.toHexWord(value, config.hexStyle));
            return config.lineParser.parseRestofTheLine(tokens, line, lineNumber, s, source);
        }
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase(".byte")) {
            tokens.remove(0);
            s.type = SourceStatement.STATEMENT_DEFINE_SPACE;
            s.space = Expression.constantExpression(1);
            s.space_value = null;
            return config.lineParser.parseRestofTheLine(tokens, line, lineNumber, s, source);
        }
        if (tokens.size()>=1 && tokens.get(0).equalsIgnoreCase(".word")) {
            tokens.remove(0);
            s.type = SourceStatement.STATEMENT_DEFINE_SPACE;
            s.space = Expression.constantExpression(2);
            s.space_value = null;
            return config.lineParser.parseRestofTheLine(tokens, line, lineNumber, s, source);
        }
        
        config.error("ASMSXDialect cannot parse line in " + source.fileName + ", " + lineNumber + ": " + line);
        return false;        
    }

    
    @Override
    public boolean newMacro(SourceMacro macro, CodeBase code) throws Exception {
        return true;
    }
    
}
