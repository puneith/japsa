package japsadev.bio.hts.newscarf;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import org.graphstream.graph.*;
import org.graphstream.graph.implementations.*;

import japsa.seq.Alphabet;
import japsa.seq.FastaReader;
import japsa.seq.Sequence;
import japsa.seq.SequenceReader;

public class BidirectedGraph extends AdjacencyListGraph{
    static int kmer=127;
    static final int TOLERATE=500;

    // *** Constructors ***
	/**
	 * Creates an empty graph.
	 * 
	 * @param id
	 *            Unique identifier of the graph.
	 * @param strictChecking
	 *            If true any non-fatal error throws an exception.
	 * @param autoCreate
	 *            If true (and strict checking is false), nodes are
	 *            automatically created when referenced when creating a edge,
	 *            even if not yet inserted in the graph.
	 * @param initialNodeCapacity
	 *            Initial capacity of the node storage data structures. Use this
	 *            if you know the approximate maximum number of nodes of the
	 *            graph. The graph can grow beyond this limit, but storage
	 *            reallocation is expensive operation.
	 * @param initialEdgeCapacity
	 *            Initial capacity of the edge storage data structures. Use this
	 *            if you know the approximate maximum number of edges of the
	 *            graph. The graph can grow beyond this limit, but storage
	 *            reallocation is expensive operation.
	 */
	public BidirectedGraph(String id, boolean strictChecking, boolean autoCreate,
			int initialNodeCapacity, int initialEdgeCapacity) {
		super(id, strictChecking, autoCreate);
		// All we need to do is to change the node & edge factory
		setNodeFactory(new NodeFactory<BidirectedNode>() {
			public BidirectedNode newInstance(String id, Graph graph) {
				return new BidirectedNode((AbstractGraph) graph, id);
			}
		});

		setEdgeFactory(new EdgeFactory<BidirectedEdge>() {
			public BidirectedEdge newInstance(String id, Node src, Node dst, boolean directed) { //stupid??
				return new BidirectedEdge(id, (AbstractNode)src, (AbstractNode)dst);
			}
		});
		
	}

	/**
	 * Creates an empty graph with default edge and node capacity.
	 * 
	 * @param id
	 *            Unique identifier of the graph.
	 * @param strictChecking
	 *            If true any non-fatal error throws an exception.
	 * @param autoCreate
	 *            If true (and strict checking is false), nodes are
	 *            automatically created when referenced when creating a edge,
	 *            even if not yet inserted in the graph.
	 */
	public BidirectedGraph(String id, boolean strictChecking, boolean autoCreate) {
		this(id, strictChecking, autoCreate, DEFAULT_NODE_CAPACITY,
				DEFAULT_EDGE_CAPACITY);
	}

	/**
	 * Creates an empty graph with strict checking and without auto-creation.
	 * 
	 * @param id
	 *            Unique identifier of the graph.
	 */
	public BidirectedGraph(String id) {
		this(id, true, false);
	}
	
