package com.github.avonengel.xomsaxhelp;

import nu.xom.Builder;
import nu.xom.Document;
import nu.xom.converters.SAXConverter;
import nu.xom.xinclude.XIncluder;
import org.apache.avalon.framework.configuration.DefaultConfigurationBuilder;
import org.apache.fop.apps.Fop;
import org.apache.fop.apps.FopFactory;
import org.apache.fop.apps.FopFactoryBuilder;
import org.apache.fop.apps.MimeConstants;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

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
import java.io.OutputStream;
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

    void convertPdf() throws Exception
    {
        System.setProperty("javax.xml.parsers.SAXParserFactory", "org.apache.xerces.jaxp.SAXParserFactoryImpl");
        final SAXParserFactory parserFactory = SAXParserFactory.newInstance("org.apache.xerces.jaxp.SAXParserFactoryImpl", XslFo.class.getClassLoader());

        parserFactory.setNamespaceAware(true);
        parserFactory.setXIncludeAware(false);
        parserFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        parserFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        //            parserFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

        XMLReader xmlReader = parserFactory.newSAXParser().getXMLReader();

        // Use XOM to resolve xi:include: Xerces does not handle nested xincludes correctly
        // found this: https://sourceforge.net/p/docbook/bugs/1327/
        // which points to this: https://issues.apache.org/jira/browse/XERCESJ-1102
        Builder parser = new Builder(xmlReader);
        Document input = parser.build(rootDocbookFile);

        // XOM's SAXConverter does not produce xml:base, so we have to write to a temporary file
        input = XIncluder.resolve(input, parser);
        //        File xincluded = outputFile.get().getAsFile().toPath().resolveSibling("xincluded.xml").toFile();
        //        try (FileWriter writer = new FileWriter(xincluded))
        //        {
        //            writer.write(input.toXML());
        //        }
        //        Source source = new SAXSource(xmlReader, new InputSource(xincluded.toURI().toASCIIString()));

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
                transformerHandler.setResult(new StreamResult(intermediate));

                if (i == 0)
                {
                    TransformerHandler handler = transformerFactory.newTransformerHandler(transformationSource);
                    handler.setResult(new StreamResult(intermediate));
                    //                    transformerFactory.newTransformer().transform(source, new StreamResult(intermediate));
                    SAXConverter saxConverter = new SAXConverter(handler);
                    Field stripBaseAttributes = SAXConverter.class.getDeclaredField("stripBaseAttributes");
                    stripBaseAttributes.setAccessible(true);
                    stripBaseAttributes.setBoolean(saxConverter, false);
                    saxConverter.convert(input);
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

        DefaultConfigurationBuilder cfgBuilder = new DefaultConfigurationBuilder();
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
                new SAXConverter(firstTransformerHandler).convert(input);
            }
        }
    }
}