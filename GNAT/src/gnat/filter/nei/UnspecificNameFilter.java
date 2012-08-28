package gnat.filter.nei;

import gnat.ConstantsNei;
import gnat.filter.Filter;
import gnat.representation.Context;
import gnat.representation.GeneRepository;
import gnat.representation.IdentificationStatus;
import gnat.representation.RecognizedEntity;
import gnat.representation.Text;
import gnat.representation.TextAnnotation;
import gnat.representation.TextRepository;
import gnat.utils.StringHelper;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;


/**
 * Also known as the CrazyRegExFilter. Removes gene names that are too unspecific
 * ("DNA binding protein") and those that most likely refer to a gene family, process, 
 * cell, disease, experimental technique (GST, LPS), etc.
 *
 * @author Joerg
 */
public class UnspecificNameFilter implements Filter {

	/** characters that appear on the left side of a proper word: white space, bracket starts, .. */
	public static String leftWordBoundary = "(^|[\\s\\(\\[\\/])";
	/** characters that appear on the right side of a proper word: white space, comma, bracket ends, .. */
	public static String rightWordBoundary = "([\\s\\,\\)\\]\\/\\;\\!\\?]|$)";

	
	/**
	 *	Removes all recognized gene names that are made up entirely of unspecific keywords or where names point to something else than a gene name,
	 *  e.g. a tissue, species, amino acid.
	 */
	public void filter (Context context, TextRepository textRepository, GeneRepository geneRepository) {
		int removed = 0;
		int total =  context.getUnidentifiedEntities().size();

		Iterator<RecognizedEntity> unidentifiedGeneNames = context.getUnidentifiedEntities().iterator();
		while (unidentifiedGeneNames.hasNext()) {
			//
			RecognizedEntity recognizedGeneName = unidentifiedGeneNames.next();
			IdentificationStatus identificationStatus = context.getIdentificationStatus(recognizedGeneName);
			HashSet<Integer> speciesIDs = new HashSet<Integer>();
			if (geneRepository != null) {
				Set<String> geneIdCandidates = identificationStatus.getIdCandidates();
				for (String gid: geneIdCandidates) {
					if (geneRepository.getGene(gid) != null)
						speciesIDs.add(geneRepository.getGene(gid).getTaxon());
				}
			}

			// the text in which this gene name occurs
			Text text = recognizedGeneName.getText();
			TextAnnotation annotation = recognizedGeneName.getAnnotation();
			String sentence = text.getSentenceAround(annotation.getTextRange().getBegin());
			// mask any characters that have a meaning in reg.exes
			String maskedGeneName = StringHelper.espaceString(recognizedGeneName.getName());

			//
			String reason = "";
			if (isUnspecific(maskedGeneName, speciesIDs))
				reason = "unspecific";
			else if (isUnspecificSingleWordCaseInsensitive(maskedGeneName))
				reason = "unspecific single word case-insensitive";
			else if (isUnspecificSingleWord(maskedGeneName))
				reason = "unspecific single word";
			else if (isTissueCellCompartment(maskedGeneName))
				reason = "tissue, cell compartment";
			else if (isUnspecificAbbreviation(recognizedGeneName.getName(), sentence, speciesIDs))
				reason = "unspecific abbreviation";
			else if (isSpecies(maskedGeneName))
				reason = "species";
			else if (isAminoAcid(maskedGeneName))
				reason = "amino acid";
			else if (textHasPlural(maskedGeneName, text.plainText))
				reason = "text has plural form";
			else if (isChromosome(recognizedGeneName.getName(), sentence))
				reason = "chromosome";
			else if (isDiseaseName(recognizedGeneName.getName()))
				reason = "disease name";
			else if (isDiseaseName(maskedGeneName))
				reason = "disease name (masked)";
			else if (isNegativePair(recognizedGeneName.getName(), sentence))
				reason = "not a gene in this context";
			else if (sentence.matches(".*" + maskedGeneName + "([\\-\\/][A-Za-z0-9]*[A-Z0-9][A-Za-z0-9]*)?( [a-z]+)? (gene|protein) family([\\.\\,\\;\\:]| [a-z]+ [a-z]+[\\s\\,\\.\\:\\;]).*"))
				reason = "gene family";
			
			//
			if (reason.length() > 0) {
				if (ConstantsNei.verbosityAtLeast(ConstantsNei.OUTPUT_LEVELS.DEBUG))
					ConstantsNei.OUT.println("UNF: removing " + recognizedGeneName.getName() + " in sentence " + sentence + "; reason: " + reason);
				context.removeRecognizedEntity(recognizedGeneName);
				removed++;
			}
		}

		if (ConstantsNei.verbosityAtLeast(ConstantsNei.OUTPUT_LEVELS.DEBUG))
			ConstantsNei.OUT.println(" "+ this.getClass().getSimpleName()+": removed "+removed+" names out of "+total);
	}


