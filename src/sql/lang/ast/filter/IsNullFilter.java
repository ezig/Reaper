package sql.lang.ast.filter;

import forward_enumeration.primitive.parameterized.InstantiateEnv;
import sql.lang.ast.Environment;
import sql.lang.ast.Hole;
import sql.lang.ast.val.NamedVal;
import sql.lang.ast.val.ValNode;
import sql.lang.datatype.NullVal;
import sql.lang.datatype.Value;
import sql.lang.exception.SQLEvalException;
import sql.lang.trans.ValNodeSubstBinding;
import util.IndentionManagement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Created by clwang on 10/17/16.
 */
public class IsNullFilter implements Filter {

    // If this flag is set to true, the filter is actually IS NOT NULL xxx
    boolean negSign = false;
    // the column to be tested
    ValNode arg;

    public IsNullFilter(ValNode arg, boolean negSign) {
        this.arg = arg;
        this.negSign = negSign;
    }

    @Override
    public boolean filter(Environment env) throws SQLEvalException {
        Value v1 = arg.eval(env);
        if (v1 instanceof NullVal) {
            if (negSign)
                return false;
            else
                return true;
        }
        if (negSign)
            return true;
        return false;
    }

    @Override
    public int getFilterLength() {
        return 1;
    }

    @Override
    public int getNestedQueryLevel() {
        int lv = 0;
        if (arg.getNestedQueryLevel() > lv)
            lv = arg.getNestedQueryLevel();
        return lv;
    }

    @Override
    public String prettyPrint(int indentLv) {
        String str = arg.prettyPrint(0) + " Is NULL";
        if (negSign)
            str = arg.prettyPrint(0) + " Is Not NULL";
        return IndentionManagement.addIndention(str, indentLv);
    }

    @Override
    public boolean containsExclusiveFilter(Filter f) {
        return false;
    }

    @Override
    public List<Hole> getAllHoles() {
        return arg.getAllHoles();
    }

    @Override
    public List<Value> getAllConstatnts() {
        return new ArrayList<>();
    }

    @Override
    public Filter instantiate(InstantiateEnv env) {
        return new IsNullFilter(arg.instantiate(env), this.negSign);
    }

    @Override
    public Filter substNamedVal(ValNodeSubstBinding vnsb) {
        return new IsNullFilter(arg.subst(vnsb), this.negSign);
    }

    @Override
    public List<String> getColumnNames() {
        if (arg instanceof NamedVal) {
            Arrays.asList(arg.getName());
        }

        return new ArrayList<>();
    }

    @Override
    public void applyRename(Map<String, String> rename) {
        if (arg instanceof NamedVal) {
            if (rename.containsKey(arg.getName())) {
                arg = new NamedVal(rename.get(arg.getName()));
            }
        }
    }
}
