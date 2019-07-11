/*

    Copyright (C) 2017 Stanford HIVDB team

    Sierra is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Sierra is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package edu.stanford.hivdb.alignment;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import edu.stanford.hivdb.mutations.Gene;
import edu.stanford.hivdb.mutations.Mutation;
import edu.stanford.hivdb.mutations.MutationSet;
import edu.stanford.hivdb.mutations.Tsms;
import edu.stanford.hivdb.mutations.WithGene;
import edu.stanford.hivdb.mutations.Sdrms;
import edu.stanford.hivdb.mutations.MutType;
import edu.stanford.hivdb.mutations.CodonTranslation;
import edu.stanford.hivdb.mutations.FrameShift;
import edu.stanford.hivdb.utilities.PrettyPairwise;
import edu.stanford.hivdb.utilities.Sequence;
import edu.stanford.hivdb.drugs.DrugClass;


/**
 * Result object of data from {@link edu.stanford.hivdb.alignment.NucAminoAligner}.
 *
 */
public class AlignedGeneSeq implements WithGene {

	// Variables assigned by CLapAlign
	private final Gene gene;
	private final Sequence sequence;
	private final int firstAA;
	private final int lastAA;
	private final int firstNA;
	private final int lastNA;
	private final int leftTrimmed;
	private final int rightTrimmed;
	private float matchPcnt;
	private transient List<AlignedSite> alignedSites;
	protected transient boolean codonAlignerTouched = false;
	protected transient boolean codon69Touched = false;
	private transient PrettyPairwise prettyPairwise;

	// Variables assigned by NucAminoAligner and changed by the methods in this class
	private String alignedNAs;

	// This information is generated by processSequence.
	private MutationSet mutations = new MutationSet();
	private List<FrameShift> frameShifts = new ArrayList<>();
	private transient MutationSet unusualMutations;
	private transient MutationSet sdrms;
	private transient Map<DrugClass, MutationSet> nonDrmTsms;

	// This may help for debugging and may be preferable to serialize than mutations
	@SuppressWarnings("unused")
	private String mutationListString;

	private transient Map<MutType, MutationSet> mutationsGroupingByMutType;


	/**
	 *
	 * @param sequence
	 * @param gene
	 * @param firstAA
	 * @param lastAA
	 * @param firstNA
	 * @param lastNA
	 * @param alignedSites
	 * @param mutations
	 * @param frameShifts
	 * @param leftTrimmed
	 * @param rightTrimmed
	 */
	public AlignedGeneSeq(
			Sequence sequence, Gene gene,
			final int firstAA, final int lastAA,
			final int firstNA, final int lastNA,
			List<AlignedSite> alignedSites,
			Collection<Mutation> mutations,
			List<FrameShift> frameShifts,
			final int leftTrimmed,
			final int rightTrimmed) {
		this.sequence = sequence;
		this.gene = gene;
		this.firstAA = firstAA;
		this.lastAA = lastAA;
		this.firstNA = firstNA;
		this.lastNA = lastNA;
		this.matchPcnt = -1;
		this.leftTrimmed = leftTrimmed;
		this.rightTrimmed = rightTrimmed;

		alignedSites = alignedSites.stream()
			.filter(m -> {
				int posAA = m.getPosAA();
				return posAA >= firstAA && posAA <= lastAA;
			})
			.collect(Collectors.toList());
		mutations = mutations.stream()
			.filter(m -> {
				int posAA = m.getPosition();
				return posAA >= firstAA && posAA <= lastAA;
			})
			.collect(Collectors.toList());
		frameShifts = frameShifts.stream()
			.filter(fs -> {
				int posAA = fs.getPosition();
				return posAA >= firstAA && posAA <= lastAA;
			})
			.collect(Collectors.toList());


		this.alignedSites = Collections.unmodifiableList(alignedSites);
		this.mutations = new MutationSet(mutations);
		this.frameShifts = Collections.unmodifiableList(frameShifts);
		mutationListString = getMutationListString();
	}

	@Override
	public Gene getGene() { return gene; }

	public Sequence getSequence() {	return sequence; }
	public List<AlignedSite> getAlignedSites() { return alignedSites; }

	/**
	 * Retrieve amino acids size of alignment.
	 * @return integer
	 */
	public int getSize() {
		return StringUtils.replace(getAlignedNAs(), "N", "").length() / 3;
	}

	public int[] getShrinkage() {
		return new int [] { leftTrimmed, rightTrimmed };
	}

