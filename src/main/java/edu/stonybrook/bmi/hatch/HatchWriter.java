package edu.stonybrook.bmi.hatch;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import loci.common.RandomAccessOutputStream;
import loci.formats.FormatException;
import loci.formats.codec.CodecOptions;
import loci.formats.tiff.IFD;

/**
 *
 * @author erich
 */
public class HatchWriter implements AutoCloseable {
    private final RandomAccessOutputStream ros;
    private final HatchSaver writer;
    
    public HatchWriter(String file) throws IOException {
        ros = new RandomAccessOutputStream(file);
        writer = new HatchSaver(ros, file);
        writer.setBigTiff(true);
        writer.setLittleEndian(true);
        writer.setWritingSequentially(true);
        writer.setCodecOptions(CodecOptions.getDefaultOptions());
        writer.writeHeader();
    }
    
    public void nextImage() throws IOException {
        ros.seek(ros.length());
    }
    

    @Override
    public void close() throws Exception {
        try {
            try (ros; writer) {
                System.out.println("closing Writer....");
            }
        } catch (IOException ex) {
            Logger.getLogger(HatchWriter.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void writeIFDStrips(IFD ifd, byte[] raw, boolean last, int x, int y) throws FormatException, IOException {
        byte[][] tilestrip = new byte[1][];
        tilestrip[0] = raw;
        writer.writeIFDStrips(ifd, tilestrip, 3, last, x, y);
    }
}
