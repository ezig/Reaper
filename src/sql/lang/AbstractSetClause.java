package sql.lang;

import forward_enumeration.table_enumerator.AbstractTableEnumerator;
import global.GlobalConfig;
import scythe_interface.ExampleDS;
import sql.lang.ast.filter.EmptyFilter;
import sql.lang.ast.table.JoinNode;
import sql.lang.ast.table.NamedTable;
import sql.lang.ast.table.SelectNode;
import sql.lang.ast.table.TableNode;
import sql.lang.ast.val.ConstantVal;
import sql.lang.datatype.Value;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
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
            List<TableNode> candidates = syn.synthesize(ds);

            System.out.println(candidates.get(0).printQuery());
            if (GlobalConfig.OPTIMIZE_READABILITY) {
                for (TableNode t : candidates) {
                    t.eliminateRenames();
                }
            }

            System.out.println(candidates.get(0).printQuery());
            // Need to correlate
            if (outputProj.getContent().size() > 1) {
                candidates = candidates.stream()
                        .map((c) -> toCorrelated(c, updatedIn))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
            }


            if (candidates.isEmpty()) {
                throw new RuntimeException(
                        String.format("Failed to synthesize set clause for col %s", outCol)
                );
            }
            terms.add(new NestedQ(outCol, candidates));
        }

        return new AbstractSetClause(terms);
    }

    private static TableNode toCorrelated(TableNode q, Table updatedIn) {
        if (q instanceof SelectNode) {
            SelectNode select = (SelectNode) q;
            List<String> filterColNames = select.getFilter().getColumnNames();
            List<String> schemaNames = updatedIn.getSchema().stream()
                    .map((c) -> String.format("%s.%s", updatedIn.getName(), c))
                    .collect(Collectors.toList());
            filterColNames.retainAll(schemaNames);
            if (filterColNames.isEmpty()) {
                return null;
            }

            if (select.getTableNode() instanceof JoinNode) {
                JoinNode join = (JoinNode) select.getTableNode();
                if (join.getTableNodes().size() > 2) {
                    return null;
                }

                List<TableNode> newTns = join.getTableNodes().stream()
                        .filter((t) -> !isTable(t, updatedIn.getName()))
                        .collect(Collectors.toList());

                if (newTns.size() == 1) {
                    return new SelectNode(select.getColumns(), newTns.get(0),select.getFilter());
                }
            }
        }

        return null;
    }

    private static boolean isTable(TableNode t, String name) {
        if (t instanceof SelectNode) {
            SelectNode select = (SelectNode) t;

            if (select.getFilter() instanceof EmptyFilter &&
                    isTable(select.getTableNode(), name) &&
                    select.getTableNode().getSchema().size() == select.getSchema().size()) {
                    return true;
            }
        } else if (t instanceof NamedTable) {
            return t.getTableName().equals(name);
        }

        return false;
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
            TableNode t = candidates.get(0);

            String q = t.printQuery();
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
