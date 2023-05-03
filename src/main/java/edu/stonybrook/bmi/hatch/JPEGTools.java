package edu.stonybrook.bmi.hatch;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import loci.common.RandomAccessInputStream;

/**
 *
 * @author erich
 */
public class JPEGTools {
    static byte FF = (byte) 0xff;
    static byte D9 = (byte) 0xd9;
    
    public static void FindEOI(byte[] buf) {
        for (int i=0; i<buf.length; i++) {
            if (Byte.compare(buf[i],FF)==0) {
                if (Byte.compare(buf[i+1],D9)==0) {
                    System.out.println("EOI : "+i+" "+buf.length);
                }
            } else {
            }
        }
    }
    
    //public static int Compare(byte a, byte b) {
//        return 0xff&(a-b);
  //  }

    public static byte[] FindFirstEOI(RandomAccessInputStream ets, byte[] r) throws IOException {
        int c=0;
        long begin = ets.getFilePointer();
        //System.out.println(r.length+" FindFirstEOI : "+begin+" "+ets.isLittleEndian()+" "+ets.length());
        r[0] = ets.readByte();
        while(ets.getFilePointer()<ets.length()) {
            c++;
            r[c] = ets.readByte();
            if (Byte.compare(r[c-1],FF)==0) {
                if (Byte.compare(r[c],D9)==0) {
                    ets.seek(begin);
                    return Arrays.copyOf(r, c);
                }
            }
        }
        return null;
    }
    
    public static byte[] FindFirstEOI(byte[] ets) throws IOException {
        int c=0;
        while(c<ets.length) {
            c++;
            if (Byte.compare(ets[c-1],FF)==0) {
                if (Byte.compare(ets[c],D9)==0) {
                    return Arrays.copyOf(ets, c+1);
                }
            }
        }
        return null;
    }
    
    public static byte[] GetJPG(RandomAccessInputStream ets, long end) throws IOException {
        long begin = ets.getFilePointer();
        byte[] buffer = new byte[(int)(end-begin)];
        ets.read(buffer);
        return buffer;
    }
        
    public static void split2433443343(byte[] buf, int c) {
        try {
            FileOutputStream fos = new FileOutputStream("/vsi/RAH-"+c+".jpg");
            fos.write(buf);
            fos.close();
        } catch (FileNotFoundException ex) {
            Logger.getLogger(JPEGTools.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(JPEGTools.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    public static byte[] Dump2ByteArray(BufferedImage bi, float compression) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageWriter jpgWriter = (ImageWriter) ImageIO.getImageWritersByFormatName("jpg").next();
        ImageWriteParam param = jpgWriter.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setProgressiveMode(ImageWriteParam.MODE_DISABLED);
        param.setCompressionQuality(compression);
        MemoryCacheImageOutputStream outputStream = new MemoryCacheImageOutputStream(baos);
        outputStream.setByteOrder(ByteOrder.LITTLE_ENDIAN);
        jpgWriter.setOutput(outputStream);
        IIOImage outputImage = new IIOImage(bi, null, null);
            try {
                jpgWriter.write(null, outputImage, param);
            } catch (IOException ex) {
                Logger.getLogger(NeoJPEGCodec.class.getName()).log(Level.SEVERE, null, ex);
            }
            jpgWriter.dispose();
        return baos.toByteArray();
    }
}
