package com.github.avonengel.xomsaxhelp;

import nu.xom.Builder;
import nu.xom.Document;
import nu.xom.Nodes;
import nu.xom.converters.SAXConverter;
import nu.xom.xinclude.XIncluder;
import nu.xom.xslt.XSLTransform;
import org.apache.fop.apps.Fop;
import org.apache.fop.apps.FopFactory;
import org.apache.fop.apps.FopFactoryBuilder;
import org.apache.fop.apps.MimeConstants;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.Result;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.OutputStream;
import java.io.Writer;
import java.lang.reflect.Field;
import java.util.LinkedList;
import java.util.List;

public class XslFo
{
    private List<File> xslFiles;
    private File rootDocbookFile;
    private boolean outputIntermediateResults = false;
    private File outputFile;

    public XslFo(List<File> xslFiles, File rootDocbookFile, boolean outputIntermediateResults, File outputFile)
    {
        this.xslFiles = xslFiles;
        this.rootDocbookFile = rootDocbookFile;
        this.outputIntermediateResults = outputIntermediateResults;
        this.outputFile = outputFile;
    }

    void convertPdfWithXom() throws Exception
    {
        XMLReader xmlReader = getXmlReader(false);

        // Use XOM to resolve xi:include: Xerces does not handle nested xincludes correctly
        // found this: https://sourceforge.net/p/docbook/bugs/1327/
        // which points to this: https://issues.apache.org/jira/browse/XERCESJ-1102
        Builder parser = new Builder(xmlReader);
        Document input = parser.build(rootDocbookFile);
        XIncluder.resolveInPlace(input, parser);

        LinkedList<TransformerHandler> transformerHandlers = new LinkedList<>();
        for (int i = 0; i < xslFiles.size(); i++)
        {
            File xslFile = xslFiles.get(i);
            final Document transformationDocument = parser.build(xslFile);
            final Nodes nodes = new XSLTransform(transformationDocument).transform(input);
            input = XSLTransform.toDocument(nodes);
            if (outputIntermediateResults)
            {
                File intermediate = outputFile.toPath().resolveSibling(i + xslFile.getName() + ".xml").toFile();
                try (Writer writer = new FileWriter(intermediate))
                {
                    writer.write(input.toXML());
                }
            }
        }

        FopFactory fopFactory = new FopFactoryBuilder(rootDocbookFile.getParentFile().toURI()).build();
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(outputFile)))
        {
            Fop fop = fopFactory.newFop(MimeConstants.MIME_PDF, out);
            buildSaxConverter(false, fop.getDefaultHandler()).convert(input);
        }
    }

    void convertPdfWithXomAndSaxon(boolean applyXmlBaseWorkaround) throws Exception
    {
        XMLReader xmlReader = getXmlReader(false);

        // Use XOM to resolve xi:include: Xerces does not handle nested xincludes correctly
        // found this: https://sourceforge.net/p/docbook/bugs/1327/
        // which points to this: https://issues.apache.org/jira/browse/XERCESJ-1102
        Builder parser = new Builder(xmlReader);
        Document input = parser.build(rootDocbookFile);
        XIncluder.resolveInPlace(input, parser);

        SAXTransformerFactory transformerFactory = (SAXTransformerFactory) SAXTransformerFactory.newInstance("net.sf.saxon.jaxp.SaxonTransformerFactory", XslFo.class.getClassLoader());

        // TODO: refactor the ugly intermediate output logic
        LinkedList<TransformerHandler> transformerHandlers = new LinkedList<>();
        for (int i = 0; i < xslFiles.size(); i++)
        {
            File xslFile = xslFiles.get(i);
            SAXSource transformationSource = new SAXSource(xmlReader, new InputSource(xslFile.toURI().toASCIIString()));
            TransformerHandler transformerHandler = transformerFactory.newTransformerHandler(transformationSource);
            if (outputIntermediateResults)
            {
                File intermediate = outputFile.toPath().resolveSibling(i + xslFile.getName() + ".xml").toFile();

                if (i == 0)
                {
                    transformerHandler.setResult(new StreamResult(intermediate));
                    buildSaxConverter(applyXmlBaseWorkaround, transformerHandler).convert(input);
                }
                else
                {
                    File previousIntermediate = outputFile.toPath().resolveSibling((i - 1) + xslFiles.get(i - 1).getName() + ".xml").toFile();
                    transformerFactory.newTransformer(transformationSource).transform(new SAXSource(xmlReader, new InputSource(previousIntermediate.toURI().toASCIIString())), new StreamResult(intermediate));
                }
            }
            else if (i > 0)
            {
                transformerHandlers.getLast().setResult(new SAXResult(transformerHandler));
            }
            transformerHandlers.add(transformerHandler);
        }

        FopFactory fopFactory = new FopFactoryBuilder(rootDocbookFile.getParentFile().toURI()).build();
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(outputFile)))
        {
            Fop fop = fopFactory.newFop(MimeConstants.MIME_PDF, out);
            Result foResult = new SAXResult(fop.getDefaultHandler());
            TransformerHandler firstTransformerHandler = transformerHandlers.getFirst();
            TransformerHandler lastTransformerHandler = transformerHandlers.getLast();
            if (outputIntermediateResults)
            {
                int index = xslFiles.size() - 1;
                File lastIntermediate = outputFile.toPath().resolveSibling(index + xslFiles.get(index).getName() + ".xml").toFile();
                transformerFactory.newTransformer().transform(new SAXSource(xmlReader, new InputSource(lastIntermediate.toURI().toASCIIString())), foResult);
            }
            else
            {
                lastTransformerHandler.setResult(foResult);
                buildSaxConverter(applyXmlBaseWorkaround, firstTransformerHandler).convert(input);
            }
        }
    }

    void convertPdfWithXercesInclude() throws Exception
    {
        // set xIncludeAware on the Xerces parser to true
        // does not resolve nested xIncludes correctly: https://issues.apache.org/jira/browse/XERCESJ-1102
        XMLReader xmlReader = getXmlReader(true);

        SAXTransformerFactory transformerFactory = (SAXTransformerFactory) SAXTransformerFactory.newInstance("net.sf.saxon.jaxp.SaxonTransformerFactory", XslFo.class.getClassLoader());

        // TODO: refactor the ugly intermediate output logic
        LinkedList<TransformerHandler> transformerHandlers = new LinkedList<>();
        for (int i = 0; i < xslFiles.size(); i++)
        {
            File xslFile = xslFiles.get(i);
            SAXSource transformationSource = new SAXSource(xmlReader, new InputSource(xslFile.toURI().toASCIIString()));
            if (outputIntermediateResults)
            {
                final StreamResult intermediateResult = new StreamResult(outputFile.toPath().resolveSibling(i + xslFile.getName() + ".xml").toFile());

                File intermediateInputFile = rootDocbookFile;
                if (i > 0)
                {
                    intermediateInputFile = outputFile.toPath().resolveSibling((i - 1) + xslFiles.get(i - 1).getName() + ".xml").toFile();
                }
                transformerFactory.newTransformer(transformationSource).transform(new SAXSource(xmlReader, new InputSource(intermediateInputFile.toURI().toASCIIString())), intermediateResult);
            }
            else if (i > 0)
            {
                TransformerHandler transformerHandler = transformerFactory.newTransformerHandler(transformationSource);
                transformerHandlers.getLast().setResult(new SAXResult(transformerHandler));
                transformerHandlers.add(transformerHandler);
            }
        }

        FopFactory fopFactory = new FopFactoryBuilder(rootDocbookFile.getParentFile().toURI()).build();
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(outputFile)))
        {
            Fop fop = fopFactory.newFop(MimeConstants.MIME_PDF, out);
            Result foResult = new SAXResult(fop.getDefaultHandler());
            if (outputIntermediateResults)
            {
                int index = xslFiles.size() - 1;
                File lastIntermediate = outputFile.toPath().resolveSibling(index + xslFiles.get(index).getName() + ".xml").toFile();
                transformerFactory.newTransformer().transform(new SAXSource(xmlReader, new InputSource(lastIntermediate.toURI().toASCIIString())), foResult);
            }
            else
            {
                transformerHandlers.getLast().setResult(foResult);
                xmlReader.setContentHandler(transformerHandlers.getFirst());
                xmlReader.parse(rootDocbookFile.toURI().toASCIIString());
            }
        }
    }

    private SAXConverter buildSaxConverter(boolean applyXmlBaseWorkaround, ContentHandler handler) throws NoSuchFieldException, IllegalAccessException
    {
        SAXConverter saxConverter = new SAXConverter(handler);
        if (applyXmlBaseWorkaround)
        {
            Field stripBaseAttributes = SAXConverter.class.getDeclaredField("stripBaseAttributes");
            stripBaseAttributes.setAccessible(true);
            stripBaseAttributes.setBoolean(saxConverter, false);
        }
        return saxConverter;
    }

    private XMLReader getXmlReader(boolean includeAware) throws ParserConfigurationException, SAXException
    {
        System.setProperty("javax.xml.parsers.SAXParserFactory", "org.apache.xerces.jaxp.SAXParserFactoryImpl");
        final SAXParserFactory parserFactory = SAXParserFactory.newInstance("org.apache.xerces.jaxp.SAXParserFactoryImpl", XslFo.class.getClassLoader());

        parserFactory.setNamespaceAware(true);
        parserFactory.setXIncludeAware(includeAware);
        parserFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        parserFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        //            parserFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

        return parserFactory.newSAXParser().getXMLReader();
    }
}