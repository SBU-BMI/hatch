package edu.stonybrook.bmi.hatch;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 *
 * @author erich
 */
public class Hatch {    
    public static String software = "hatch 4.1.0 by Wing-n-Beak";
    private static final String[] ext = new String[] {".vsi", ".svs", ".tif"};
    private static final String errorlog = "error.log";
    private static final Logger LOGGER;
    public static final String HELP = Hatch.software+"\n"+
        """
        usage: hatch <src> <dest>
        -v : verbose
        """;
    
    static {
         try {
             LogManager.getLogManager().readConfiguration(Hatch.class.getResourceAsStream("/logging.properties"));
         } catch (IOException | SecurityException | ExceptionInInitializerError ex) {
             Logger.getLogger(Hatch.class.getName()).log(Level.SEVERE, "Failed to read logging.properties file", ex);
         }
         LOGGER = Logger.getLogger(Hatch.class.getName());
     }

    public Hatch() {}
    
    private static void Traverse(HatchParameters params) {
        Path s = params.src.toPath();
        Path d = params.dest.toPath();
        ThreadPoolExecutor engine = new ThreadPoolExecutor(params.fp,params.fp,0L,TimeUnit.MILLISECONDS,new LinkedBlockingQueue<>());
        engine.prestartAllCoreThreads();
        try (Stream<Path> X = Files.walk(params.src.toPath())) {
            X
                .filter(Objects::nonNull)
                .filter(path->{
                    if (params.filter==null) {
                        return true;
                    }
                    return path.toString().contains(params.filter);
                })
                .filter(fff -> {
                    for (String ext1 : ext) {
                        if (fff.toFile().toString().toLowerCase().endsWith(ext1)) {
                            return true;
                        }
                    } 
                    return false;
                })    
                .forEach(f->{
                    String frag = s.relativize(f).toString();
                    frag = frag.substring(0,frag.length()-4)+".tif";
                    Path t = Path.of(d.toString(), frag);
                    if (t.toFile().exists()&&params.overwrite) {
                        t.toFile().delete();
                    }
                    engine.submit(new FileProcessor(params, f, t));                  
                });
        } catch (IOException ex) {            
            LOGGER.log(Level.SEVERE, "FILE PROCESSOR ERROR --> {0} {1} {2}", new Object[]{params.src.toString(), params.dest.toString(), ex.toString()});
        }
        int cc = engine.getActiveCount()+engine.getQueue().size();
        while ((engine.getActiveCount()+engine.getQueue().size())>0) {
            int curr = engine.getActiveCount()+engine.getQueue().size();
            if (cc!=curr) {
                cc=curr;
                if (params.verbose) LOGGER.log(Level.INFO, "All jobs submitted...waiting for {0}", curr);
            }
        }
        LOGGER.info("Engine shutdown");
        engine.shutdown();
    }
    
    private static String getFileNameBase(File file) {
        String tail = file.getName();
        return tail.substring(0,tail.length()-4);
    }
    
