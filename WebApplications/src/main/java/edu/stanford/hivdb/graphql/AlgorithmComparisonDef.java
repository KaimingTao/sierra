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

package edu.stanford.hivdb.graphql;

import graphql.schema.*;
import static graphql.Scalars.*;
import static graphql.schema.GraphQLArgument.newArgument;
import static graphql.schema.GraphQLObjectType.newObject;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static graphql.schema.GraphQLInputObjectType.newInputObject;
import static graphql.schema.GraphQLInputObjectField.newInputObjectField;

import edu.stanford.hivdb.drugs.DrugResistanceAlgorithm;
import edu.stanford.hivdb.hivfacts.HIV;
import edu.stanford.hivdb.mutations.MutationSet;
import edu.stanford.hivdb.drugresistance.algorithm.AlgorithmComparison;

import static edu.stanford.hivdb.graphql.DrugDef.oDrug;
import static edu.stanford.hivdb.graphql.DrugClassDef.oDrugClass;
import static edu.stanford.hivdb.graphql.DrugResistanceDef.oSIR;
import static edu.stanford.hivdb.graphql.DrugResistanceAlgorithmDef.oASIAlgorithm;

public class AlgorithmComparisonDef {

	protected static List<Map<String, Object>> fetchAlgorithmComparisonData(
			MutationSet<HIV> allMuts,
			Collection<String> algorithmNames,
			Map<String, String> customAlgorithms) {
		HIV hiv = HIV.getInstance();
		Collection<DrugResistanceAlgorithm<HIV>> algorithms = hiv.getDrugResistAlgorithms(algorithmNames); 
		customAlgorithms.entrySet().stream().forEach(e -> {
			algorithms.add(new DrugResistanceAlgorithm<>(
				/* name =        */ e.getKey(),
				/* strain =      */ hiv.getStrain("HIV1"),
				/* xmlText =     */ e.getValue()));
		});
		AlgorithmComparison<HIV> algCmp = new AlgorithmComparison<>(allMuts, algorithms);
		return algCmp.getComparisonResults()
			.stream()
			.collect(Collectors.groupingBy(cds -> cds.getDrug().getDrugClass()))
			.entrySet()
			.stream()
			.map(e -> {
				Map<String, Object> r = new HashMap<>();
				r.put("drugClass", e.getKey());
				r.put("drugScores", e.getValue());
				return r;
			})
			.collect(Collectors.toList());
	}

	public static GraphQLInputObjectType
		iASICustomAlgorithm = newInputObject()
		.name("CustomASIAlgorithm")
		.field(newInputObjectField()
			.name("name")
			.type(GraphQLString)
			.description("Algorithm name.")
			.build())
		.field(newInputObjectField()
			.name("xml")
			.type(GraphQLString)
			.description("ASI XML data.")
			.build())
		.build();

	public static GraphQLArgument aASIAlgorithmArgument = newArgument()
		.name("algorithms")
		.description("One or more of the built-in ASI algorithms.")
		.type(new GraphQLList(oASIAlgorithm))
		.build();

	public static GraphQLArgument aASICustomAlgorithmArgument = newArgument()
		.name("customAlgorithms")
		.description("One or more of custom ASI algorithms.")
		.type(new GraphQLList(iASICustomAlgorithm))
		.build();

	public static GraphQLObjectType oComparableDrugScore = newObject()
		.name("ComparableDrugScore")
		.field(field -> field
			.name("drug")
			.type(oDrug)
			.description("Drug of this score."))
		.field(field -> field
			.name("algorithm")
			.type(GraphQLString)
			.description("The name of algorithm which calculated this score."))
		.field(field -> field
			// TODO: verify if field -> field works here
			.name("SIR")
			.type(oSIR)
			.description(
				"One of the three step resistance levels of the drug."))
		.field(field -> field
			.type(GraphQLString)
			.name("interpretation")
			.description(
				"Readable resistance level defined by the algorithm for the drug."))
		.field(field -> field
			.type(GraphQLString)
			.name("explanation")
			.description(
				"Text explanation on how this level get calculated."))
		.build();

	public static GraphQLObjectType oAlgorithmComparison = newObject()
		.name("AlgorithmComparison")
		.field(field -> field
			.name("drugClass")
			.type(oDrugClass))
		.field(field -> field
			.name("drugScores")
			.type(new GraphQLList(oComparableDrugScore)))
		.build();
}
