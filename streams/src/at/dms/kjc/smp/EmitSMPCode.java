package at.dms.kjc.smp;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

import at.dms.kjc.JEmittedTextExpression;
import at.dms.kjc.JExpression;
import at.dms.kjc.JExpressionStatement;
import at.dms.kjc.JFieldDeclaration;
import at.dms.kjc.JIntLiteral;
import at.dms.kjc.JMethodCallExpression;
import at.dms.kjc.JMethodDeclaration;
import at.dms.kjc.JThisExpression;
import at.dms.kjc.JStatement;
import at.dms.kjc.backendSupport.ComputeCodeStore;
import at.dms.kjc.backendSupport.ComputeNode;
import at.dms.kjc.backendSupport.EmitCode;
import at.dms.kjc.common.CodegenPrintWriter;
import at.dms.kjc.sir.SIRCodeUnit;
import at.dms.kjc.KjcOptions;

/**
 * Emit c code for tiles
 * 
 * @author mgordon
 *
 */
public class EmitSMPCode extends EmitCode {
    
    public static final String MAIN_FILE = "main.c";
    
    public EmitSMPCode(SMPBackEndFactory backendBits) {
        super(backendBits);
    }
    
    public static void doit(SMPBackEndFactory backendBits) {
        try {
            // generate the makefile that will compile all the tile executables
            generateMakefile();
            
            // generate header containing code to get clock cycles
            generateClockHeader();
            
            // generate header containing barrier implementation
            generateBarrierHeader();
            
            // add stats useful for performance debugging
            if(KjcOptions.debug) {
            	for (Core core : SMPBackend.chip.getCores()) {
                    if (!core.getComputeCode().shouldGenerateCode())
                        continue;

                    core.getComputeCode().addSteadyLoopStatementFirst(Util.toStmt("start_times[" + core.getCoreID() + "][perfstats_iter_n" + core.getCoreID() + "] = rdtsc()"));
            		
                    if(KjcOptions.smp > 1)
                        core.getComputeCode().addSteadyLoopStatement(Util.toStmt("barrier_times[" + core.getCoreID() + "][perfstats_iter_n" + core.getCoreID() + "] = rdtsc()"));
            	}
            }
            
            // for all the cores, add a barrier at the end of the steady state, do it here because we are done
            // with all code gen
            CoreCodeStore.addBarrierSteady();
            
            // add more stats useful for performance debugging
            if(KjcOptions.debug) {
            	for (Core core : SMPBackend.chip.getCores()) {
                    if (!core.getComputeCode().shouldGenerateCode())
                        continue;

            		core.getComputeCode().addSteadyLoopStatement(Util.toStmt("end_times[" + core.getCoreID() + "][perfstats_iter_n" + core.getCoreID() + "] = rdtsc()"));
                    core.getComputeCode().addSteadyLoopStatement(Util.toStmt("perfstats_iter_n" + core.getCoreID() + "++"));
            	}

                JExpression[] perfStatsOutputArgs = new JExpression[0];
                SMPBackend.chip.getNthComputeNode(0).getComputeCode()
                    .addCleanupStatement(new JExpressionStatement(null, 
                                                new JMethodCallExpression(null, new JThisExpression(null),
                                                        "perfStatsOutput", perfStatsOutputArgs),
                                                         null));
            }

            for (Core core : SMPBackend.chip.getCores()) {
                if (!core.getComputeCode().shouldGenerateCode())
                    continue;

                core.getComputeCode().addCleanupStatement(Util.toStmt("pthread_exit(NULL)"));
            }

            // call to buffer initialization and CPU affinity setting
            for (Core core : SMPBackend.chip.getCores()) {
                if (!core.getComputeCode().shouldGenerateCode())
                    continue;

                core.getComputeCode().addFunctionCallFirst(core.getComputeCode().getBufferInitMethod(), new JExpression[0]);
                
                if(!KjcOptions.nobind) {
                    JExpression[] setAffinityArgs = new JExpression[1];
                    setAffinityArgs[0] = new JIntLiteral(core.getCoreID());
                    core.getComputeCode().addFunctionCallFirst("setCPUAffinity", setAffinityArgs);
                }
            }

            // make sure that variables and methods are unique across cores            
            List<ComputeCodeStore<?>> codeStores = new LinkedList<ComputeCodeStore<?>>();
            for (Core core : SMPBackend.chip.getCores()) {
                if (!core.getComputeCode().shouldGenerateCode())
                    continue;
                
                codeStores.add(core.getComputeCode());
            }
            
            CodeStoreRenameAll.renameOverAllCodeStores(codeStores);
            
            // write out C code
            CodegenPrintWriter p = new CodegenPrintWriter(new BufferedWriter(new FileWriter(MAIN_FILE, false)));
            
            EmitSMPCode codeEmitter = new EmitSMPCode(backendBits);
            
            codeEmitter.generateCHeader(p);
            codeEmitter.generateSharedGlobals(p);

            if(KjcOptions.debug)
                codeEmitter.generatePerfStatsOutput(p);

            if(!KjcOptions.nobind)
                codeEmitter.generateSetAffinity(p);
                        
            for (Core core : SMPBackend.chip.getCores()) {
                // if no code was written to this core's code store, then skip it
                if (!core.getComputeCode().shouldGenerateCode())
                    continue;
    
                codeEmitter.emitCodeForComputeNode(core, p);
            }

            codeEmitter.generateMain(p);

            p.close();
            
        } catch (IOException e) {
            throw new AssertionError("I/O error" + e);
        }
    }
    
