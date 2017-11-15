package sql.lang.ast.filter;

import forward_enumeration.primitive.parameterized.InstantiateEnv;
import sql.lang.Table;
import sql.lang.ast.Environment;
import sql.lang.ast.Hole;
import sql.lang.datatype.Value;
import sql.lang.exception.SQLEvalException;
import sql.lang.trans.ValNodeSubstBinding;

import java.util.*;

/**
 * Created by clwang on 1/4/16.
 */
public class EmptyFilter implements Filter {

    @Override
    public boolean filter(Environment env) throws SQLEvalException {
        return true;
    }

    @Override
    public int getFilterLength() {
        return 1;
    }

    @Override
    public int getNestedQueryLevel() {
        return 0;
    }

    @Override
    public String prettyPrint(int indentLv) {
        return "";
    }

    @Override
    public boolean containsExclusiveFilter(Filter f) {
        return false;
    }

    @Override
    public List<Hole> getAllHoles() {
        return new ArrayList<>();
    }

    @Override
    public List<Value> getAllConstants() {
        return new ArrayList<>();
    }

    @Override
    public Filter instantiate(InstantiateEnv env) {
        return this;
    }

    @Override
    public Filter substNamedVal(ValNodeSubstBinding vnsb) {
        return this;
    }

    public SortedSet<Integer> filter(Table t) {
        Integer numRows = t.getContent().size();
        SortedSet<Integer> out = new TreeSet<>();

        Integer i = 0;
        while (i < numRows) {
            out.add(i);
            i++;
        }

        return out;
    }

    @Override
    public List<String> getColumnNames() {
        return new ArrayList<>();
    }

    @Override
    public void applyRename(Map<String, String> rename) {

    }
}
