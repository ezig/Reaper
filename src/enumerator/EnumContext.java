package enumerator;

import com.apple.laf.AquaButtonBorder;
import javafx.util.Pair;
import sql.lang.DataType.ValType;
import sql.lang.DataType.Value;
import sql.lang.Table;
import sql.lang.ast.Environment;
import sql.lang.ast.table.*;
import sql.lang.ast.val.NamedVal;
import sql.lang.ast.val.ValNode;
import sql.lang.exception.SQLEvalException;
import sql.lang.trans.ValNodeSubstBinding;
import util.DebugHelper;

import java.util.*;
import java.util.function.Function;
import java.util.jar.Attributes;
import java.util.stream.Collector;
import java.util.stream.Collectors;

/**
 * An environment used to store
 * The context is a data structure
 *  used to store constraint contexts
 * Created by clwang on 12/23/15.
 */
public class EnumContext {

    // those tables
    private Map<Table, List<TableNode>> memoizedTables = new HashMap<>();

    private Map<Table, List<TableNode>> exportedResult = new HashMap<>();

    public static final boolean noMemoization = false;

    private int maxFilterLength = 2;

    private List<TableNode> tablesNodes = new ArrayList<>();
    private List<ValNode> valNodes = new ArrayList<>();

    // ** the only part of the context that should not mutate once intialized
    private List<TableNode> parameterizedTables = new ArrayList<>();

    List<Function<List<Value>, Value>> aggrfuns = new ArrayList<>();

    private Map<String, ValType> typeMap = new HashMap<>();

    public EnumContext() {}

    public void updateTableNodes(List<TableNode> tns) {

        if (noMemoization) {
            this.tablesNodes.addAll(tns);
            return;
        }

        for (TableNode tn : tns) {
            try {
                boolean added = false;
                Table t = tn.eval(new Environment());

                if (t.getContent().size() == 0)
                    continue;

                for (Map.Entry<Table, List<TableNode>> entry : memoizedTables.entrySet()) {
                    if (entry.getKey().contentStrictEquals(t)) {
                        memoizedTables.get(entry.getKey()).add(tn);
                        added = true;
                        break;
                    }
                }
                if (!added) {
                    ArrayList<TableNode> ar = new ArrayList<>();
                    ar.add(tn);
                    memoizedTables.put(t, ar);
                }
            } catch (SQLEvalException e) {
                System.out.println("[EnumContext58] TableNode not executable.");
            }
        }
        this.tablesNodes = memoizedTables.keySet()
                .stream().map(t -> new NamedTable(t)).collect(Collectors.toList());
    }

    public void setValNodes(List<ValNode> vns) { this.valNodes = vns; }

    public void setMaxFilterLength(int maxLength) { this.maxFilterLength = maxLength; }
    public int getMaxFilterLength() { return this.maxFilterLength; }

    public void setParameterizedTables(List<TableNode> tbs) { this.parameterizedTables = tbs; }

    public List<TableNode> getParameterizedTables() { return this.parameterizedTables; }

    public EnumContext(List<Table> tbs, Constraint c) {
        this.updateTableNodes(tbs.stream().map(t -> new NamedTable(t)).collect(Collectors.toList()));
        valNodes = c.constValNodes();
        this.aggrfuns = c.getAggrFuns();
    }

    // Extend atomic tables in the context
    public static EnumContext extendTable(
            EnumContext ec, List<TableNode> tables) {

        EnumContext newEC = EnumContext.deepCopy(ec);
        newEC.updateTableNodes(tables);
        return newEC;
    }

    // Extend the atomic values in the context
    public static EnumContext extendTypeMap(
            EnumContext ec, Map<String, ValType> map) {
        EnumContext newEC = EnumContext.deepCopy(ec);
        for (Map.Entry<String, ValType> i : map.entrySet()) {
            newEC.valNodes.add(new NamedVal(i.getKey()));
        }
        newEC.typeMap.putAll(ec.typeMap);
        newEC.typeMap.putAll(map);
        return newEC;
    }

    public List<TableNode> getBasicTableNodes() {
        return this.tablesNodes;
    }

    public List<TableNode> getTableNodes() {
        List<TableNode> result = new ArrayList<>();
        for (Table t : this.memoizedTables.keySet()) {
            result.add(this.memoizedTables.get(t).get(0));
        }
        return result;
    }

    public List<ValNode> getValNodes() {
        List<ValNode> allValNodes = new ArrayList<ValNode>();
        allValNodes.addAll(this.valNodes);
        return allValNodes;
    }