    public void generateCHeader(CodegenPrintWriter p) {
        generateIncludes(p);
        
        //THE NUMBER OF SS ITERATIONS FOR EACH cycle counting block 
        p.println("#define ITERATIONS " + KjcOptions.numbers);   
    }
    
    /**
     * Standard code for front of a C file here.
     * 
     */
    public static void generateIncludes(CodegenPrintWriter p) {
    	p.println("#ifndef _GNU_SOURCE");
    	p.println("#define _GNU_SOURCE");
    	p.println("#endif");
    	p.println();
        p.println("#include <stdio.h>");    // in case of FileReader / FileWriter
        p.println("#include <math.h>");     // in case math functions
        p.println("#include <stdlib.h>");
        p.println("#include <unistd.h>");
        p.println("#include <fcntl.h>");
        p.println("#include <pthread.h>");
        p.println("#include <sched.h>");
        p.println("#include <sys/types.h>");
        p.println("#include <sys/stat.h>");
        p.println("#include <sys/mman.h>");
        p.println("#include <string.h>");

        p.println("#include \"barrier.h\"");
        p.println("#include \"rdtsc.h\"");

        if (KjcOptions.fixedpoint)
            p.println("#include \"fixed.h\"");
        p.println("#include \"structs.h\"");
        if (KjcOptions.profile)
            p.println("#include <sys/profiler.h>");

        p.newLine();
        p.newLine();
    }

    public void generateSharedGlobals(CodegenPrintWriter p) {
        p.println();

        if(KjcOptions.iterations != -1) {
            p.println("// Number of steady-state iterations");
            p.println("int maxSteadyIter = " + KjcOptions.iterations + ";");
            p.println();
        }

        if(KjcOptions.debug) {
            assert(KjcOptions.iterations != -1);

            p.println("// Debugging stats");
            p.println("uint64_t start_times[" + KjcOptions.smp + "][" + KjcOptions.iterations + "];");
            p.println("uint64_t barrier_times[" + KjcOptions.smp + "][" + KjcOptions.iterations + "];");
            p.println("uint64_t end_times[" + KjcOptions.smp + "][" + KjcOptions.iterations + "];");
            p.println();

            p.println("int perfcore_iter;");
            for (Core core : SMPBackend.chip.getCores()) {
                if (!core.getComputeCode().shouldGenerateCode())
                    continue;

                p.println("int perfstats_iter_n" + core.getCoreID() + " = 0;");
            }

            p.println();
        }

        p.println("// Global barrier");
        p.println("barrier_t barrier;");
        p.println();
        p.println("// Shared buffers");
        p.println(SMPBackend.chip.getOffChipMemory().getComputeCode().getGlobalText());
    }
    