	/**
	 * 
	 * @param term
	 * @return
	 */
	public static boolean isUnspecific (String term) {
		HashSet<Integer> speciesIDs = new HashSet<Integer>();
		return isUnspecific(term, speciesIDs);
	}

	
	/**
	 * Decides whether a name is too unspecific: mainly checks for compound names that do not
	 * have any reference concering the exact identify of a protein. Also removes units and
	 * diseases/syndromes, single letter names<br>
	 * Examples: DNA binding protein, nerve growth factor, human polymerase, cell-surface glycoprotein.
	 *
	 * @param term
	 * @return
	 */
	public static boolean isUnspecific (String term, HashSet<Integer> speciesIDs) {
		term = term.trim();
		term = term.replaceFirst("[\\,\\-\\;]+$", "");

		// reject single characters or whole abstracts --> TODO check if there is a name>75 in the masterlist!
		//if (term.length() < 3 || term.length() > 75) return true;
		if (term.length() == 1
				&& !speciesIDs.contains(7227)) {
				//&& !ImmediateContextFilterForHuman.isDiseaseName(term)) {
			return true;
		}
//		if (term.length() == 2) {
//			// keep some valid names
//			// keep some disease names(abbreviations) -- they should be checked again with
//			// the ImmediateContextFilter: DM, NF, Lu, Le, Se
//			// maybe add TX
//			if (!term.matches("([LKP]\\d)")) return true;
//		}

		// reject numbers
		if (term.matches("[\\d\\.\\s]+")) return true;


		// specifiers that often occurr in name field entries of databases (mostly in brackets) or are just too unspecific
		if(term.matches("([Aa]utosomal recessive|splicing|protease|checkpoint|T cell|cytoskeleton|C\\-terminal fragments" +
				"|skeletal muscle|liver|kidney|expressed in .*|endothelial|murine|gene product|polypeptide|deafness|congenital" +
				"|nonsyndromic|up\\-regulated|hematopoietic|retinal|macular|embryonic|myopia|catalytic subunits?|regulatory subunits?" +
				"|T cell differentiation|processing of separase|chromosome|translocated to|mutated|bind|bind DNA|dendritic|repeats" +
				"|autosomal recessive deafness|congenital deafness|tandem|lipid raft|[Ee]ndothelial cell|glucose|cadherin superfamily" +
				"|cadherin family" +
				"|.* domain|processing of separase|molecular weight|[Cc]\\-terminal|epithelial|immunodeficiency|inhibitor securin" +
				"|[Ii]nhibitor binding|type I lissencephaly|antisense|secreted|high affinity|Collagen XVIII|transmembrane protease" +
				"|transmembrane serine protease|regulatory subunit NEMO|interacting|autosomal dominant|retina|actin cytoskeleton" +
				"|intestinal epithelial|juvenile|Rho family|.* chemokine|X\\-linked retinoschisis|secreted photoreceptor" +
				"|putative secreted photoreceptor|soluble L1|Soluble CD2|loss of heterozygosity|Kv4.2 potassium channel|alpha1 AMPK" +
				"|testis|K\\(\\+\\)|mm K\\(\\+\\)|Src homology|antiproliferative|focal|gamma IP-10|receptor trafficking|serine protease" +
				"|homology|skin-derived|unknown function|lymphoid|EST|CNS" +
				// added to SF-Gnat acc. "100-test"
				"|body weight|bone mineral content|long bones|renal cystic disease severity|GAGA transcription factor" +
				"|[Dd]ominant megacolon|sex-peptide|gamma\\(c\\)|Ames dwarf|arch|obese)"

		)){
			return true;
		}

		// units and some typical mistakes (and 1, or in) --> bp has to keep Bp50!
		if (term.matches("^(aa|bp\\s[0-9]{1,2}|kd|mg|Ki|nM|CD|Sci|Proc|Acad|\\d+ h" +
				"|[\\d\\.]+[\\s\\-]?[Kk][Dd][Aa]" +
				"|or\\sin|and\\s[1Ii]|for\\s4|is\\s4|[Aa]\\sgene|[Aa]t\\s5|[Aa]\\sC|is\\s1|at\\s\\d" +
				"|as a|a PS|or if|a [ACGTU]|d or|a part" +

				"|factor[\\s\\-]\\d|factor[\\s\\-](alpha|beta|gamma|delta)" +
				"|receptor\\s\\d|[Ii]soforms?[\\s\\-]?\\d+|[Ii]sozymes?[\\s\\-]\\d+?" +
				"|[A-Z]\\receptor|S phase" +

				"|protein\\s[A-Za-z]|protein[\\s\\-][0-9]+" +
				"|beta[\\s\\-]\\d+|\\d+[\\s\\-]beta([\\s\\-]\\d+)?" +
				"|alpha[\\s\\-]\\d+|\\d+[\\s\\-]alpha([\\s\\-]\\d+)?" +

				"|(alpha|beta|gamma|delta|epsilon|eta|kappa|lambda)\\s[A-Za-z0-9]" +
				"|[A-Za-z0-9][\\s\\-](alpha|beta|gamma|delta|epsilon|eta|kappa|lambda)" +
				"|(alpha|beta|gamma|delta|epsilon|eta|kappa|lambda)\\schain" +
				"|[A-Za-z][\\s\\-]protein" +
				"|[Aa]\\s[0-9]{1,2})$"))
			return true;

		// processes
		if (term.endsWith("nesis") || term.endsWith("gression") || term.endsWith("vation"))
			return true;

		// cAMP, vAMP --> TODO removes CAMP (EntrezGene 820)
		if (term.toLowerCase().endsWith("amp") && term.length() == 4)
			return true;

		// a list of name parts that, in any combination, result in an unspecific mention
		// protein types and functions, cellular/tissue locations, processes
		// latest add ons: polypetide release (factor), (growth) hormone, "lung cancer"   --in brackets: token was already included
		if (term.matches("^([\\s\\,\\.\\-\\;\\:\\(\\)\\/]" +
				"|isoform|subunit|ligand|complement|chain|site|form|domain|autoantigen|antigen|sequence|homolog|type" +
				"|subtype|motif|group|candidate|molecule|superfamily|family|subfamily|transcript|[Ff]ragment" +
				"|[fF]actor|regulator|inhibitor|suppressor|translocator|activator|[rR]eceptor|[lL]igand|adaptor|adapter" +
				"|nucleoprotein|oncoprotein|phosphoprotein|glycoprotein|[Pp]rotein|polypeptide" +
				"|RNA|DNA|dna|cDNA|rna|mRNA|mrna|mRna|tRNA|tRna|trna" +
				"|histone|collagen|neuron" +
				"|caspase|kinase|phosphatase|polymerase|coactivator|activator|transporter" +
				"|hormone" +
				//"" +
				"|[eE]xpression|activation|transduction|transcription|adhesion|interaction" +
				"|release" +
				"|[aA]ssociated|induced|coupled|related|linked|associated|conserved|mediated|expressed|advanced|localized" +
				"|activating|regulating|signaling|binding|bound|containing|docking|transforming" +
				"|export|trafficking" +
				//"" +
				"|breast|colon|stem|cell|muscle|cellular|extracellular|intestinal|nuclear|surface|membrane|brain" +
				"|epidermal|ectodermal|vesicle|mitochondrial|pancreatic|ubiquitous|fetal" +
				"|chicken|mammalian|human" +
				//"" +
				"|cancer|carcinoma|tumor|obesity|lung cancer" +
				//"" +
				"|apoptosis|death|growth|maturation|necrosis|signal|repair|survival|stress|division|adhesion" +
				"|control|excision|fusion|cycle" +
				//"" +
				"|heat|shock|proteoglycan|core|homeobox" +
				//"" +
				"|chemokine|cytokine|potassium|calcium|sodium|retinol|pyruvate|vitamin|glutamate|[Zz]inc" +
				"|estrogen|thrombin|arrestin|actin|ubiquitin|mucin|urotensin|disintegrin|activin|chromatin|calmodulin" +
				"|tubulin|cyclin|immunoglobulin|heparin|GTP" +
				"|tyrosine|serine|threonine|alanine|arginine|asparagine|cysteine|glutamine|leucine|isoleucine" +
				"|glycine|methionine|histidine|proline|lysine|phenylalanie|thryptophan|valine" +
				//"" +
				"|low|high|highly|non|heterogeneous|homogeneous|light|heavy|negative|novel|putative" +
				"|dependent|accessory|peripheral|regulatory|deficient|terminal|transcriptional|inducible|soluble" +
				"|dual|specificity|specific|nucleic|acid|putative|peroxisomal|basic" +
				"|nucleolar|secretory" +
				"" +
				// new and to sort:
				"|susceptibility|paired|like|specific|muscle|testis" +
				"|mobility|programmed|matrix|channel|end|ciliary|neurotrophic|retinoid|germinal|center|neural|finger" +
				"|fibroblast|lymphokine" +
				//"" +
				//"|A|N" +
				"|[a-z]+ine[\\s\\-]rich" +
				"|[a-z]+ant" +
				//"" +
				"|two" +
				//"" +
				"|to|by|that|like|a|[tT]he|for|of|and|or|with|in" +
				//"" +
				"|[Aa]ntigen|lymphocyte|cytoplasmic|helicase|retinoic|acid|plasminogen|cytoskeletal|anchor" +
				"|[Aa]nti|integral|membrane|[Nn]eutrophil|ubiquitin" +
				"|basic|leucine|zipper|putative|transmembrane|proteasome|responsive" +

				// removed:
				// |clathrin|I|i|II|ii|iii|III|gamma|delta|alpha|beta
				// |[a-z]phin|[a-z]+[^u]lin|[a-z]+asia|[a-z]+itis
				// |mannose

				")+('?s)?$"))
			return true;
		return false;
	}


