package edu.stonybrook.bmi.hatch;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
import loci.formats.ome.OMEPyramidStore;
import loci.formats.services.OMEXMLService;
import loci.formats.tiff.PhotoInterp;
import loci.formats.tiff.TiffRational;
import ome.units.UNITS;
import ome.units.quantity.Length;
import ome.xml.model.enums.DimensionOrder;
import ome.xml.model.enums.PixelType;
import ome.xml.model.primitives.PositiveInteger;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 *
 * @author erich
 */
public class X2TIF implements AutoCloseable {
    private FormatReader reader;
    private final String inputFile;
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
    private HatchWriter writer;
    private IMetadata meta;
    private byte compression;
    private static Logger LOGGER;
    private XMP xmp = null;
    private String xcompression = null;
    
    public X2TIF(HatchParameters params, String src, String dest, Integer series) {
        LOGGER = Logger.getLogger(X2TIF.class.getName());
        time = new StopWatch();
        inputFile = src;
        this.params = params;
        if (params.verbose) {
            LOGGER.log(Level.INFO,"initializing...");
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
            //reader.getGlobalMetadata().forEach((k,v)->{
              //  System.out.println("META : "+k+"   "+v);
            //});
            //DicomWriter ha;
            if (reader instanceof SVSReader rrr) {               
                if (reader.getGlobalMetadata().containsKey("Compression")) {
                    xcompression = (String) rrr.getGlobalMetadata().get("Compression");
                    xcompression = xcompression.trim();
                }
            }
            if (series==null) {
                maximage = MaxImage(reader);
            } else {
                maximage = series;
            }
            if ((series!=null)&&((series<0)||(series>reader.getSeriesCount()))) {
                LOGGER.log(Level.INFO, "Series doesn''t exist : {0} --> {1}", new Object[]{src, series});
                System.exit(0);
            }
            try {
               writer = new HatchWriter(dest);
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, "FILE PROCESSOR ERROR --> {0} {1} {2}", new Object[]{params.src, params.dest, ex.toString()});
            }
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
                LOGGER.log(Level.INFO, "Image Size   : {0}x{1}", new Object[]{width, height});
                LOGGER.log(Level.INFO, "Tile size    : {0}x{1}", new Object[]{tileSizeX, tileSizeY});
                LOGGER.log(Level.INFO, "Compression  : {0}", xcompression);
            }
            //String xml = service.getOMEXML(omexml);
            //Systsm.out.println(xml);
            try {
                if (xcompression==null) {                
                    if (params.verbose) {
                        LOGGER.log(Level.INFO,"NULL compression specified...trying JPEG...no promises...");
                    }
                } else if (("JPEG-2000".equals(xcompression))&&params.jp2) {
                    
                } else if (!"JPEG".equals(xcompression)) {
                    throw new Error("Hatch can only convert images that have JPEG compression.");   
                }
            } catch (Error e){
                LOGGER.log(Level.SEVERE, "{0} : {1}  {2}", new Object[]{e.getLocalizedMessage(), src, dest});
                System.exit(0);
            }
            int size = Math.max(width, height);
            int ss = (int) Math.ceil(Math.log(size)/Math.log(2));
            int tiless = (int) Math.ceil(Math.log(tileSizeX)/Math.log(2));
            depth = ss-tiless+2;
            TileSize = reader.getOptimalTileHeight() * reader.getOptimalTileWidth() * 24;
            if (params.verbose) {
                LOGGER.log(Level.INFO, "# of scales to be generated : {0}", depth);
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
        } catch (DependencyException ex) {
            LOGGER.log(Level.SEVERE, "DependencyException : {0}  {1}", new Object[]{src, dest});
        } catch (ServiceException ex) {
            LOGGER.log(Level.SEVERE, "ServiceException : {0}  {1}", new Object[]{src, dest});
        } catch (FormatException ex) {
            LOGGER.log(Level.SEVERE, "FormatException : {0}  {1}", new Object[]{src, dest});
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "IOException : {0}  {1}", new Object[]{src, dest});
        }
        xmp = new XMP();   
        FindMeta(xmp);
    }
    
    private void FindMeta(XMP xmp) {
        switch (reader) {
            case CellSensReader r -> {                
                OMEPyramidStore mx = (OMEPyramidStore) reader.getMetadataStore();
                try {
                    String objectiveID = mx.getObjectiveSettingsID(maximage);
                    int instrument = -1;
                    int objective = -1;
                    int numberOfInstruments = mx.getInstrumentCount();
                    for (int ii = 0; ii < numberOfInstruments; ii++) {
                        int numObjectives = mx.getObjectiveCount(ii);
                        for (int oi = 0; 0 < numObjectives; oi++) {
                            if (objectiveID.equals(mx.getObjectiveID(ii, oi))) {
                                instrument = ii;
                                objective = oi;
                                break;
                            }
                        }
                    }
                    BigDecimal t = BigDecimal.valueOf(mx.getPlaneExposureTime(maximage, 0).value(UNITS.MILLISECOND).doubleValue());
                    xmp.setExposureTime(t);
                    if (instrument >= 0 ) {
                        xmp.setMagnification(BigDecimal.valueOf(mx.getObjectiveNominalMagnification(instrument, objective)));
                        String manu = mx.getDetectorManufacturer(instrument, objective);
                        if (manu!=null) {
                            xmp.setManufacturer(manu);
                        }
                        String model = mx.getDetectorModel(instrument, objective);
                        if (model!=null) {
                            xmp.setManufacturerDeviceName(model);
                        }
                    }
                } catch (NullPointerException ex) {}
                BigDecimal xpp = BigDecimal.valueOf(px.doubleValue()).divide(BigDecimal.TEN);        
                BigDecimal ypp = BigDecimal.valueOf(py.doubleValue()).divide(BigDecimal.TEN);
                xpp = xpp.divide(BigDecimal.valueOf(1000d));
                ypp = ypp.divide(BigDecimal.valueOf(1000d));
                xpp = BigDecimal.ONE.divide(xpp, 5, RoundingMode.HALF_UP);
                ypp = BigDecimal.ONE.divide(ypp, 5, RoundingMode.HALF_UP);
                xpp = xpp.multiply(BigDecimal.valueOf(1000d));
                ypp = ypp.multiply(BigDecimal.valueOf(1000d));
                xmp.setSizePerPixelXinMM(xpp);
                xmp.setSizePerPixelYinMM(ypp);
            }
            case SVSReader r -> {
                System.out.println("YAY -> "+r.getIFDs().get(0).getIFDValue(IFD.Y_CB_CR_SUB_SAMPLING));
                Map<String,Object> list = r.getSeriesMetadata();                
                xmp.setMagnification(BigDecimal.valueOf(Double.parseDouble((String) list.get("AppMag"))));
                xmp.setManufacturer((String) list.get("Image Description"));
                xmp.setManufacturerDeviceName((String) list.get("ScanScope ID"));
                if (list.containsKey("Exposure Time")) {
                    Double exposuretime = Double.valueOf((String) list.get("Exposure Time"));
                    if (list.containsKey("Exposure Scale")) {
                        Double exposurescale = Double.valueOf((String) list.get("Exposure Scale"));                
                        xmp.setExposureTime( BigDecimal.valueOf(exposuretime).multiply(BigDecimal.valueOf( exposurescale ).multiply(BigDecimal.valueOf(1000d))));
                    }
                }
                BigDecimal mpp = BigDecimal.valueOf(Double.parseDouble((String) list.get("MPP"))).multiply(BigDecimal.valueOf(1000000));
                xmp.setSizePerPixelXinMM(mpp);
                xmp.setSizePerPixelYinMM(mpp);
                //IFD ifd = r.getCurrentIFD();
                //byte[] iccprofile = (byte[]) ifd.getIFDValue(34675);
                //xmp.setICCColorProfile(iccprofile);
                //xmp.setICCColorProfile((String) list.get("ICC Profile"));
            }
            default -> {}
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
            LOGGER.log(Level.SEVERE, "FILE PROCESSOR ERROR --> {0} {1} {2}", new Object[]{params.src, params.dest, ex.toString()});
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "FILE PROCESSOR ERROR --> {0} {1} {2}", new Object[]{params.src, params.dest, ex.toString()});
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
               LOGGER.log(Level.SEVERE, "FILE PROCESSOR ERROR --> {0} {1} {2}", new Object[]{params.src, params.dest, ex.toString()});
            }
            jpgWriter.dispose();
            Files.write(f.toPath(), baos.toByteArray());
        } catch (FileNotFoundException ex) {
            LOGGER.log(Level.SEVERE, "FILE PROCESSOR ERROR --> {0} {1} {2}", new Object[]{params.src, params.dest, ex.toString()});
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "FILE PROCESSOR ERROR --> {0} {1} {2}", new Object[]{params.src, params.dest, ex.toString()});
        }
    }
    
    public static void Display(byte[] buffer, int from, int to) {
        System.out.println("");
        for (int i=from; i<(buffer.length+to); i++) {
            System.out.print(String.format("%02x",buffer[i])+" ");
        }
        System.out.println("");
    }
    
    public short[] byte2short(byte[] byteArray) {
        short[] shortArray = new short[byteArray.length];
        for (int i = 0; i < shortArray.length; i++) {
            shortArray[i] = (short) byteArray[i];
        }
        return shortArray;
    }
    
    public void readWriteTiles() throws FormatException, IOException {
        if (params.verbose) {
            LOGGER.log(Level.INFO,"transferring image data...");
        }
        reader.setSeries(maximage);       
        int nXTiles = width / tileSizeX;
        int nYTiles = height / tileSizeY;
        if (nXTiles * tileSizeX != width) nXTiles++;
        if (nYTiles * tileSizeY != height) nYTiles++;
        int numtiles = nXTiles*nYTiles;
        pyramid = new Pyramid(params,nXTiles,nYTiles,tileSizeX,tileSizeY,width,height);
        byte[] rawbuffer = new byte[TileSize+20];
        loci.formats.tiff.IFD ifd = new loci.formats.tiff.IFD();
        ifd.put(IFD.RESOLUTION_UNIT, 3);
        ifd.put(IFD.X_RESOLUTION, px);
        ifd.put(IFD.Y_RESOLUTION, py);
        ifd.put(IFD.TILE_WIDTH, tileSizeX);
        ifd.put(IFD.TILE_LENGTH, tileSizeY);
        ifd.put(IFD.IMAGE_WIDTH, (long) width);
        ifd.put(IFD.IMAGE_LENGTH, (long) height);
        ifd.put(IFD.TILE_OFFSETS, new long[numtiles]);
        ifd.put(IFD.TILE_BYTE_COUNTS, new long[numtiles]);
        //DicomWriter ha;
        //DicomJSONProvider meta = new DicomJSONProvider();
       //meta.readTagSource(inputFile);
        if (xmp!=null) {
            ifd.putIFDValue(700, byte2short(xmp.getXMPString().getBytes(StandardCharsets.UTF_8)));
        }
        if (xcompression==null) {
            xcompression = "UNKNOWN";
        }
        switch (xcompression) {
            //case "JPEG-2000":
//                compression = 2;
                //ifd.put(IFD.COMPRESSION, 34712); 
  //              ifd.put(IFD.COMPRESSION, 33005);
                //ifd.put(IFD.COMPRESSION, 33003);
    //            break;
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
            //ifd.put(IFD.Y_CB_CR_SUB_SAMPLING, new int[] {2, 1});
            ifd.put(IFD.Y_CB_CR_SUB_SAMPLING, new int[] {1, 1});
            ifd.putIFDValue(IFD.PHOTOMETRIC_INTERPRETATION, PhotoInterp.Y_CB_CR.getCode());
        } else if (reader instanceof SVSReader rrr) {
            IFDList list = rrr.getIFDs();
            IFD rah = list.get(0);
            if (reader.getIFDs().get(0).containsKey(IFD.Y_CB_CR_SUB_SAMPLING)) {
                short[] samp = reader.getIFDs().get(0).getIFDShortArray(IFD.Y_CB_CR_SUB_SAMPLING);
                if ((samp[0]==2)&&(samp[1]==2)) {
                    ifd.putIFDValue(IFD.PHOTOMETRIC_INTERPRETATION, (int) rah.get(IFD.PHOTOMETRIC_INTERPRETATION));
                } else {
                    ifd.putIFDValue(IFD.Y_CB_CR_SUB_SAMPLING, samp);
                }                
            } else {
                ifd.putIFDValue(IFD.PHOTOMETRIC_INTERPRETATION, PhotoInterp.Y_CB_CR.getCode());
                ifd.put(IFD.Y_CB_CR_SUB_SAMPLING, new int[] {1, 1});
            }            
        } else {
            throw new Error("IFD.PHOTOMETRIC_INTERPRETATION ERROR!!!");
        }
       // JPEG2000Codec codec = new JPEG2000Codec();
        byte method = 0;
        if (reader instanceof CellSensReader) {
            method = 1;
        } else if (reader instanceof SVSReader) {
            method = 2;
        } else if (reader instanceof TiffReader) {
            method = 3;
        }
        for (int y=0; y<nYTiles; y++) {
            if (params.verbose) {
                float perc = 100f*y/nYTiles;
                LOGGER.log(Level.INFO,String.format("%.2f%%",perc));
            }
            for (int x=0; x<nXTiles; x++) {
                //int tileX = x * tileSizeX;
                //int tileY = y * tileSizeX;
                //int effTileSizeX = (tileX + tileSizeX) < width ? tileSizeX : width - tileX;
                //int effTileSizeY = (tileY + tileSizeY) < height ? tileSizeY : height - tileY;
                byte[] raw;
                switch (method) {
                    case 1:
                    case 2:
                    case 3:
                        raw = reader.getRawBytes(rawbuffer, 0, y, x);
                        break;
                    default:
                       raw = reader.openCompressedBytes(0, x, y); 
                }
                //byte[] raw = reader.getRawBytes(rawbuffer, 0, y, x);

                writer.writeIFDStrips(ifd, raw, false, x*tileSizeX, y*tileSizeY);
                //Dump2File3(raw, x, y);
                switch (compression) {
                    case 0:
                        //BufferedImage bi = ImageIO.read(new ByteArrayInputStream(raw));
                        //bi = bi.getSubimage(0, 0, effTileSizeX, effTileSizeY);
                        //pyramid.put(bi, x, y);
                        pyramid.put(raw, x, y);
                        break;
                        /*
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
                        break;*/
                    default:
                        throw new Error("Unknown Compression!");
                }                                  
            }
        }
        if (params.verbose) {
            time.Cumulative();
            LOGGER.log(Level.INFO,"Generate image pyramid...");
        }
        ifd.remove(700);
        ifd.remove(IFD.Y_CB_CR_SUB_SAMPLING);
        ifd.remove(IFD.IMAGE_DESCRIPTION);
        ifd.remove(IFD.SOFTWARE);
        ifd.remove(IFD.X_RESOLUTION);
        ifd.remove(IFD.Y_RESOLUTION);
        ifd.put(IFD.COMPRESSION, 7);
        for (int s=1;s<depth;s++) {
            if (params.verbose) {
                LOGGER.log(Level.INFO, "Level : {0} of {1}", new Object[]{s, depth});
            }
            if (params.verbose) {
                LOGGER.log(Level.INFO,"Lump...");
            }
            pyramid.Lump();
            if (params.verbose) {
                LOGGER.log(Level.INFO,"Shrink...");
            }
            pyramid.Shrink();
            if (params.verbose) {
                LOGGER.log(Level.INFO, "{0} X {1}", new Object[]{pyramid.gettilesX(), pyramid.gettilesY()});
                LOGGER.log(Level.INFO, "Resolution S={0} {1}x{2}", new Object[]{s, pyramid.getWidth(), pyramid.getHeight()});
            }
            writer.nextImage();
            if (params.verbose) {
                LOGGER.log(Level.INFO, "Writing level {0}...", s);
            }
            numtiles = pyramid.gettilesX()*pyramid.gettilesY();
            ifd.put(IFD.NEW_SUBFILE_TYPE, 1L);
            ifd.put(IFD.IMAGE_WIDTH, (long) pyramid.getWidth());
            ifd.put(IFD.IMAGE_LENGTH, (long) pyramid.getHeight());
            ifd.put(IFD.TILE_OFFSETS, new long[numtiles]);
            ifd.put(IFD.TILE_BYTE_COUNTS, new long[numtiles]);
            ifd.putIFDValue(IFD.PHOTOMETRIC_INTERPRETATION, PhotoInterp.Y_CB_CR.getCode());            
            for (int y=0; y<pyramid.gettilesY(); y++) {
                for (int x=0; x<pyramid.gettilesX(); x++) {
                    byte[] b = pyramid.GetImageBytes(x, y);
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
            LOGGER.log(Level.SEVERE, "FILE PROCESSOR ERROR --> {0} {1} {2}", new Object[]{params.src, params.dest, ex.toString()});
        } 
        return bb;
    }
    
    public void Execute() throws FormatException, IOException {
        readWriteTiles();
        time.Cumulative();
    }

    @Override
    public void close() throws Exception {
        reader.close();
        writer.close();
    }
}
