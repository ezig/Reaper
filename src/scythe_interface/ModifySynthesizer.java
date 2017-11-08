package scythe_interface;

import forward_enumeration.table_enumerator.AbstractTableEnumerator;
import sql.lang.Table;
import sql.lang.TableRow;
import sql.lang.ast.filter.EmptyFilter;
import sql.lang.ast.filter.Filter;
import sql.lang.ast.table.SelectNode;
import sql.lang.ast.table.TableNode;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public abstract class ModifySynthesizer {
    public static long TimeOut = 600000;

    public abstract void Synthesize(String exampleFilePath, AbstractTableEnumerator enumerator);

    protected static List<Filter> generateCandidateFilters(String exampleFilePath,
                                                         AbstractTableEnumerator enumerator,
                                                         ExampleDS exampleDS,
                                                         Table modifiedOnly) {
        List<Filter> filters = new ArrayList<>();

        if (modifiedOnly.getContent().size() != exampleDS.tModify.getContent().size()) {
            exampleDS.output = modifiedOnly;
            List<TableNode> candidates = Synthesizer.SynthesizeWAggr(exampleFilePath, enumerator, -1, exampleDS);

            if (candidates.size() == 0) {
                throw new IllegalStateException("Unable to learn a classifier query for I/O examples");
            }

            for (TableNode t : candidates) {
                if (! (t instanceof SelectNode)) {
                    System.err.println("Something went wrong, synthesized invalid classifier " + t);
                    continue;
                }

                // Cast is safe based on check above
                SelectNode s = (SelectNode) t;
                filters.add(s.getFilter());
            }
        } else {
            filters.add(new EmptyFilter());
        }

        return filters;
    }

    protected static List<TableRow> getRowsAtIndices(Table t, List<Integer> idxs) {
        return IntStream.range(0, t.getContent().size())
                .filter(idxs::contains)
                .mapToObj(t.getContent()::get)
                .collect(Collectors.toList());
    }

    protected abstract List<Integer> getModifiedIndices(Table modified, Table orig);

    protected abstract boolean isValidInput(ExampleDS exampleDS);
}