package scythe_interface;

import forward_enumeration.table_enumerator.AbstractTableEnumerator;
import global.GlobalConfig;
import sql.lang.Table;

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
