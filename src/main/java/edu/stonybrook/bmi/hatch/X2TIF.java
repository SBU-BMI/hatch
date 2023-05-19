package edu.stonybrook.bmi.hatch;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.common.services.ServiceFactory;
import loci.formats.CoreMetadata;
import loci.formats.FormatException;
import loci.formats.gui.AWTImageTools;
import loci.formats.meta.IMetadata;
import loci.formats.meta.MetadataRetrieve;
import loci.formats.services.OMEXMLService;
import loci.formats.tiff.IFD;
import loci.formats.tiff.PhotoInterp;
import loci.formats.tiff.TiffRational;
import ome.codecs.CodecException;
import ome.codecs.JPEG2000Codec;
import ome.units.UNITS;
import ome.units.quantity.Length;
import ome.xml.model.enums.DimensionOrder;
import ome.xml.model.enums.PixelType;
import ome.xml.model.primitives.PositiveInteger;

/**
 *
 * @author erich
 */
public class X2TIF implements AutoCloseable {
    private FormatReader reader;
    private final String inputFile;
    private final String outputFile;
    private int tileSizeX;
    private int tileSizeY;
    private int height;
    private int width;
    private int maximage;
    private Pyramid pyramid;
    private final StopWatch time;
    private int depth;
    private Length ppx;
    private Length ppy;
    private TiffRational px;
    private TiffRational py;
    private int TileSize;
    private final HatchParameters params;
    //private final Model m;
    private HatchWriter writer;
    private IMetadata meta;
    private byte compression;
    
