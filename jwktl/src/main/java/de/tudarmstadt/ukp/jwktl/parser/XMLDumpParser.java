/*******************************************************************************
 * Copyright 2013
 * Ubiquitous Knowledge Processing (UKP) Lab
 * Technische Universität Darmstadt
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package de.tudarmstadt.ukp.jwktl.parser;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Stack;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import de.tudarmstadt.ukp.jwktl.api.WiktionaryException;
import org.apache.tools.bzip2.CBZip2InputStream;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Implementation of {@link IWiktionaryDumpParser} for processing XML files
 * downloaded from http://download.wikimedia.org/backup-index.html. There
 * can be different specializations of this class that focus on a certain
 * aspect of the dump, e.g., parsing the full text on the article pages and
 * create an object structure from them, processing some aspects of
 * the user pages, filtering the article pages, etc. The base class should
 * be somewhat generic.
 * @author Christian M. Meyer
 */
public abstract class XMLDumpParser implements IWiktionaryDumpParser {
	
	protected class XMLDumpHandler extends DefaultHandler {

		protected StringBuffer contentBuffer;
		protected Stack<String> tags;

		@Override
		public void startDocument() throws SAXException {
			tags = new Stack<String>();
			contentBuffer = new StringBuffer();
			onParserStart();
		}
			
		@Override
		public void characters(char[] ch, int start, int length)
				throws SAXException {
			contentBuffer.append(ch, start, length);
		}

		@Override
		public void startElement(String uri, String localName, String qName,
				Attributes attributes) throws SAXException {
			tags.push(qName);
			contentBuffer.setLength(0);
			
			onElementStart(qName, this);			
		}
		
		@Override
		public void endElement(String uri, String localName, String qName)
			throws SAXException {
			tags.pop();		
			
			onElementEnd(qName, this);
		}
		
		@Override
		public void endDocument() throws SAXException {
			onParserEnd();
		}
		
		/** Returns the contents of the currently active XML element. */
		public String getContents() {
			return contentBuffer.toString();
		}
		
		/** Returns whether there is a non-empty content within the currently 
		 *  active XML element. */
		public boolean hasContents() {
			return (contentBuffer.length() > 0);
		}
		
		/** Returns the XML tag name of the parent of the currently active
		 *  XML element. */
		public String getParent() {
			return tags.peek();
		}
		
	}

	/** The file extension for bzip2 files that is used for the automatic 
	 *  detection of the file format. */
	public static final String BZ2_FILE_EXTENSION = ".bz2";


	/** Parses the given XML dump file. The file format is automatically
	 *  detected using the file extension: it can be either bzip2 compressed
	 *  or uncompressed XML. Internally, a SAX parser is used.
	 *  @throws WiktionaryException in case of any parser errors. */
	public void parse(final File dumpFile) throws WiktionaryException {
		try {
			parseStream(openDumpFile(dumpFile));
		} catch (IOException e) {
			throw new WiktionaryException(e);
		}
	}

	protected void parseStream(InputStream in) throws IOException {
		try {
			// Run the SAX parser.
			getParser().parse(in, new XMLDumpHandler());
		} catch (SAXException e) {
			throw new WiktionaryException("XML parse error", e);
		} finally {
			try {
				in.close();
			} catch (IOException ignored) {
			}
		}
	}

	/** Hotspot that is invoked on starting the parser. Use this hotspot to 
	 *  initialize your data. */
	protected void onParserStart() {}

	/** Hotspot that is invoked for each opening XML element. */
	protected abstract void onElementStart(final String name, 
			final XMLDumpHandler handler);

	/** Hotspot that is invoked for each closing XML element. */
	protected abstract void onElementEnd(final String name, 
			final XMLDumpHandler handler);
	
	/** Hotspot that is invoked on finishing the parsing. Use this hotspot
	 *  for cleaning up and closing resources. */
	protected void onParserEnd() {}

	/**
	 * Opens a .bz2 file for reading
	 * @param file the bz2 file
	 * @return the input stream containing decompressed data
	 * @throws IOException if file is invalid or cannot be read
	 */
	protected InputStream openBz2File(File file) throws IOException {
		InputStream in = new BufferedInputStream(new FileInputStream(file));
		final byte[] header = new byte[2];
		if (in.read(header) != 2) {
			throw new IOException("Unable to decompress dump file");
		}
		if (header[0] != 'B' || header[1] != 'Z') {
			throw new IOException("Invalid file header");
		} else {
			return new CBZip2InputStream(in);
		}
	}

	// Open the dump file; decompress if necessary.
	private InputStream openDumpFile(File dumpFile) throws IOException {
		if (dumpFile.getName().endsWith(BZ2_FILE_EXTENSION)) {
			return openBz2File(dumpFile);
		} else {
			return new FileInputStream(dumpFile);
		}
	}

	private SAXParser getParser() {
		try {
			SAXParserFactory factory = SAXParserFactory.newInstance();
			return factory.newSAXParser();
		} catch (ParserConfigurationException e) {
			throw new WiktionaryException("SAX parser could not be configured", e);
		} catch (SAXException e) {
			throw new WiktionaryException("XML parse error", e);
		}
	}
}
