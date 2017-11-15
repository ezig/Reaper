package sql.lang.ast.table;

import sql.lang.AbstractSetClause;
import sql.lang.Table;
import sql.lang.TableRow;
import sql.lang.ast.filter.Filter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class UpdateNode {
    private Table update;
    private List<Filter> candidateFilters;
    private AbstractSetClause setClause;

    public UpdateNode(Table update, List<Filter> candidateFilters, AbstractSetClause setClause) {
        this.update = update;
        this.candidateFilters = candidateFilters;
        this.setClause = setClause;
    }

    public void print() {
        String out = "[No." + 1 + "]===============================\n";
        out += "UPDATE " + update.getName() + "\n";
        out += "SET " + setClause.concretize() + "\n";

        String whereFilter = candidateFilters.get(0).prettyPrint(0);
        if (whereFilter.length() > 0) {
            out += "WHERE " + candidateFilters.get(0).prettyPrint(0) + "\n";
        }

        out = out.replaceAll("\\[[^\\[\\]]*\\]", "");

        System.out.println(out);
    }

    // checks whether any combination of the candidate filters + set clauses produces desired result
    // (sanity check for output produced by transformed example)
    public Boolean evalMatches(Table in, List<Integer> modifiedIndices, Table inUpdatedOnly, Table outUpdatedOnly) {
        List<String> schema = in.getSchema();
        Integer numCols = schema.size();

        for (Filter filter: candidateFilters) {
            List<Integer> indices = new ArrayList<>(filter.filter(in));
            if (indices.equals(modifiedIndices) && setClause.evalMatches(inUpdatedOnly, outUpdatedOnly)) {
                return true;
            }
        }

        return false;
    }

}
