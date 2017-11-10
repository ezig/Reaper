package scythe_interface;

import sql.lang.Table;
import sql.lang.TableRow;
import sql.lang.datatype.NumberVal;
import sql.lang.datatype.ValType;
import sql.lang.datatype.Value;

import java.lang.annotation.Annotation;
import java.util.*;
import java.util.function.IntPredicate;
import java.util.regex.*;
import java.util.stream.Collectors;


public class ExampleTransformer {
    private enum AnnotationType {
        None, Id
    }

    private class ColumnAnnotation {
        private AnnotationType type;
        private Optional<Integer> id;
        private Pattern pattern = Pattern.compile("[^\\[\\]]*\\[id(\\d*)\\]");

        ColumnAnnotation(String colName) {
            Matcher m = pattern.matcher(colName);
            if (m.matches()) {
                type = AnnotationType.Id;
                Integer id = Integer.parseInt(m.group(1));
                this.id = Optional.of(id);
            } else {
                type = AnnotationType.None;
                this.id = null;
            }
        }

        AnnotationType getAnnotationType() {
            return this.type;
        }

        Optional<Integer> getId() {
            return this.id;
        }
    }

    private class RandomBagOfIntegers {
        private List<Integer> bag;

        RandomBagOfIntegers(IntPredicate exclude, Integer size) {
            int[] bagArr = new Random()
                    .ints(1, 100000)
                    .filter(exclude)
                    .distinct()
                    .limit(size)
                    .toArray();

            bag = Arrays.stream(bagArr).boxed().collect(Collectors.toList());
        }

        // returns n ints (or however many are left in the bag) with the guarantee
        // that the ints in the list are ordered
        SortedSet<Integer> getIntegers(Integer n) {
            Random rand = new Random();
            SortedSet<Integer> out = new TreeSet<>();

            int i = n;
            while (i > 0 && !bag.isEmpty()) {
                out.add(bag.get(0));
                bag.remove(0);
                i--;
            }

            return out;
        }
    }

    private ExampleDS exampleDS;
    private Map<String, ColumnAnnotation> annotations;
    private Integer num_ids;
    private Map<Integer, Set<Integer>> ids;
    private Map<Integer, Map<Integer, Integer>> transformation;

    public ExampleTransformer(ExampleDS exampleDS) {
        this.exampleDS = exampleDS;
    }

    // performs safety check: don't allow permutations if IDs are modified as part of query
    // and computes all column annotations and gathers all ids to be permuted
    // returns false if safety check fails
    private Boolean prepare() {
        annotations = new HashMap<>();
        ids = new HashMap<>();
        transformation = new HashMap<>();
        num_ids = 0;

        List<Table> inputs = exampleDS.inputs;
        Table tModify = exampleDS.tModify;
        Table output = exampleDS.output;

        List<TableRow> outputContent = output.getContent();

        for (Table t: inputs) {
            List<String> schema = t.getSchema();
            List<TableRow> content = t.getContent();

            for (int colNum = 0; colNum < schema.size(); colNum++) {
                String colName = schema.get(colNum);
                String key = t.getName() + '.' + colName;
                ColumnAnnotation annotation = new ColumnAnnotation(colName);
                annotations.put(key, annotation);

                if (annotation.getAnnotationType() == AnnotationType.Id) {
                    Integer idGroup = annotation.getId().get();

                    for (int rowNum = 0; rowNum < content.size(); rowNum++) {
                        Object val = content.get(rowNum).getValue(colNum).getVal();
                        assert(val instanceof Double);
                        Integer iVal = ((Double) val).intValue();

                        if (ids.get(idGroup) == null) {
                            ids.put(idGroup, new HashSet<>());
                        }
                        Set<Integer> idsSet = ids.get(idGroup);
                        if (!idsSet.contains(iVal)) {
                            idsSet.add(iVal);
                            num_ids++;
                        }

                        if (t.getName().equals(tModify.getName())) {
                            Object newVal = outputContent.get(rowNum).getValue(colNum).getVal();

                            if (!val.equals(newVal)) {
                                return false;
                            }
                        }
                    }
                }
            }
        }

        return true;
    }

    // get set of all user-provided integer constants
    // overapproximation in that we truncate all double values
    private Set<Integer> getIntegerConstants(List<Value> constants) {
        Set<Integer> out = new HashSet<>();

        for (Value constant: constants) {
            if (constant.getValType() == ValType.NumberVal) {
                Integer iVal = ((NumberVal) constant).getVal().intValue();
                out.add(iVal);
            }
        }

        return out;
    }


    private void generateTransformation() {

        Set<Integer> integerConstants = getIntegerConstants(this.exampleDS.enumConfig.getConstValues());
        IntPredicate excludeConstants = (i) -> (!integerConstants.contains(i));

        RandomBagOfIntegers bag = new RandomBagOfIntegers(excludeConstants, num_ids);

        // assign random numbers in increasing order this preserves order within id columns
        for (Integer idGroup: this.ids.keySet()) {
            Set<Integer> idSet = this.ids.get(idGroup);
            Map<Integer, Integer> setTransformation = new HashMap<>();
            SortedSet<Integer> randomInts = bag.getIntegers(idSet.size());

            Iterator<Integer> randomItr = randomInts.iterator();
            for (Integer id: idSet) {
                assert(randomItr.hasNext());
                setTransformation.put(id, randomItr.next());
            }

            this.transformation.put(idGroup, setTransformation);
        }
    }

    private Table transformTable(Table t) {
        Table transformed = t.duplicate();
        List<String> schema = transformed.getSchema();
        List<TableRow> content = transformed.getContent();

        for (int colNum = 0; colNum < schema.size(); colNum++) {
            String colName = schema.get(colNum);
            String key = t.getName() + '.' + colName;
            ColumnAnnotation annotation = this.annotations.get(key);
            if (annotation.getAnnotationType() == AnnotationType.Id) {
                Integer idGroup = annotation.getId().get();

                for (int rowNum = 0; rowNum < content.size(); rowNum++) {
                    TableRow row = content.get(rowNum);
                    Object val = row.getValue(colNum).getVal();
                    assert(val instanceof Double);
                    Integer iVal = ((Double) val).intValue();
                    NumberVal newVal = new NumberVal(this.transformation.get(idGroup).get(iVal));
                    row.setValue(colNum, newVal);
                }
            }
        }

        return transformed;
    }


    public Optional<ExampleDS> transform(ExampleDS exampleDS) {
        Boolean shouldTransform = prepare();

        if (!shouldTransform || ids.size() == 0) {
            return null;
        }

        generateTransformation();


        // perform the transform
        ExampleDS transformedExample = new ExampleDS(exampleDS);

        List<Table> transformedInputs = transformedExample.inputs;
        for (int i = 0; i < transformedInputs.size(); i++) {
            Table t = transformedInputs.get(i);
            transformedInputs.set(i, transformTable(t));
        }
        transformedExample.tModify = transformTable(transformedExample.tModify);
        transformedExample.output = transformTable(transformedExample.output);

        return Optional.of(transformedExample);
    }
}
