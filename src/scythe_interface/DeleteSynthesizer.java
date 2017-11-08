package scythe_interface;

import forward_enumeration.table_enumerator.AbstractTableEnumerator;
import global.GlobalConfig;
import sql.lang.Table;
import sql.lang.TableRow;
import sql.lang.ast.filter.Filter;

import java.util.ArrayList;
import java.util.List;


public class DeleteSynthesizer extends ModifySynthesizer {
    public void Synthesize(String exampleFilePath, AbstractTableEnumerator enumerator) {
        ExampleDS exampleDS = ExampleDS.readFromFile(exampleFilePath);
        if (GlobalConfig.PRINT_LOG) {
            System.out.println("\tFile: " + exampleFilePath);
            System.out.println("\tEnumerator: " + enumerator.getClass().getSimpleName());
        }

        if (!isValidInput(exampleDS)) {
            throw new IllegalStateException("Example file contained illegal update input");
        }

        Table orig = exampleDS.tModify;
        Table modified = exampleDS.output;

        List<Integer> deletedIndices = getModifiedIndices(modified, orig);

        List<TableRow> deletedRows = getRowsAtIndices(orig, deletedIndices);

        Table deletedOnly = new Table();
        deletedOnly.initialize(orig.getName(), orig.getSchema(), deletedRows);

        List<Filter> candidateFilters = generateCandidateFilters(exampleFilePath, enumerator, exampleDS, deletedOnly);

        printDelete(orig, candidateFilters);
    }

    private static void printDelete(Table delete, List<Filter> candidateFilters) {
        System.out.println("DELETE " + delete.getName());

        String whereFilter = candidateFilters.get(0).prettyPrint(0);
        if (whereFilter.length() > 0) {
            System.out.println("WHERE " + candidateFilters.get(0).prettyPrint(0));
        }
    }

    protected List<Integer> getModifiedIndices(Table modified, Table orig) {
        List<Integer> modifiedIndices = new ArrayList<>();

        int i = 0, j = 0;
        int origRowCount = orig.getContent().size();
        int modifiedRowCount = modified.getContent().size();

        while (i < origRowCount && j < modifiedRowCount) {
            if (!orig.getContent().get(i).equals(modified.getContent().get(j))) {
                modifiedIndices.add(i);
            } else {
                j++;
            }
            i++;
        }

        while (i  < origRowCount) {
            modifiedIndices.add(i);
            i++;
        }

        return modifiedIndices;
    }

    protected boolean isValidInput(ExampleDS exampleDS) {
        Table orig = exampleDS.tModify;
        Table modified = exampleDS.output;

        if (orig.getSchema().size() != modified.getSchema().size()) {
            return false;
        }

        // table after query must contain a subset of the rows of table before query
        if (!orig.getContent().containsAll(orig.getContent())) {
            return false;
        }

        return true;
    }
}