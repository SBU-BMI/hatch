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
import loci.formats.meta.IMetadata;
import loci.formats.meta.MetadataRetrieve;
import loci.formats.ome.OMEPyramidStore;
import loci.formats.services.OMEXMLService;
import loci.formats.tiff.IFD;
import loci.formats.tiff.PhotoInterp;
import loci.formats.tiff.TiffRational;
import ome.units.UNITS;
import ome.units.quantity.Length;
import ome.xml.model.enums.DimensionOrder;
import ome.xml.model.enums.PixelType;
import ome.xml.model.primitives.PositiveInteger;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;

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
    private OMETiffWriter writer;
    private final StopWatch time;
    private int depth;
    private Length ppx;
    private Length ppy;
    private TiffRational px;
    private TiffRational py;
    private int TileSize;
    private IMetadata meta;
    private final HatchParameters params;
    private final Model m;
    
    public X2TIF(HatchParameters params, String src, String dest) {
        time = new StopWatch();
        inputFile = src;
        outputFile = dest;
        this.params = params;
        if (params.meta) {
            m = ModelFactory.createDefaultModel();
        } else {
            m = null;
        }
    }
    
    public int MaxImage(FormatReader reader) {
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
    
    private void SetupWriter() {
        writer = new OMETiffWriter();
        ServiceFactory factory;
        try {
            factory = new ServiceFactory();
            OMEXMLService service = factory.getInstance(OMEXMLService.class);
            meta = service.createOMEXMLMetadata();
            int i=0;
            meta.setImageID("Image:"+i,i);
            meta.setPixelsID("Pixels:"+i,i);
            meta.setChannelID("Channel:"+i+":0", i, 0);
            meta.setChannelSamplesPerPixel(new PositiveInteger(3),i, 0);
            meta.setPixelsBigEndian(false, i);
            meta.setPixelsInterleaved(true, i);
            meta.setPixelsDimensionOrder(DimensionOrder.XYCZT, i);
            meta.setPixelsType(PixelType.UINT8, i);
            meta.setPixelsSizeX(new PositiveInteger(width),i);
            meta.setPixelsSizeY(new PositiveInteger(height),i);
            meta.setPixelsSizeZ(new PositiveInteger(1), i);
            meta.setPixelsSizeC(new PositiveInteger(3), i);
            meta.setPixelsSizeT(new PositiveInteger(1), i);
            meta.setPixelsPhysicalSizeX(ppx, i);
            meta.setPixelsPhysicalSizeY(ppy, i);
            meta.setPixelsPhysicalSizeZ(new Length(1, UNITS.MICROMETER), i);
            int w = width;
            int h = height;
            for (int j=1; j<depth; j++) {
                //int cx = ((w%(2*tileSizeX))==1) ? 1 : 0;
                //int cy = ((h%(2*tileSizeX))==1) ? 1 : 0;
                //w = (w / 2) - cx;
                //h = (h / 2) - cy;
                int wholenumxtiles = w/(2*tileSizeX);
                int wholenumytiles = h/(2*tileSizeY);
                //int numxtiles = Math.round(w/(2*tileSizeX));
                //int numytiles = Math.round(h/(2*tileSizeY));
                int fractionx = w - (wholenumxtiles*tileSizeX*2);
                int fractiony = h - (wholenumytiles*tileSizeY*2);
                if (fractionx==1) {
                    w = wholenumxtiles * tileSizeX;
                } else {
                    w = wholenumxtiles * tileSizeX + (fractionx/2);
                }
                if (fractiony==1) {
                    h = wholenumytiles * tileSizeY;
                } else {
                    h = wholenumytiles * tileSizeY + (fractiony/2);
                }
                if (params.verbose) {
                    System.out.println("Pyramid : "+w+" "+ h +" "+((int) (1+ Math.ceil(w/tileSizeX)))+" "+((int) (1+ Math.ceil(h/tileSizeX))));
                }
                ((OMEPyramidStore) meta).setResolutionSizeX(new PositiveInteger(w), 0, j);
                ((OMEPyramidStore) meta).setResolutionSizeY(new PositiveInteger(h), 0, j);
            }
            writer.setMetadataRetrieve(meta);
            writer.setBigTiff(true);
            writer.setId(outputFile);
            writer.setCompression("JPEG");
            writer.setWriteSequentially(true);
            writer.setInterleaved(true);
            writer.setTileSizeX(tileSizeX);
            writer.setTileSizeY(tileSizeY);
        } catch (DependencyException | ServiceException | FormatException | IOException ex) {
            Logger.getLogger(X2TIF.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    private void initialize() {
        ServiceFactory factory;
        try {
            factory = new ServiceFactory();
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
            try {
                if (reader.metadata.get("Compression")==null) {
                    if (params.verbose) {
                        System.out.println("NULL compression specified...trying JPEG...no promises...");
                    }
                } else if (reader.metadata.get("Compression")!="JPEG") {
                    throw new Error("Hatch can only convert images that have JPEG compression.");
                }
            } catch (Error e){
                System.out.println(e.getLocalizedMessage());
                System.exit(0);
            }
            File dest = new File(outputFile);
            if (dest.exists()) {
                dest.delete();
            }
            int size = Math.max(width, height);
            int ss = (int) Math.ceil(Math.log(size)/Math.log(2));
            int tiless = (int) Math.ceil(Math.log(tileSizeX)/Math.log(2));
            depth = ss-tiless+2;
            TileSize = reader.getOptimalTileHeight() * reader.getOptimalTileWidth() * 24;
            if (params.verbose) {
                System.out.println("# of scales to be generated : "+depth);
            }
            SetupWriter();
        } catch (DependencyException | ServiceException | FormatException | IOException ex) {
            Logger.getLogger(X2TIF.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    public int effSize(int tileX, int width) {
        return (tileX + tileSizeX) < width ? tileSizeX : width - tileX;
    }
    
    public void SetPPS() {
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
            File f = new File("/vsi/dump/0 === "+a+"-"+b+".jpg");
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
            File f = new File("/vsi/dump2/0 === "+a+"-"+b+"X.jpg");
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
        
    public void readWriteTiles() throws FormatException, IOException {
        if (params.verbose) {
            System.out.println("transferring image data...");
        }
        reader.setSeries(maximage);
        int nXTiles = width / tileSizeX;
        int nYTiles = height / tileSizeY;
        if (nXTiles * tileSizeX != width) nXTiles++;
        if (nYTiles * tileSizeY != height) nYTiles++;
        pyramid = new Pyramid(nXTiles,nYTiles,tileSizeX,tileSizeY);
        writer.setSeries(0);
        writer.setResolution(0);
        byte[] rawbuffer = new byte[TileSize+20];
        for (int y=0; y<nYTiles; y++) {
            if (params.verbose) {
                float perc = 100f*y/nYTiles;
                System.out.println(perc+"%");
            }
            for (int x=0; x<nXTiles; x++) {
                int tileX = x * tileSizeX;
                int tileY = y * tileSizeY;
                int effTileSizeX = (tileX + tileSizeX) < width ? tileSizeX : width - tileX;
                int effTileSizeY = (tileY + tileSizeY) < height ? tileSizeY : height - tileY;
                byte[] buf = reader.openBytes(0, tileX, tileY, effTileSizeX, effTileSizeY);
                byte[] raw = reader.getRawBytes(rawbuffer,0, y, x);
                IFD ifd = new IFD();
                ifd.put(IFD.RESOLUTION_UNIT, 3);
                ifd.put(IFD.X_RESOLUTION, px);
                ifd.put(IFD.Y_RESOLUTION, py);
                if (inputFile.toLowerCase().endsWith(".vsi")) {
                    ifd.putIFDValue(IFD.PHOTOMETRIC_INTERPRETATION, PhotoInterp.Y_CB_CR.getCode());
                } else if (inputFile.toLowerCase().endsWith(".svs")) {
                    ifd.putIFDValue(IFD.PHOTOMETRIC_INTERPRETATION, PhotoInterp.RGB.getCode());
                }
                ifd.put(777, raw);
                writer.saveBytes(0, buf, ifd, tileX, tileY, effTileSizeX, effTileSizeY);
                BufferedImage bi = ImageIO.read(new ByteArrayInputStream(raw));
                bi = bi.getSubimage(0, 0, effTileSizeX, effTileSizeY);
                pyramid.put(bi, x, y);
            }
        }
        if (params.verbose) {
            time.Cumulative();
            System.out.println("Generate image pyramid...");
        }
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
            writer.setSeries(0);
            writer.setResolution(s);
            if (params.verbose) {
                System.out.println("Writing level "+s+"...");
            }
            for (int y=0; y<pyramid.gettilesY(); y++) {
                for (int x=0; x<pyramid.gettilesX(); x++) {
                    byte[] b = pyramid.GetImageBytes(x, y);
                    IFD ifd = new IFD();
                    ifd.put(IFD.RESOLUTION_UNIT, 3);
                    ifd.put(IFD.X_RESOLUTION, px);
                    ifd.put(IFD.Y_RESOLUTION, py);
                    //if (inputFile.toLowerCase().endsWith(".vsi")) {
                      //  ifd.putIFDValue(IFD.PHOTOMETRIC_INTERPRETATION, PhotoInterp.RGB.getCode());
                    //}
                    ifd.putIFDValue(IFD.PHOTOMETRIC_INTERPRETATION, PhotoInterp.Y_CB_CR.getCode());
                    ifd.put(777, b);
                    BufferedImage bi = pyramid.getBufferedImage(x, y);
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ImageIO.write(bi, "jpg", baos);
                    byte[] raw = baos.toByteArray();
                    //byte[] raw = ((DataBufferByte)bi.getRaster().getDataBuffer()).getData();
                    //System.out.println("Writing Pyramid Tile : "+x+" "+y+" "+tw+" "+th);
                    //writer.saveBytes(0, raw, ifd, x*tileSizeX, y*tileSizeY, tw, th);
                    writer.saveBytes(0, raw, ifd, x*tileSizeX, y*tileSizeY, tileSizeX, tileSizeY);
                    //pyramid.Dump2File(raw, x, y);
                }
            }
        }
    }
    
    public void Execute() {
        if (params.verbose) {
            System.out.println("initializing...");
        }
        initialize();
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
