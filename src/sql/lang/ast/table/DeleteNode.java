package sql.lang.ast.table;

import sql.lang.Table;
import sql.lang.ast.filter.Filter;

import java.util.ArrayList;
import java.util.List;

public class DeleteNode {
    private Table delete;
    private List<Filter> candidateFilters;

    public DeleteNode(Table delete, List<Filter> candidateFilters) {
        this.delete = delete;
        this.candidateFilters = candidateFilters;
    }

    public void print() {
        String out = "DELETE " + delete.getName() + "\n";

        String whereFilter = candidateFilters.get(0).prettyPrint(0);
        if (whereFilter.length() > 0) {
            out += "WHERE " + candidateFilters.get(0).prettyPrint(0) + "\n";
        }

        out = out.replaceAll("\\[[^\\[\\]]*\\]", "");

        System.out.println(out);
    }

    public Boolean evalMatches(Table in, List<Integer> modifiedIndices) {
        List<String> schema = in.getSchema();
        Integer numCols = schema.size();

        for (Filter filter: candidateFilters) {
            List<Integer> indices = new ArrayList<>(filter.filter(in));
            if (indices.equals(modifiedIndices)) {
                return true;
            }
        }

        return false;
    }
}
