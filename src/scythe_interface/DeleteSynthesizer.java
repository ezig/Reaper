package scythe_interface;

import forward_enumeration.table_enumerator.AbstractTableEnumerator;
import global.GlobalConfig;
import sql.lang.Table;
import sql.lang.TableRow;
import sql.lang.ast.filter.Filter;
import sql.lang.ast.table.DeleteNode;
import sql.lang.ast.table.UpdateNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


public class DeleteSynthesizer extends ModifySynthesizer {

    public DeleteNode Synthesize(String exampleFilePath,
                           AbstractTableEnumerator enumerator,
                           ExampleDS exampleDS) {
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

        DeleteNode deleteNode = new DeleteNode(orig, candidateFilters);

        return deleteNode;
    }

    private Boolean checkSynthesisResult(DeleteNode deleteNode, ExampleDS exampleDS) {
        Table orig = exampleDS.tModify;
        Table modified = exampleDS.output;

        List<Integer> deletedIndices = getModifiedIndices(modified, orig);

        return deleteNode.evalMatches(orig, deletedIndices);
    }

    public void Synthesize(String exampleFilePath, AbstractTableEnumerator enumerator) {
        // read file
        ExampleDS exampleDS = ExampleDS.readFromFile(exampleFilePath);
        ExampleTransformer transformer = new ExampleTransformer(exampleDS);
        Optional<ExampleDS> transformedExampleDSOptional = transformer.transform();

        if (transformedExampleDSOptional.isPresent()) {
            ExampleDS transformedExampleDS = transformedExampleDSOptional.get();
            DeleteNode deleteNode = Synthesize(exampleFilePath, enumerator, transformedExampleDS);
            if (checkSynthesisResult(deleteNode, exampleDS)) {
                printDelete(deleteNode);
            }
        } else {
            DeleteNode deleteNode = Synthesize(exampleFilePath, enumerator, exampleDS);
            printDelete(deleteNode);
        }
    }

    private static void printDelete(DeleteNode deleteNode) { deleteNode.print(); }

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