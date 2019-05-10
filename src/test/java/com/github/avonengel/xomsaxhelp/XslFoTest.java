package com.github.avonengel.xomsaxhelp;

import ch.qos.logback.classic.spi.ILoggingEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

class XslFoTest
{
    private static final Logger LOGGER = LoggerFactory.getLogger(XslFoTest.class);
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

    @BeforeEach
    void clearStaticAppender()
    {
        StaticAppender.clearEvents();
    }

    Path getTempDir(String subDirectory) {
        final String testTempDir = System.getenv("TEST_TEMP_DIR");
        if (testTempDir != null)
        {
            final Path dir = Paths.get(testTempDir).resolve(subDirectory);
            LOGGER.info("Test temp directory: {}", dir);
            dir.toFile().mkdirs();
            return dir;
        } else
        {
            return tempDir;
        }
    }

    @Test
    void convertPdfWithXomSaxonAndWorkaround() throws Exception
    {
        // Arrange
        final File outputFile = getTempDir("xomSaxonWithWorkaround").resolve(docbookFile.getName() + ".pdf").toFile();
        final XslFo xslFo = new XslFo(Collections.singletonList(foXslFile), docbookFile, false, outputFile);

        // Act
        xslFo.convertPdfWithXomAndSaxon(true);

        // Assert
        final List<ILoggingEvent> events = StaticAppender.getEvents();
        assertThat(events, not(hasItem(hasProperty("message", containsString("Image not found")))));
    }
    @Test
    void convertPdfWithXom() throws Exception
    {
        // Arrange
        final File outputFile = getTempDir("xom").resolve(docbookFile.getName() + ".pdf").toFile();
        final XslFo xslFo = new XslFo(Collections.singletonList(foXslFile), docbookFile, false, outputFile);

        // Act
        xslFo.convertPdfWithXom();

        // Assert
        final List<ILoggingEvent> events = StaticAppender.getEvents();
        assertThat(events, not(hasItem(hasProperty("message", containsString("Image not found")))));
    }

    @Test
    void convertPdfWithXomSaxonWithoutWorkaround() throws Exception
    {
        // Arrange
        final File outputFile = getTempDir("xomSaxonWithoutWorkaround").resolve(docbookFile.getName() + ".pdf").toFile();
        final XslFo xslFo = new XslFo(Collections.singletonList(foXslFile), docbookFile, false, outputFile);

        // Act
        xslFo.convertPdfWithXomAndSaxon(false);

        // Assert
        final List<ILoggingEvent> events = StaticAppender.getEvents();
        assertThat(events, not(hasItem(hasProperty("message", containsString("Image not found")))));
    }

    @Test
    void convertPdfWithXercesXinclude() throws Exception
    {
        // Arrange
        final File outputFile = getTempDir("xerces").resolve(docbookFile.getName() + ".pdf").toFile();
        final XslFo xslFo = new XslFo(Collections.singletonList(foXslFile), docbookFile, true, outputFile);

        // Act
        xslFo.convertPdfWithXercesInclude();

        // Assert
        final List<ILoggingEvent> events = StaticAppender.getEvents();
        assertThat(events, not(hasItem(hasProperty("message", containsString("Image not found")))));
    }
}