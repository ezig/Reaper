package forward_enumeration.context;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import sql.lang.Table;
import sql.lang.datatype.Value;
import sql.lang.ast.table.AggregationNode;
import sql.lang.ast.val.ConstantVal;
import sql.lang.ast.val.ValNode;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Created by clwang on 1/7/16.
 * Configuration used in enumeration
 */
public class EnumConfig {

    // depth of the enumeration always starts at 2
    int maxDepth = 2;
    List<ConstantVal> constValNodes = new ArrayList<>();

    Map<String, List<ConstantVal>> updateConstants = new HashMap<>();
    List<Function<List<Value>, Value>> aggrFuns = new ArrayList<>();

    // the maximum number of parameters allowed in Exists clause, if the number is 0, Exists will not be allowed
    int numberOfParam = 0;
    List<Table> existsCore = new ArrayList<>();

    Table requiredBase = null;

    // determine whether user provided constants are included in the result constValNodes
    public boolean containsDerivedConstants = false;

    public EnumConfig deepCopy() {
        List<Value> constants = new ArrayList<>();
        constants.addAll(this.getConstValues());
        Map<String, List<Value>> updateConstants = this.getUpdateConstantValues();
        List<Function<List<Value>, Value>> aggrFuns = new ArrayList<>();
        aggrFuns.addAll(this.aggrFuns);
        List<Table> existsCore = new ArrayList<>();
        existsCore.addAll(this.existsCore);
        Table requiredBase = this.requiredBase;

        return new EnumConfig(this.maxDepth, constants, updateConstants, aggrFuns, this.numberOfParam, existsCore, requiredBase);
    }

    public void setMaxDepth(int maxDepth) {
        this.maxDepth = maxDepth;
    }

    public EnumConfig(int maxDepth,
                      List<Value> constants,
                      List<Function<List<Value>, Value>> aggrFuns,
                      int numberOfParam,
                      List<Table> existsCore) {
        this(maxDepth, constants, null, aggrFuns, numberOfParam, existsCore, null);
    }

    public EnumConfig(int maxDepth,
                      List<Value> constants,
                      Map<String, List<Value>> updateConstants,
                      List<Function<List<Value>, Value>> aggrFuns,
                      int numberOfParam,
                      List<Table> existsCore) {
        this(maxDepth, constants, updateConstants, aggrFuns, numberOfParam, existsCore, null);
    }

    public EnumConfig(int maxDepth,
                      List<Value> constants,
                      Map<String, List<Value>> updateConstants,
                      List<Function<List<Value>, Value>> aggrFuns,
                      int numberOfParam,
                      List<Table> existsCore,
                      Table requiredBase) {
        this.maxDepth = maxDepth;
        this.constValNodes = constants.stream()
                .map(c -> new ConstantVal(c)).collect(Collectors.toList());
        this.updateConstants = updateConstants.entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey(),
                        e -> e.getValue().stream().map(ConstantVal::new).collect(Collectors.toList())));
        this.aggrFuns = aggrFuns;
        this.numberOfParam = numberOfParam;
        this.existsCore.addAll(existsCore);
        this.requiredBase = requiredBase;
    }

    public Map<String, List<ConstantVal>> getUpdateConstants() {
        return this.updateConstants;
    }

    public void setConstants(List<ConstantVal> constants) {
        this.constValNodes = constants;
    }

    public void setRequiredBase(Table requiredBased) {
        this.requiredBase = requiredBased;
    }
    public Table getRequiredBase() {
        return this.requiredBase;
    }

    public List<Value> getUserProvidedConstValues() {
        if (containsDerivedConstants) return new ArrayList<>();
        return this.constValNodes.stream().map(vn -> ((ConstantVal)vn).getValue()).collect(Collectors.toList());
    }
    public List<Value> getConstValues() {
        return this.constValNodes.stream().map(vn -> ((ConstantVal)vn).getValue()).collect(Collectors.toList());
    }

    public Map<String, List<Value>> getUpdateConstantValues() {
        return updateConstants.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        e -> e.getValue().stream().map(ConstantVal::getValue).collect(Collectors.toList())));
    }

    public int getNumberOfParam() { return this.numberOfParam; }
    public List<Table> getExistsCores() { return this.existsCore; }
    public List<Function<List<Value>, Value>> getAggrFuns() {
        return this.aggrFuns;
    }
    public void setAggrFunctions(Collection<Function<List<Value>, Value>> aggrFunctions) {
        this.aggrFuns = new ArrayList<>();
        aggrFuns.addAll(aggrFunctions);
    }

    public List<ConstantVal> constValNodes() {
        return this.constValNodes;
    }
    public int maxDepth() { return this.maxDepth; }
    public void addConstVals(Set<Value> vals) {
        this.constValNodes.addAll(vals.stream().map(v -> new ConstantVal(v)).collect(Collectors.toSet()));
    }
    public void setExistsCore(int numberOfParam, List<Table> existsCore) {
        this.numberOfParam = numberOfParam;
        this.existsCore.addAll(existsCore);
    }

    private class JsonConstraint {
        List<String> constants;
        Map<String, List<String>> updateConstants;
        List<String> aggregation_functions;

        public JsonConstraint(String fileContent) {
            Gson gson = new GsonBuilder().create();
            JsonConstraint p = gson.fromJson(fileContent, JsonConstraint.class);
            this.constants = p.constants;
            this.updateConstants = p.updateConstants;
            this.aggregation_functions = p.aggregation_functions;
            if (constants == null) constants = new ArrayList<>();
            if (updateConstants == null) updateConstants = new HashMap<>();
            if (aggregation_functions == null) aggregation_functions = new ArrayList<>();
        }

        public String toString() {
            String s = "";
            s += "constants: " + constants.stream().reduce(String::concat) + "\n";
            s += "updateConstants: " + updateConstants.entrySet().stream().map(Object::toString).reduce(String::concat) + "\n";
            s += "aggr: " + aggregation_functions.stream().reduce(String::concat) + "\n";
            return s;
        }
    }

    public EnumConfig(String fileContent) {
        JsonConstraint p = new JsonConstraint(fileContent);
        this.updateConstants = p.updateConstants.entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey(),
                        e -> e.getValue().stream().map(Value::parse)
                                .map(ConstantVal::new).collect(Collectors.toList())));
        this.constValNodes = p.constants.stream()
                .map(c -> new ConstantVal(Value.parse(c))).collect(Collectors.toList());
        this.aggrFuns = p.aggregation_functions.stream().map(
                x -> {
                    switch (x) {
                        case "min": return AggregationNode.AggrMin;
                        case "max": return AggregationNode.AggrMax;
                        case "avg": return AggregationNode.AggrAvg;
                        case "count": return AggregationNode.AggrCount;
                        case "count-distinct": return AggregationNode.AggrCountDistinct;
                        case "concat": return AggregationNode.AggrConcat;
                        case "sum": return AggregationNode.AggrSum;
                        case "first": return AggregationNode.AggrFirst;
                        default: return null;
                    }
                }
        ).filter(x ->  x != null).collect(Collectors.toList());

        if (this.aggrFuns.contains(AggregationNode.AggrConcat))
            aggrFuns.add(AggregationNode.AggrConcat2);
    }
}
