package sql.lang;

import scythe_interface.ExampleDS;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class AbstractSetClause {
    List<AbstractSetTerm> terms;

    private AbstractSetClause(List<AbstractSetTerm> terms) {
        this.terms = terms;
    }

    public static AbstractSetClause enumerateFromIO(TableRow input, TableRow output, ExampleDS ds) {
        int nColumns = input.getValues().size();

        List<AbstractSetTerm> terms = new ArrayList<>();

        for (int i = 0; i < nColumns; i++) {
            terms.add(AbstractSetTerm.enumerateFromIO(
                    input.getValue(i), output.getValue(i), input, ds.tUpdate.getSchema().get(i), ds)
            );
        }

        return new AbstractSetClause(terms);
    }

    public void intersect(AbstractSetClause other) {
        for (int i = 0; i < terms.size(); i++) {
            terms.get(i).intersect(other.terms.get(i));
        }
    }

    public String concretize() {
        return terms.stream()
                .map(AbstractSetTerm::concretize)
                .filter((s) -> s.length() > 0)
                .collect(Collectors.joining(", "));
    }
}
