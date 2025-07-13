package com.AutomatePipeline.run.service.PomServices;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

@Service
public class PomManipulations {

    // Method to get the revision value from pom.xml
    public String getRevisionFromPom(String pomFilePath) throws IOException {
        SAXBuilder saxBuilder = new SAXBuilder();
        Document document;
        try {
            document = saxBuilder.build(new File(pomFilePath));
        } catch (Exception e) {
            throw new IOException("Failed to parse the pom.xml file.", e);
        }

        Element rootElement = document.getRootElement();
        Element propertiesElement = findPropertiesElement(rootElement);
        if (propertiesElement == null) {
            throw new IllegalArgumentException("<properties> tag not found in the pom.xml.");
        }

        Element revisionElement = propertiesElement.getChild("revision", propertiesElement.getNamespace());
        if (revisionElement == null) {
            throw new IllegalArgumentException("<revision> tag not found in the <properties> section.");
        }

        return revisionElement.getText();
    }

    // Method to modify the revision value in pom.xml
    public String modifyPomFile(String pomFilePath) throws IOException {

        SAXBuilder saxBuilder = new SAXBuilder();
        Document document;
        boolean hasXmlDeclaration;

        // Read the original file as a string to check for the XML declaration
        String originalContent = Files.readString(Paths.get(pomFilePath));
        hasXmlDeclaration = originalContent.trim().startsWith("<?xml");

        try {
            document = saxBuilder.build(new File(pomFilePath));
        } catch (Exception e) {
            throw new IOException("Failed to parse the pom.xml file.", e);
        }

        Element rootElement = document.getRootElement();
        Element propertiesElement = findPropertiesElement(rootElement);
        if (propertiesElement == null) {
            throw new IllegalArgumentException("<properties> tag not found in the pom.xml.");
        }

        Element revisionElement = propertiesElement.getChild("revision", propertiesElement.getNamespace());
        if (revisionElement == null) {
            // Fallback: Check for main <version> tag directly under <project>
            revisionElement = rootElement.getChild("version", rootElement.getNamespace());
            if (revisionElement == null) {
                throw new IllegalArgumentException("<revision> tag not found in the <properties> section, and <version> tag not found in the root.");
            }
        }

        String revisionValue = revisionElement.getText();
        String[] parts = revisionValue.split("-");
        String[] versionParts = parts[0].split("\\.");
        int lastNumber = Integer.parseInt(versionParts[versionParts.length - 1]);
        versionParts[versionParts.length - 1] = String.valueOf(lastNumber + 1);
        String newRevisionValue = String.join(".", versionParts) + (parts.length > 1 ? "-" + parts[1] : "");
        revisionElement.setText(newRevisionValue);

        try (FileWriter writer = new FileWriter(new File(pomFilePath))) {
            XMLOutputter xmlOutputter = new XMLOutputter();
            Format format = Format.getPrettyFormat();
            format.setTextMode(Format.TextMode.PRESERVE); // Prevents collapsing empty tags
            format.setEncoding("UTF-8"); // Ensures encoding is set to UTF-8

            // Only omit the declaration if it wasn't present in the original file
            format.setOmitDeclaration(!hasXmlDeclaration);
            xmlOutputter.setFormat(format);
            xmlOutputter.output(document, writer);
        }

        return newRevisionValue;
    }

    // Helper method to find the <properties> element
    private Element findPropertiesElement(Element rootElement) {
        if ("properties".equals(rootElement.getName())) {
            return rootElement;
        }

        for (Element child : rootElement.getChildren()) {
            Element found = findPropertiesElement(child);
            if (found != null) {
                return found;
            }
        }

        return null;
    }
}