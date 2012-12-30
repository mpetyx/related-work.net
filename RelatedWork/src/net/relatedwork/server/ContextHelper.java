package net.relatedwork.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletContext;

import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.index.Index;
import org.neo4j.helpers.collection.MapUtil;
import org.neo4j.index.impl.lucene.LuceneIndex;
import org.neo4j.kernel.EmbeddedGraphDatabase;
import org.neo4j.kernel.EmbeddedReadOnlyGraphDatabase;

import net.relatedwork.server.executables.CustomTokenAnalyzer;
import net.relatedwork.server.neo4jHelper.DBNodeProperties;
import net.relatedwork.server.userHelper.UserInformation;
import net.relatedwork.server.utils.Config;
import net.relatedwork.server.utils.IOHelper;
import net.relatedwork.shared.SuggestTree;

public class ContextHelper {

	public static final String SUGGEST_TREE = "sugges-ttree";
	public static final String READ_ONLY_NEO4J = "read-only-neo4j";
	public static final String RW_NEO4J = "rw-neo4j";
	
	public static final String SEARCH_IDX_NEO4J = DBNodeProperties.SEARCH_INDEX_NAME;
	public static final String SEARCH_IDX_GWT = "searchIdx";
	
	public static final String PAPER_IDX_NEO4J = "source_idx";
	public static final String PAPER_IDX_GWT = "paper-idx";
	
	public static final String URI_IDX = DBNodeProperties.URI_INDEX_NAME;

	// Get NEO4J DB
//	public static EmbeddedReadOnlyGraphDatabase getReadOnlyGraphDatabase(ServletContext servletContext){
//		EmbeddedReadOnlyGraphDatabase graphDB = (EmbeddedReadOnlyGraphDatabase)servletContext.getAttribute(READ_ONLY_NEO4J);
//		if (graphDB == null){
//			IOHelper.log("Adding neo4j RO db to servletContext");
//			graphDB = new EmbeddedReadOnlyGraphDatabase(Config.get().neo4jDbPath);
//			servletContext.setAttribute(READ_ONLY_NEO4J, graphDB);
//		}
//		return graphDB;
//	}

	// Get NEO4J RW version
	public static EmbeddedGraphDatabase getGraphDatabase(ServletContext servletContext){
		EmbeddedGraphDatabase graphDB = (EmbeddedGraphDatabase)servletContext.getAttribute(RW_NEO4J);
		if (graphDB == null){
			IOHelper.log("Adding neo4j RW db to servletContext");
			graphDB = new EmbeddedGraphDatabase(Config.get().neo4jDbPath);
			servletContext.setAttribute(RW_NEO4J, graphDB);
		}
		return graphDB;
	}

	
	// Auto Completion
	public static SuggestTree<Integer> getSuggestTree(
			ServletContext servletContext) {
		SuggestTree<Integer> tree = (SuggestTree<Integer>) servletContext.getAttribute(SUGGEST_TREE);
		if (tree == null){
			IOHelper.log("build new suggesttree from disk");
			tree = new SuggestTree<Integer>(5,new Comparator<Integer>(){
				@Override
				public int compare(Integer o1, Integer o2) {
					return -o1.compareTo(o2);
				}});
			
			HashMap<String, Integer> map = new HashMap<String, Integer>();
			BufferedReader reader = IOHelper.openReadFile(Config.get().autoCompleteFile);
			String line = "";
			try {
				while ((line=reader.readLine())!=null){
					String[] values = line.split("\t\t");
					if (values.length!=2)continue;
					map.put(values[1], Integer.parseInt(values[0]));
				}
			} catch (NumberFormatException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			tree.build(map);
			servletContext.setAttribute(SUGGEST_TREE, tree);
		}
		return tree;
	}

	// Search index
	public static Index<Node> getSearchIndex(ServletContext servletContext){
//		EmbeddedReadOnlyGraphDatabase graphDB = getReadOnlyGraphDatabase(servletContext);
		EmbeddedGraphDatabase graphDB = getGraphDatabase(servletContext);
		Index<Node> index = (Index<Node>)servletContext.getAttribute(SEARCH_IDX_GWT);
		if (index == null){
			IOHelper.log("Adding search index - " + SEARCH_IDX_GWT + "- to servletContext.");
			index = graphDB.index().forNodes(SEARCH_IDX_NEO4J,
					MapUtil.stringMap("analyzer", CustomTokenAnalyzer.class.getName())
					);
			((LuceneIndex<Node>) index).setCacheCapacity("key", 300000);
			servletContext.setAttribute(SEARCH_IDX_GWT,index);
		}
		return index;
	}

	// URI index
	public static Index<Node> getUriIndex(ServletContext servletContext){
//		EmbeddedReadOnlyGraphDatabase graphDB = getReadOnlyGraphDatabase(servletContext);
		EmbeddedGraphDatabase graphDB = getGraphDatabase(servletContext);
		Index<Node> index = (Index<Node>)servletContext.getAttribute(URI_IDX);
		if (index == null){
			IOHelper.log("Adding uri index - " + URI_IDX + " - to servletContext");
			index = graphDB.index().forNodes(URI_IDX);
			((LuceneIndex<Node>) index).setCacheCapacity(DBNodeProperties.URI, 300000);
			servletContext.setAttribute(URI_IDX,index);
		}
		return index;
	}

	public static Node getNodeByUri(String uri, ServletContext servletContext) {
		return getUriIndex(servletContext).get(DBNodeProperties.URI, uri).getSingle();
	}

	public static Node getUserNodeFromEamil(String email, ServletContext servletContext) {
		return getNodeByUri("rw:user:" + email, servletContext);
	}

	public static void indexUserNode(Node userNode, String email, ServletContext servletContext) {
		getUriIndex(servletContext).add(userNode, DBNodeProperties.URI, "rw:user:"+ email);
		
	}	

}