	//just to make AbstractGraph.removeEdge(AbstractEdge, boolean, boolean, boolean) visible
	protected void removeEdgeDup(AbstractEdge edge, boolean graphCallback,
			boolean sourceCallback, boolean targetCallback) {
		this.removeEdge(edge, graphCallback, sourceCallback, targetCallback);
	
	}
	protected BidirectedEdge addEdge(AbstractNode src, AbstractNode dst, boolean dir0, boolean dir1){
		BidirectedEdge tmp = addEdge(BidirectedEdge.createID(src, dst, dir0, dir1), src, dst);
//		String s1=tmp.toString();
//		//tmp.setDir0(dir0);
//		//tmp.setDir1(dir1);
//		if(!s1.equals(tmp.toString()))
//			System.out.println(s1 + " ---> " + tmp);
		return tmp;
	}
	/**********************************************************************************
	 * ****************************Algorithms go from here*****************************
	 */
    public BidirectedGraph(){
    	this("Assembly graph",true,false,1000,100000);
        setKmerSize(127);//default kmer size used by SPAdes to assembly MiSeq data
    }
    public void loadFromFile(String graphFile) throws IOException{
        setAutoCreate(true);
        setStrict(false);
		//1. next iterate over again to read the connections
		SequenceReader reader = new FastaReader(graphFile);
		Sequence seq;
		int shortestLen = 10000;
		while ((seq = reader.nextSequence(Alphabet.DNA())) != null){
			if(seq.length()<shortestLen)
				shortestLen=seq.length();
			
			String[] adjList = seq.getName().split(":");
			String name = adjList[0];
			boolean dir0=name.contains("'")?false:true;
			
			name=name.replaceAll("[^a-zA-Z0-9_.]", "").trim(); //EDGE_X_length_Y_cov_Z
			
			String nodeID = name.split("_")[1];
			AbstractNode node = addNode(nodeID);
			node.setAttribute("name", name);
			
			if(dir0){
				seq.setName(name);
				//current.setSequence(seq);
				node.setAttribute("seq", seq);
			}
			if (adjList.length > 1){
				String[] nbList = adjList[1].split(",");
				for(int i=0; i < nbList.length; i++){
					String neighbor = nbList[i];
					// note that the direction is read reversely in the dest node
					boolean dir1=neighbor.contains("'")?true:false;
					neighbor=neighbor.replaceAll("[^a-zA-Z0-9_.]", "").trim();
					
					String neighborID = neighbor.split("_")[1];
					AbstractNode nbr = addNode(neighborID);
					
					addEdge(node, nbr, dir0, dir1);
					//e.addAttribute("ui.label", e.getId());
				}
			}
			
		}

		//rough estimation of kmer used
		if((shortestLen-1) != getKmerSize())
			setKmerSize(shortestLen-1);
		
		reader.close();
    }
    /*
     * Read paths from contigs.path and reduce the graph
     */
    public void readPathsFromSpades(String paths) throws IOException{

		BufferedReader pathReader = new BufferedReader(new FileReader(paths));
		
		String s;
		//Read contigs from contigs.paths and refer themselves to contigs.fasta
		boolean flag=false;
		while((s=pathReader.readLine()) != null){
			if(s.contains("NODE")){
				flag=s.contains("'")?false:true;
				continue;
			}else if(flag){
				BidirectedPath path=new BidirectedPath(this, s);
//				System.out.println("Using path to reduce: " + path.getId());
//				System.out.println("Before reduce => Node: " + getNodeCount() + " Edge: " + getEdgeCount());
				
//				AbstractNode comp=
				this.reduce(path);

//				if(comp!=null){
//					System.out.println("Reverting node: " + comp.getId());
//					revert(comp);
//			        System.out.println("After revert => Node: " + getNodeCount() + " Edge: " + getEdgeCount());
//
//				}
			}	
				

		}
		pathReader.close();
    }
	
    public static int getKmerSize(){
    	return BidirectedGraph.kmer;
    }
    public static void setKmerSize(int kmer){
    	BidirectedGraph.kmer=kmer;
    }
    
