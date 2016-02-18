package sql.lang.ast.table;

import enumerator.parameterized.InstantiateEnv;
import javafx.util.Pair;
import sql.lang.DataType.ValType;
import sql.lang.Table;
import sql.lang.ast.Environment;
import sql.lang.ast.Hole;
import sql.lang.ast.Node;
import sql.lang.exception.SQLEvalException;
import sql.lang.trans.ValNodeSubstBinding;

import java.util.List;

/**
 * Created by clwang on 12/16/15.
 */
public interface TableNode extends Node {
    Table eval(Environment env) throws SQLEvalException;

    List<String> getSchema();
    String getTableName();

    List<ValType> getSchemaType();
    int getNestedQueryLevel();
    //boolean equalsToTableNode(TableNode tn);

    String prettyPrint(int indentLv);
    List<Hole> getAllHoles();

    TableNode instantiate(InstantiateEnv env);
    TableNode substNamedVal(ValNodeSubstBinding vnsb);
    List<NamedTable> namedTableInvolved();
    TableNode tableSubst(List<Pair<TableNode,TableNode>> pairs);
}