    private static void generateMakefile() throws IOException {
        CodegenPrintWriter p = new CodegenPrintWriter(new BufferedWriter(new FileWriter("Makefile", false)));

        p.println("CC = icc");
        p.println("CFLAGS = -O2");
        p.println("LIBS = -pthread");
        p.println();
        p.println("all: main.c");
        p.println("\t$(CC) $(CFLAGS) $(LIBS) -o smp" + KjcOptions.smp + " main.c");
        p.println("debug: main.c");
        p.println("\t$(CC) $(CFLAGS) $(LIBS) -g -o smp" + KjcOptions.smp + "_debug main.c");
        p.println();
        p.close();

    }
    
    private static void generateClockHeader() throws IOException {
        CodegenPrintWriter p = new CodegenPrintWriter(new BufferedWriter(new FileWriter("rdtsc.h", false)));
        
        p.println("#ifndef RDTSC_H");
        p.println("#define RDTSC_H");
        p.println("");
        p.println("#include <stdint.h>");
        p.println("");
        p.println("/* Returns the number of clock cycles that have passed since the machine");        
        p.println(" * booted up. */");
        p.println("static __inline__ uint64_t rdtsc(void)");
        p.println("{");
        p.println("  uint32_t hi, lo;");
        p.println("  asm volatile (\"rdtsc\" : \"=a\"(lo), \"=d\"(hi));");
        p.println("  return ((uint64_t ) lo) | (((uint64_t) hi) << 32);");
        p.println("}");
        p.println("");
        p.println("#endif");
        p.close();
    }
    
    private static void generateBarrierHeader() throws IOException {
        CodegenPrintWriter p = new CodegenPrintWriter(new BufferedWriter(new FileWriter("barrier.h", false)));
        
        p.println("#ifndef BARRIER_H");
        p.println("#define BARRIER_H");
        p.println("");
        p.println("int FetchAndDecr(int *mem)");
        p.println("{");
        p.println("  int val = -1;");
        p.println("");
        p.println("  asm volatile (\"lock; xaddl %0,%1\"");
        p.println("		: \"=r\" (val), \"=m\" (*mem)");
        p.println("		: \"0\" (val), \"m\" (*mem)");
        p.println("		: \"memory\", \"cc\");");
        p.println("  return val;");
        p.println("}");
        p.println("");
        p.println("typedef struct barrier {");
        p.println("  int num_threads;");
        p.println("  int count;");
        p.println("  volatile int generation;");
        p.println("} barrier_t;");
        p.println("");
        p.println("void barrier_init(barrier_t *barrier, int num_threads) {");
        p.println("  barrier->num_threads = num_threads;");
        p.println("  barrier->count = num_threads;");
        p.println("  barrier->generation = 0;");
        p.println("}");
        p.println("");
        p.println("void barrier_wait(barrier_t *barrier) {");
        p.println("  int cur_gen = barrier->generation;");
        p.println("");
        p.println("  if(FetchAndDecr(&barrier->count) == 1) {");
        p.println("    barrier->count = barrier->num_threads;");
        p.println("    barrier->generation++;");
        p.println("  }");
        p.println("  else {");
        p.println("    while(cur_gen == barrier->generation);");
        p.println("  }");
        p.println("}");
        p.println("#endif");
        p.close();
    }
    
