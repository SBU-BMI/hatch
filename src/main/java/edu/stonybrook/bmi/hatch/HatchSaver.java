package edu.stonybrook.bmi.hatch;

import java.io.IOException;
import loci.common.RandomAccessOutputStream;
import loci.formats.FormatException;
import loci.formats.tiff.IFD;
import loci.formats.tiff.TiffSaver;

/**
 *
 * @author erich
 */
public class HatchSaver extends TiffSaver {
    private Long sequentialTileFilePointer;
    
    /**
     *
     * @param out
     * @param filename
     */
    public HatchSaver(RandomAccessOutputStream out, String filename) {
        super(out,filename);
    }
    
    public void writeIFDStrips(IFD ifd, byte[][] strips, int nChannels, boolean last, int x, int y) throws FormatException, IOException {
        int tilesPerRow = (int) ifd.getTilesPerRow();
        int tilesPerColumn = (int) ifd.getTilesPerColumn();
        boolean interleaved = ifd.getPlanarConfiguration() == 1;
        long[] byteCounts;
        long[] offsets;
        long[] ifdByteCounts = ifd.getIFDLongArray(IFD.TILE_BYTE_COUNTS);
        byteCounts = ifdByteCounts;
        int tileOrStripOffsetX = x / (int) ifd.getTileWidth();
        int tileOrStripOffsetY = y / (int) ifd.getTileLength();
        int firstOffset = (tileOrStripOffsetY * tilesPerRow) + tileOrStripOffsetX;
        long[] ifdOffsets = ifd.getIFDLongArray(IFD.TILE_OFFSETS);
        offsets = ifdOffsets.clone();
        ifd.putIFDValue(IFD.TILE_BYTE_COUNTS, byteCounts);
        ifd.putIFDValue(IFD.TILE_OFFSETS, offsets);
        long fp = out.getFilePointer();
        if (tileOrStripOffsetX == 0 && tileOrStripOffsetY == 0) {
            sequentialTileFilePointer = fp;
        } else {
            fp = sequentialTileFilePointer;
        }
        if (fp == out.getFilePointer()) {
            writeIFD(ifd, 0);
        }
        int tileCount = tilesPerRow * tilesPerColumn;
        long stripStartPos = out.length();
        long totalStripSize = 0;
        for (byte[] strip : strips) {
            totalStripSize += strip.length;
        }
        out.seek(stripStartPos + totalStripSize);
        out.seek(stripStartPos);
        long stripOffset = 0;
        for (int i=0; i<strips.length; i++) {
            out.seek(stripStartPos + stripOffset);
            stripOffset += strips[i].length;
            int index = interleaved ? i : (i / nChannels) * nChannels;
            int c = interleaved ? 0 : i % nChannels;
            int thisOffset = firstOffset + index + (c * tileCount);
            offsets[thisOffset] = out.getFilePointer();
            byteCounts[thisOffset] = strips[i].length;
            out.write(strips[i]);
        }
        ifd.putIFDValue(IFD.TILE_BYTE_COUNTS, byteCounts);
        ifd.putIFDValue(IFD.TILE_OFFSETS, offsets);
        long endFP = out.getFilePointer();
        out.seek(fp);
        writeIFD(ifd, last ? 0 : endFP);
    }
}
