package sql.lang.ast.filter;

import forward_enumeration.primitive.parameterized.InstantiateEnv;
import sql.lang.Table;
import sql.lang.TableRow;
import sql.lang.ast.table.TableNode;
import sql.lang.ast.val.ConstantVal;
import sql.lang.ast.val.NamedVal;
import sql.lang.datatype.*;
import sql.lang.ast.Environment;
import sql.lang.ast.Hole;
import sql.lang.exception.SQLEvalException;
import sql.lang.ast.val.ValNode;
import sql.lang.trans.ValNodeSubstBinding;
import util.IndentionManagement;

import java.util.*;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Created by clwang on 12/14/15.
 * A comparator between values
 */
public class VVComparator implements Filter {

    List<ValNode> args = new ArrayList<>();
    BiFunction<Value, Value, Boolean> compareFunc;

    public VVComparator(List<ValNode> args, BiFunction func) {
        this.args = args;
        this.compareFunc = func;
    }

    @Override
    public boolean filter(Environment env) throws SQLEvalException {
        // two arguments
        Value v1 = args.get(0).eval(env);
        Value v2 = args.get(1).eval(env);
        return compareFunc.apply(v1, v2);
    }

    @Override
    public int getFilterLength() {
        return 1;
    }

    @Override
    public int getNestedQueryLevel() {
        int lv = 0;
        for (ValNode vn : this.args) {
            if (vn.getNestedQueryLevel() > lv)
                lv = vn.getNestedQueryLevel();
        }
        return lv;
    }

    @Override
    public String prettyPrint(int indentLv) {
        String result = args.get(0).prettyPrint(indentLv + 1).trim()
                + " "
                + OperatorName(this.compareFunc)
                + " "
                + args.get(1).prettyPrint(indentLv + 1).trim();
        return IndentionManagement.addIndention(result, indentLv);
    }

    @Override
    public boolean containsExclusiveFilter(Filter f) {
        if (f instanceof VVComparator) {
            boolean exclusive = true;
            for (ValNode v : this.args) {
                if (! ((VVComparator) f).args.contains(v))
                    return false;
            }
            for (ValNode v : ((VVComparator) f).args) {
                if (! this.args.contains(v))
                    return false;
            }
            return exclusive;
        }
        return f.containsExclusiveFilter(this);
    }

    public static BiFunction<Value, Value, Boolean> lt = (v1, v2) -> {

        if (! v1.getValType().equals(v2.getValType())) {
            System.out.println("[Error@VVComparator45] " + "Comparing between none-number value: " + v1.toString() + " and " + v2.toString());
        }

        // TODO: double check this
        if (v1 instanceof NullVal || v2 instanceof NullVal)
            return false;

        if (v1 instanceof NumberVal && v2 instanceof NumberVal) {
            return ((NumberVal)v1).getVal() < ((NumberVal)v2).getVal();
        } else if (v1 instanceof DateVal && v2 instanceof DateVal) {
            return ((DateVal)v1).getVal().compareTo(((DateVal)v2).getVal()) < 0 ;
        }
        return false;
     };

    public static BiFunction<Value, Value, Boolean> eq = (v1, v2) -> {
        return v1.getVal().equals(v2.getVal());
    };

    public static BiFunction<Value, Value, Boolean> neq = (v1, v2) -> ! eq.apply(v1, v2);

    public static BiFunction<Value, Value, Boolean> gt = (v1, v2) -> lt.apply(v2, v1);

    public static BiFunction<Value, Value, Boolean> le = (v1, v2) -> ! gt.apply(v1, v2);

    public static BiFunction<Value, Value, Boolean> ge = (v1, v2) -> ! lt.apply(v1, v2);

    public static List<BiFunction<Value, Value, Boolean>> getAllFunctions() {
        return Arrays.asList(lt, eq, gt, le, ge, neq);
    }

    protected static String OperatorName(BiFunction<Value, Value, Boolean> op) {
        if (op.equals(eq)) return "=";
        else if (op.equals(le)) return "<=";
        else if (op.equals(ge)) return ">=";
        else if (op.equals(lt)) return "<";
        else if (op.equals(gt)) return ">";
        else if (op.equals(neq)) return "<>";
        else return "??";
    }

    @Override
    public List<Hole> getAllHoles() {
        List<Hole> result = new ArrayList<>();
        this.args.forEach(vn -> result.addAll(vn.getAllHoles()));
        return result;
    }

    @Override
    public List<Value> getAllConstants() {
        List<Value> values = new ArrayList<>();
        for (ValNode vn : this.args) {
            if (vn instanceof ConstantVal) {
                values.add(((ConstantVal) vn).getValue());
            }
        }
        return values;
    }

    @Override
    public Filter instantiate(InstantiateEnv env) {
        return new VVComparator(
                args.stream().map(vn -> vn.instantiate(env)).collect(Collectors.toList()),
                this.compareFunc);
    }

    @Override
    public Filter substNamedVal(ValNodeSubstBinding vnsb) {
        Filter f = new VVComparator(args.stream().map(vn -> vn.subst(vnsb)).collect(Collectors.toList()), this.compareFunc);
        return f;
    }

    @Override
    public List<String> getColumnNames() {
        List <String> cols = new ArrayList<>();

        for (ValNode vn: this.args) {
            if (vn instanceof NamedVal) {
                cols.add(vn.getName());
            }
        }

        return cols;
    }

    @Override
    public boolean applyRename(Map<String, String> rename) {
        boolean changeMade = false;

        for (int i = 0; i < this.args.size(); i++) {
            ValNode vn = this.args.get(i);

            if (vn instanceof NamedVal) {
                if (rename.containsKey(vn.getName())) {
                    args.set(i, new NamedVal(rename.get(vn.getName())));
                    changeMade = true;
                }
            }
        }

        return changeMade;
    }

    @Override
    public Filter colToNestedQ(String colName, TableNode nested) {
        List<Integer> idxs = IntStream.range(0, 2)
                .filter(i -> args.get(i).getName().equals(colName))
                .boxed()
                .collect(Collectors.toList());

        if (idxs.size() == 0) {
            return this;
        } else if (idxs.size() == 2) {
            throw new IllegalStateException("VVComparator had both sides of comparison as " + colName);
        }

        int valIdx = 1 - idxs.get(0);

        return new NestedQueryCompFilter(args.get(valIdx), nested, compareFunc, valIdx == 0);
    }

    public List<ValNode> getArgs() { return this.args; }
    public BiFunction<Value, Value, Boolean> getComparator() { return this.compareFunc; }

    public SortedSet<Integer> filter(Table t) {
        Integer numRows = t.getContent().size();
        SortedSet<Integer> out = new TreeSet<>();

        List<TableRow> content = t.getContent();
        String tName = t.getName();

        for (Integer i = 0; i < numRows; i++) {
            TableRow row = content.get(i);
            Environment env = new Environment(row, tName);

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
}
