package gnat.representation;

import gnat.ConstantsNei;
import gnat.ISGNProperties;
import gnat.retrieval.PubmedAccess;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import uk.ac.man.documentparser.dataholders.Document;
import uk.ac.man.documentparser.input.DocumentIterator;

/**
 * A text factory reads input from files and generates Text objects from them.
 * This includes extracting the TextContextModel for each text.
 *
 * @author Joerg Hakenberg
 *
 */

public class TextFactory {

	/** Stores a mapping from PubMed IDs to GO codes; will be added to a Text's ContextModel. */
	static Map<Integer, Set<Integer>> pubmed2gocodes = new HashMap<Integer, Set<Integer>>();
	/** Stores a mapping from PubMed IDs to GO terms; will be added to a Text's ContextModel. */
	static Map<Integer, Set<String>> pubmed2goterms  = new HashMap<Integer, Set<String>>();
	
	
	/**
	 * Loads a text repository from the given directories.<br>
	 * Supported file formats (identified by extensions) are .txt, .xml, and .medline.xml.
	 *
	 * @param directories
	 * @return
	 */
	public static TextRepository loadTextRepositoryFromDirectories (Collection<String> directories) {
		TextRepository textRepository = new TextRepository();

		System.err.println("#TextFactory loading GO codes and terms...");
		String file = ISGNProperties.get("pubmedId2GO");
		if (file != null && file.length() > 0) {
			File FILE = new File(file);
			if (FILE.exists() && FILE.canRead()) {
				try {
					BufferedReader br = new BufferedReader(new FileReader(file));
					String line;
					while ((line = br.readLine()) != null) {
						String[] cols = line.split("\t");
						// assume one PMID in column 1
						String[] pmids = new String[]{cols[0]};
						// could be multiple, separated by , ; |
						if (cols[0].matches(".*[\\;\\,\\|]\\s?.*"))
							pmids = cols[0].split("[\\;\\,\\|]\\s*");
						int gocode = Integer.parseInt(cols[1].toLowerCase().replaceFirst("go:", ""));
						for (String pmid: pmids) {
							int pubmed = Integer.parseInt(pmid);
							Set<Integer> codes = pubmed2gocodes.get(pubmed);
							if (codes == null) {
								codes = new HashSet<Integer>();
								pubmed2gocodes.put(pubmed, codes);
							}
							codes.add(gocode);
						}
					}
					br.close();
				} catch (IOException ioe) {
					ioe.printStackTrace();
				}
			} else
				System.err.println("#TextFactory was expecting PubMed-to-GO mappings in " + file);
		}
		
		System.err.println("#TextFactory loading texts from directories...");
		for (String dir: directories) {
			//Set<String> textIds = new TreeSet<String>();
	
			// read all plain texts from the directory
			File DIR = new File(dir);
			if (DIR.exists() && DIR.canRead()) {
				if (DIR.isDirectory()) {
					if (!dir.endsWith("/")) dir += "/";					
					// get the list of all files in that directory, load each if it is a .txt file
					String[] files = DIR.list();
					for (String filename : files) {
						if (filename.endsWith(".txt")){
							Text text = loadTextFromFile(dir + filename);
							textRepository.addText(text);
						} else if (filename.endsWith(".medline.xml")){
							Text text = loadTextFromMedlineXmlfile(dir + filename);
							textRepository.addText(text);
						} else if (filename.endsWith(".medlines.xml")){
							textRepository.addTexts(loadTextsFromMedlineSetXmlfile(dir + filename));
						} else if (filename.endsWith(".xml")) {
							Text text = loadTextFromXmlfile(dir + filename);
							textRepository.addText(text);
						}
					}

				} else {
					// load an individual file
					Text text = loadTextFromFile(dir);
					textRepository.addText(text);
					//textIds.add(text.ID);
				}
			}
		}
		
		if (ConstantsNei.OUTPUT_LEVEL.compareTo(ConstantsNei.OUTPUT_LEVELS.STATUS) >= 0)
			System.out.println("#TextRepository loaded with " + textRepository.size() + " texts.");

		return textRepository;
	}
	
	
	/**
	 * Loads a text repository from the given directories. Convenience methods that calls
	 * {@link #loadTextRepositoryFromDirectories(Collection)}.<br>
	 * Supported file formats (identified by extensions) are .txt, .xml, and .medline.xml.
	 *
	 * @param directories
	 * @return
	 */
	public static TextRepository loadTextRepositoryFromDirectories (String... directories) {
		List<String> listOfDirectories = new LinkedList<String>();
		
		for (String dir: directories)
			listOfDirectories.add(dir);
			
		return loadTextRepositoryFromDirectories(listOfDirectories);
	}