	public String getAlignedNAs() {
		if (this.alignedNAs == null) {
			StringBuilder alignedNAs = new StringBuilder();
			String naSeq = sequence.getSequence();

			for (AlignedSite site : alignedSites) {
				int posNA = site.getPosNA();
				int lengthNA = site.getLengthNA();
				StringBuilder codon = new StringBuilder();
				if (lengthNA > 0) {
					codon.append(
						naSeq.substring(posNA - 1, posNA - 1 + Math.min(lengthNA, 3))
					);
				}
				if (lengthNA < 3) {
					codon.append(StringUtils.repeat('-', 3 - lengthNA));
				}
				alignedNAs.append(codon);
			}
			this.alignedNAs = alignedNAs.toString();
		}
		return this.alignedNAs;
	} // Need

	public String getAlignedAAs() {
		return CodonTranslation.simpleTranslate(
			this.getAlignedNAs(), firstAA, gene.getReference());
	}
	
	public String getAdjustedAlignedNAs() {
		return gene.adjustNAAlignment(getAlignedNAs(), firstAA, lastAA);
	}

	public String getAdjustedAlignedAAs() {
		return gene.adjustAAAlignment(getAlignedAAs(), firstAA, lastAA);
	}

	public int getFirstNA() { return firstNA; }
	public int getLastNA() { return lastNA; }
	public int getFirstAA() { return firstAA;}  // Need
	public int getLastAA() { return lastAA; } // Need
	
	protected int getNumDiscordantNAs() {
		int numDiscordantNAs = 0;
		for (Mutation mut : mutations) {
			if (mut.getTriplet().equals("NNN")) {
				// NNN doesn't count
				continue;
			}
			if (mut.isDeletion()) {
				numDiscordantNAs += 3;
			} else {
				numDiscordantNAs += 3; //CodonTranslation.getMinimalNAChanges(mut.getTriplet(), mut.getConsensus());
			}
		}
		for (FrameShift fs: frameShifts) {
			if (fs.isInsertion()) {
				numDiscordantNAs += fs.getSize();
			}
		}
		return numDiscordantNAs;
		
	}

	public float getMatchPcnt() {
		if (matchPcnt == -1) {
			int numNAs = lastNA - firstNA + 1;
			for (Mutation mut : mutations) {
				if (mut.getTriplet().equals("NNN")) {
					// NNN doesn't count
					numNAs -= 3;
					continue;
				}
				if (mut.isDeletion()) {
					numNAs += 3;
				}
			}
			for (FrameShift fs: frameShifts) {
				if (fs.isDeletion()) {
					numNAs += fs.getSize();
				}
			}
			matchPcnt = 100 - 100 * (float) getNumDiscordantNAs() / (float) numNAs;
		}
		return matchPcnt;
	}

	public MutationSet getMutations() { return mutations; }  // Need
	public List<FrameShift> getFrameShifts() { return frameShifts; } // Need

	public PrettyPairwise getPrettyPairwise() {
		if (prettyPairwise == null) {
			prettyPairwise = new PrettyPairwise(
				gene, getAlignedNAs(), firstAA,
				getMutations(), Collections.emptyList());
		}
		return prettyPairwise;
	}

	public String getMutationListString() { return mutations.join(); } // Need

	public MutationSet getInsertions() { return mutations.getInsertions(); }
	public MutationSet getDeletions() { return mutations.getDeletions(); }
	public MutationSet getStopCodons() { return mutations.getStopCodons(); }
	public MutationSet getHighlyAmbiguousCodons() {
		return mutations.getAmbiguousCodons();
	}

	public Map<MutType, MutationSet> groupMutationsByMutType() {
		if (mutationsGroupingByMutType == null) {
			mutationsGroupingByMutType = Collections.unmodifiableMap(
				mutations.groupByMutType(gene));
		}
		return mutationsGroupingByMutType;
	}

	public MutationSet getMutationsByMutType(MutType mutType) {
		return groupMutationsByMutType().getOrDefault(mutType, new MutationSet());
	}

	public MutationSet getUnusualMutations() {
		if (unusualMutations == null) {
			unusualMutations = mutations.getUnusualMutations();
		}
		return unusualMutations;
	}

	public MutationSet getUnusualMutationsAtDrugResistancePositions() {
		return getUnusualMutations().getAtDRPMutations();
	}

	public MutationSet getSdrms() {
		if (sdrms == null) {
			sdrms = Sdrms.getSdrms(getMutations());
		}
		return sdrms;
	}

	public Map<DrugClass, MutationSet> getNonDrmTsms() {
		if (nonDrmTsms == null) {
			nonDrmTsms = new EnumMap<>(DrugClass.class);
			for (DrugClass drugClass : gene.getDrugClasses()) {
				MutationSet tsms = Tsms.getTsmsForDrugClass(drugClass, mutations);
				MutationSet drms = mutations.getDRMs(drugClass);
				nonDrmTsms.put(drugClass, tsms.subtractsBy(drms));
			}
		}
		return nonDrmTsms;
	}

}
