package edu.stonybrook.bmi.hatch;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.internal.Lists;
import java.io.File;
import java.util.List;

/**
 *
 * @author erich
 */
public class HatchParameters {
    @Parameter(names = {"-help","-h"}, help = true)
    private boolean help;
    
    public boolean isHelp() {
        return help;
    }
    
    @Parameter(names = "-src", description = "Source Folder or File", required = true)
    public File src;

    @Parameter(names = "-dest", description = "Destination Folder or File", required = true)
    public File dest;  
    
    @Parameter(names = "-fp", description = "# of file processors")
    public int fp = 1;  

    @Parameter(names = "-cores", description = "# of cores for processing")
    public int cores = 1;  
    
    @Parameter
    public List<String> parameters = Lists.newArrayList();
    
    @Parameter(names = {"-v","-verbose"})
    public boolean verbose = false;
    
    @Parameter(names = "-meta")
    public boolean meta = false;
}