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

package edu.stanford.hivdb.drugresistance;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import edu.stanford.hivdb.drugresistance.database.CommentType;
import edu.stanford.hivdb.drugresistance.database.ConditionalComments;
import edu.stanford.hivdb.drugresistance.database.ConditionalComments.BoundComment;
import edu.stanford.hivdb.drugs.Drug;
import edu.stanford.hivdb.drugs.DrugClass;
import edu.stanford.hivdb.mutations.Gene;
import edu.stanford.hivdb.mutations.MutType;
import edu.stanford.hivdb.mutations.Mutation;
import edu.stanford.hivdb.mutations.MutationSet;

public abstract class GeneDR {

	// Required data structures used to instantiate the class
	protected final Gene gene;
	protected final MutationSet mutations;

	// Optional data structures used to instantiate the class
	protected Map<MutType, MutationSet> mutTypes;
	protected Map<CommentType, List<BoundComment>> commentsByTypes;

	protected Map<DrugClass, Map<Drug, Map<Mutation, Double>>> drugClassDrugMutScores;
	protected Map<DrugClass, Map<Drug, Map<MutationSet, Double>>> drugClassDrugComboMutScores;

	// Data structures generated by methods to provide improved access to the data
	protected Map<DrugClass, Map<Mutation, Map<Drug, Double>>> drugClassMutDrugScores = new EnumMap<>(DrugClass.class);
	protected Map<DrugClass, Map<MutationSet, Map<Drug, Double>>> drugClassComboMutDrugScores = new EnumMap<>(DrugClass.class);
	protected Map<DrugClass, Map<Mutation, Map<Drug, Double>>> drugClassMutAllDrugScores = new EnumMap<>(DrugClass.class);
	private Map<DrugClass, Map<MutationSet, Map<Drug, Double>>> drugClassComboMutAllDrugScores = new EnumMap<>(DrugClass.class);

	protected GeneDR(Gene gene, MutationSet mutations) {
		this.gene = gene;
		this.mutations = mutations;
	}

	protected void postConstructor() {
		populateDrugClassMutDrugScores();
		populateDrugClassDrugComboMutDrugScores();
		populateDrugClassMutAllDrugScores();
		populateDrugClassComboMutAllDrugScores();
	}

	public Gene getGene() { return gene; }

	public abstract Map<Drug, Double> getDrugClassTotalDrugScores(DrugClass drugClass);
	public abstract Double getTotalDrugScore(Drug drug);
	public abstract Integer getDrugLevel(Drug drug);
	public abstract String getDrugLevelText(Drug drug);
	public abstract String getDrugLevelSIR(Drug drug);

	public MutationSet getMutations() { return mutations; }

	public Map<MutType, MutationSet> groupMutationsByTypes() {
		if (mutTypes == null) {
			mutTypes = mutations.groupByMutType(gene);
		}
		return mutTypes;
	}

	public MutationSet getMutationsByType(MutType mutType) {
		return groupMutationsByTypes().getOrDefault(mutType, new MutationSet());
	}

	public Map<CommentType, List<BoundComment>> groupCommentsByTypes() {
		if (commentsByTypes == null) {
			commentsByTypes = ConditionalComments.getComments(this)
				.stream()
				.collect(Collectors.groupingBy(
					cmt -> cmt.getType(),
					TreeMap::new,
					Collectors.toList()
				));
		}
		return commentsByTypes;
	}

	public List<BoundComment> getCommentsByType(CommentType commentType) {
		return groupCommentsByTypes().getOrDefault(commentType, Collections.emptyList());
	}

	public Map<Mutation, Map<Drug, Double>> getIndividualMutAllDrugScoresForDrugClass(DrugClass drugClass) {
		return drugClassMutAllDrugScores.get(drugClass);
	}

	public Map<MutationSet, Map<Drug, Double>> getComboMutAllDrugScoresForDrugClass(DrugClass drugClass) {
		return drugClassComboMutAllDrugScores.get(drugClass);
	}


	public boolean drugClassHasScoredMuts (DrugClass drugClass) {
		return drugClassHasScoredIndividualMuts(drugClass) || drugClassHasScoredComboMuts(drugClass);
	}

	public boolean drugClassHasScoredIndividualMuts (DrugClass drugClass) {
		return drugClassMutDrugScores.containsKey(drugClass);
	}

	public boolean drugClassHasScoredComboMuts (DrugClass drugClass) {
		return drugClassComboMutDrugScores.containsKey(drugClass);
	}

	public Map<Mutation, Double> getScoredIndividualMutsForDrug (Drug drug) {
		DrugClass drugClass = drug.getDrugClass();
		return drugClassDrugMutScores
			.getOrDefault(drugClass, Collections.emptyMap())
			.getOrDefault(drug, Collections.emptyMap());
	}

	public Map<MutationSet, Double> getScoredComboMutsForDrug (Drug drug) {
		DrugClass drugClass = drug.getDrugClass();
		return drugClassDrugComboMutScores
			.getOrDefault(drugClass, Collections.emptyMap())
			.getOrDefault(drug, Collections.emptyMap());
	}

	public boolean drugHasScoredMuts (Drug drug) {
		return drugHasScoredIndividualMuts(drug) || drugHasScoredComboMuts(drug);
	}

	public boolean drugHasScoredIndividualMuts (Drug drug) {
		DrugClass drugClass = drug.getDrugClass();
		if (!drugClassHasScoredMuts(drugClass)) {
			return false;
		}
		for (Mutation mut : drugClassMutAllDrugScores.get(drugClass).keySet()) {
			if (!(drugClassMutAllDrugScores.get(drugClass).get(mut).get(drug) == 0)) {
				return true;
			}
		}
		return false;
	}

