package edu.stonybrook.bmi.hatch;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.imageio.stream.ImageInputStream;

/**
 *
 * @author erich
 */
public class Validate2 {
    
    public static boolean file(Path path) {
        Logger LOGGER = Logger.getLogger(Validate2.class.getName());
        javax.imageio.ImageReader reader = null;
        ImageInputStream input;
        try {
            input = ImageIO.createImageInputStream(path.toFile());
            Iterator<javax.imageio.ImageReader> readers = ImageIO.getImageReadersByFormatName("tif");
            javax.imageio.ImageReader ir = null;
            while (readers.hasNext()) {
                ir = readers.next();
                //System.out.println("IMAGE CLASS --> "+ir.getClass().getCanonicalName());
                if ("com.twelvemonkeys.imageio.plugins.tiff.TIFFImageReader".equals(ir.getClass().getCanonicalName())) {
                    //System.out.println("YES! : "+ir.getClass().getCanonicalName());
                    reader = ir;
                } else {
                    //System.out.println("NOPE : "+ir.getClass().getCanonicalName());
                }
            }
            if (ir==null) {
                throw new IllegalArgumentException("No reader for: " + path);
            }
            //System.out.println("READER IS ---> "+reader.getClass().getCanonicalName());
            reader.setInput(input);
            try {
                if (reader.getWidth(0)==0) {
                    LOGGER.severe(path.toString()+" X dimension is ZERO");
                    return false;
                }
            } catch (IOException ex) {
                Logger.getLogger(Validate2.class.getName()).log(Level.SEVERE, null, ex);
                return false;
            }
            try {
                if (reader.getHeight(0)==0) {
                    LOGGER.severe(path.toString()+" Y dimension is ZERO");
                    return false;
                }
            } catch (IOException ex) {
                LOGGER.severe(ex.getMessage());
                return false;
            }
            try {
                int last = reader.getNumImages(true)-1;
                if ((reader.getWidth(last)>1024)||(reader.getHeight(last)>1024)) {
                    LOGGER.severe(path.toString()+" smallest scaled image ("+reader.getNumImages(true)+" - "+reader.getWidth(last)+"x"+reader.getHeight(last)+") must be less than 1024x1024");
                    return false; 
                }
            } catch (IOException ex) {
                LOGGER.severe(ex.getMessage());
                return false;
            }
        } catch (IOException ex) {
            LOGGER.severe(ex.getMessage());
            return false;
        }
        return true;
    }   
}
