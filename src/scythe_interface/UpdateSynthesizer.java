package scythe_interface;

import forward_enumeration.table_enumerator.AbstractTableEnumerator;
import global.GlobalConfig;
import sql.lang.Table;
import sql.lang.TableRow;

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
        int nRows = output.getContent().size();
        List<Integer> updatedIdxs = new ArrayList<>();

        for (int i = 0; i < nRows; i++) {
            if (!output.getContent().get(i).equals(update.getContent().get(i))) {
                updatedIdxs.add(i);
            }
        }

        List<TableRow> updatedRows = IntStream.range(0, nRows)
                .filter(i -> updatedIdxs.contains(i))
                .mapToObj(update.getContent()::get)
                .collect(Collectors.toList());

        Table updatedOnly = new Table();
        updatedOnly.initialize(update.getName(), update.getSchema(), updatedRows);

        exampleDS.output = updatedOnly;
        Synthesizer.SynthesizeWAggr(exampleFilePath, enumerator, 0, exampleDS);
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
