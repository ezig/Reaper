package scythe_interface;

import forward_enumeration.table_enumerator.AbstractTableEnumerator;
import global.GlobalConfig;
import sql.lang.AbstractSetClause;
import sql.lang.Table;
import sql.lang.TableRow;
import sql.lang.ast.filter.Filter;

import java.util.*;

public class UpdateSynthesizer extends ModifySynthesizer {

    public void Synthesize(String exampleFilePath, AbstractTableEnumerator enumerator) {
        // read file
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

        // Can safely assume at this point that update table and output have same number of rows
        List<Integer> updatedIndices = getModifiedIndices(modified, orig);

        List<TableRow> updatedRows = getRowsAtIndices(orig, updatedIndices);
        List<TableRow> updatedOutputs = getRowsAtIndices(modified, updatedIndices);

        Optional<ExampleDS> permutedExample = ExampleTransformer.transform(exampleDS);

        Table updatedOnly = new Table();
        updatedOnly.initialize(orig.getName(), orig.getSchema(), updatedRows);
        Table updatedOutputOnly = new Table();
        updatedOutputOnly.initialize(orig.getName(), orig.getSchema(), updatedOutputs);

        List<Filter> candidateFilters = generateCandidateFilters(exampleFilePath, enumerator, exampleDS, updatedOnly);

        AbstractSetClause setClause = AbstractSetClause.enumerateFromIO(
                updatedOnly,
                updatedOutputOnly,
                exampleDS,
                exampleFilePath,
                enumerator,
                (ds -> Synthesizer.SynthesizeWAggr(exampleFilePath, enumerator, -1, ds)));

        printUpdate(orig, candidateFilters, setClause);
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

    protected List<Integer> getModifiedIndices(Table modified, Table orig) {
        List<Integer> modifiedIndices = new ArrayList<>();

        for (int i = 0; i < orig.getContent().size(); i++) {
            if (!modified.getContent().get(i).equals(orig.getContent().get(i))) {
                modifiedIndices.add(i);
            }
        }
        return modifiedIndices;
    }

    protected boolean isValidInput(ExampleDS exampleDS) {
        Table output = exampleDS.output;
        Table update = exampleDS.tModify;

        if (output.getContent().size() != update.getContent().size()) {
            return false;
        }

        if (output.getSchema().size() != update.getSchema().size()) {
            return false;
        }

        return true;
    }
}