	public static boolean isAminoAcid (String name) {
		return name.matches("(" +
				"Ala|[Aa]lanine" +
				"|Arg|[Aa]rginine" +
				"|Asn|[Aa]sparagine" +
				"|Asp|[Aa]spartic acid" +
				"|Cys|[Cc]ysteine" +
				"|Gln|[Gg]lutamine" +
				"|Gly|[Gg]lycine" +
				"|Glu|[Gg]lutamic acid" +
				"|His|[Hh]istidine" +
				"|Ile|[Ii]soleucine" +
				"|Leu|[Ll]eucine" +
				"|Lys|[Ll]ysine?" +
				"|Met|[Mm]ethionine" +
				"|Phe|[Pp]enylalanine" +
				"|Pro|[Pp]roline" +
				"|Ser|[Ss]erine" +
				"|Thr|[Tt]hreonine" +
				"|Trp|[Tt]ryptophane?" +
				"|Tyr|[Tt]yrosine" +
				"|Val|[Vv]aline)s?");
	}


	/**
	 * 
	 * @param name
	 * @param sentence
	 * @return
	 */
	public static boolean isUnspecificAbbreviation (String name, String sentence) {
		HashSet<Integer> speciesIDs = new HashSet<Integer>();
		return isUnspecificAbbreviation(name, sentence, speciesIDs);
	}


