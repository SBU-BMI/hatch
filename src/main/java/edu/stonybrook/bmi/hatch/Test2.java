package edu.stonybrook.bmi.hatch;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 *
 * @author erich
 */
public class Test2 {
    
    public static void main(String[] args) throws IOException {
        BufferedImage bi = ImageIO.read(new File("\\boom\\atook.jp2"));
        ImageIO.write(bi, "jpeg", new File("\\boom\\RAY.jpg"));
    }
}
