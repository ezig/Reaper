package scythe_interface;

import com.sun.javaws.exceptions.InvalidArgumentException;
import forward_enumeration.table_enumerator.*;
import global.Statistics;

public class Main {

    private enum SynthesisType {
        Update, Delete, WAggr, Select
    }

    // the interface for running the tool in
    public static void main(String[] args) {

        if (args.length < 2) {
            System.out.println("[ERROR] Not enough arguments provided.");
            System.out.println("  usage: java -jar path enumerator_name");
            System.exit(-1);
        }

        // for logging purpose
        System.setErr(System.out);

        String filename = args[0];
        String enumerator = args[1];

        SynthesisType type = SynthesisType.Select;

        for (int i = 2; i < args.length; i++) {
            switch (args[i]) {
                case "-aggr":
                    type = SynthesisType.WAggr;
                    break;
                case "-del":
                    type = SynthesisType.Delete;
                    break;
                case "-update":
                    type = SynthesisType.Update;
                    break;
                default:
                    throw new IllegalArgumentException("Unexpected command line flag " + args[i]);
            }
        }

        AbstractTableEnumerator en = enumeratorSwitch(enumerator);

        switch (type) {
            case Select:
                System.out.println("[[Synthesizing Select]]");
                Synthesizer.Synthesize(filename, en);
                break;
            case WAggr:
                System.out.println("[[Synthesizing Select With Aggregation Functions]]");
                Synthesizer.SynthesizeWAggr(filename, en);
                break;
            case Update:
                System.out.println("[[Synthesizing Update]]");
                (new UpdateSynthesizer()).Synthesize(filename, en);
                break;
            case Delete:
                System.out.println("[[Synthesizing Delete]]");
                (new DeleteSynthesizer()).Synthesize(filename, en);
                break;
        }

        // Statistics.printAllStatistics();
    }

    public static AbstractTableEnumerator enumeratorSwitch(String name) {
        if (name.equals("StagedEnumerator"))
            return new StagedEnumerator();
        else if (name.equals("CanonicalEnumeratorOnTheFly"))
            return new CanonicalTableEnumeratorOnTheFly();
        else {
            System.out.println("The enumerator [" + name + "] is currently not supported.");
            System.exit(-1);
        }
        return null;
    }

}
