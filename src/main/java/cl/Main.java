/*
 * author: Santiago Ontañón Villar (Brain Games)
 */
package cl;

import workers.DotGenerator;
import code.CodeBase;
import workers.AnnotatedSourceCodeGenerator;
import workers.pattopt.PatternBasedOptimizer;
import workers.SourceCodeGenerator;
import workers.SourceCodeTableGenerator;
import workers.SymbolTableGenerator;

public class Main {
    
    public static String VERSION_STRING = "v1.2";

    public static void main(String args[]) throws Exception {
        // Set up the MDL configuration:
        MDLConfig config = new MDLConfig();

        // Add the workers in the order in which they should be executed:
        config.registerWorker(new PatternBasedOptimizer(config));
        config.registerWorker(new DotGenerator(config));
        config.registerWorker(new SymbolTableGenerator(config));
        config.registerWorker(new SourceCodeTableGenerator(config));
        config.registerWorker(new SourceCodeGenerator(config));
        config.registerWorker(new AnnotatedSourceCodeGenerator(config));

        // Parse command line arguments:
        if (!config.parseArgs(args)) System.exit(1);
        
        // If there is nothing to do, just terminate:
        if (!config.somethingToDo()) return;

        // Parse the code base:
        CodeBase code = new CodeBase(config);
        if (!config.codeBaseParser.parseMainSourceFile(config.inputFile, code)) System.exit(2);
        
        // Execute all the requested workers according to the command-line arguments:
        if (!config.executeWorkers(code)) System.exit(3);
    }
}
