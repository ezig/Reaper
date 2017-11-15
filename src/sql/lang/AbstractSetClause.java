package sql.lang;

import forward_enumeration.table_enumerator.AbstractTableEnumerator;
import scythe_interface.ExampleDS;
import sql.lang.ast.Environment;
import sql.lang.ast.table.TableNode;
import sql.lang.ast.val.ConstantVal;
import sql.lang.datatype.Value;
import sql.lang.exception.SQLEvalException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class AbstractSetClause {
    final private List<TermFun> terms;

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
                                                    String exampleFilePath,
                                                    AbstractTableEnumerator en,
                                                    SynthesizeFun syn) {
        List<TermFun> terms = new ArrayList<>();

        ds.tModify = null;
        int nColumns = updatedIn.getSchema().size();
        for (int i = 0; i < nColumns; i++) {
            String outCol = updatedOut.getSchema().get(i);
            List<ConstantVal> colConstants = ds.enumConfig.getUpdateConstants().getOrDefault(outCol, new ArrayList<>());

            Table inputProj = updatedIn.projection(Collections.singletonList(i));
            Table outputProj = updatedOut.projection(Collections.singletonList(i));

            if (inputProj.contentStrictEquals(outputProj)) {
                terms.add(new Identity());
                continue;
            }

            boolean foundConstant = false;
            for (ConstantVal c : colConstants) {
                if (outputProj.getColumnByIndex(0).stream().allMatch((v) -> v.equals(c.getValue()))) {
                    terms.add(new Constant(outCol, c.getValue()));
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

            ds.tModify = null;
            ds.output = outputProj;

            ds.enumConfig.setConstants(colConstants);

            // TODO: check for failed synthesis here
            terms.add(new NestedQ(outCol, syn.synthesize(ds)));
        }

        return new AbstractSetClause(terms);
    }

    private interface TermFun {
        String concretize();

        List<Table> eval(Table t, Integer colIndex);
    }

    private static class Identity implements TermFun {
        @Override
        public String concretize() {
            return "";
        }

        public List<Table> eval(Table t, Integer colIndex) {
            List<Table> out = new ArrayList<>();

            out.add(t.projection(Collections.singletonList(colIndex)));

            return out;
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

        public List<Table> eval(Table t, Integer colIndex) {
            List<Table> out = new ArrayList<>();

            Integer projColIndex = t.retrieveIndex(this.projCol);
            assert(projColIndex != -1);

            Table projColTable = t.projection(Collections.singletonList(projColIndex));
            List<String> newSchema = new ArrayList<>();
            newSchema.add(t.getSchema().get(colIndex));
            projColTable.renameColumns(newSchema);

            out.add(projColTable);

            return out;
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

        public List<Table> eval(Table t, Integer colIndex) {
            List<Table> out = new ArrayList<>();

            Table constant = t.projection(Collections.singletonList(colIndex));

            for (TableRow row: constant.getContent()) {
                row.setValue(0, this.constV);
            }

            out.add(constant);

            return out;
        }
    }

    private static class NestedQ implements TermFun {
        private List<TableNode> candidates = null;
        private String outCol = null;

        private NestedQ(String outCol, List<TableNode> candidates) {
            this.candidates = candidates;
            this.outCol = outCol;
        }

        @Override
        public String concretize() {
            String q = candidates.get(0).printQuery();
            return outCol + " = (" + q.substring(0, q.length() - 1) + ")";
        }

        public List<Table> eval(Table t, Integer colIndex) {
            List<Table> out = new ArrayList<>();

            List<String> schema = new ArrayList<>();
            schema.add(t.getSchema().get(colIndex));

            for (TableNode candidate: candidates) {
                try {
                    Table queryCol = candidate.eval(new Environment());
                    queryCol.renameColumns(schema);
                    out.add(queryCol);
                } catch (SQLEvalException e) {
                    e.printStackTrace();
                }
            }

            return out;
        }

        public void removeFirstNCandidates(Integer n) {
            assert (n <= this.candidates.size());

            Integer i = 0;
            while (i < n) {
                this.candidates.remove(0);
            }
        }
    }

    public String concretize() {
        return terms.stream()
                .map(TermFun::concretize)
                .filter((s) -> s.length() > 0)
                .collect(Collectors.joining(", "));
    }

    // returns true if there is *a* list of set clause candidates that
    // would produce the desired output. If returns true, the checked
    // candidates will be at the head of the candidate list (if the
    // term is NestedQ).
    public Boolean evalMatches(Table in, Table out) {
        assert(terms.size() == in.getSchema().size());
        assert(terms.size() == out.getSchema().size());

        for (Integer i = 0; i < terms.size(); i++) {
            List<Table> colResults = terms.get(i).eval(in, i);
            Table desiredResult = out.projection(Collections.singletonList(i));

            Integer j = 0;

            while (j < colResults.size()) {
                if (colResults.get(j).tableEquals(desiredResult)) {
                    break;
                }
                j++;
            }

            if (j >= colResults.size()) {
                return false;
            }

            if (j != 0) {
                TermFun term = terms.get(i);
                assert(term instanceof NestedQ);
                ((NestedQ) term).removeFirstNCandidates(j);
            }
        }

        return true;
    }
}
