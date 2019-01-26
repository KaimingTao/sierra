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

package edu.stanford.hivdb.mutations;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import edu.stanford.hivdb.utilities.JdbcDatabase;
import edu.stanford.hivdb.utilities.Cachable;

public class DRMs {
	@Cachable.CachableField
	private static MutationSet drms;
	private static final JdbcDatabase db;

	static {
		db = JdbcDatabase.getDefault();
		Cachable.setup(DRMs.class, () -> {
			try {
				populateDRMs();
			} catch (SQLException e) {
				throw new ExceptionInInitializerError(e);
			}
		});
	}

	/**
	 * Return the set of DRMs present in the list of submitted mutations
	 * @param seqMuts
	 * @return just those mutations that are DRMs
	 */
	public static MutationSet getDRMs(MutationSet seqMuts) {
		return seqMuts.intersectsWith(drms);
	}

	public static boolean isDRM(Mutation mut) {
		return drms.hasSharedAAMutation(mut, /* ignoreRefOrStops = */false);
	}

	public static boolean isAtDRPosition(Mutation mut) {
		GenePosition gp = mut.getGenePosition();
		return drms.get(gp) != null;

	}

	private static void populateDRMs() throws SQLException {
		final String sqlStatement1 =
			"SELECT Gene, Pos, AA " +
			"FROM tblScores " +
			"GROUP BY Gene, Pos, AA ORDER BY Gene, Pos, AA";
		final String sqlStatement2 =
			"SELECT Gene, Rule " +
			"FROM tblCombinationScores " +
			"Group BY Gene, Rule ORDER BY Gene, Rule";

		List<Collection<Mutation>> allDrms = new ArrayList<>();
		allDrms.add(db.iterate(sqlStatement1, rs -> {
			Gene gene = Gene.valueOf(Strain.HIV1, rs.getString("Gene"));
			int pos = rs.getInt("Pos");
			String aa = rs.getString("AA");
			return new AAMutation(gene, pos, aa.toCharArray(), 0xff);
		}));
		allDrms.addAll(db.iterate(sqlStatement2, rs -> {
			Gene gene = Gene.valueOf(Strain.HIV1, rs.getString("Gene"));
			String rule = rs.getString("Rule");
			rule = AA.toInternalFormat(rule);
			return new MutationSet(gene, rule).displayAmbiguities();
		}));
		drms = new MutationSet(allDrms
		.stream()
		.reduce((m1, m2) -> {
			m1.addAll(m2);
			return m1;
		})
		.get());
	}
}