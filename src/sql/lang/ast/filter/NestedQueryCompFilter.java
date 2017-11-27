package sql.lang.ast.filter;

import forward_enumeration.primitive.parameterized.InstantiateEnv;
import sql.lang.Table;
import sql.lang.TableRow;
import sql.lang.ast.Environment;
import sql.lang.ast.Hole;
import sql.lang.ast.table.TableNode;
import sql.lang.ast.val.NamedVal;
import sql.lang.ast.val.ValNode;
import sql.lang.datatype.Value;
import sql.lang.exception.SQLEvalException;
import sql.lang.trans.ValNodeSubstBinding;
import util.IndentionManagement;

import java.util.*;
import java.util.function.BiFunction;

public class NestedQueryCompFilter implements Filter {
    private final BiFunction<Value, Value, Boolean> compFun;
    private ValNode val;
    private final TableNode tn;
    private final boolean valFirst;

    public NestedQueryCompFilter(ValNode val, TableNode tn, BiFunction<Value, Value, Boolean> compFun, boolean valFirst) {
        this.val = val;
        this.tn = tn;
        this.compFun = compFun;
        this.valFirst = valFirst;
    }

    @Override
    public boolean filter(Environment env) throws SQLEvalException {
        Value v1 = val.eval(env);
        Table nestedT =  tn.eval(new Environment());

        // This is safe because result of query should be 1x1
        Value v2 = nestedT.getContent().get(0).getValue(0);

        return (valFirst ? compFun.apply(v1, v2) : compFun.apply(v2, v1));
    }

    @Override
    public int getFilterLength() {
        return 1;
    }

    @Override
    public int getNestedQueryLevel() {
        return tn.getNestedQueryLevel();
    }

    @Override
    public String prettyPrint(int indentLv) {
        // get rid of trailing semi
        String tableString = tn.printQuery();
        tableString = tableString.substring(0, tableString.length() - 1);

        if (valFirst) {
            return IndentionManagement.addIndention(
                    val.prettyPrint(0) +
                            " " +
                            VVComparator.OperatorName(compFun) +
                            "\r\n" +
                            IndentionManagement.addIndention(tableString, 1), indentLv);
        } else {


            return IndentionManagement.addIndention(
                    "\r\n" +
                    tableString +
                            " " +
                            VVComparator.OperatorName(compFun) +
                            " " +
                            val.prettyPrint(0), indentLv);
        }
    }

    @Override
    public boolean containsExclusiveFilter(Filter f) {
        return false;
    }

    @Override
    public List<Hole> getAllHoles() {
        return tn.getAllHoles();
    }

    @Override
    public List<Value> getAllConstants() {
        return tn.getAllConstants();
    }

    @Override
    public Filter instantiate(InstantiateEnv env) {
        return new NestedQueryCompFilter(val, tn.instantiate(env), compFun, valFirst);
    }

    @Override
    public Filter substNamedVal(ValNodeSubstBinding vnsb) {
        return new NestedQueryCompFilter(val.subst(vnsb), tn.substNamedVal(vnsb), compFun, valFirst);
    }

    @Override
    public SortedSet<Integer> filter(Table t) {
        int numRows = t.getContent().size();
        SortedSet<Integer> out = new TreeSet<>();

        for (int i = 0; i < numRows; i++) {
            TableRow row = t.getContent().get(i);

            Environment env = new Environment(row, t.getName());

            try {
                if (this.filter(env)) {
                    out.add(i);
                }
            } catch (SQLEvalException e) {
                e.printStackTrace();
            }
        }

        return out;
    }

    @Override
    public List<String> getColumnNames() {
        return Arrays.asList(val.getName());
    }

    @Override
    public boolean applyRename(Map<String, String> rename) {
        // Rename should only apply to the val, not to the nested query based on the way this filter is used
        if (val instanceof NamedVal && rename.containsKey(val.getName())) {
            val = new NamedVal(rename.get(val.getName()));
            return true;
        }

        return false;
    }

    @Override
    public Filter colToNestedQ(String colName, TableNode nested) {
        throw new RuntimeException("colToNestedQ called on filter that already contained nested Q?");
    }
}
