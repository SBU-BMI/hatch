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

    @Parameter(names = {"-v","-verbose"})
    public boolean verbose = false;

    @Parameter(names = {"-o","-overwrite"})
    public boolean overwrite = false;
    
    @Parameter(names = {"-r","-retry"})
    public boolean retry = false;
    
    @Parameter(names = {"-validate"})
    public boolean validate = false;
    
    @Parameter(names = "-jp2", hidden = true)
    public boolean jp2 = false;
    
    @Parameter(names = {"-quality","-q"}, description = "pyramid JPEG compression quality 0.0 < q <1.0")
    public float quality = 1.0f;
    
    @Parameter(names = {"-s","-series"}, description = "specify source series separated by commas")
    public List<String> series = Lists.newArrayList();
    
}