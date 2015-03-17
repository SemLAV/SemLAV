package semLAV;

import com.hp.hpl.jena.graph.Triple;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.shared.Lock;
import com.hp.hpl.jena.rdf.model.StmtIterator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

/**
 * Created by Maxime on 08/03/15.
 */
public class IncludingStreamV3Worker implements Runnable {

    private IncludingStreamV3Pool pool;
    private int i;

    public IncludingStreamV3Worker(IncludingStreamV3Pool pool, int i) {
        this.pool = pool;
        this.i = i;
    }

    @Override
    public void run() {
        pool.runNow[i] = true;
        Triple k = pool.keys[i];
        ArrayList<Predicate> rvs = pool.buckets.get(k);
        if (pool.current[i] < rvs.size()) {
            Predicate view = rvs.get(pool.current[i]);
            pool.current[i] = pool.current[i] + 1;
            if (evaluateQueryThreaded.include(pool.includedViewsSet, view, pool.constants)) {
        try {
            System.out.println(Thread.currentThread().getName()+" :including view: "+view);
            long start = System.currentTimeMillis();
            Model tmp =  pool.catalog.getModel(view, pool.constants);
            pool.wrapperTimer.addTime(System.currentTimeMillis()-start);

            System.out.println(Thread.currentThread().getName()+" :temporal model size: "+tmp.size());
            StmtIterator it = tmp.listStatements();
            while(it.hasNext()) {
                Statement stmt = it.nextStatement();
                try {
                    pool.graphUnion.enterCriticalSection(Lock.WRITE);
                    start = System.currentTimeMillis();
                    pool.graphUnion.add(stmt);
                    pool.graphCreationTimer.addTime(System.currentTimeMillis() - start);
                    System.out.println(Thread.currentThread().getName()+" : new truple added");
                } finally {
                    pool.graphUnion.leaveCriticalSection();
                }
            }
            pool.includedViews.increase();
        } catch (OutOfMemoryError oome) {
            pool.workerError(i, true);
        } catch (com.hp.hpl.jena.n3.turtle.TurtleParseException tpe) {
            pool.workerError(i, false);
        }
            }
        } else {
            pool.finished[i] = true;
        }
        pool.runNow[i] = false;
    }
}