    /**
     * Given a ComputeNode and a CodegenPrintWrite, print all code for the ComputeNode.
     * Channel information relevant to the ComputeNode is printed based on data in the
     * BackEndFactory passed when this class was instantiated.
     * 
     * @param n The ComputeNode to emit code for.
     * @param p The CodegenPrintWriter (left open on return).
     */
    public void emitCodeForComputeStore (SIRCodeUnit fieldsAndMethods,
            ComputeNode n, CodegenPrintWriter p, CodeGen codegen) {
        
        // Standard final optimization of a code unit before code emission:
        // unrolling and constant prop as allowed, DCE, array destruction into scalars.
        System.out.println("Optimizing...");
        (new at.dms.kjc.sir.lowering.FinalUnitOptimize()).optimize(fieldsAndMethods);
        
        p.println("// code for tile " + n.getUniqueId());
        
        p.println(((Core)n).getComputeCode().getGlobalText());
        
        // generate function prototypes for methods so that they can call each other
        // in C.
        codegen.setDeclOnly(true);
        for (JMethodDeclaration method : fieldsAndMethods.getMethods()) {
            method.accept(codegen);
        }
        p.println("");
        codegen.setDeclOnly(false);

        // generate code for ends of channels that connect to code on this ComputeNode
        Set <RotatingBuffer> outputBuffers = OutputRotatingBuffer.getOutputBuffersOnCore((Core)n);
        Set <InputRotatingBuffer> inputBuffers = InputRotatingBuffer.getInputBuffersOnCore((Core)n);
        
        // externs
        for (RotatingBuffer c : outputBuffers) {
            if (c.writeDeclsExtern() != null) {
                for (JStatement d : c.writeDeclsExtern()) { d.accept(codegen); }
            }
        }
       
        for (RotatingBuffer c : inputBuffers) {
            if (c.readDeclsExtern() != null) {
                for (JStatement d : c.readDeclsExtern()) { d.accept(codegen); }
            }
        }

        for (RotatingBuffer c : outputBuffers) {
            if (c.dataDecls() != null) {
                // wrap in #ifndef for case where different ends have
                // are in different files that eventually get concatenated.
                p.println();
                p.println("#ifndef " + c.getIdent() + "_CHANNEL_DATA");
                for (JStatement d : c.dataDecls()) { d.accept(codegen); p.println();}
                p.println("#define " + c.getIdent() + "_CHANNEL_DATA");
                p.println("#endif");
            }
        }
        
        for (RotatingBuffer c : inputBuffers) {
            if (c.dataDecls() != null && ! outputBuffers.contains(c)) {
                p.println("#ifndef " + c.getIdent() + "_CHANNEL_DATA");
                for (JStatement d : c.dataDecls()) { d.accept(codegen); p.println();}
                p.println("#define " + c.getIdent() + "_CHANNEL_DATA");
                p.println("#endif");
            }
        }

        for (RotatingBuffer c : outputBuffers) {
            p.println("/* output buffer " + "(" + c.getIdent() + " of " + c.getFilterNode() + ") */");
            if (c.writeDecls() != null) {
                for (JStatement d : c.writeDecls()) { d.accept(codegen); p.println();}
            }
            if (c.pushMethod() != null) { c.pushMethod().accept(codegen); }
        }

        for (RotatingBuffer c : inputBuffers) {
            p.println("/* input buffer (" + c.getIdent() + " of " + c.getFilterNode() + ") */");
            if (c.readDecls() != null) {
                for (JStatement d : c.readDecls()) { d.accept(codegen); p.println();}
            }
            if (c.peekMethod() != null) { c.peekMethod().accept(codegen); }
            if (c.assignFromPeekMethod() != null) { c.assignFromPeekMethod().accept(codegen); }
            if (c.popMethod() != null) { c.popMethod().accept(codegen); }
            if (c.assignFromPopMethod() != null) { c.assignFromPopMethod().accept(codegen); }
            if (c.popManyMethod() != null) { c.popManyMethod().accept(codegen); }
         }
        p.println("");
        
        // generate declarations for fields
        for (JFieldDeclaration field : fieldsAndMethods.getFields()) {
            field.accept(codegen);
        }
        p.println("");
        
        //handle the buffer initialization method separately because we do not want it
        //optimized (it is not in the methods list of the code store
        ((Core)n).getComputeCode().getBufferInitMethod().accept(codegen);
        
        // generate functions for methods
        codegen.setDeclOnly(false);
        for (JMethodDeclaration method : fieldsAndMethods.getMethods()) {
            method.accept(codegen);
        }
    }
    
