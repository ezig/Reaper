package scythe_interface;

import forward_enumeration.table_enumerator.AbstractTableEnumerator;
import global.GlobalConfig;
import sql.lang.AbstractSetClause;
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

public class UpdateSynthesizer {
    public static long TimeOut = 600000;

    public static void SynthesizeUpdate(String exampleFilePath, AbstractTableEnumerator enumerator) {
        // read file
        ExampleDS exampleDS = ExampleDS.readFromFile(exampleFilePath);
        if (GlobalConfig.PRINT_LOG) {
            System.out.println("[[Update synthesis start]]");
            System.out.println("\tFile: " + exampleFilePath);
            System.out.println("\tEnumerator: " + enumerator.getClass().getSimpleName());
        }

        if (!validateUpdateInput(exampleDS)) {
            throw new IllegalStateException("Example file contained illegal update input");
        }

        Table output = exampleDS.output;
        Table update = exampleDS.tUpdate;

        // Can safely assume at this point that update table and output have same number of rows
        List<Integer> updatedIdxs = getUpdatedIndices(output, update);

        List<TableRow> updatedRows = getRowsAtIndices(update, updatedIdxs);
        List<TableRow> updatedOutputs = getRowsAtIndices(output, updatedIdxs);

        Table updatedOnly = new Table();
        updatedOnly.initialize(update.getName(), update.getSchema(), updatedRows);
        Table updatedOutputOnly = new Table();
        updatedOutputOnly.initialize(output.getName(), output.getSchema(), updatedOutputs);

        List<Filter> candidateFilters = generateCandidateFilters(exampleFilePath, enumerator, exampleDS, updatedOnly);

        AbstractSetClause setClause = AbstractSetClause.enumerateFromIO(
                updatedOnly,
                updatedOutputOnly,
                exampleDS,
                exampleFilePath,
                enumerator,
                (ds -> Synthesizer.SynthesizeWAggr(exampleFilePath, enumerator, -1, ds)));

        printUpdate(update, candidateFilters, setClause);
    }

    private static void printUpdate(Table update, List<Filter> candidateFilters, AbstractSetClause setClause) {
        System.out.println("[No." + 1 + "]===============================");
        System.out.println("UPDATE " + update.getName());
        System.out.println("SET " + setClause.concretize());

        String whereFilter = candidateFilters.get(0).prettyPrint(0);
        if (whereFilter.length() > 0) {
            System.out.println("WHERE " + candidateFilters.get(0).prettyPrint(0));
        }
    }

    private static List<Filter> generateCandidateFilters(String exampleFilePath,
                                                       AbstractTableEnumerator enumerator,
                                                       ExampleDS exampleDS,
                                                       Table updatedOnly) {
        List<Filter> filters = new ArrayList<>();

        if (updatedOnly.getContent().size() != exampleDS.tUpdate.getContent().size()) {
            exampleDS.output = updatedOnly;
            List<TableNode> candidates =
                    Synthesizer.SynthesizeWAggr(exampleFilePath, enumerator, 0, exampleDS);

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

    private static List<TableRow> getRowsAtIndices(Table t, List<Integer> idxs) {
        return IntStream.range(0, t.getContent().size())
                    .filter(idxs::contains)
                    .mapToObj(t.getContent()::get)
                    .collect(Collectors.toList());
    }

    private static List<Integer> getUpdatedIndices(Table output, Table update) {
        List<Integer> updatedIdxs = new ArrayList<>();

        for (int i = 0; i < update.getContent().size(); i++) {
            if (!output.getContent().get(i).equals(update.getContent().get(i))) {
                updatedIdxs.add(i);
            }
        }
        return updatedIdxs;
    }

    private static boolean validateUpdateInput(ExampleDS exampleDS) {
        Table output = exampleDS.output;
        Table update = exampleDS.tUpdate;

        if (output.getContent().size() != update.getContent().size()) {
            return false;
        }

        if (output.getSchema().size() != update.getSchema().size()) {
            return false;
        }

        return true;
    }
}