    /**
     * 
     * @param p Path to be grouped as a virtually vertex
     */
    public AbstractNode reduce(BidirectedPath p){
    	//do nothing if the path has only one node
    	if(p.getEdgeCount()<1)
    		return null;
    	//add the new composite Node to the graph
    	//compare id from sense & anti-sense to get the unique one
    	AbstractNode comp = addNode(p.getId().compareTo(p.getReversedComplemented().getId())>0?
    								p.getReversedComplemented().getId():p.getId());
    	
    	comp.addAttribute("path", p);
    	comp.addAttribute("seq", p.spelling());
        comp.addAttribute("ui.label", comp.getId());
        comp.setAttribute("ui.style", "text-offset: -10;"); 
        comp.setAttribute("ui.class", "marked");
        try { Thread.sleep(100); } catch (Exception e) {}

    	//store unique nodes on p for removing
    	ArrayList<String> tobeRemoved=new ArrayList<String>();
    	for(Node n:p.getEachNode()){
    		if(isUnique(n))
    			tobeRemoved.add(n.getId());
    	}
    	BidirectedNode 	start = (BidirectedNode) p.getRoot(),
    					end = (BidirectedNode) p.peekNode();
    	boolean startDir = ((BidirectedEdge) p.getEdgePath().get(0)).getDir(start), 
    			endDir = ((BidirectedEdge) p.peekEdge()).getDir(end);
    	//set neighbors of the composite Node
    	Iterator<Edge> startEdges = startDir?start.getEnteringEdgeIterator():start.getLeavingEdgeIterator(),
    					endEdges = endDir?end.getEnteringEdgeIterator():end.getLeavingEdgeIterator();
    	while(startEdges.hasNext()){
    		BidirectedEdge e = (BidirectedEdge) startEdges.next();
    		BidirectedNode opNode = e.getOpposite(start);
    		boolean opDir = e.getDir(opNode);
    		//Edge tmp=
    		addEdge(BidirectedEdge.createID(comp, opNode, false, opDir), comp, opNode);//always into start node
    		//System.out.println("From " + start.getId() + ": " + tmp.getId() + " added!");
    	}
    	
    	while(endEdges.hasNext()){
    		BidirectedEdge e = (BidirectedEdge) endEdges.next();
    		BidirectedNode opNode = e.getOpposite(end);
    		boolean opDir = e.getDir(opNode);
    		//Edge tmp=
    		addEdge(BidirectedEdge.createID(comp, opNode, true, opDir), comp, opNode);//always out of end node
    	
    		//System.out.println("From " + end.getId() + ": " + tmp.getId() + " added!");

    	}

    	for(String nLabel:tobeRemoved){
    		//System.out.println("About to remove " + nLabel);
    		removeNode(nLabel);
    	}
    		
    	//TODO: remove bubbles...
    	return comp;
    }
    /**
     * 
     * @param v Node to be reverted (1-level reverting)
     */
    public void revert(AbstractNode v){
    	//TODO: revert to initial status by extracting a complex vertex into its initial components
    	Path p=v.getAttribute("path");
    	if(p==null) return;
    	
    	BidirectedNode 	start = (BidirectedNode) p.getRoot(),
    					end = (BidirectedNode) p.peekNode();
    	boolean startDir = ((BidirectedEdge) p.getEdgePath().get(0)).getDir(start), 
    			endDir = ((BidirectedEdge) p.peekEdge()).getDir(end);
    	
    	//add back all neighbor edges of this composite vertex
    	Iterator<Edge> 	startEdges = v.getEnteringEdgeIterator(),
						endEdges = v.getLeavingEdgeIterator();
    	//add back all nodes from the path
		for(Node n:p.getNodeSet()){
			if(getNode(n.getId())!=null)
				continue;
			Node tmp = addNode(n.getId());
			tmp.addAttribute("seq", (japsa.seq.Sequence)n.getAttribute("seq"));
			tmp.addAttribute("name", (String)n.getAttribute("name"));
			tmp.addAttribute("path", (BidirectedPath)n.getAttribute("path"));

			//System.out.println("Adding back edge "+tmp.getId());
		}
		while(startEdges.hasNext()){
			BidirectedEdge e = (BidirectedEdge) startEdges.next();
			BidirectedNode opNode = e.getOpposite(v);
			boolean opDir = e.getDir(opNode);
			//Edge tmp = 
			addEdge(BidirectedEdge.createID(start, opNode, !startDir, opDir), start, opNode);
			//System.out.println("Adding back edge "+tmp.getId());
		}
		
		while(endEdges.hasNext()){
			BidirectedEdge e = (BidirectedEdge) endEdges.next();
			BidirectedNode opNode = e.getOpposite(v);
			boolean opDir = e.getDir(opNode);
			//Edge tmp = 
			addEdge(BidirectedEdge.createID(end, opNode, !endDir, opDir), end, opNode);
			//System.out.println("Adding back edge "+tmp.getId());
		}

    	//add back all edges from the path
		for(Edge e:p.getEdgeSet()){
			//Edge tmp = 
			addEdge(e.getId(), e.getSourceNode().getId(), e.getTargetNode().getId());
			//System.out.println("Adding back edge "+tmp.getId());
		}
    	//finally remove the composite node
    	removeNode(v);
    	}
    /*
     * Important function: determine if a node is able to be removed or not
     */
    public static boolean isUnique(Node node){
    	boolean res = false;
    	if(node.getDegree()<=2){
    		if(((Sequence)node.getAttribute("seq")).length() > 5000 || node.getDegree()==0)
    			res=true;
    	}
    		
    	return res;
    }
}