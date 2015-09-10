/*****************************************************************************
 * Copyright (c) Minh Duc Cao, Monash Uni & UQ, All rights reserved.         *
 *                                                                           *
 * Redistribution and use in source and binary forms, with or without        *
 * modification, are permitted provided that the following conditions        *
 * are met:                                                                  * 
 *                                                                           *
 * 1. Redistributions of source code must retain the above copyright notice, *
 *    this list of conditions and the following disclaimer.                  *
 * 2. Redistributions in binary form must reproduce the above copyright      *
 *    notice, this list of conditions and the following disclaimer in the    *
 *    documentation and/or other materials provided with the distribution.   *
 * 3. Neither the names of the institutions nor the names of the contributors*
 *    may be used to endorse or promote products derived from this software  *
 *    without specific prior written permission.                             *
 *                                                                           *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS   *
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, *
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR    *
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR         *
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,     *
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,       *
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR        *
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF    *
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING      *
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS        *
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.              *
 ****************************************************************************/

/**************************     REVISION HISTORY    **************************
 * 07/09/2014 - Minh Duc Cao: Created                                        
 *  
 ****************************************************************************/

package japsa.bio.np;


import japsa.bio.alignment.ProbFSM.Emission;
import japsa.bio.alignment.ProbFSM.ProbOneSM;
import japsa.bio.alignment.ProbFSM.ProbThreeSM;
import japsa.seq.Alphabet;
import japsa.seq.FastaReader;
import japsa.seq.Sequence;
import japsa.seq.SequenceOutputStream;
import japsa.util.HTSUtilities;
import japsa.util.Logging;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMRecordIterator;
import htsjdk.samtools.SamInputResource;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.ValidationStringency;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;

//
// Design:
//Requires:
// - a File containing a list of gene, say gene.fasta
// - a file containing list of gene allele, say alleles.fasta, linked to gene.fasta
// - a file containg gene information

/**
 * 
 * @author minhduc
 *
 */


public class RealtimeResistanceGene {

	ResistanceGeneFinder resistFinder;		

	private HashMap<String, ArrayList<Sequence>> alignmentMap;
	int currentReadCount = 0;
	long currentBaseCount = 0;

	public String msa = "kalign";
	public String global = "hmm";

	public double scoreThreshold = 2;
	public boolean twoDOnly = false;
	public RealtimeResistanceGene(int read, int time, String output, String tmp) throws IOException{
		resistFinder = new ResistanceGeneFinder(this, output);
		resistFinder.setReadPeriod(read);
		resistFinder.setTimePeriod(time * 1000);

	}




	/**
	 * @param bamFile
	 * @param geneFile
	 * @throws IOException
	 * @throws InterruptedException 
	 */
	public void typing(String bamFile) throws IOException, InterruptedException{
		//DateFormat df = new SimpleDateFormat("dd/MM/yy HH:mm:ss");
		//Logging.info("START : " + df.format(Calendar.getInstance().getTime()));		

		alignmentMap = new HashMap<String, ArrayList<Sequence>> ();

		SamReaderFactory.setDefaultValidationStringency(ValidationStringency.SILENT);
		SamReader samReader;
		if ("-".equals(bamFile))
			samReader = SamReaderFactory.makeDefault().open(SamInputResource.of(System.in));
		else
			samReader = SamReaderFactory.makeDefault().open(new File(bamFile));

		SAMRecordIterator samIter = samReader.iterator();

		Thread t = new Thread(resistFinder, "SSS");
		t.start();

		String readName = "";
		//A dummy sequence
		Sequence readSequence = new Sequence(Alphabet.DNA(),1,"");
		while (samIter.hasNext()){
			SAMRecord record = samIter.next();

			if (this.twoDOnly && !record.getReadName().contains("twodim")){
				continue;
			}

			if (!record.getReadName().equals(readName)){
				readName = record.getReadName();

				currentReadCount ++;	
				currentBaseCount += record.getReadLength();

				//Get the read
				if (!record.getReadUnmappedFlag()){
					readSequence = new Sequence(Alphabet.DNA(), record.getReadString(), readName);
					if (record.getReadNegativeStrandFlag()){
						readSequence = Alphabet.DNA.complement(readSequence);
						readSequence.setName(readName);
					}
				}
			}

			if (record.getReadUnmappedFlag())
				continue;			
			//assert: the read sequence is stored in readSequence with the right direction
			String	geneID = record.getReferenceName();
			if (!resistFinder.geneMap.containsKey(geneID))
				continue;

			int refLength =  resistFinder.geneMap.get(geneID).length();

			synchronized(this){
				ArrayList<Sequence> alignmentList = alignmentMap.get(geneID);
				if (alignmentList == null){
					alignmentList = new ArrayList<Sequence>();
					alignmentMap.put(geneID, alignmentList);				
				}

				//put the sequence into alignment list
				Sequence readSeq = HTSUtilities.spanningSequence(record, readSequence, refLength, 20);
				if (readSeq == null){
					Logging.warn("Read sequence is NULL sequence ");
				}else{
					alignmentList.add(readSeq);
				}
			}//synchronized(this)
		}//while
		resistFinder.stopWaiting();
		samIter.close();
		samReader.close();

		Logging.info("END : " + new Date());
	}	

