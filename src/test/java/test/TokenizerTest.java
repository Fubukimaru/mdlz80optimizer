/*
 * Author: Santiago Ontañón Villar (Brain Games)
 */
package test;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import parser.Tokenizer;

/**
 *
 * @author santi
 */
public class TokenizerTest {

    @Test public void test1() {
        Assert.assertArrayEquals(new String[]{"ld","a",",","2"}, tokenize("ld a,2"));
    }
    @Test public void test2() {
        Assert.assertArrayEquals(new String[]{"ex","af",",","af'"}, tokenize("ex af,af'"));
    }
    @Test public void test3() {
        Assert.assertArrayEquals(new String[]{"ex","af",",","AF'"}, tokenize("ex af,AF'"));
    }
    @Test public void test4() {
        Assert.assertArrayEquals(new String[]{"variable","<<","2"}, tokenize("variable<<2"));
    }
    @Test public void test5() {
        Assert.assertArrayEquals(
                new String[]{"ds","(","(","$","+","1","-","1",")",">>","8",")","!=","(","$",">>","8",")","&&","(","100H","-","(","$","&","0FFH",")",")","||","0"},
                tokenize("ds (($ + 1 - 1) >> 8) != ($ >> 8) && (100H - ($ & 0FFH)) || 0"));
    }
    @Test public void test6() {
        Assert.assertArrayEquals(new String[]{".include"}, tokenize(".include"));
    }
    @Test public void test7() {
        Assert.assertArrayEquals(new String[]{"ld","a",",","(","hl",")"}, tokenize("ld a,(hl)"));
    }
    @Test public void test8() {
        Assert.assertArrayEquals(new String[]{"ld","a",",","[","hl","]"}, tokenize("ld a,[hl]"));
    }
    @Test public void test9() {
        Assert.assertArrayEquals(new String[]{"@@my_local_label",":"}, tokenize("@@my_local_label:"));
    }
    @Test public void test10() {
        Assert.assertArrayEquals(new String[]{"GameStatus",":","#","1"}, tokenize("GameStatus: # 1"));
    }
    @Test public void test11() {
        Assert.assertArrayEquals(new String[]{"GameStatus",":","#","#10"}, tokenize("GameStatus: # #10"));
    }
    @Test public void test12() {
        Assert.assertArrayEquals(new String[]{"ld","a",",","(","hl",")","; Comment"}, tokenize("ld a,(hl) ; Comment"));
    }
    @Test public void test13() {
        Assert.assertArrayEquals(new String[]{"ld","a",",","(","hl",")","//C style comment"}, tokenize("ld a,(hl) //C style comment"));
    }
    @Test public void test14() {
        Assert.assertArrayEquals(new String[]{"/*","C","++","style","comment","*/"}, tokenize("/*C++ style comment*/"));
    }
    @Test public void test15() {
        Assert.assertArrayEquals(new String[]{"\"string\\t\\r\\n\""}, tokenize("\"string\\t\\r\\n\""));
    }
    @Test public void test15a() {
        Tokenizer.stringEscapeSequences.put("t", "\t");
        Tokenizer.stringEscapeSequences.put("r", "\r");
        Tokenizer.stringEscapeSequences.put("n", "\n");
        Assert.assertArrayEquals(new String[]{"\"string\t\r\n\""}, tokenize("\"string\\t\\r\\n\""));
        Tokenizer.stringEscapeSequences.clear();
    }
    @Test public void test16() {
        Assert.assertArrayEquals(new String[]{";\"J\" \"I\" \"H\" \"G\" \"F\" \"E\" \"D\" \"C\""}, tokenize(";\"J\" \"I\" \"H\" \"G\" \"F\" \"E\" \"D\" \"C\""));
    }
    @Test public void test17() {
        Assert.assertArrayEquals(new String[]{"dw","fix","(","0.05",")"}, tokenize("dw	fix(0.05)"));
    }
    @Test public void test18() {
        Assert.assertArrayEquals(new String[]{"db","(","fix","(","0.2","*","cos","(","angle","*","pi","/","180.0",")",")",")","&","0FFh"}, tokenize("db	(fix(0.2*cos(angle*pi/180.0)))&0FFh"));
    }
    @Test public void test19() {
        Assert.assertArrayEquals(new String[]{"ld","a",",","(","hl","++",")"}, tokenize("ld	a,(hl++)"));
    }
    @Test public void test20() {
        Assert.assertArrayEquals(new String[]{"ld","a",",","(","hl","+","+",")"}, tokenize("ld	a,(hl+ +)"));
    }
    @Test public void test21() {
        Assert.assertArrayEquals(new String[]{"db","(","%","+","1",")","*","(","%%","+","1",")","/","(","%","%","8",")"}, tokenize("db (%+1)*(%%+1)/(% % 8)"));
    }
    @Test public void test22() {
        Tokenizer.allowAndpersandHex = true;
        Assert.assertArrayEquals(new String[]{"&C0DE"}, tokenize("&C0DE"));
        Tokenizer.allowAndpersandHex = false;
    }
    @Test public void test23() {
        Assert.assertArrayEquals(new String[]{"_main","::"}, tokenize("_main::"));
    }
    @Test public void test24() {
        Assert.assertArrayEquals(new String[]{"ld","hl",",","#0x0000"}, tokenize("ld	hl, #0x0000"));
    }
    @Test public void test25() {
        Tokenizer.sdccStyleHashMarksForConstants = true;
        Assert.assertArrayEquals(new String[]{"ld","hl",",","#","0x0000"}, tokenize("ld	hl, #0x0000"));
        Tokenizer.sdccStyleHashMarksForConstants = false;
    }
    @Test public void test26() {
        Tokenizer.sdccStyleDollarInLabels = true;
        Assert.assertArrayEquals(new String[]{"00102$",":"}, tokenize("00102$:"));
        Tokenizer.sdccStyleDollarInLabels = true;
    }
    @Test public void test27() {
        Tokenizer.stringEscapeSequences.put("\\", "\\");
        Tokenizer.stringEscapeSequences.put("\"", "\"");
        Assert.assertArrayEquals(new String[]{"db", "\"\"\"", ",", "\"~\""}, tokenize("db \"\\\"\", \"~\""));
        Tokenizer.stringEscapeSequences.clear();
    }

    
    private static String[] tokenize(String line)
    {
        List<String> tokens = Tokenizer.tokenize(line);
        return tokens != null
                ? tokens.toArray(new String[0])
                : null;
    }
}
