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
public class Validate {
    
    public static javax.imageio.ImageReader getReader(Path path) {
        Iterator<javax.imageio.ImageReader> readers = ImageIO.getImageReadersByFormatName("tif");
        while (readers.hasNext()) {
            javax.imageio.ImageReader ir = readers.next();
            if ("com.twelvemonkeys.imageio.plugins.tiff.TIFFImageReader".equals(ir.getClass().getCanonicalName())) {
                return ir;
            } else {
                throw new Error("No Twelve Monkeys.");
            }
        }
        return null;
    }
    
    public static boolean file(Path path) {
        Logger LOGGER = Logger.getLogger(Validate.class.getName());
        javax.imageio.ImageReader reader = getReader(path);
        if (reader==null) {
            throw new IllegalArgumentException("No reader for: " + path);
        }
        try {
            ImageInputStream input = ImageIO.createImageInputStream(path.toFile());
            reader.setInput(input);
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "{0} ==> {1}", new Object[]{path.toString(), ex.getMessage() });
            return false;
        }
        try {
            if (reader.getWidth(0)==0) {
                LOGGER.log(Level.SEVERE, "{0} X dimension is ZERO", path.toString());
                return false;
            }
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "{0} ==> {1}", new Object[]{path.toString(), ex.getMessage() });
            return false;
        }
        try {
            if (reader.getHeight(0)==0) {
                LOGGER.log(Level.SEVERE, "{0} Y dimension is ZERO", path.toString());
                return false;
            }
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "{0} ==> {1}", new Object[]{path.toString(), ex.getMessage() });
            return false;
        }
        try {
            int last = reader.getNumImages(true)-1;
            if ((reader.getWidth(last)>1024)||(reader.getHeight(last)>1024)) {
                LOGGER.log(Level.SEVERE, "{0} smallest scaled image ({1} - {2}x{3}) must be less than 1024x1024", new Object[]{path.toString(), reader.getNumImages(true), reader.getWidth(last), reader.getHeight(last)});
                return false;
            }
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "{0} ==> {1}", new Object[]{path.toString(), ex.getMessage() });
            return false;
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "{0} ==> {1}", new Object[]{path.toString(), ex.getMessage() });
            return false;
        }
        return true;
    }   
}