	/**
	 *
	 * @return
	 */
	public static boolean isUnspecificAbbreviation (String name, String sentence, HashSet<Integer> speciesIDs) {
		if (name.length() > 2) return false;
		//if (ImmediateContextFilterForHuman.isDiseaseName(name)) return false;
		if (name.length() <= 2 && !speciesIDs.contains(7227)) {
			name = StringHelper.espaceString(name);
			if (sentence.matches(".*[\\s\\(]" + name + "[\\s\\,\\)]([^\\s]+\\s)?(gene|protein|locus|loci)s?.*")) return false;
			if (sentence.matches(".*(gene|protein|locus\\sfor|loci\\sfor)s?[\\s\\,\\)]([^\\s]+\\s)?[\\(\\s]?" + name + "[\\s\\,\\)].*")) return false;
			return true;
		}
		return false;
	}


	/**
	 *
	 * @param name
	 * @return
	 */
	public static boolean isUnspecificSingleWordCaseInsensitive (String name) {
		return name.toLowerCase().trim().matches("(for|in|of|at|an" +
				"|milk|cycling|enabled|blast|lipase|golgi|fusion|proteins?|nuclear|sex" +
				"|similar\\sto|rough\\sdeal|alternative\\ssplicing|membrane[\\s\\-]?bound" +
				"|proton[\\s\\-]?pump|partial|macrophage|condensed|proteins?)");
	}


