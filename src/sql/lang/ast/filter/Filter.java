package sql.lang.ast.filter;

import forward_enumeration.primitive.parameterized.InstantiateEnv;
import sql.lang.Table;
import sql.lang.ast.Environment;
import sql.lang.ast.Hole;
import sql.lang.ast.table.TableNode;
import sql.lang.datatype.Value;
import sql.lang.exception.SQLEvalException;
import sql.lang.trans.ValNodeSubstBinding;

import java.util.List;
import java.util.SortedSet;
import java.util.Map;

/**
 * Created by clwang on 12/14/15.
 */
public interface Filter {

    // for evaluation
    boolean filter(Environment env) throws SQLEvalException;

    // for calculating the length of the filter
    int getFilterLength();
    int getNestedQueryLevel();

    String prettyPrint(int indentLv);
    //boolean equalsToFilter(Filter f);

    // we also consider same as "exclusive"
    boolean containsExclusiveFilter(Filter f);
    List<Hole> getAllHoles();
    List<Value> getAllConstants();
    Filter instantiate(InstantiateEnv env);
    Filter substNamedVal(ValNodeSubstBinding vnsb);

    SortedSet<Integer> filter(Table t);

    // Get all the column names used in this filter
    List<String> getColumnNames();
    boolean applyRename(Map<String, String> rename);
    Filter colToNestedQ(String colName, TableNode nested);
}
