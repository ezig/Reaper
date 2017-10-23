package sql.lang;

import scythe_interface.ExampleDS;
import sql.lang.datatype.Value;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class AbstractSetTerm {
    private final Set<TermFun> funs;
    private final String columnName;

    private AbstractSetTerm(Set<TermFun> funs, String columnName) {
        this.funs = funs;
        this.columnName = columnName;
    }

    public static AbstractSetTerm enumerateFromIO(Value input,
                                                  Value output,
                                                  TableRow iRow,
                                                  String columnName,
                                                  ExampleDS ds) {
        Set<TermFun> funs = new HashSet<>();

        if (input.equals(output)) {
            funs.add(new Identity());
        }

        for (Value c : ds.enumConfig.getConstValues()) {
            if (output.equals(c)) {
                funs.add(new Constant(c));
            }
        }

        int nColumns = iRow.getValues().size();
        for (int i = 0; i < nColumns; i++) {
            if (output.equals(iRow.getValue(i))) {
                funs.add(new Projection(ds.tUpdate.getSchema().get(i)));
            }
        }

        return new AbstractSetTerm(funs, columnName);
    }

    public void intersect(AbstractSetTerm other) {
        this.funs.retainAll(other.funs);
    }

    public String concretize() {
        return (new ArrayList<>(this.funs)).get(0).concretize(this.columnName);
    }

    private interface TermFun {
        String concretize(String columnName);
    }

    private static class Identity implements TermFun {
        @Override
        public String concretize(String columnName) {
            return "";
        }
    }

    private static class Projection implements TermFun {
        private String col;

        public Projection(String col) {
            this.col = col;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Projection that = (Projection) o;

            return col.equals(that.col);
        }

        @Override
        public int hashCode() {
            return col.hashCode();
        }

        @Override
        public String concretize(String columnName) {
            return columnName + " = " + col;
        }
    }

    private static class Constant implements TermFun {
        private final Value constV;

        public Constant(Value v) {
            this.constV = v;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Constant constant = (Constant) o;

            return constV.equals(constant.constV);
        }

        @Override
        public int hashCode() {
            return constV.hashCode();
        }

        @Override
        public String concretize(String columnName) {
            return columnName + " = " + constV;
        }
    }
}