	/**
	 *
	 * @param name
	 * @return
	 */
	public static boolean isUnspecificSingleWord (String name) {
		return name.trim().matches("(aim|fat|tube|part|mass|[Tt]runcated|envelope" +
				"|gels|[Tt]ag|ORF|secretory|mediator|inactive|pump|cis|killer|[a-z]+[\\s\\-]?binding" +
				"|islet|homeobox|insulin|clamp|MHC\\s[Cc]lass\\s[Ii][Ii]?" +
				"|transactivator|open reading frame|scaffold|fused|blot" +
				"|II|VII|[Aa]lpha|[Bb]eta|[Gg]amma|[Dd]elta|[Ee]psilon|tau|zeta" +
				"|great|tissue|simple|face|nude|type|raft|partial|bind|cord|Chr|rank|anti" +
				"|can|not|was|has|on|via|use|up|acidic|longest|best|raised|multiple" +
				"|Ca2|CA|C\\-C|CHO|Cys|pro|how|early|similar|no|period|rod" +
				"|interleukins|releases?|origins?|chemokines?|sons?|nets?" +
				//"|LPS" +
				"|[a-z]+s" +
				// added for Lupus/IBD project:
				"|mild|platelet|drip|sera|neo|radix|spliceosomal|hip|Part I|[Uu]rinary protein|urine protein" +
				"|Chi|dot|rash|pulmonary function|BMI|toll|min|lethal|pan|Med|celiac" +
				"|Abs|Ags|UTR|expand|killer" +
				"|alpha1|alpha4|beta1|gamma1" +
				"|[Pp]roteasome|[Ii]ntestinal|flu|Dan|and 1|as 1|or 2" +
				"|CD4|CD8|Mai|dL" +
				// BC2 gn test
				"|kbp|helical|post\\-?synaptic|min\\-1|death[\\-\\s]inducing|sub|repressor" +
				"|early[\\-\\s]response|[Nn]on\\-histone[\\-\\s]chromosomal|pituitary|a catalytic" +
				"|P\\(k\\)|[Mm]ediator complex|trans-Golgi network|D or" +
				")");
	}

	


	/**
	 *
	 *	Returns true if the name is followed by a keyword indicating that this name is a disease.
	 *
	 * @param name
	 * @return
	 */
	public static boolean isDiseaseName (String name) {
		boolean ret = false;
		// cannot remove "... disease" directly, b/c "a gene associated with ... disease"!
		if (name.trim().matches(
				".*([a-z]+(phase|osis|topy|trophy|itis|noma|phoma|axia|emia|stoma)|syndrome|failure|disease|severity)"))
			ret = true;
		if (name.trim().matches(
				"(NF|[Nn]eurofibromatosis([\\s\\-][12])?" +
				"|DM" + //|myotonic\\sdystrophy" +
				"|Lu|Lutheran\\sblood\\sgroup" +
				"|Se" +
				"|H" +
				"|Le|Lewis\\sblood\\sgroup" +
				"|Rb" + //|retinoblastoma" +
				"|LW|LW\\sblood\\sgroup|Landsteiner[\\s\\-]Wiener\\sblood\\sgroup" +
				"|autoimmune susceptibility" +
				"|severe combined immunodeficiency" +
				"|FHC" +//|familial\\shypercholesterolemia" +
				// Lupus/IBD:
				"|adipose|SLE|multiple sclerosis|anti\\-?phospholipid syndrome|[a-z]+ syndrome" +
				"|thrombocytopenia|renal amyloidosis|IBD" +
				// BC2 gn text
				"|hepatocellular\\scarcinoma|hereditary\\shemochromatosis|promyelocytic\\sleukemia" +
				"|retinitis pigmentosa|multiple endocrine neoplasia" +
				")"))
			ret = true;

		return ret;
	}
	

	/**
	 *
	 *	Returns true if this disease name is mentioned together with a locus or genes/proteins in this sentence.
	 *
	 * @param name
	 * @param sentence
	 * @return
	 */
	public static boolean keepDiseaseName (String name, String sentence) {
		try {
	        return (
	        	   sentence.matches(".*" + leftWordBoundary + "(locus|loci|location|chromosom[a-z]+|gene.+associated)" + rightWordBoundary + ".*")
	        	|| sentence.matches(".*" + leftWordBoundary + name.replaceAll("([\\+\\-\\*\\(\\)\\[\\]\\{\\}])", "\\\\$1") + rightWordBoundary + ".*" + leftWordBoundary + "(gene|protein)s?" + rightWordBoundary + ".*")
	        	);
        }
        catch (java.util.regex.PatternSyntaxException e) {
	        return false;
        }
	}


