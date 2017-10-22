package scythe_interface;

import com.sun.javaws.exceptions.InvalidArgumentException;
import forward_enumeration.table_enumerator.*;
import global.Statistics;

public class Main {

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

        boolean synthesizeWAggr = false;
        boolean synthesizeUpdate = false;

        for (int i = 2; i < args.length; i++) {
            switch (args[i]) {
                case "-aggr":
                    synthesizeWAggr = true;
                    break;
                case "-update":
                    synthesizeUpdate = true;
                    break;
                default:
                    throw new IllegalArgumentException("Unexpected command line flag " + args[i]);
            }
        }

        AbstractTableEnumerator en = enumeratorSwitch(enumerator);

        if (synthesizeUpdate) {
            UpdateSynthesizer.SynthesizeUpdate(filename, en);
        } else {
            if (synthesizeWAggr) {
                System.out.println("[[Synthesizing With Aggregation Functions]]");
                Synthesizer.SynthesizeWAggr(filename, en);
            } else {
                System.out.println("[[Synthesizing]]");
                Synthesizer.Synthesize(filename, en);
            }
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