    // get aggregation functions that
    public List<Function<List<Value>, Value>> getAggrFuns(ValType type) {
        List<Function<List<Value>, Value>> fs = AggregationNode.getAllAggrFunctions(type);
        return fs.stream().filter(f -> this.aggrfuns.contains(f)).collect(Collectors.toList());
    }

    public ValType getValType(String name) {
        return typeMap.get(name);
    }

    public static EnumContext deepCopy(EnumContext ec) {
        EnumContext newEC = new EnumContext();
        newEC.tablesNodes.addAll(ec.tablesNodes);
        newEC.aggrfuns.addAll(ec.aggrfuns);
        newEC.valNodes.addAll(ec.valNodes);
        newEC.typeMap.putAll(ec.typeMap);
        newEC.maxFilterLength = ec.maxFilterLength;
        newEC.parameterizedTables = ec.parameterizedTables;
        // this field is shared
        newEC.memoizedTables = ec.memoizedTables;
        return newEC;
    }

    public void debugPrint() {
        System.out.println(" EC PRINT START ");
        System.out.println("~~==~~ tables");
        //DebugHelper.printTableNodes(this.tablesNodes);
        System.out.println("diffTables: " + this.tablesNodes.size() + ", AllTables: " + this.memoizedTables.entrySet().stream().map(i -> i.getValue().size()).reduce(
                0,
                (a, b) -> a + b));
        System.out.println("~~==~~ valnodes");
        DebugHelper.printValNodes(this.valNodes);
        System.out.println(" EC PRINT END ");
    }

    public List<TableNode> lookup(Table t) {
        for (Map.Entry<Table, List<TableNode>> entry : memoizedTables.entrySet()) {
            if (entry.getKey().contentStrictEquals(t)) {
                return memoizedTables.get(entry.getKey());
            }
        }
        return new ArrayList<>();
    }

    public List<TableNode> export(TableNode tn, List<NamedTable> alreadyLookedUp, List<NamedTable> inputNamedTables) {

        List<TableNode> result = new ArrayList<>();

        List<NamedTable> namedTables = tn.namedTableInvolved();

        if (namedTables.isEmpty())
            return Arrays.asList(tn);

        List<NamedTable> tableToSubst = new ArrayList<>();

        for (NamedTable nt : namedTables) {
            boolean contained = false;
            for (NamedTable it : inputNamedTables) {
                if (it.getTable().contentEquals(nt.getTable())) {
                    contained = true;
                }
            }
            if (contained == false) {
                tableToSubst.add(nt);
            }
        }

        if (tableToSubst.isEmpty()) {
            // does not contain any other intermediate tables
            return Arrays.asList(tn);
        }

        for (NamedTable nt : namedTables) {
            for (NamedTable alt : alreadyLookedUp) {
                if (alt.getTable().contentEquals(nt.getTable()))
                    return Arrays.asList();
            }
        }

        List<NamedTable> newAlreadyLookedUp = new ArrayList<>();
        newAlreadyLookedUp.addAll(alreadyLookedUp);
        newAlreadyLookedUp.addAll(namedTables);

        List<List<TableNode>> targetedMaps = new ArrayList<>();
        for (NamedTable nt : tableToSubst) {
            List<TableNode> lkupResult = this.lookup(nt.getTable());
            List<TableNode> candidatesForNt = new ArrayList<>();
            lkupResult.stream().forEach(
                    lkupTn -> candidatesForNt.addAll(
                            this.export(lkupTn,
                                    newAlreadyLookedUp.stream().distinct().collect(Collectors.toList()),
                                    inputNamedTables)));
            targetedMaps.add(candidatesForNt);
        }

        List<List<TableNode>> ImageSet = product(targetedMaps);

        for (List<TableNode> oneMap : ImageSet) {
            List<Pair<TableNode, TableNode>> substPair = new ArrayList<>();
            for (int i = 0; i < oneMap.size(); i ++) {
                substPair.add(new Pair<>(tableToSubst.get(i), oneMap.get(i)));
            }
            result.add(tn.tableSubst(substPair));
        }

        return result;
    }

    public static List<List<TableNode>> product(List<List<TableNode>> tns) {
        List<List<TableNode>> result = new ArrayList<>();

        if (tns.size() == 1) {
            for (TableNode tn : tns.get(0)) {
                result.add(Arrays.asList(tn));
            }
            return result;
        }

        List<List<TableNode>> tailProd = product(tns.subList(1, tns.size()));
        for (TableNode tn : tns.get(0)) {
            for (List<TableNode> tailTN : tailProd) {
                ArrayList<TableNode> temp = new ArrayList();
                temp.add(tn);
                temp.addAll(tailTN);
                result.add(temp);
            }
        }
        return result;
    }
}