	//TODO: way to improve performance:
	//1. 
	//3. options: gene or antibiotics class
	//4. 
	//5. Future improve: incrementally multiple alignment

	public static class ResistanceGeneFinder extends RealtimeAnalysis{
		public String prefix = "tmp";		
		RealtimeResistanceGene resistGene;

		HashMap<String, ArrayList<Sequence>> alignmentMapSnap = new HashMap<String, ArrayList<Sequence> >();
		HashMap<String, String> gene2ProteinID;
		HashMap<String, String> gene2Group;			
		HashMap<String, Sequence> geneMap;
		ArrayList<String> geneList = new ArrayList<String>();
		//Set of genes confirmed to have found
		HashSet<String> predictedGenes = new HashSet<String>();

		SequenceOutputStream sos;

		public ResistanceGeneFinder(RealtimeResistanceGene resistGene, String output) throws IOException{
			this.resistGene = resistGene;			
			getGeneClassInformation();
			sos = SequenceOutputStream.makeOutputStream(output);
		}	

		private void antiBioticAnalysis(){
			//1. Make a snapshot of the current alignment
			synchronized(resistGene){
				lastTime = System.currentTimeMillis();
				lastReadNumber = resistGene.currentReadCount;
				for (String gene:resistGene.alignmentMap.keySet()){
					ArrayList<Sequence> readMap = resistGene.alignmentMap.get(gene);					 
					alignmentMapSnap.put(gene, (ArrayList<Sequence>) readMap.clone());
				}
				try {
					antiBioticsProfile();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		/****
		 * 
		 * @throws IOException
		 * @throws InterruptedException
		 */

		int runIndex;
		private void antiBioticsProfile() throws IOException, InterruptedException{
			//Get list of genes from my
			for (String geneID: geneList){				
				if (predictedGenes.contains(geneID))
					continue;

				ArrayList<Sequence> alignmentList =  alignmentMapSnap.get(geneID);
				if (alignmentList == null)
					continue;
				
				if (alignmentList.size() < 3){
					Logging.info("Too small for " + geneID + " " + alignmentList.size()); 
					continue;
				}


				Sequence consensus = 
					ErrorCorrection.consensusSequence(alignmentList, prefix + "_" + geneID + "_" + runIndex, resistGene.msa);

				if (consensus == null){
					//Not consider this gene at all
					continue;//gene
				}

				Sequence gene = geneMap.get(geneID);
				
				if (resistGene.global.equals("hmm")){
					double score = checkHMM(consensus, gene);
					Logging.info("SGF: " + score + " " + geneID + " " + alignmentList.size() + " " + gene2ProteinID.get(geneID) + " " + gene2Group.get(geneID));

					if (score >= resistGene.scoreThreshold){
						addPreditedGene(geneID);
						Logging.info("ADDF " + geneID);//
						//Logging.info("ADDF " + geneID + " " + resistGene.gene2Group.get(geneID)+ " " + resistGene.gene2PrimaryGroup.get(geneID) + " " + geneID);						
						continue;//for gene
					}					
				}else{
					/*****************************************************************/
					String consensusFile = prefix + "consensus" + geneID + "_" + resistGene.currentReadCount + ".fasta"; 
					consensus.writeFasta(consensusFile);				
					{	
						double score = checkNeedle(consensusFile, gene);
						Logging.info("SGF: " + score + " " + geneID + " " + alignmentList.size() + " " + gene2ProteinID.get(geneID) + " " + gene2Group.get(geneID));

						if (score >= resistGene.scoreThreshold){
							addPreditedGene(geneID);
							Logging.info("ADDF " + geneID);							
							continue;//for gene
						}
					}					
				}
			}

			Logging.info("===Found " + predictedGenes.size() + " vs " + geneMap.size() + "  " + alignmentMapSnap.size());

		}

		private void addPreditedGene(String geneID) throws IOException{
			predictedGenes.add(geneID);		
			sos.print(new Date(this.lastTime) + "\t" + this.lastTime +"\t" + geneID + "\t" + gene2Group.get(geneID) + "\n");			
			sos.flush();
		}


		private static double checkHMM(Sequence consensus, Sequence gene){
			if (gene.length() > 2700 || consensus.length() > 4000 || gene.length() * consensus.length() > 6000000){
				Logging.info("SKIP " + gene.getName() + " " + gene.length() + " vs " + consensus.length());			
				return 0;
			}

			//ProbThreeSM tsmF = new ProbThreeSM(gene);
			ProbOneSM tsmF = new ProbOneSM(gene);
			double cost = 100000000;						
			for (int c = 0; c < 10; c++){
				tsmF.resetCount();
				Emission retState = tsmF.alignGenerative(consensus);
				if (cost  <= retState.myCost)
					break;//for c

				cost = retState.myCost;
				int emitCount = tsmF.updateCount(retState);
				Logging.info("Iter " + c + " : " + emitCount + " states and " + cost + " bits " + consensus.length() + "bp " + consensus.getName() + " by " + gene.getName());
				tsmF.reEstimate();	
			}				
			return (consensus.length() * 2 - cost) / gene.length();
		}

		private double checkNeedle(String consensusFile, Sequence gene) throws IOException, InterruptedException{
			//Needle the gene
			String geneID = gene.getName();
			String faAFile = "geneAlleles/out_" + geneID + ".fasta";
			String needleOut = prefix + geneID + "_" + this.lastReadNumber + "_consensus.needle";

			String cmd = "needle -gapopen 10 -gapextend 0.5 -asequence " 
				+ faAFile + " -bsequence " + consensusFile + " -outfile " + needleOut;
			Logging.info("Running " + cmd);
			Process process = Runtime.getRuntime().exec(cmd);
			process.waitFor();		
			Logging.info("Run'ed " + cmd );

			BufferedReader scoreBf = new BufferedReader(new FileReader(needleOut));
			String scoreLine = null;
			double score = 0;
			while ((scoreLine = scoreBf.readLine())!=null){
				String [] scoreToks = scoreLine.split(" ");					
				if (scoreToks.length == 3 && scoreToks[1].equals("Score:")){
					score += Double.parseDouble(scoreToks[2]);
					break;//while
				}					
			}//while
			scoreBf.close();
			return score / gene.length();
		}

		private void getGeneClassInformation() throws IOException{
			ArrayList<Sequence> drGeneList = FastaReader.readAll("F.fasta", Alphabet.DNA());

			geneMap    = new HashMap<String, Sequence>();
			gene2Group = new HashMap<String, String>();
			gene2ProteinID = new HashMap<String, String>();


			for (Sequence seq:drGeneList){
				geneMap.put(seq.getName(), seq);
				geneList.add(seq.getName());

				String desc = seq.getDesc();
				String [] toks = desc.split(";");
				for (String tok:toks){
					if (tok.startsWith("dg=")){
						gene2Group.put(seq.getName(), tok.substring(3));
					}
					if (tok.startsWith("geneID=")){
						String proteinID = tok.substring(7);
						int iEnd = proteinID.indexOf('_');
						if (iEnd > 0)
							proteinID = proteinID.substring(0, iEnd);
						gene2ProteinID.put(seq.getName(), proteinID);
					}
				}				
			}

			Logging.info("geneList = " + drGeneList.size());
			Logging.info("geneMap = " + geneMap.size());
			Logging.info("gene2Group = " + gene2Group.size());		
		}

		/* (non-Javadoc)
		 * @see japsa.bio.np.RealtimeAnalysis#close()
		 */
		@Override
		protected void close() {
			try {
				sos.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		/* (non-Javadoc)
		 * @see japsa.bio.np.RealtimeAnalysis#analysis()
		 */
		@Override
		protected void analysis() {
			antiBioticAnalysis();

		}

		/* (non-Javadoc)
		 * @see japsa.bio.np.RealtimeAnalysis#getCurrentRead()
		 */
		@Override
		protected int getCurrentRead() {
			return resistGene.currentReadCount;

		}
	}
}