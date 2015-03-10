package semLAV;

import com.hp.hpl.jena.graph.Triple;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.shared.Lock;

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
        Triple k = pool.keys[i];
        ArrayList<Predicate> rvs = pool.buckets.get(k);
        if (pool.current[i] < rvs.size()) {
            Predicate view = rvs.get(pool.current[i]);
            pool.current[i] = pool.current[i] + 1;
            if (evaluateQueryThreaded.include(pool.includedViewsSet, view, pool.constants)) {
        pool.graphUnion.enterCriticalSection(Lock.WRITE);
        try {
            System.out.println("including view: "+view);
            long start = System.currentTimeMillis();
            Model tmp =  pool.catalog.getModel(view, pool.constants);
            pool.wrapperTimer.addTime(System.currentTimeMillis()-start);
            System.out.println("temporal model size: "+tmp.size());
            start = System.currentTimeMillis();
            pool.graphUnion.add(tmp);
            pool.graphCreationTimer.addTime(System.currentTimeMillis() - start);
            pool.includedViews.increase();
        } catch (OutOfMemoryError oome) {
            pool.workerError(i, true);
        } catch (com.hp.hpl.jena.n3.turtle.TurtleParseException tpe) {
            pool.workerError(i, false);
        } finally {
            pool.graphUnion.leaveCriticalSection();
        }
            }
        } else {
            pool.finished[i] = true;
        }
        pool.runNow[i] = false;
    }
}
