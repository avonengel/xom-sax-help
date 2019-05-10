package com.github.avonengel.xomsaxhelp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Collections;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.io.FileMatchers.anExistingFile;

class XslFoTest
{
    private File foXslFile;
    private File docbookFile;
    @TempDir
    Path tempDir;

    @BeforeEach
    void initDocbookFile() throws URISyntaxException
    {
        URL docbookFileUrl = XslFoTest.class.getResource("/article_example.xml");
        assertThat(docbookFileUrl, notNullValue());
        docbookFile = new File(docbookFileUrl.toURI());
    }

    @BeforeEach
    void initFoXslFile() throws URISyntaxException
    {
        URL foXslFileUrl = XslFoTest.class.getResource("/docbook/fo/docbook.xsl");
        assertThat(foXslFileUrl, notNullValue());
        foXslFile = new File(foXslFileUrl.toURI());
    }

    @Test
    void convertPdf() throws Exception
    {
        // Arrange
        final File outputFile = tempDir.resolve(docbookFile.getName() + ".pdf").toFile();
        final XslFo xslFo = new XslFo(Collections.singletonList(foXslFile), docbookFile, false, outputFile);

        // Act
        xslFo.convertPdf();

        // Assert
        assertThat(outputFile, anExistingFile());
    }
}