    public void generatePerfStatsOutput(CodegenPrintWriter p) {
        p.println();
        p.println("void perfStatsOutput() {");
        p.indent();
        p.println("int core, iter;");
        p.println("uint64_t min_start, min_barrier, min_end;");
        p.println("uint64_t work_time, barrier_time;");
        p.println("float aggregate_work_percentage = 0;");
        p.println("uint64_t core_work_totals[" + KjcOptions.smp + "];");
        p.println();

        p.println("for (core = 0 ; core < " + KjcOptions.smp + " ; core++)");
        p.indent();
        p.println("core_work_totals[core] = 0;");
        p.outdent();
        p.println();

        p.println("for (iter = 0 ; iter < " + KjcOptions.iterations + " ; iter++) {");
        p.indent();

        p.println("printf(\"Steady-state iteration: %d\\n\", iter);");
        p.println("printf(\"=======================\\n\");");
        p.println();

        p.println("min_start = -1;");
        p.println("for (core = 0 ; core < " + KjcOptions.smp + " ; core++)");
        p.indent();
        p.println("if (start_times[core][iter] < min_start)");
        p.indent();
        p.println("min_start = start_times[core][iter];");
        p.outdent();
        p.outdent();
        p.println();

        p.println("min_barrier = -1;");
        p.println("for (core = 0 ; core < " + KjcOptions.smp + " ; core++)");
        p.indent();
        p.println("if (barrier_times[core][iter] < min_barrier)");
        p.indent();
        p.println("min_barrier = barrier_times[core][iter];");
        p.outdent();
        p.outdent();
        p.println();

        p.println("barrier_time = 0;");
        p.println("for (core = 0 ; core < " + KjcOptions.smp + " ; core++)");
        p.indent();
        p.println("if (end_times[core][iter] - start_times[core][iter] > barrier_time)");
        p.indent();
        p.println("barrier_time = end_times[core][iter] - start_times[core][iter];");
        p.outdent();
        p.outdent();
        p.println();

        p.println("work_time = 0;");
        p.println("for (core = 0 ; core < " + KjcOptions.smp + " ; core++)");
        p.indent();
        p.println("work_time += barrier_times[core][iter] - start_times[core][iter];");
        p.outdent();
        p.println();

        p.println("min_end = -1;");
        p.println("for (core = 0 ; core < " + KjcOptions.smp + " ; core++)");
        p.indent();
        p.println("if (end_times[core][iter] < min_end)");
        p.indent();
        p.println("min_end = end_times[core][iter];");
        p.outdent();
        p.outdent();
        p.println();

        p.println("for (core = 0 ; core < " + KjcOptions.smp + " ; core++)");
        p.indent();
        p.println("core_work_totals[core] += barrier_times[core][iter] - start_times[core][iter];");
        p.outdent();
        p.println();

        p.println("for (core = 0 ; core < " + KjcOptions.smp + " ; core++)");
        p.indent();
        p.println("printf(\"Thread %3d, start:   %10llu            %llu\\n\", core, start_times[core][iter] - min_start, start_times[core][iter]);");
        p.outdent();
        p.println();

        p.println("for (core = 0 ; core < " + KjcOptions.smp + " ; core++)");
        p.indent();
        p.println("printf(\"Thread %3d, barrier: %10llu %10llu %llu\\n\", core, barrier_times[core][iter] - min_barrier, barrier_times[core][iter] - start_times[core][iter], barrier_times[core][iter]);");
        p.outdent();
        p.println();

        p.println("for (core = 0 ; core < " + KjcOptions.smp + " ; core++)");
        p.indent();
        p.println("printf(\"Thread %3d, end:     %10llu %10llu %llu\\n\", core, end_times[core][iter] - min_end, end_times[core][iter] - start_times[core][iter], end_times[core][iter]);");
        p.outdent();
        p.println();

        p.println("printf(\"\\n\");");

        p.println("printf(\"Total work: %llu\\n\", work_time);");
        p.println("printf(\"Total time: %llu\\n\", barrier_time * " + KjcOptions.smp + ");");
        p.println("printf(\"Work percentage: %f\\n\", ((float)work_time / (float)(barrier_time * " + KjcOptions.smp + ")) * 100);");
        p.println("printf(\"\\n\");");
        p.println();

        p.println("aggregate_work_percentage += ((float)work_time / (float)(barrier_time * " + KjcOptions.smp + "));");

        p.outdent();
        p.println("}");
        p.println();

        p.println("printf(\"Aggregate stats\\n\");");
        p.println("printf(\"===============\\n\");");
        p.println("printf(\"Aggregate work percentage: %f\\n\", (aggregate_work_percentage / " + KjcOptions.iterations + ") * 100);");
        p.println("for (core = 0 ; core < " + KjcOptions.smp + " ; core++)");
        p.indent();
        p.println("printf(\"Core %d avg work per steady state: %llu\\n\", core, core_work_totals[core] / " + KjcOptions.iterations + ");");
        p.outdent();

        p.outdent();
        p.println("}");
    }

