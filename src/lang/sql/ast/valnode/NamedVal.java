package lang.sql.ast.valnode;

import forward_enumeration.context.EnumContext;
import forward_enumeration.enum_components.parameterized.InstantiateEnv;
import lang.sql.dataval.ValType;
import lang.sql.dataval.Value;
import lang.sql.ast.Environment;
import lang.sql.ast.Hole;
import lang.sql.exception.SQLEvalException;
import lang.sql.transformation.ValNodeSubstitution;
import util.IndentationManager;

import java.util.ArrayList;
import java.util.List;

/**
 * A value represented by a column name
 * Created by clwang on 12/16/15.
 */
public class NamedVal implements ValNode {

    String name;

    public NamedVal(String name) {
        this.name = name;
    }

    @Override
    public Value eval(Environment env) throws SQLEvalException {
        return env.lookup(name);
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public ValType getType(EnumContext ctxt) {
        return ctxt.getValType(this.name);
    }

    @Override
    public String prettyPrint(int lv) {
        return IndentationManager.addIndention(name, lv);
    }

    @Override
    public int getNestedQueryLevel() {
        return 0;
    }

    @Override
    public boolean equalsToValNode(ValNode vn) {
        if (vn instanceof NamedVal)
            return this.name.equals(((NamedVal) vn).name);
        return false;
    }

    @Override
    public List<Hole> getAllHoles() {
        return new ArrayList<>();
    }

    @Override
    public ValNode instantiate(InstantiateEnv env) {
        return this;
    }

    @Override
    public ValNode subst(ValNodeSubstitution vnb) {
        return vnb.lookupImage(this);
    }
}