	public boolean drugHasScoredComboMuts (Drug drug) {
		DrugClass drugClass = drug.getDrugClass();
		if (!drugClassHasScoredComboMuts(drugClass)) {
			return false;
		}
		for (MutationSet muts : drugClassComboMutAllDrugScores.get(drugClass).keySet()) {
			if (!(drugClassComboMutAllDrugScores.get(drugClass).get(muts).get(drug) == 0)) {
				return true;
			}
		}
		return false;
	}


	// Converts drugClassDrugMutScores: DrugClass => Drug => Mutation => score to
	// drugClassMutsDrugScores DrugClass => Mutation => Drug => score
	private void populateDrugClassMutDrugScores() {
		for (DrugClass drugClass : drugClassDrugMutScores.keySet()) {
			for (Drug drug : drugClassDrugMutScores.get(drugClass).keySet()) {
				for (Mutation mut : drugClassDrugMutScores.get(drugClass).get(drug).keySet()) {
					double score = drugClassDrugMutScores.get(drugClass).get(drug).get(mut);
					if (!drugClassMutDrugScores.containsKey(drugClass)) {
						drugClassMutDrugScores.put(drugClass, new HashMap<Mutation, Map<Drug, Double>>());
					}
					if (!drugClassMutDrugScores.get(drugClass).containsKey(mut)) {
						drugClassMutDrugScores.get(drugClass).put(mut, new HashMap<Drug, Double>());
					}
					drugClassMutDrugScores.get(drugClass).get(mut).put(drug, score);
				}
			}
		}
	}

	// Converts drugClassDrugComboMutScores: DrugClass => Drug => Mutation => score to
	// drugClassComboMutsDrugScores DrugClass => Mutation => Drug => score
	private void populateDrugClassDrugComboMutDrugScores() {
		for (DrugClass drugClass : drugClassDrugComboMutScores.keySet()) {
			for (Drug drug : drugClassDrugComboMutScores.get(drugClass).keySet()) {
				for (MutationSet comboMuts : drugClassDrugComboMutScores.get(drugClass).get(drug).keySet()) {
					double score = drugClassDrugComboMutScores.get(drugClass).get(drug).get(comboMuts);
					if (!drugClassComboMutDrugScores.containsKey(drugClass)) {
						drugClassComboMutDrugScores.put(drugClass, new HashMap<>());
					}
					if (!drugClassComboMutDrugScores.get(drugClass).containsKey(comboMuts)) {
						drugClassComboMutDrugScores.get(drugClass).put(comboMuts, new EnumMap<>(Drug.class));
					}
					drugClassComboMutDrugScores.get(drugClass).get(comboMuts).put(drug, score);
				}
			}
		}
	}

	// Uses drugClassMutDrugScores: DrugClass => Mutation => Drug => score
	// Creates drugClassMutsAllDrugScores: DrugClass => Mutation => Drug => score
	// All drugs are assigned a score for any mutation of that drug's drugClass
	private void populateDrugClassMutAllDrugScores() {
		for (DrugClass drugClass : drugClassMutDrugScores.keySet()) {
			for (Mutation mut : drugClassMutDrugScores.get(drugClass).keySet()) {
				if(!drugClassMutAllDrugScores.containsKey(drugClass)) {
					drugClassMutAllDrugScores.put(drugClass, new HashMap<Mutation, Map<Drug, Double>>());
				}
				if (!drugClassMutAllDrugScores.get(drugClass).containsKey(mut)) {
					drugClassMutAllDrugScores.get(drugClass).put(mut, new HashMap<Drug, Double>());
				}

				for (Drug drug : drugClass.getDrugsForHivdbTesting()) {
					if (drugClassMutDrugScores.get(drugClass).get(mut).containsKey(drug)) {
						double score = drugClassMutDrugScores.get(drugClass).get(mut).get(drug);
						drugClassMutAllDrugScores.get(drugClass).get(mut).put(drug, score);
					} else {
						drugClassMutAllDrugScores.get(drugClass).get(mut).put(drug, 0.0);
					}
				}
			}
		}
	}

	// Uses drugClassComboMutDrugScores: DrugClass => List<Mutation> => Drug => score
	// Creates drugClassComboMutAllDrugScores: DrugClass => List<Mutation> => Drug => score
	// All drugs are assigned a score for any mutation of that drug's drugClass
	private void populateDrugClassComboMutAllDrugScores() {
		for (DrugClass drugClass : drugClassComboMutDrugScores.keySet()) {
			for (MutationSet comboMut : drugClassComboMutDrugScores.get(drugClass).keySet()) {
				if(!drugClassComboMutAllDrugScores.containsKey(drugClass)) {
					drugClassComboMutAllDrugScores.put(drugClass, new HashMap<>());
				}
				if (!drugClassComboMutAllDrugScores.get(drugClass).containsKey(comboMut)) {
					drugClassComboMutAllDrugScores.get(drugClass).put(comboMut, new EnumMap<>(Drug.class));
				}

				for (Drug drug : drugClass.getDrugsForHivdbTesting()) {
					double score;
					if (drugClassComboMutDrugScores.get(drugClass).get(comboMut).containsKey(drug)) {
						score = drugClassComboMutDrugScores.get(drugClass).get(comboMut).get(drug);
					} else {
						score = 0.0;
					}
					drugClassComboMutAllDrugScores.get(drugClass).get(comboMut).put(drug, score);
				}
			}
		}
	}
}