    public X2TIF(HatchParameters params, String src, String dest) {
        time = new StopWatch();
        inputFile = src;
        outputFile = dest;
        this.params = params;
        try {
            writer = new HatchWriter(dest);
        } catch (IOException ex) {
            Logger.getLogger(X2TIF.class.getName()).log(Level.SEVERE, null, ex);
        }
        if (params.meta) {
            // m = ModelFactory.createDefaultModel();
        } else {
            //  m = null;
        }
        if (params.verbose) {
            System.out.println("initializing...");
        }
        try {
            ServiceFactory factory = new ServiceFactory();
            OMEXMLService service = factory.getInstance(OMEXMLService.class);
            IMetadata omexml = service.createOMEXMLMetadata();
            String end = inputFile.substring(inputFile.length()-4).toLowerCase();
            switch (end) {
                case ".tif":
                    reader = new TiffReader();
                    break;
                case ".svs":
                    reader = new SVSReader();
                    break;
                case ".vsi":
                    reader = new CellSensReader();
                    break;
                default:
                    break;
            }            
            reader.setMetadataStore(omexml);
            reader.setId(inputFile);
            maximage = MaxImage(reader);
            reader.setSeries(maximage);
            tileSizeX = reader.getOptimalTileWidth();
            tileSizeY = reader.getOptimalTileHeight();
            width = reader.getSizeX();
            height = reader.getSizeY();
            MetadataRetrieve retrieve = (MetadataRetrieve) reader.getMetadataStore();
            ppx = retrieve.getPixelsPhysicalSizeX(maximage);
            ppy = retrieve.getPixelsPhysicalSizeY(maximage);            
            SetPPS();
            if (params.verbose) {
                System.out.println("Image Size   : "+width+"x"+height);
                System.out.println("Tile size    : "+tileSizeX+"x"+tileSizeY);
                System.out.println("Compression  : "+reader.metadata.get("Compression"));
            }
            //String xml = service.getOMEXML(omexml);
            //System.out.println(xml);
            try {
                if (reader.metadata.get("Compression")==null) {
                    if (params.verbose) {
                        System.out.println("NULL compression specified...trying JPEG...no promises...");
                    }
                } else if ((reader.metadata.get("Compression")=="JPEG-2000")&&params.jp2) {
                    
                } else if (reader.metadata.get("Compression")!="JPEG") {
                    throw new Error("Hatch can only convert images that have JPEG compression.");
                }
            } catch (Error e){
                System.out.println(X2TIF.class.getName()+" : "+e.getLocalizedMessage());
                System.exit(0);
            }
            File fdest = new File(outputFile);
            if (fdest.exists()) {
                fdest.delete();
            }
            int size = Math.max(width, height);
            int ss = (int) Math.ceil(Math.log(size)/Math.log(2));
            int tiless = (int) Math.ceil(Math.log(tileSizeX)/Math.log(2));
            depth = ss-tiless+2;
            TileSize = reader.getOptimalTileHeight() * reader.getOptimalTileWidth() * 24;
            if (params.verbose) {
                System.out.println("# of scales to be generated : "+depth);
            }
            meta = service.createOMEXMLMetadata();
            meta.setImageID("Image:0", 0);
            meta.setPixelsID("Pixels:0", 0);
            meta.setChannelID("Channel:0", 0, 0);
            meta.setChannelSamplesPerPixel(new PositiveInteger(3), 0, 0);
            meta.setPixelsBigEndian(!reader.isLittleEndian(), 0);
            meta.setPixelsInterleaved(reader.isInterleaved(), 0);
            meta.setPixelsSizeX(new PositiveInteger(tileSizeX), 0);
            meta.setPixelsSizeY(new PositiveInteger(tileSizeY), 0);
            meta.setPixelsDimensionOrder(DimensionOrder.XYZCT, 0);
            meta.setPixelsType(PixelType.UINT8, 0);
            meta.setPixelsSizeX(new PositiveInteger(tileSizeX), 0);
            meta.setPixelsSizeY(new PositiveInteger(tileSizeY), 0);
            meta.setPixelsSizeZ(new PositiveInteger(1), 0);
            meta.setPixelsSizeC(new PositiveInteger(3), 0);
            meta.setPixelsSizeT(new PositiveInteger(1), 0);
        } catch (DependencyException | ServiceException | FormatException | IOException ex) {
            Logger.getLogger(X2TIF.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    private int MaxImage(FormatReader reader) {
        int ii = 0;
        int maxseries = 0;
        int maxx = Integer.MIN_VALUE;
        for (CoreMetadata x : reader.getCoreMetadataList()) {
            if (x.sizeX>maxx) {
                maxseries = ii;
                maxx = x.sizeX;
            }
            ii++;
        }
        if (params.verbose) System.out.println("MAX IMAGE SIZE IS SERIES : "+maxseries);
        return maxseries;
    }
    
    public int effSize(int tileX, int width) {
        return (tileX + tileSizeX) < width ? tileSizeX : width - tileX;
    }
    
    private void SetPPS() {
        Double physicalSizeX = ppx == null || ppx.value(UNITS.MICROMETER) == null ? null : ppx.value(UNITS.MICROMETER).doubleValue();
        if (physicalSizeX == null || physicalSizeX == 0) {
            physicalSizeX = 0d;
        } else {
            physicalSizeX = 1d / physicalSizeX;
        }
        Double physicalSizeY = ppy == null || ppy.value(UNITS.MICROMETER) == null ? null : ppy.value(UNITS.MICROMETER).doubleValue();
        if (physicalSizeY == null || physicalSizeY == 0) {
            physicalSizeY = 0d;
        } else {
            physicalSizeY = 1d / physicalSizeY;
        }
        px = new TiffRational((long) (physicalSizeX * 1000 * 10000), 1000);
        py = new TiffRational((long) (physicalSizeY * 1000 * 10000), 1000);
    }
    
    public void Dump2File3(byte[] buffer, int a, int b) {
        try {
            File f = new File("/boom/dump/0 === "+a+"-"+b+".jp2");
            if (!f.getParentFile().exists()) {
                f.getParentFile().mkdirs();
            }
            try (FileOutputStream fos = new FileOutputStream(f)) {
                fos.write(buffer);
                fos.flush();
            }
        } catch (FileNotFoundException ex) {
            Logger.getLogger(Pyramid.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(Pyramid.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    public void DumpBI2File3(BufferedImage bi, int a, int b) {
        try {
            File f = new File("/boom/dump2/0 === "+a+"-"+b+"X.jpg");
            if (!f.getParentFile().exists()) {
                f.getParentFile().mkdirs();
            }
            ImageWriter jpgWriter = (ImageWriter) ImageIO.getImageWritersByFormatName("jpg").next();
            ImageWriteParam param = jpgWriter.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setProgressiveMode(ImageWriteParam.MODE_DISABLED);
            param.setCompressionQuality(1.0f);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            MemoryCacheImageOutputStream outputStream = new MemoryCacheImageOutputStream(baos);
            jpgWriter.setOutput(outputStream);
            IIOImage outputImage = new IIOImage(bi, null, null);
            try {
                jpgWriter.write(null, outputImage, param);
            } catch (IOException ex) {
                Logger.getLogger(NeoJPEGCodec.class.getName()).log(Level.SEVERE, null, ex);
            }
            jpgWriter.dispose();
            Files.write(f.toPath(), baos.toByteArray());
        } catch (FileNotFoundException ex) {
            Logger.getLogger(Pyramid.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(Pyramid.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    public static void Display(byte[] buffer, int from, int to) {
        System.out.println("");
        for (int i=from; i<(buffer.length+to); i++) {
            System.out.print(String.format("%02x",buffer[i])+" ");
        }
        System.out.println("");
    }
        
    public void readWriteTiles() throws FormatException, IOException {
        if (params.verbose) {
            System.out.println("transferring image data...");
        }
        reader.setSeries(maximage);
        int nXTiles = width / tileSizeX;
        int nYTiles = height / tileSizeY;
        if (nXTiles * tileSizeX != width) nXTiles++;
        if (nYTiles * tileSizeY != height) nYTiles++;
        int numtiles = nXTiles*nYTiles;
        pyramid = new Pyramid(params,nXTiles,nYTiles,tileSizeX,tileSizeY,width,height);
        byte[] rawbuffer = new byte[TileSize+20];
        IFD ifd = new IFD();
        ifd.put(IFD.RESOLUTION_UNIT, 3);
        ifd.put(IFD.X_RESOLUTION, px);
        ifd.put(IFD.Y_RESOLUTION, py);
        ifd.put(IFD.TILE_WIDTH, tileSizeX);
        ifd.put(IFD.TILE_LENGTH, tileSizeY);
        ifd.put(IFD.IMAGE_WIDTH, (long) width);
        ifd.put(IFD.IMAGE_LENGTH, (long) height);
        ifd.put(IFD.TILE_OFFSETS, new long[numtiles]);
        ifd.put(IFD.TILE_BYTE_COUNTS, new long[numtiles]);
        String comp = (String) reader.metadata.get("Compression");
        if (comp==null) {
            comp = "UNKNOWN";
        }
        switch (comp) {
            case "JPEG-2000":
                compression = 2;
                //ifd.put(IFD.COMPRESSION, 34712); 
                ifd.put(IFD.COMPRESSION, 33005);
                //ifd.put(IFD.COMPRESSION, 33003);
                break;
            case "JPEG":
            case "UNKNOWN":
                compression = 0;
                ifd.put(IFD.COMPRESSION, 7);
                break;
            default:
                throw new Error("Should never get here");
        }
        ifd.put(IFD.BITS_PER_SAMPLE, new int[] {8, 8, 8});
        ifd.put(IFD.SAMPLES_PER_PIXEL, 3);
        ifd.put(IFD.PLANAR_CONFIGURATION, 1);
        ifd.put(IFD.SOFTWARE, Hatch.software);
        ifd.putIFDValue(IFD.IMAGE_DESCRIPTION, "");
        ifd.put(IFD.ORIENTATION, 1);
        ifd.put(IFD.X_RESOLUTION, px);
        ifd.put(IFD.Y_RESOLUTION, py);
        ifd.put(IFD.RESOLUTION_UNIT, 3);
        ifd.put(IFD.SAMPLE_FORMAT, new int[] {1, 1, 1});
        if (inputFile.toLowerCase().endsWith(".vsi")) {
            ifd.put(IFD.Y_CB_CR_SUB_SAMPLING, new int[] {2, 1});
            ifd.putIFDValue(IFD.PHOTOMETRIC_INTERPRETATION, PhotoInterp.Y_CB_CR.getCode());
        } else if (inputFile.toLowerCase().endsWith(".svs")) {
            IFD x = reader.getIFDs().get(maximage);
            ifd.putIFDValue(IFD.PHOTOMETRIC_INTERPRETATION, (int) x.get(IFD.PHOTOMETRIC_INTERPRETATION));
            //ifd.putIFDValue(IFD.PHOTOMETRIC_INTERPRETATION, PhotoInterp.RGB.getCode());
        } else {
            throw new Error("IFD.PHOTOMETRIC_INTERPRETATION ERROR!!!");
        }
        JPEG2000Codec codec = new JPEG2000Codec();
        for (int y=0; y<nYTiles; y++) {
            if (params.verbose) {
                float perc = 100f*y/nYTiles;
                System.out.println(String.format("%.2f%%",perc));
            }
            for (int x=0; x<nXTiles; x++) {
                int tileX = x * tileSizeX;
                int tileY = y * tileSizeX;
                int effTileSizeX = (tileX + tileSizeX) < width ? tileSizeX : width - tileX;
                int effTileSizeY = (tileY + tileSizeY) < height ? tileSizeY : height - tileY;
                byte[] raw = reader.getRawBytes(rawbuffer, 0, y, x);
                writer.writeIFDStrips(ifd, raw, false, x*tileSizeX, y*tileSizeY);
                //Dump2File3(raw, x, y);
                switch (compression) {
                    case 0:
                        BufferedImage bi = ImageIO.read(new ByteArrayInputStream(raw));
                        bi = bi.getSubimage(0, 0, effTileSizeX, effTileSizeY);
                        pyramid.put(bi, x, y);
                        break;
                    case 2:
                        try {
                            byte[] buff = codec.decompress(raw);
                            BufferedImage bix = byte2bi(buff);
                            //DumpBI2File3(bix,x,y);
                            bix = bix.getSubimage(0, 0, effTileSizeX, effTileSizeY);
                            pyramid.put(bix, x, y);
                        } catch (CodecException ex) {
                            Logger.getLogger(X2TIF.class.getName()).log(Level.SEVERE, null, ex);
                        }
                        break;
                    default:
                        throw new Error("Unknown Compression!");
                }                                  
            }
        }
        if (params.verbose) {
            time.Cumulative();
            System.out.println("Generate image pyramid...");
        }
        //ifd.put(IFD.NEW_SUBFILE_TYPE, 1);
        ifd.remove(IFD.Y_CB_CR_SUB_SAMPLING);
        ifd.remove(IFD.IMAGE_DESCRIPTION);
        ifd.remove(IFD.SOFTWARE);
        ifd.remove(IFD.X_RESOLUTION);
        ifd.remove(IFD.Y_RESOLUTION);
        ifd.put(IFD.COMPRESSION, 7);
        for (int s=1;s<depth;s++) {
            if (params.verbose) {
                System.out.println("Level : "+s+" of "+depth);
            }
            System.out.println("Lump...");
            pyramid.Lump();
            time.Lap();
            System.out.println("Shrink...");
            pyramid.Shrink();
            time.Lap();
            time.Cumulative();
            System.out.println(pyramid.gettilesX()+" X "+pyramid.gettilesY());
            System.out.println("Resolution S="+s+" "+pyramid.getWidth()+"x"+pyramid.getHeight());
            writer.nextImage();
            if (params.verbose) {
                System.out.println("Writing level "+s+"...");
            }
            numtiles = pyramid.gettilesX()*pyramid.gettilesY();
            ifd.put(IFD.IMAGE_WIDTH, (long) pyramid.getWidth());
            ifd.put(IFD.IMAGE_LENGTH, (long) pyramid.getHeight());
            ifd.put(IFD.TILE_OFFSETS, new long[numtiles]);
            ifd.put(IFD.TILE_BYTE_COUNTS, new long[numtiles]);
            ifd.putIFDValue(IFD.PHOTOMETRIC_INTERPRETATION, PhotoInterp.Y_CB_CR.getCode());            
            for (int y=0; y<pyramid.gettilesY(); y++) {
                for (int x=0; x<pyramid.gettilesX(); x++) {
                    byte[] b = pyramid.GetImageBytes(x, y);
                    BufferedImage bi = pyramid.getBufferedImage(x, y);
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ImageIO.write(bi, "jpg", baos);
                    writer.writeIFDStrips(ifd, b, ((x==(pyramid.gettilesX()-1))&&(y==(pyramid.gettilesY()-1))&&(s==depth-1)), x*tileSizeX, y*tileSizeY);
                }
            }
        }
    }
    
    private BufferedImage byte2bi(byte[] buf) {
        BufferedImage bb = null;
        try {
            bb = AWTImageTools.makeImage(buf, reader.isInterleaved(), meta, 0);
            return bb;
        } catch (FormatException ex) {
            Logger.getLogger(X2TIF.class.getName()).log(Level.SEVERE, null, ex);
        } 
        return bb;
    }
    
    public void Execute() {
        try {
            readWriteTiles();
            time.Cumulative();
        } catch(IOException | FormatException e) {
            System.err.println(e.toString());
        }
    }

    @Override
    public void close() throws Exception {
        reader.close();
        writer.close();
    }
}