    public static void main(String[] args) {
        LOGGER.setLevel(Level.SEVERE);
        loci.common.DebugTools.setRootLevel("WARN");
        if (args.length==0) {
            System.out.println("please specify parameters.  Try 'hatch -help' for help! :-)");
            System.exit(0);
        }
        HatchParameters params = new HatchParameters();
        JCommander jc = JCommander.newBuilder().addObject(params).build();
        jc.setProgramName(Hatch.software+"\nhatch");
        try {
            jc.parse(args);
            LOGGER.log(Level.INFO,params.toString());
            if (params.verbose) {
                LOGGER.setLevel(Level.INFO);
            }
            if (params.isHelp()) {
                jc.usage();
                System.exit(0);
            } else if (params.src.exists()) {
                if (params.src.isDirectory()) {
                    if (!params.dest.exists()) {
                        params.dest.mkdir();
                    }
                    if (!params.dest.isDirectory()) {
                        jc.usage();
                    } else {
                        params.series.clear();  // ignore series parameter
                        Traverse(params);
                    }
                } else {
                    // Source is a single file
                    if (!params.dest.exists()&&(!params.dest.toString().toLowerCase().endsWith(".tif"))) {
                        params.dest.mkdirs();
                    }
                    if (params.dest.exists()) {
                        if (!params.dest.isDirectory()) {
                            params.dest.delete();
                            if (params.series.size()>1) {
                                jc.usage();
                            } else {
                                Integer series = null;
                                if (params.series.size()==1) {
                                    series = Integer.valueOf(params.series.get(0));
                                }
                                try (X2TIF v2t = new X2TIF(params, params.src.toString(), params.dest.toString(), series)){
                                    v2t.Execute();
                                } catch (Exception ex) {
                                    LOGGER.log(Level.SEVERE, "FILE PROCESSOR ERROR --> {0} {1} {2}", new Object[]{params.src.toString(), params.dest.toString(), ex.toString()});
                                }
                            }
                        } else {
                            // destination is a directory
                            if (params.series.isEmpty()) {
                                File dest = Path.of(params.dest.toString(),getFileNameBase(params.src)+".tif").toFile();
                                try (X2TIF v2t = new X2TIF(params, params.src.toString(), dest.toString(), null);) {
                                    v2t.Execute();
                                } catch (Exception ex) {
                                    LOGGER.log(Level.SEVERE, "FILE PROCESSOR ERROR --> {0} {1} {2}", new Object[]{params.src.toString(), params.dest.toString(), ex.toString()});
                                }  
                            } else {
                                params.series.forEach(s->{
                                    File dest = Path.of(params.dest.toString(),getFileNameBase(params.src)+"-series-"+s+".tif").toFile();
                                    try (X2TIF v2t = new X2TIF(params, params.src.toString(), dest.toString(), Integer.valueOf(s));) {
                                        v2t.Execute();
                                    } catch (Exception ex) {
                                        LOGGER.log(Level.SEVERE, "FILE PROCESSOR ERROR --> {0} {1} {2}", new Object[]{params.src.toString(), params.dest.toString(), ex.toString()});
                                    }  
                                });
                            }
                        }
                    } else {
                        // Source and destination are a file
                        params.series.clear(); // ignore series parameter
                        try (X2TIF v2t = new X2TIF(params, params.src.toString(), params.dest.toString(), null);) {
                            v2t.Execute();
                        } catch (Exception ex) {
                            LOGGER.log(Level.SEVERE, "FILE PROCESSOR ERROR --> {0} {1} {2}", new Object[]{params.src.toString(), params.dest.toString(), ex.toString()});
                        } 
                    }
                }
            } else {
                LOGGER.log(Level.SEVERE, "{0} does not exist!", params.src.toString());
            }  
        } catch (ParameterException ex) {
            LOGGER.severe(ex.getMessage());
        }
    }
}

class FileProcessor implements Callable<String> {
    private final HatchParameters params;
    private final File src;
    private final File dest;
    private static final Logger LOGGER;

    static {
         try {
             LogManager.getLogManager().readConfiguration(FileProcessor.class.getResourceAsStream("/logging.properties"));
         } catch (IOException | SecurityException | ExceptionInInitializerError ex) {
             Logger.getLogger(FileProcessor.class.getName()).log(Level.SEVERE, "Failed to read logging.properties file", ex);
         }
         LOGGER = Logger.getLogger(FileProcessor.class.getName());
     }
    
    public FileProcessor(HatchParameters params, Path src, Path dest) {
        this.params = params;
        this.src = src.toFile();
        this.dest = dest.toFile();
        //LOGGER = Logger.getLogger(Hatch.class.getName());
    }
    
    @Override
    public String call() {
        if (dest.exists()) {
            if (params.overwrite) {
                dest.delete();
            } else {
                if (params.retry) {
                    if (!Validate.file(dest.toPath())) {                    
                        dest.delete();
                    }
                } else {
                    if (params.validate) {
                        Validate.file(dest.toPath());
                    }
                    return null;
                }
            }
        }
        if ((!dest.exists())&&(!params.validateonly)) {
            dest.getParentFile().mkdirs();
            try (X2TIF v2t = new X2TIF(params, src.toString(), dest.toString(), null)) {
                v2t.Execute();
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, "FILE PROCESSOR ERROR: {0} {1} {2}", new Object[]{src, dest, ex.toString()});
            }        
        }
        if (dest.exists()&&((params.validate)||params.validateonly)) {
            Validate.file(dest.toPath());
        }
        return null;
    }    
}
