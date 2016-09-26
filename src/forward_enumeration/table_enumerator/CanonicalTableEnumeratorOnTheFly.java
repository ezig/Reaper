package forward_enumeration.table_enumerator;

import forward_enumeration.context.EnumContext;
import forward_enumeration.container.QueryContainer;
import forward_enumeration.canonical_enum.datastructure.TableTreeNode;
import forward_enumeration.canonical_enum.components.EnumAggrTableNode;
import forward_enumeration.canonical_enum.components.EnumFilterNamed;
import forward_enumeration.canonical_enum.components.EnumJoinTableNodes;
import forward_enumeration.canonical_enum.components.EnumProjection;
import sql.lang.Table;
import sql.lang.ast.table.TableNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by clwang on 3/31/16.
 * Perform enumeration based on SynthSQL grammar, and enumeration results are stored into query chests on the fly.
 */
public class CanonicalTableEnumeratorOnTheFly extends AbstractTableEnumerator {

    @Override
    public List<TableNode> enumTable(EnumContext ec, int depth) {

        List<TableNode> result = new ArrayList<>();

        QueryContainer qc = QueryContainer.initWithInputTables(ec.getInputs(), QueryContainer.ContainerType.TableLinks);

        enumTableWithoutProj(ec, qc, depth); // all intermediate result are stored in qc

        ec.setTableNodes(qc.getRepresentativeTableNodes());
        EnumProjection.emitEnumProjection(ec, ec.getOutputTable(), qc);

        // this is not always enabled, as this export algorithm is only designed for canonical SQL enumerator
        if (qc.getContainerType() == QueryContainer.ContainerType.TableLinks) {

            Set<Table> leafNodes = new HashSet<>();
            leafNodes.addAll(ec.getInputs());
            List<TableTreeNode> trees = qc.getTableLinks().findTableTrees(ec.getOutputTable(), leafNodes, 4);

            int totalQueryCount = 0;
            for (TableTreeNode t : trees) {
                t.inferQuery(ec);
                result.addAll(t.treeToQuery());

                int count = t.countQueryNum();
                //System.out.println("Queries corresponds to this tree: " + count);
                totalQueryCount += count;
            }

            System.out.println("Total Tree Count: " + trees.size());
            System.out.println("Total Query Count: " + totalQueryCount);
        }

        return result;
    }

    public static void enumTableWithoutProj(EnumContext ec, QueryContainer qc, int depth) {

        ec.setTableNodes(qc.getRepresentativeTableNodes());
        EnumFilterNamed.emitEnumFilterNamed(ec, qc);

        System.out.println("[Stage 1] EnumFilterNamed: \n\t"
                + "Total Table by now: " + qc.getRepresentativeTableNodes().size());

        ec.setTableNodes(qc.getRepresentativeTableNodes());
        EnumAggrTableNode.emitEnumAggrNodeWFilter(ec, qc);

        System.out.println("[Stage 2] EnumAggregationNode: \n\t"
                + "Total Table by now: " + qc.getRepresentativeTableNodes().size());

        for (int i = 1; i <= depth; i ++) {
            //System.out.println("[Level] " + i);

            ec.setTableNodes(qc.getRepresentativeTableNodes());

            // check whether a result can be obtained by projection
            List<TableNode> tns = EnumProjection.enumProjection(ec, ec.getOutputTable());
            if (! tns.isEmpty())
                break;

            EnumJoinTableNodes.emitEnumJoinWithFilter(ec, qc);
            //System.out.println("There are " + tns.size() + " queries in the enumeration of this level");
            //List<TableNode> renamed = tns.parallelStream().map(tn -> RenameTNWrapper.tryRename(tn)).collect(Collectors.toList());
            // System.out.println("After renamed: " + renamed.size());
            //qc.insertQueries(renamed);

            //System.out.println("after enumJoinWithFilter: " + qc.getRepresentativeTableNodes().size() + " tables");
            System.out.println("[Stage " + (2 + i) + "] EnumJoin" + i + " \n\t"
                   // + "Tables generated: " + (qc.tracked.size()) + "\n\t"
                    + "Total Table by now: " + qc.getRepresentativeTableNodes().size());
        }
    }
}