    public void generateSetAffinity(CodegenPrintWriter p) {
        p.println();
        p.println("void setCPUAffinity(int core) {");
        p.indent();
        
        p.println("cpu_set_t cpu_set;");
        p.println("CPU_ZERO(&cpu_set);");
        p.println("CPU_SET(core, &cpu_set);");
        p.println();
        
        p.println("if(pthread_setaffinity_np(pthread_self(), sizeof(cpu_set_t), &cpu_set) < 0) {");
        p.indent();
        p.println("printf(\"Error setting pthread affinity\\n\");");
        p.println("exit(-1);");
        p.outdent();
        p.println("}");
        
        p.outdent();
        p.println("}");
    }
    
    /**
     * Generate a "main" function for the current tile (this is used for filter code).
     */
    public void generateMain(CodegenPrintWriter p) {
        p.println();
        p.println();
        p.println("// main() Function Here");
        // dumb template to override
        p.println("int main(int argc, char** argv) {");
        p.indent();

        // figure out how many cores will participate in barrier
        int barrier_count = 0;
        for(Core core : SMPBackend.chip.getCores())
            if(core.getComputeCode().shouldGenerateCode())
                barrier_count++;

        p.println();
        p.println("// Initialize barrier");
        p.println("barrier_init(&barrier, " + barrier_count + ");");
        
        p.println();
        p.println("// Spawn threads");
        p.println("int rc;");
        p.println("pthread_attr_t attr;");
        p.println("void *status;");
        p.println();
        p.println("pthread_attr_init(&attr);");
        p.println("pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_JOINABLE);");

        for(Core core : SMPBackend.chip.getCores()) {
            if(!core.getComputeCode().shouldGenerateCode())
                continue;

            p.println();
            p.println("pthread_t thread_n" + core.getCoreID() + ";");
            p.println("if ((rc = pthread_create(&thread_n" + core.getCoreID() + ", NULL, " +
                    core.getComputeCode().getMainFunction().getName() + ", (void *)NULL)) < 0)");
            p.indent();
            p.println("printf(\"Error creating thread for core " + core.getCoreID() + ": %d\\n\", rc);");
            p.outdent();
        }
        
        p.println();
        p.println("pthread_attr_destroy(&attr);");
        
        for(Core core : SMPBackend.chip.getCores()) {
            if(!core.getComputeCode().shouldGenerateCode())
                continue;

            p.println();
            p.println("if ((rc = pthread_join(thread_n" + core.getCoreID() + ", &status)) < 0) {");
            p.indent();
            p.println("printf(\"Error joining thread for core " + core.getCoreID() + ": %d\\n\", rc);");
            p.println("exit(-1);");
            p.outdent();
            p.println("}");
        }
        
        p.println();
        p.println("// Exit");
        p.println("pthread_exit(NULL);");
        p.outdent();
        p.println("}");
    }
}