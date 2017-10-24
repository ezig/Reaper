package sql.lang;

import scythe_interface.ExampleDS;
import sql.lang.ast.table.TableNode;
import sql.lang.datatype.Value;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class AbstractSetClause {
    final List<TermFun> terms;

    private AbstractSetClause(List<TermFun> terms) {
        this.terms = terms;
    }

    @FunctionalInterface
    public interface SynthesizeFun {
        List<TableNode> synthesize(ExampleDS exampleDS);
    }

    public static AbstractSetClause enumerateFromIO(Table updatedIn,
                                                    Table updatedOut,
                                                    ExampleDS ds,
                                                    SynthesizeFun syn) {
        List<TermFun> terms = new ArrayList<>();

        ds.tUpdate = null;
        int nColumns = updatedIn.getSchema().size();
        for (int i = 0; i < nColumns; i++) {
            String outCol = updatedOut.getSchema().get(i);

            Table inputProj = updatedIn.projection(Collections.singletonList(i));
            Table outputProj = updatedOut.projection(Collections.singletonList(i));

            if (inputProj.contentStrictEquals(outputProj)) {
                terms.add(new Identity());
                continue;
            }

            boolean foundConstant = false;
            for (Value c : ds.enumConfig.getConstValues()) {
                if (outputProj.getColumnByIndex(0).stream().allMatch((v) -> v.equals(c))) {
                    terms.add(new Constant(outCol, c));
                    foundConstant = true;
                    break;
                }
            }
            if (foundConstant) {
                continue;
            }

            boolean foundProjection = false;
            for (int c = 0; c < nColumns; c++) {
                if (updatedIn.getColumnByIndex(c).equals(outputProj.getColumnByIndex(0))) {
                    terms.add(new Projection(outCol, updatedOut.getSchema().get(c)));
                    foundProjection = true;
                    break;
                }
            }
            if (foundProjection) {
                continue;
            }

            ds.tUpdate = null;
            ds.output = outputProj;

            // TODO: check for failed synthesis here
            terms.add(new NestedQ(outCol, syn.synthesize(ds)));
        }

        return new AbstractSetClause(terms);
    }

    private interface TermFun {
        String concretize();
    }

    private static class Identity implements TermFun {
        @Override
        public String concretize() {
            return "";
        }
    }

    private static class Projection implements TermFun {
        private final String outCol;
        private final String projCol;

        public Projection(String outCol, String projCol) {
            this.outCol = outCol;
            this.projCol = projCol;
        }

        @Override
        public String concretize() {
            return outCol + " = " + projCol;
        }
    }

    private static class Constant implements TermFun {
        private final Value constV;
        private final String outCol;

        public Constant(String outCol, Value v) {
            this.constV = v;
            this.outCol = outCol;
        }

        @Override
        public String concretize() {
            return outCol + " = " + constV.toString();
        }
    }

    private static class NestedQ implements TermFun {
        private final List<TableNode> candidates;
        private final String outCol;

        private NestedQ(String outCol, List<TableNode> candidates) {
            this.candidates = candidates;
            this.outCol = outCol;
        }

        @Override
        public String concretize() {
            String q = candidates.get(0).printQuery();
            return outCol + " = (" + q.substring(0, q.length() - 1) + ")";
        }
    }

    public String concretize() {
        return terms.stream()
                .map(TermFun::concretize)
                .filter((s) -> s.length() > 0)
                .collect(Collectors.joining(", "));
    }
}
