package sql.lang.ast.table;

import enumerator.parameterized.InstantiateEnv;
import javafx.util.Pair;
import sql.lang.DataType.ValType;
import sql.lang.Table;
import sql.lang.ast.Environment;
import sql.lang.ast.Hole;
import sql.lang.ast.filter.Filter;
import sql.lang.exception.SQLEvalException;
import sql.lang.trans.ValNodeSubstBinding;
import util.IndentionManagement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by clwang on 12/16/15.
 */
public class NamedTable implements TableNode {

    Table table;

    public NamedTable(Table table) {
        this.table = table;
    }

    @Override
    public Table eval(Environment env) throws SQLEvalException {
        return table.duplicate();
    }

    @Override
    public List<String> getSchema() {
        return table.getQualifiedMetadata();
    }

    @Override
    public String getTableName() {
        return table.getName();
    }

    @Override
    public List<ValType> getSchemaType() {
        return table.getSchemaType();
    }

    @Override
    public int getNestedQueryLevel() {
        return 1;
    }

    @Override
    public String prettyPrint(int indentLv) {
        return IndentionManagement.addIndention(this.getTableName(), indentLv);
    }

    @Override
    public List<Hole> getAllHoles() {
        return new ArrayList<>();
    }

    public Table getTable() { return this.table; }

    @Override
    public TableNode instantiate(InstantiateEnv env) {
        return this;
    }

    @Override
    public TableNode substNamedVal(ValNodeSubstBinding vnsb) {
        return this;
    }

    @Override
    public List<NamedTable> namedTableInvolved() {
        return Arrays.asList(this);
    }

    @Override
    public TableNode tableSubst(List<Pair<TableNode,TableNode>> pairs) {
        try {
            for (Pair<TableNode, TableNode> p : pairs)
            if (this.table.contentEquals(p.getKey().eval(new Environment())))
                return p.getValue();
        } catch (SQLEvalException e) {
            e.printStackTrace();
        }
        return this;
    }

}