	/**
	 * Loads the given documents into the text repository.
	 * @param documents
	 * @return
	 */
	public static TextRepository loadTextRepository (DocumentIterator documents) {
		TextRepository textRepository = new TextRepository();

		for (Document d : documents){
			Text t = new Text(d.getID(), d.toString());
			textRepository.addText(t);
		}
		
		return textRepository;
	}


	/**
	 * Gets a {@link Text} from the given filename. The {@link Text}'s ID will be the filename minus
	 * its extension.
	 * 
	 * @param filename
	 * @return
	 */
	public static Text loadTextFromFile (String filename) {
		// remove the extension from the filename to get an ID
		String id = filename.replaceFirst("^(.+)\\..*?$", "$1");
		
		//System.out.println("id: "+ id);
		
		StringBuilder file_content = new StringBuilder();
		
		try {
			BufferedReader br = new BufferedReader(new FileReader(filename));
			String line;
			while ((line = br.readLine()) != null) {
				file_content.append(line);
				file_content.append("\n");
			}
			br.close();
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}
		
		Text aText = new Text(id);
		if (filename.endsWith(".xml"))
			aText.setPlainFromXml(file_content.toString());
		else
			aText.setPlainText(file_content.toString());

		// every Text needs a context model
		TextContextModel tcm = new TextContextModel(aText.ID);
		tcm.addPlainText(aText.getPlainText());
		
		//System.err.println(aText.getID() + "\t" + aText.getPMID());
		if (pubmed2gocodes.containsKey(aText.getPMID())) {
			Set<Integer> gocodes = pubmed2gocodes.get(aText.getPMID());
			String[] scodes = new String[gocodes.size()];
			int s = 0;
			for (int gocode: gocodes)
				scodes[s++] = ""+gocode;
			tcm.addCodes(scodes, GeneContextModel.CONTEXTTYPE_GOCODES);
			//System.err.println("#Added " + scodes.length + " GO codes for text " + aText.getPMID());
		}

		// add the extracted context model to the text
		aText.setContextModel(tcm);

		return aText;
	}
	
	
	/**
	 * Gets a {@link Text} from the given filename. The {@link Text}'s ID will be the filename minus
	 * its extension.
	 * 
	 * @param filename
	 * @return
	 */
	public static Text loadTextFromXmlfile (String filename) {
		// remove the extension from the filename to get an ID
		String id = filename.replaceFirst("^(.+)\\..*?$", "$1");
		
		//System.out.println("id: "+ id);
		
		StringBuilder plaintext = new StringBuilder();
		StringBuilder xmltext   = new StringBuilder();
		
		try {
			BufferedReader br = new BufferedReader(new FileReader(filename));
			String line;
			while ((line = br.readLine()) != null) {
				xmltext.append(line);
				xmltext.append("\n");
				
				plaintext.append(Text.xmlToPlainSentence(line));
				plaintext.append("\n");
			}
			br.close();
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}
		
		// finally construct the Text to be returned
		Text aText = new Text(id, plaintext.toString());
		aText.originalXml = xmltext.toString();
		aText.sentencesInXML = true;

		// every Text needs a context model
		TextContextModel tcm = new TextContextModel(aText.ID);
		tcm.addPlainText(aText.getPlainText());

		// add the extracted context model to the text
		aText.setContextModel(tcm);

		return aText;
	}
	
	
	/**
	 * Gets a {@link Text} from the given filename.<br>
	 * The assumed format is MEDLINE XML ("PubmedArticle"), and the plain text will be taken from
	 * the ArticleTitle and AbstractText elements only.
	 * 
	 * @param filename
	 * @return
	 */
	public static Text loadTextFromMedlineXmlfile (String filename) {
		// remove the extension from the filename to get an ID
		String id = filename.replaceFirst("^(.*\\/)?(.+?)\\..*?$", "$2");
		
		//System.out.println("id: "+ id);
		
		StringBuilder xml = new StringBuilder();
		String title = "";
		try {
			BufferedReader br = new BufferedReader(new FileReader(filename));
			String line;
			while ((line = br.readLine()) != null) {
				xml.append(line);
				xml.append("\n");
				
				if (line.matches(".*<ArticleTitle>.*</ArticleTitle>.*")) {
					title = line.replaceFirst("^.*<ArticleTitle>(.*)</ArticleTitle>.*$", "$1");
				}
			}
			br.close();
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}
		
		Text aText = new Text(id);
		aText.setPlainFromXml(xml.toString());
		aText.title = title;

		String pmid = PubmedAccess.getPubMedIdFromXML(xml.toString());
		if (pmid != null && !pmid.equals("-1") && pmid.matches("\\d+")) {
			aText.setPMID(Integer.parseInt(pmid));
			aText.ID = pmid;
		}
		
		// every Text needs a context model
		TextContextModel tcm = new TextContextModel(aText.ID);
		tcm.addPlainText(aText.getPlainText());

		// add the extracted context model to the text
		aText.setContextModel(tcm);
		
		//aText.jdocument = PubmedAccess.getAbstractsAsDocument(aText.originalXml);
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

	    factory.setNamespaceAware(true);
	    DocumentBuilder builder;
		try {
			builder = factory.newDocumentBuilder();
		    aText.jdocument = builder.parse(new ByteArrayInputStream(aText.originalXml.getBytes()));
		} catch (ParserConfigurationException e) {
			e.printStackTrace();
		} catch (SAXException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return aText;
	}
	
	
	/**
	 * Gets a set of {@link Text Texts} from the given file.<br>
	 * The assumed format is MEDLINE XML ("PubmedArticleSet"), and the plain text will be taken from
	 * the ArticleTitle and AbstractText elements only.
	 * 
	 * @param filename
	 * @return
	 */
	public static Collection<Text> loadTextsFromMedlineSetXmlfile (String filename) {
		// remove the extension from the filename to get an ID
		String id = filename.replaceFirst("^(.*\\/)?(.+?)\\..*?$", "$2");
		
		List<Text> temp_texts = new LinkedList<Text>();
		
		StringBuilder xml = new StringBuilder();
		String title = "";
		try {
			BufferedReader br = new BufferedReader(new FileReader(filename));
			String line;
			while ((line = br.readLine()) != null) {
				
				if (line.matches(".*<PubmedArticle.*")) {
					xml.setLength(0);
					xml.append(line);
					xml.append("\n");
					continue;
				}
				
				xml.append(line);
				xml.append("\n");
				if (line.matches(".*<ArticleTitle>.*</ArticleTitle>.*")) {
					title = line.replaceFirst("^.*<ArticleTitle>(.*)</ArticleTitle>.*$", "$1");
				}
				
				if (line.matches(".*</PubmedArticle>.*")) {
					Text aText = new Text(id);
					aText.setPlainFromXml(xml.toString());
					aText.title = title;

					String pmid = PubmedAccess.getPubMedIdFromXML(xml.toString());
					if (pmid != null && !pmid.equals("-1") && pmid.matches("\\d+")) {
						aText.setPMID(Integer.parseInt(pmid));
						aText.ID = pmid;
					}
					
					// every Text needs a context model
					TextContextModel tcm = new TextContextModel(aText.ID);
					tcm.addPlainText(aText.getPlainText());

					// add the extracted context model to the text
					aText.setContextModel(tcm);
					
					//aText.jdocument = PubmedAccess.getAbstractsAsDocument(aText.originalXml);
					DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

				    factory.setNamespaceAware(true);
				    DocumentBuilder builder;
					try {
						builder = factory.newDocumentBuilder();
					    aText.jdocument = builder.parse(new ByteArrayInputStream(aText.originalXml.getBytes()));
					} catch (ParserConfigurationException e) {
						e.printStackTrace();
					} catch (SAXException e) {
						e.printStackTrace();
					} catch (IOException e) {
						e.printStackTrace();
					}
					
					temp_texts.add(aText);
					//System.err.println("ADDED text " + aText.ID);
					
					// reset buffer
					xml.setLength(0);
				}
				
			}
			br.close();
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}
		
		return temp_texts;
	}
}
