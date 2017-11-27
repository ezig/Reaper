package scythe_interface;

import forward_enumeration.table_enumerator.AbstractTableEnumerator;
import sql.lang.Table;
import sql.lang.TableRow;
import sql.lang.ast.Environment;
import sql.lang.ast.filter.*;
import sql.lang.ast.table.JoinNode;
import sql.lang.ast.table.NamedTable;
import sql.lang.ast.table.SelectNode;
import sql.lang.ast.table.TableNode;
import sql.lang.ast.val.NamedVal;
import sql.lang.datatype.Value;
import sql.lang.exception.SQLEvalException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public abstract class ModifySynthesizer {
    public static long TimeOut = 600000;

    public abstract void Synthesize(String exampleFilePath, AbstractTableEnumerator enumerator);

    protected static List<Filter> generateCandidateFilters(String exampleFilePath,
                                                         AbstractTableEnumerator enumerator,
                                                         ExampleDS exampleDS,
                                                         Table modifiedOnly) {
        List<Filter> filters = new ArrayList<>();

        if (modifiedOnly.getContent().size() != exampleDS.tModify.getContent().size()) {
            exampleDS.output = modifiedOnly;
            List<TableNode> candidates = Synthesizer.SynthesizeWAggr(exampleFilePath, enumerator, -1, exampleDS);

            candidates.forEach(tableNode -> tableNode.eliminateRenames(true));

            candidates = candidates.stream()
                    .map(c -> attemptClassifierTransform(c, exampleDS.tModify.getName()))
                    .filter(c -> isValidClassifier(c, exampleDS.tModify))
                    .collect(Collectors.toList());

            if (candidates.size() == 0) {
                throw new IllegalStateException("Unable to learn a classifier query for I/O examples");
            }

            for (TableNode t : candidates) {
                // Cast is safe based on the filter above
                SelectNode s = (SelectNode) t;
                filters.add(s.getFilter());
            }
        } else {
            filters.add(new EmptyFilter());
        }

        return filters;
    }

    public static boolean isTable(TableNode t, String name) {
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

    private static TableNode attemptClassifierTransform(TableNode t, String tModifyName) {
        if (t instanceof SelectNode) {
            SelectNode outerS = (SelectNode) t;

            if (outerS.getTableNode() instanceof JoinNode) {
                JoinNode jNode = (JoinNode) outerS.getTableNode();

                if (jNode.getTableNodes().size() == 2) {
                    TableNode tModify;
                    TableNode tNested;

                    if (isTable(jNode.getTableNodes().get(0), tModifyName)) {
                        tModify = jNode.getTableNodes().get(0);
                        tNested = jNode.getTableNodes().get(1);
                    } else if (isTable(jNode.getTableNodes().get(1), tModifyName)) {
                        tNested = jNode.getTableNodes().get(0);
                        tModify = jNode.getTableNodes().get(1);
                    } else {
                        return t;
                    }

                    if (!tModify.getSchema().equals(outerS.getSchema())) {
                        return t;
                    }

                    Table nestedRes;

                    try {
                        nestedRes = tNested.eval(new Environment());
                    } catch (SQLEvalException e) {
                        throw new RuntimeException(e);
                    }

                    if (!(nestedRes.getContent().size() == 1 && nestedRes.getContent().get(0).getValues().size() == 1)) {
                        return t;
                    }


                    String elimColName = tNested.getSchema().get(0);
                    // removing renames is possible now that it's extracted from join and we have the name of the
                    // column that will be used in the filter
                    tNested.eliminateRenames(true);

                    Filter newFilter = outerS.getFilter().colToNestedQ(elimColName, tNested);

                    // SELECT * FROM
                    // tModify
                    // WHERE x = (SELECT ...)
                    return new SelectNode(outerS.getColumns(),
                            ((SelectNode) tModify).getTableNode(),
                            newFilter);
                }
            }
        }

        return t;
    }


    // Memoized supplier for a table
    private static class MemoizedTable {
        private final Supplier<Table> supplier;
        private final Map<String, Table> cache = new HashMap<>();

        public MemoizedTable(Supplier<Table> supplier) {
            this.supplier = supplier;
        }

        public Table get() {
            return cache.computeIfAbsent("key", k -> supplier.get());
        }
    }

    // Returns true just when `t` is of the form
    // SELECT *
    // FROM tModify
    // WHERE p
    private static boolean isValidClassifier(TableNode t, Table tModify) {
        if (t instanceof SelectNode) {
            SelectNode s = (SelectNode) t;

            if (s.getTableNode() instanceof NamedTable) {
                NamedTable nt = (NamedTable) s.getTableNode();

                if (!nt.getTableName().equals(tModify.getName())) {
                    return false;
                }

                // Build the select * query in case we need to check the synthesized query
                TableNode checkNode = new SelectNode(nt.getSchema().stream().map(NamedVal::new).collect(Collectors.toList()),
                        new NamedTable(tModify),
                        s.getFilter());

                // Create a memoized supplied since we may or may not need to actually evaluate the query
                MemoizedTable mt = new MemoizedTable(() -> {
                    try {
                        return checkNode.eval(new Environment());
                    } catch (SQLEvalException e) {
                        throw new RuntimeException(e);
                    }
                });

                // Can assume that t and tModify have same number of columns or query would not have been synthesized
                for (int i = 0; i < t.getSchema().size(); i++) {
                    String actualColName = t.getSchema().get(i);
                    String expectedColName = nt.getSchema().get(i);

                    if (!actualColName.equals(expectedColName) &&
                            !colsEquals(actualColName, expectedColName, mt.get())) {
                        return false;
                    }
                }

                return true;
            }
        }

        return false;
    }

    private static boolean colsEquals(String col1name, String col2name, Table t) {
        List<Value> col1 = t.getColumnByIndex(t.getSchema().indexOf(col1name));
        List<Value> col2 = t.getColumnByIndex(t.getSchema().indexOf(col2name));

        return col1.equals(col2);
    }

    protected static List<TableRow> getRowsAtIndices(Table t, List<Integer> idxs) {
        return IntStream.range(0, t.getContent().size())
                .filter(idxs::contains)
                .mapToObj(t.getContent()::get)
                .collect(Collectors.toList());
    }

    protected abstract List<Integer> getModifiedIndices(Table modified, Table orig);

    protected abstract boolean isValidInput(ExampleDS exampleDS);
}