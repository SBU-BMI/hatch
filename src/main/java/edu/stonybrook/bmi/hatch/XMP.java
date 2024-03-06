package edu.stonybrook.bmi.hatch;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.UUID;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.riot.RDFWriterBuilder;
import org.apache.jena.vocabulary.RDF;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

/**
 *
 * @author erich
 */
public class XMP {
    private Double magnification = null;
    private Double ppsx = null;
    private Double ppsy = null;
    private final String uuid;
    
    public XMP() {
        this.uuid = "https://dummyabcde.com/"+UUID.randomUUID().toString();
    }
    
    public String getUUID() {
        return uuid;
    }
    
    public void setMagnification(Double magnification) {        
        this.magnification = magnification;
    }
    
    public void setSizePerPixelXinMM(Double ppsx) {
        this.ppsx = ppsx;
    }
    
    public void setSizePerPixelYinMM(Double ppsy) {
        this.ppsy = ppsy;
    }
    
    public byte[] getXMP() {
        Model m = ModelFactory.createDefaultModel();
        Resource root = m.createResource(uuid);
        if (magnification!=null) {
            root.addProperty(m.createProperty("http://ns.adobe.com/DICOM/ObjectiveLensPower"), String.valueOf(magnification));
        }
        if ((ppsx!=null)&&(ppsy!=null)) {
            ArrayList<RDFNode> list = new ArrayList<>(); 
            DecimalFormat formatter = new DecimalFormat("0.000000000");
            list.add(m.createLiteral(formatter.format(ppsy)));
            list.add(m.createLiteral(formatter.format(ppsx)));            
            root.addProperty(m.createProperty("http://ns.adobe.com/DICOM/PixelSpacing"), m.createList(list.iterator()));
        }
        m.setNsPrefix("DICOM", "http://ns.adobe.com/DICOM/");
        m.setNsPrefix("rdf", RDF.uri);
        m.setNsPrefix("xmpMM", "http://ns.adobe.com/xap/1.0/mm/");
        m.setNsPrefix("xmp", "http://ns.adobe.com/xap/1.0/");
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        RDFWriterBuilder builder = RDFWriterBuilder.create();
         builder
            .source(m)
            .lang(Lang.RDFXML)
            .base(uuid)
            .output(os);
        builder.build();
        builder = RDFWriterBuilder.create();
         builder
            .source(m)
            .lang(Lang.RDFXML)
            .base(uuid)
            .output(System.out);
        builder.build();
        return os.toByteArray();
    }
    
    public String getXMPString() {
        String packet = new String(getXMP(),StandardCharsets.UTF_8);
        packet = "<?xpacket begin='﻿\uFEFF' id='W5M0MpCehiHzreSzNTczkc9d'?>\n<x:xmpmeta xmlns:x='adobe:ns:meta/' x:xmptk='"+Hatch.software+"'>\n"+packet;
        packet = packet+"</x:xmpmeta>\n"+(new String(new char[2424]).replace('\0', ' '))+"\n<?xpacket end='w'?>\n";
        return packet;
    }
    
    public static InputStream grab() {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true); // Enable namespace awareness
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse("xmp.xml");
            XPathFactory xpathFactory = XPathFactory.newInstance();
            XPath xpath = xpathFactory.newXPath();
            xpath.setNamespaceContext(new NamespaceContext() {
                @Override
                public String getNamespaceURI(String prefix) {
                    if (prefix.equals("rdf")) {
                        return RDF.getURI();
                    }
                    return null;
                }
                @Override
                public Iterator getPrefixes(String val) { return null; }
                @Override
                public String getPrefix(String uri) { return null; }
            });            
            String expression = "//rdf:RDF"; // XPath expression to find the rdf:RDF element
            XPathExpression expr = xpath.compile(expression);
            Node node = (Node) expr.evaluate(doc, XPathConstants.NODE);
            if (node != null) {
                System.out.println("Found node: " + node.getNodeName());
                StringWriter writer = new StringWriter();
                Transformer transformer = TransformerFactory.newInstance().newTransformer();
                transformer.transform(new DOMSource(node), new StreamResult(writer));                
                System.out.println(writer.toString());
                byte[] byteArray = writer.toString().getBytes(StandardCharsets.UTF_8);
                return new ByteArrayInputStream(byteArray);                
            } else {
                System.out.println("Node not found.");
                return new FileInputStream("xmp.xml");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void main(String args[]) throws FileNotFoundException {         
        Model xml = ModelFactory.createDefaultModel();
        xml.setNsPrefix("DICOM", "http://ns.adobe.com/DICOM/");
        xml.setNsPrefix("rdf", RDF.uri);
        xml.setNsPrefix("x", "adobe:ns:meta/");
        xml.setNsPrefix("photoshop", "http://ns.adobe.com/photoshop/1.0/");
        xml.setNsPrefix("xmp", "http://ns.adobe.com/xap/1.0/");
        System.out.println("XXXXXXXXXXXXXXXXXXXXXXXXXXXX=========================================================================================");
         RDFDataMgr.read(xml, grab(), Lang.RDFXML);
         System.out.println("YYYYYYYYYYYYYYYYYYYYYYYYYY=========================================================================================");
         RDFDataMgr.write(System.out, xml, Lang.TURTLE);
         System.out.println("=========================================================================================");
         RDFWriterBuilder builder = RDFWriterBuilder.create();
         builder
            .source(xml)
            .lang(Lang.RDFXML)
            .base("http://njh.me/")
            .output(System.out);
        builder.build();
        RDFDataMgr.write(System.out, xml, RDFFormat.TURTLE_PRETTY);
        
        
        System.out.println("YAY !!!!=========================================================================================");
        XMP xmp = new XMP();
        xmp.setMagnification(40.4);
        xmp.setSizePerPixelXinMM(0.43);
        xmp.setSizePerPixelYinMM(0.41);
        System.out.println(xmp.getXMPString());        

    }
}
