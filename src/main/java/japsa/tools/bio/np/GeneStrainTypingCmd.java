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

/*****************************************************************************
 *                           Revision History                                
 * 7 Aug 2015 - Minh Duc Cao: Created                                        
 * 
 ****************************************************************************/
package japsa.tools.bio.np;

import java.io.BufferedReader;
import java.io.IOException;

import japsa.bio.np.GeneStrainTyping;
import japsa.seq.SequenceOutputStream;
import japsa.seq.SequenceReader;
import japsa.util.CommandLine;
import japsa.util.IntArray;
import japsa.util.Logging;
import japsa.util.deploy.Deployable;

/**
 * @author minhduc
 *
 */

@Deployable(
	scriptName = "jsa.np.geneStrainTyping", 
	scriptDesc = "Strain typing using present/absence of gene")
public class GeneStrainTypingCmd extends CommandLine{	
	public GeneStrainTypingCmd(){
		super();
		Deployable annotation = getClass().getAnnotation(Deployable.class);		
		setUsage(annotation.scriptName() + " [options]");
		setDesc(annotation.scriptDesc());
		
		addString("output", "output.dat",  "Output file");
		addString("profile", null,  "Output file containing gene profile of all strains");
		addString("bamFile", null,  "The bam file");
		addString("geneFile", null,  "The gene file");

		addInt("top", 10,  "The number of top strains");
		addInt("scoreThreshold", 0,  "The alignment score threshold");
		addString("tmp", "tmp/t",  "Temporary folder");
		addString("hours", null,  "The file containging hours against yields, if set will output acording to tiime");

		addInt("timestamp", 0,  "Timestamp to check, if <=0 then use read number instead");
		addInt("read", 500,  "Number of reads before a typing, NA if timestamp is set");

		addBoolean("twodonly", false,  "Use only two dimentional reads");
		addInt("sim", 0,  "Scale for simulation");
		addBoolean("GUI", false,  "Run on GUI");
	
		
		addStdHelp();		
	} 

	/**
	 * @param args
	 */
	public static void main(String[] args) throws IOException, InterruptedException{
		CommandLine cmdLine = new GeneStrainTypingCmd();		
		args = cmdLine.stdParseLine(args);
		
		/**********************************************************************/

		String output = cmdLine.getStringVal("output");
		String profile = cmdLine.getStringVal("profile");
		String bamFile = cmdLine.getStringVal("bam");
		String geneFile = cmdLine.getStringVal("geneFile");		
		String tmp = cmdLine.getStringVal("tmp");
		String hours = cmdLine.getStringVal("hours");
		int top = cmdLine.getIntVal("top");		
		int read = cmdLine.getIntVal("read");
		boolean GUI = cmdLine.getBooleanVal("GUI");
		int timestamp = cmdLine.getIntVal("timestamp");

		{
			GeneStrainTyping paTyping = new GeneStrainTyping(GUI);	
			paTyping.simulation = cmdLine.getIntVal("sim");
			paTyping.prefix = tmp;
			paTyping.readNumber = read;
			if (hours !=null){
				BufferedReader bf = SequenceReader.openFile(hours);
				String line = bf.readLine();//first line
				paTyping.hoursArray = new IntArray();
				paTyping.readCountArray = new IntArray();

				while ((line = bf.readLine())!= null){
					String [] tokens = line.split("\\s");
					int hrs = Integer.parseInt(tokens[0]);
					int readCount = Integer.parseInt(tokens[2]);

					paTyping.hoursArray.add(hrs);
					paTyping.readCountArray.add(readCount);	
				}
			}


			if (paTyping.readNumber < 1)
				paTyping.readNumber = 1;

			paTyping.datOS = SequenceOutputStream.makeOutputStream(output);
			paTyping.datOS.print("step\treads\tbases\tstrain\tprob\tlow\thigh\tgenes\n");
			paTyping.readGenes(geneFile);
			paTyping.readKnowProfiles(profile);
			Logging.info("Read in " + paTyping.profileList.size() + " gene profiles");

			paTyping.timestamp = timestamp;

			if (GUI)
				paTyping.startGUI();

			paTyping.typing(bamFile,  top);
			paTyping.datOS.close();
		}
	}


}