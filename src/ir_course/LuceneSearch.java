package ir_course;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.tartarus.snowball.ext.PorterStemmer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.en.EnglishAnalyzer;

public class LuceneSearch {

	private static final String QUERY = "query";
	private static final String ABSTRACT_TEXT = "abstractText";
	private Directory index;
	private static final String TITLE = "title";
	private static final int TASK_NUMBER = 3;
	private static final String I_SEARCH_TASK_NUMBER = "irelevancy"; //for indexing
	private static final String SEARCH_TASK_NUMBER = "searchTaskNumber"; //for storing
	private static final String I_RELEVANCY = "isearchTaskNumber"; //for indexing
	private static final String RELEVANCY = "relevancy"; //for storing

	public LuceneSearch() {
		index = new RAMDirectory();
	}

	public void index(List<DocumentInCollection> docs) throws IOException {

		// implement the Lucene indexing here
		StandardAnalyzer analyzer = new StandardAnalyzer();
		IndexWriterConfig config = new IndexWriterConfig(analyzer);

		IndexWriter writer;
		try {
			writer = new IndexWriter(index, config);

			for (DocumentInCollection storedDoc : docs) {
				Document doc = new Document();
				doc.add(new TextField(TITLE, storedDoc.getTitle(), Field.Store.YES));
				doc.add(new TextField(ABSTRACT_TEXT, storedDoc.getAbstractText(), Field.Store.YES));
				doc.add(new IntPoint(I_SEARCH_TASK_NUMBER, storedDoc.getSearchTaskNumber()));
				doc.add(new StoredField(SEARCH_TASK_NUMBER, storedDoc.getSearchTaskNumber()));
				int rel = 0;
				if (storedDoc.isRelevant()) {
					rel = 1;
				}

				doc.add(new IntPoint(I_RELEVANCY, rel));
				doc.add(new StoredField(RELEVANCY, rel));
				doc.add(new TextField(QUERY, storedDoc.getQuery(), Field.Store.YES));
				writer.addDocument(doc);
			}
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}
	
	public static String removeStopWords(String queryStr) throws IOException {
		StandardTokenizer tokenizer = new StandardTokenizer();
		tokenizer.setReader(new StringReader(queryStr));
		CharArraySet stopWords = EnglishAnalyzer.getDefaultStopSet();
		
        TokenStream streamStop = new StopFilter(tokenizer, stopWords);
        StringBuilder sb = new StringBuilder();
        CharTermAttribute charTermAttribute = tokenizer.addAttribute(CharTermAttribute.class);
        streamStop.reset();
        while (streamStop.incrementToken()) {
            String term = charTermAttribute.toString();
            sb.append(term + " ");
        }

        streamStop.end();
        streamStop.close();

        tokenizer.close();  


        return sb.toString();
	}

	public Query buildQuery(String query_s, boolean usePorter, boolean useStopWords) throws IOException {
		// build query
		if (useStopWords) {
			query_s = removeStopWords(query_s);
		}
		List<String> queryVector = Arrays.asList(query_s.toLowerCase().split(" "));
		BooleanQuery.Builder query = new BooleanQuery.Builder();
		Query exactQuery = IntPoint.newExactQuery(I_SEARCH_TASK_NUMBER, TASK_NUMBER);
		query.add(exactQuery, Occur.MUST);
		
		PorterStemmer stemmer = new PorterStemmer();
		
		if (queryVector != null) {
			for (String s : queryVector) {
				if (usePorter) {
					stemmer.setCurrent(s);
					stemmer.stem();
					s = stemmer.getCurrent();
				}
				TermQuery termQ = new TermQuery(new Term(TITLE, s));
				query.add(termQ, Occur.SHOULD);
			}
		}
		
		if (queryVector != null) {
			for (String s : queryVector) {
				TermQuery termQ = new TermQuery(new Term(ABSTRACT_TEXT, s));
				if (usePorter) {
					stemmer.setCurrent(s);
					stemmer.stem();
					s = stemmer.getCurrent();
				}
				query.add(termQ, Occur.SHOULD);
			}
		}
		
		return query.build();
	}

	public List<DocumentInCollection> search(String s, int hitNumber, boolean isBM25) throws IOException {

		List<DocumentInCollection> results = new LinkedList<DocumentInCollection>();

		// implement the Lucene search here

		Query query = buildQuery(s, true, true);

		// search
		DirectoryReader ireader = DirectoryReader.open(index);
		IndexSearcher isearcher = new IndexSearcher(ireader);
		if(!isBM25){
			//BM25
			isearcher.setSimilarity(new BM25Similarity());
		}else{
			//VSM
			isearcher.setSimilarity(new ClassicSimilarity());
		}
		
		
		ScoreDoc[] hits = isearcher.search(query, hitNumber).scoreDocs;

		for (int i = 0; i < hits.length; i++) {
			Document hitDoc = isearcher.doc(hits[i].doc);
			DocumentInCollection doc = new DocumentInCollection();
			doc.setAbstractText(hitDoc.get(ABSTRACT_TEXT));
			doc.setTitle(hitDoc.get(TITLE));
			doc.setQuery(hitDoc.get(QUERY));
			int rel = Integer.parseInt(hitDoc.get(RELEVANCY));
			if(rel==0){
				doc.setRelevant(false);
			}else{
				doc.setRelevant(true);;
			}
			
			doc.setSearchTaskNumber(Integer.parseInt(hitDoc.get(SEARCH_TASK_NUMBER)));
			results.add(doc);
			
			System.out.print(hitDoc.get(TITLE));
			System.out.println(" - " + hits[i].score);
		}

		return results;
	}

}