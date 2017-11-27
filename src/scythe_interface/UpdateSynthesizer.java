package scythe_interface;

import forward_enumeration.table_enumerator.AbstractTableEnumerator;
import global.GlobalConfig;
import sql.lang.AbstractSetClause;
import sql.lang.Table;
import sql.lang.TableRow;
import sql.lang.ast.filter.Filter;
import sql.lang.ast.table.UpdateNode;

import java.util.*;

public class UpdateSynthesizer extends ModifySynthesizer {

    private UpdateNode Synthesize(String exampleFilePath,
                                  AbstractTableEnumerator enumerator,
                                  ExampleDS exampleDS) {
        if (!isValidInput(exampleDS)) {
            throw new IllegalStateException("Example file contained illegal update input");
        }

        Table orig = exampleDS.tModify;
        Table modified = exampleDS.output;

        // Can safely assume at this point that update table and output have same number of rows
        List<Integer> updatedIndices = getModifiedIndices(modified, orig);

        List<TableRow> updatedRows = getRowsAtIndices(orig, updatedIndices);
        List<TableRow> updatedOutputs = getRowsAtIndices(modified, updatedIndices);

        Table updatedOnly = new Table();
        updatedOnly.initialize(orig.getName(), orig.getSchema(), updatedRows);
        Table updatedOutputOnly = new Table();
        updatedOutputOnly.initialize(orig.getName(), orig.getSchema(), updatedOutputs);

        List<Filter> candidateFilters = generateCandidateFilters(exampleFilePath, enumerator, exampleDS, updatedOnly);

        AbstractSetClause setClause = AbstractSetClause.enumerateFromIO(
                updatedOnly,
                updatedOutputOnly,
                new ExampleDS(exampleDS),
                exampleFilePath,
                enumerator,
                (ds -> Synthesizer.SynthesizeWAggr(exampleFilePath, enumerator, -1, ds)));

        UpdateNode updateNode = new UpdateNode(orig, candidateFilters, setClause);

        return updateNode;
    }

    private Boolean checkSynthesisResult(UpdateNode updateNode, ExampleDS exampleDS) {
        Table orig = exampleDS.tModify;
        Table modified = exampleDS.output;

        List<Integer> updatedIndices = getModifiedIndices(modified, orig);

        List<TableRow> updatedRows = getRowsAtIndices(orig, updatedIndices);
        List<TableRow> updatedOutputs = getRowsAtIndices(modified, updatedIndices);

        Table updatedOnly = new Table();
        updatedOnly.initialize(orig.getName(), orig.getSchema(), updatedRows);
        Table updatedOutputOnly = new Table();
        updatedOutputOnly.initialize(orig.getName(), orig.getSchema(), updatedOutputs);

        return updateNode.evalMatches(orig, updatedIndices, updatedOnly, updatedOutputOnly);
    }

    public void Synthesize(String exampleFilePath, AbstractTableEnumerator enumerator) {
        // read file
        ExampleDS exampleDS = ExampleDS.readFromFile(exampleFilePath);
        ExampleTransformer transformer = new ExampleTransformer(exampleDS);
        Optional<ExampleDS> transformedExampleDSOptional = transformer.transform();

        if (transformedExampleDSOptional.isPresent()) {
            ExampleDS transformedExampleDS = transformedExampleDSOptional.get();
            UpdateNode updateNode = Synthesize(exampleFilePath, enumerator, transformedExampleDS);
            if (checkSynthesisResult(updateNode, exampleDS)) {
                printUpdate(updateNode);
            }
        } else {
            UpdateNode updateNode = Synthesize(exampleFilePath, enumerator, exampleDS);
            printUpdate(updateNode);
        }
    }

    private static void printUpdate(UpdateNode updateNode) {
        updateNode.print();
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