	/**
	 *
	 * @param name
	 * @return
	 */
	public static boolean isTissueCellCompartment (String name) {
		return name.trim().matches(
				"([a-z]+(plasmic|plastic)" +
				"|[a-z]+(skeletal|\\sretina)|amyloid|neuronal" +
				"|[a-z]+(phil)" +
				"|[a-z]+(cytes?)|[a-z]+ic cell" +
				"|[a-z]ial|[a-z]ic" +
				"|[Ss]tem cells?" + 
				")"
		);
	}

	
	/**
	 *	Returns true if the name is followed by a keyword indicating that this name is a cell line.
	 *
	 * @param name
	 * @param sentence
	 * @return
	 */
	public static boolean isCellLine (String name, String sentence) {//, String text) {
		// mask any characters that have a meaning in reg.exes
		name = StringHelper.espaceString(name);
		
		if (sentence.matches(".*" + leftWordBoundary  + name + rightWordBoundary + "(cell|culture)s?.*")) {
			//System.out.println("#Checking '" + name + "' for cell line in sentence '" + sentence + "' => yes");
			return true;
		}
//		if (sentence.matches(".*" + leftWordBoundary  + name + rightWordBoundary + "sequences?.*")) {
//			System.out.print("#Checking '" + name + "' for cell line in sentence '" + sentence + "' => yes");
//			return true;
//		}
		if (name.matches("CD\\d+")) {
			if (sentence.matches(".*" + leftWordBoundary  + name + rightWordBoundary + "([A-Za-z\\-]+ )?(cell)s?.*")) {
				//System.out.println("#Checking '" + name + "' for cell line in sentence '" + sentence + "' => yes");
				return true;
			}
		}
		if (sentence.matches(".*" + leftWordBoundary  + name + "[\\-\\/][0-9]+[A-Z]*" + rightWordBoundary + "([a-z]+ ){0,2}(cell|culture)s?.*")) {
			//System.out.println("#Checking '" + name + "' for cell line in sentence '" + sentence + "' => yes");
			return true;
		}
		return false;
	}



	/**
	 * Naive implementation: checks if the name refers to a species.
	 * @param name
	 * @return true if the name refers to a species
	 */
	public static boolean isSpecies (String name) {
		return name.trim().toLowerCase().matches(
				"(human|man|patient|mouse|mice|murine|pig|hiv|rabbit|coli|avian|chimp|chicken|rat|e\\. coli)"
		);
	}


	/**
	 * Checks if the given name found in a sentence refers to a (human!) chromosome:<br>
	 * it should have at least a chromosome number and arm (13p), maybe also bands (13p.23), and
	 * the name should also appear next to the word 'chromosome'
	 * 
	 * @param name
	 * @param sentence
	 * @return true if the given name refers to a chromosome
	 */
	public static boolean isChromosome (String name, String sentence) {
		if (!name.matches("(X|Y|[\\d]+[pq](\\.[\\d\\.]+)?)")) return false;
		name = StringHelper.espaceString(name);
		return sentence.matches(".*(chromosome " + name + "|" + name + " chromosome).*");
	}
	
	
	/**
	 * Some gene names refer to the actual gene only in very few cases. Examples are<br>
	 * - GST (glutathione-S-transferase, an experimental technique for pulldowns),<br>
	 * - LPS (lipopolysaccharide in most cases, not IRF6),<br>
	 * which need to be filtered out. 
	 * 
	 * @param name
	 * @param sentence
	 * @return 
	 */
	public static boolean isNegativePair (String name, String sentence) {
		if (name.equals("LPS") && sentence.matches(".*(induce|administ|stimulat).*")) return true;
		if (name.equals("GST") && sentence.matches(".*(pull\\-?down|assay|fusion|purification|\\Wtag\\W|blotting|anti\\-?body).*")) return true;
		return false;
	}
	
	
	
	/**
	 * 
	 * @param name
	 * @return
	 */
	public static boolean textHasPlural (String name, String text) {
		// in-general mention
		name = StringHelper.espaceString(name);
		try {
			if (text.matches(".*[\\s\\(\\[\"]" + name + "s[\\s\\,\\.\\]\\)\"].*")
					// but not a reference to a group of similar genes
					&& !text.matches(".*(homolog|ortholog|similar)[a-z]*[^\\.\\;\\:]*[\\s\\(\\[]" + name + "s[\\s\\,\\.\\]\\)\"].*")) {
				if (ConstantsNei.verbosityAtLeast(ConstantsNei.OUTPUT_LEVELS.DEBUG))
					System.out.println("#UNF: Text contains plural form: " + name);
				return true;
			}
		} catch (java.util.regex.PatternSyntaxException e) {
		}
		return false;
	}


}