// Generated by optgen from KjcOptions.opt
package at.dms.kjc;

import gnu.getopt.Getopt;
import gnu.getopt.LongOpt;

public class KjcOptions extends at.dms.util.Options {

    public KjcOptions(String name) {
	super(name);
    }

    public KjcOptions() {
	this("Kjc");
    }

    public int raw = -1;
    public boolean constprop = false;
    public boolean unroll = false;
    public boolean fusion = false;
    public boolean partition = false;
    public boolean streamit = false;
    public boolean beautify = false;
    public boolean verbose = false;
    public boolean java = false;
    public String encoding = null;
    public boolean nowrite = false;
    public int warning = 1;
    public boolean nowarn = false;
    public int optimize = 1;
    public boolean multi = false;
    public boolean deprecation = false;
    public int proc = 2;
    public String destination = null;
    public String classpath = null;
    public boolean debug = false;
    public String lang = "1.1";
    public String filter = "at.dms.kjc.DefaultFilter";
    
    public boolean processOption(int code, Getopt g) {
	switch (code) {
	case 'r':
	    raw = getInt(g, 16) ; return true;
	case 'c':
	    constprop = !false; return true;
	case 'u':
	    unroll = !false; return true;
	case 'o':
	    fusion = !false; return true;
	case 'a':
	    partition = !false; return true;
	case 's':
	    streamit = !false; return true;
	case 'b':
	    beautify = !false; return true;
	case 'v':
	    verbose = !false; return true;
	case 'j':
	    java = !false; return true;
	case 'e':
	    encoding = getString(g, ""); return true;
	case 'n':
	    nowrite = !false; return true;
	case 'w':
	    warning = getInt(g, 2); return true;
	case '*':
	    nowarn = !false; return true;
	case 'O':
	    optimize = getInt(g, 2); return true;
	case 'm':
	    multi = !false; return true;
	case 'D':
	    deprecation = !false; return true;
	case 'p':
	    proc = getInt(g, 0); return true;
	case 'd':
	    destination = getString(g, ""); return true;
	case 'C':
	    classpath = getString(g, ""); return true;
	case 'g':
	    debug = !false; return true;
	case 'l':
	    lang = getString(g, ""); return true;
	case 'f':
	    filter = getString(g, ""); return true;
	default:
	    return super.processOption(code, g);
	}
    }

    public String[] getOptions() {
	String[]	parent = super.getOptions();
	String[]	total = new String[parent.length + 22];
	System.arraycopy(parent, 0, total, 0, parent.length);
	total[parent.length + 0] = "  --beautify, -b:       Beautifies the source code [false]";
	total[parent.length + 1] = "  --verbose, -v:        Prints out information during compilation [false]";
	total[parent.length + 2] = "  --java, -j:           Generates java source code instead of class [false]";
	total[parent.length + 3] = "  --encoding, -e<String>: Sets the character encoding for the source file(s).";
	total[parent.length + 4] = "  --nowrite, -n:        Only checks files, doesn't generate code [false]";
	total[parent.length + 5] = "  --warning, -w<int>:   Maximal level of warnings to be displayed [1]";
	total[parent.length + 6] = "  --nowarn, -*:         Not used, for compatibility purpose [false]";
	total[parent.length + 7] = "  --optimize, -O<int>:  Optimizes X times [1]";
	total[parent.length + 8] = "  --multi, -m:          Compiles in multi threads mode [false]";
	total[parent.length + 9] = "  --deprecation, -D:    Tests for deprecated members [false]";
	total[parent.length + 10] = "  --proc, -p<int>:      Maximal number of threads to use [2]";
	total[parent.length + 11] = "  --destination, -d<String>: Writes files to destination";
	total[parent.length + 12] = "  --classpath, -C<String>: Changes class path to classpath";
	total[parent.length + 13] = "  --debug, -g:          Produces debug information (does nothing yet) [false]";
	total[parent.length + 14] = "  --lang, -l<String>:   Sets the source language (1.1, 1.2, kopi) [1.1]";
	total[parent.length + 15] = "  --filter, -f<String>: Warning filter [at.dms.kjc.DefaultFilter]";
	total[parent.length + 16] = "  --streamit, -s:       Turns on StreaMIT mode [false]";    

	total[parent.length + 17] = "  --raw, -r<int>:            Compile for RAW with a square layout, with <int> tiles per side";
	total[parent.length + 18] = "  --constprop, -c:       Turns on StreamIt Constant Prop";
	total[parent.length + 19] = "  --unroll, -u:          StreamIt Unroll";
	total[parent.length + 20] = "  --fusion, -o:          Perform filter fusion";
	total[parent.length + 21] = "  --partition, -a:       Automatically partition stream graph";
	return total;
    }


