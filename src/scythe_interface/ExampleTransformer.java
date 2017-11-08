package scythe_interface;

import sql.lang.Table;
import sql.lang.TableRow;
import sql.lang.datatype.NumberVal;

import java.util.*;

public class ExampleTransformer {
    private enum ColumnAnnotation {
        None, Id
    }

    private static ColumnAnnotation getColumnAnnotation(String colName) {
        int open = colName.indexOf('[');
        int close = colName.indexOf(']');

        if (open != -1 && open < close) {
            String annotation = colName.substring(open + 1, close);
            if (annotation.equals("id")) {
                return ColumnAnnotation.Id;
            }
        }
        return ColumnAnnotation.None;
    }

    // safety check: don't allow permutations if IDs are modified as part of query
    private static Boolean shouldTransform(ExampleDS exampleDS) {
        List<Table> inputs = exampleDS.inputs;
        Table tModify = exampleDS.tModify;
        Table output = exampleDS.output;

        List<String> tModifySchema = tModify.getSchema();
        List<TableRow> tModifyContent = tModify.getContent();
        List<TableRow> outputContent = output.getContent();

        for (int colNum = 0; colNum < tModifySchema.size(); colNum++) {
            String colName = tModifySchema.get(colNum);
            if (getColumnAnnotation(colName) == ColumnAnnotation.Id) {
                for (int rowNum = 0; rowNum < tModifyContent.size(); rowNum++) {
                    Object oldVal = tModifyContent.get(rowNum).getValue(colNum).getVal();
                    Object newVal = outputContent.get(rowNum).getValue(colNum).getVal();
                    if (!oldVal.equals(newVal)) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    // returns sorted set of all values in columns with ID annotation
    private static SortedSet<Integer> getIds(ExampleDS exampleDS) {
        List<Table> inputs = exampleDS.inputs;
        Table tModify = exampleDS.tModify;
        Table output = exampleDS.output;

        SortedSet<Integer> ids = new TreeSet<>();

        for (Table t: inputs) {
            List<String> schema = t.getSchema();
            List<TableRow> content = t.getContent();
            for (int colNum = 0; colNum < schema.size(); colNum++) {
                String colName = schema.get(colNum);
                if (getColumnAnnotation(colName) == ColumnAnnotation.Id) {
                    for (int rowNum = 0; rowNum < content.size(); rowNum++) {
                        Object val = content.get(rowNum).getValue(colNum);
                        assert(val instanceof NumberVal);
                        ids.add(((NumberVal) val).getVal().intValue());
                    }
                }
            }
        }

        return ids;
    }

    private static Map<Integer, Integer> generateTransformation(SortedSet<Integer> ids) {
        int[] permuted = new Random()
                .ints(1, 10000)
                .distinct()
                .limit(ids.size())
                .toArray();

        Arrays.sort(permuted);

        Map<Integer, Integer> transformation = new HashMap<>();

        int i = 0;
        for (Integer id: ids) {
            transformation.put(id, permuted[i]);
            i++;
        }

        return transformation;
    }

    private static Table transformTable(Table t, Map<Integer, Integer> transformation) {
        Table transformed = t.duplicate();
        List<String> schema = transformed.getSchema();
        List<TableRow> content = transformed.getContent();

        for (int colNum = 0; colNum < schema.size(); colNum++) {
            String colName = schema.get(colNum);
            if (getColumnAnnotation(colName) == ColumnAnnotation.Id) {
                for (int rowNum = 0; rowNum < content.size(); rowNum++) {
                    TableRow row = content.get(rowNum);
                    Object val = row.getValue(colNum);
                    assert(val instanceof NumberVal);
                    Integer iVal = ((NumberVal) val).getVal().intValue();
                    NumberVal newVal = new NumberVal(transformation.get(iVal));
                    row.setValue(colNum, newVal);
                }
            }
        }

        return transformed;
    }


    public static Optional<ExampleDS> transform(ExampleDS exampleDS) {
        List<Table> inputs = exampleDS.inputs;
        Table tModify = exampleDS.tModify;
        Table output = exampleDS.output;

        if (!shouldTransform(exampleDS)) {
            return null;
        }
        SortedSet<Integer> ids = getIds(exampleDS);

        if (ids.size() == 0) {
            return null;
        }

        Map<Integer, Integer> transformation = generateTransformation(ids);

        // perform the transform
        ExampleDS permutedExample = new ExampleDS(exampleDS);

        List<Table> transformedInputs = permutedExample.inputs;
        for (int i = 0; i < transformedInputs.size(); i++) {
            Table t = transformedInputs.get(i);
            transformedInputs.set(i, transformTable(t, transformation));
        }
        permutedExample.tModify = transformTable(permutedExample.tModify, transformation);
        permutedExample.output = transformTable(permutedExample.output, transformation);

        return Optional.of(permutedExample);
    }
}
