/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.format.maven;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * Parses untrusted XML, such as POMs and {@code maven-metadata.xml}, with DTDs, external entities
 * and XInclude all disabled (OWASP XML External Entity Prevention Cheat Sheet). A document with a
 * {@code DOCTYPE} is refused outright, which also stops entity-expansion bombs.
 */
public final class SafeXml {

    private SafeXml() {}

    public static Document parse(byte[] xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(THROWING);
            builder.setEntityResolver((publicId, systemId) -> refuseEntity());
            return builder.parse(new ByteArrayInputStream(xml));
        } catch (SAXException | IOException e) {
            throw new InvalidXmlException("Invalid or unsafe XML");
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("The XML parser does not support the required safety features", e);
        }
    }

    /** The last line of defence if a DOCTYPE ever got past the parser's features. */
    private static InputSource refuseEntity() throws SAXException {
        throw new SAXException("External entities are not allowed");
    }

    /** Stops the parser printing to stderr and makes every problem fatal. */
    private static final ErrorHandler THROWING = new ErrorHandler() {
        @Override
        public void warning(SAXParseException e) throws SAXException {
            throw e;
        }

        @Override
        public void error(SAXParseException e) throws SAXException {
            throw e;
        }

        @Override
        public void fatalError(SAXParseException e) throws SAXException {
            throw e;
        }
    };

    /** XML that is malformed or uses a refused feature. */
    public static class InvalidXmlException extends IllegalArgumentException {

        private static final long serialVersionUID = 1L;

        public InvalidXmlException(String message) {
            super(message);
        }
    }
}