    public String getShortOptions() {
	return "acuosbvje:nw::*O::mDp:d:C:gl:f:" + super.getShortOptions();
    }


    public void version() {
	System.out.println("Version 1.5B released 9 August 2001");
    }


    public void usage() {
	System.err.println("usage: at.dms.kjc.Main [option]* [--help] <java-files>");
    }


    public void help() {
	System.err.println("usage: at.dms.kjc.Main [option]* [--help] <java-files>");
	printOptions();
	System.err.println();
	version();
	System.err.println();
	System.err.println("This program is part of the Kopi Suite.");
	System.err.println("For more info, please see: http://www.dms.at/kopi");
    }

    public LongOpt[] getLongOptions() {
	LongOpt[]	parent = super.getLongOptions();
	LongOpt[]	total = new LongOpt[parent.length + LONGOPTS.length];
    
	System.arraycopy(parent, 0, total, 0, parent.length);
	System.arraycopy(LONGOPTS, 0, total, parent.length, LONGOPTS.length);
    
	return total;
    }

    private static final LongOpt[] LONGOPTS = {
	new LongOpt("streamit", LongOpt.NO_ARGUMENT, null, 's'),
	new LongOpt("beautify", LongOpt.NO_ARGUMENT, null, 'b'),
	new LongOpt("verbose", LongOpt.NO_ARGUMENT, null, 'v'),
	new LongOpt("java", LongOpt.NO_ARGUMENT, null, 'j'),
	new LongOpt("encoding", LongOpt.REQUIRED_ARGUMENT, null, 'e'),
	new LongOpt("nowrite", LongOpt.NO_ARGUMENT, null, 'n'),
	new LongOpt("warning", LongOpt.OPTIONAL_ARGUMENT, null, 'w'),
	new LongOpt("nowarn", LongOpt.NO_ARGUMENT, null, '*'),
	new LongOpt("optimize", LongOpt.OPTIONAL_ARGUMENT, null, 'O'),
	new LongOpt("multi", LongOpt.NO_ARGUMENT, null, 'm'),
	new LongOpt("deprecation", LongOpt.NO_ARGUMENT, null, 'D'),
	new LongOpt("proc", LongOpt.REQUIRED_ARGUMENT, null, 'p'),
	new LongOpt("destination", LongOpt.REQUIRED_ARGUMENT, null, 'd'),
	new LongOpt("classpath", LongOpt.REQUIRED_ARGUMENT, null, 'C'),
	new LongOpt("debug", LongOpt.NO_ARGUMENT, null, 'g'),
	new LongOpt("lang", LongOpt.REQUIRED_ARGUMENT, null, 'l'),
	new LongOpt("filter", LongOpt.REQUIRED_ARGUMENT, null, 'f'),
	new LongOpt("constprop", LongOpt.NO_ARGUMENT, null, 'c'),
	new LongOpt("unroll", LongOpt.NO_ARGUMENT, null, 'u'),
	new LongOpt("fusion", LongOpt.NO_ARGUMENT, null, 'o'),
	new LongOpt("partition", LongOpt.NO_ARGUMENT, null, 'a'),
	new LongOpt("raw", LongOpt.REQUIRED_ARGUMENT, null, 'r')
    };
}
