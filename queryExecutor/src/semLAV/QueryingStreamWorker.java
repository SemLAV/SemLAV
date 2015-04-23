package semLAV;

import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QueryExecutionFactory;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.shared.LockMRSW;
import com.hp.hpl.jena.shared.LockSRMW;

import java.io.File;
import java.util.concurrent.TimeUnit;
import com.hp.hpl.jena.reasoner.Reasoner;

/**
 * Created by Maxime on 23/04/15.
 */
public class QueryingStreamWorker implements Runnable {

    QueryingStream pool;
    int nbWorker;
    Model graphUnion;
    Reasoner reasoner;
    boolean testing;
    boolean visualization;
    String fileName;
    Timer executionTimer;
    Query query;
    int id;
    int tempValue;

    public QueryingStreamWorker(QueryingStream pool, int nbWorker, Model graphUnion, Reasoner reasoner, boolean testing, boolean visualization, String fileName, Timer executionTimer, Query query, int id, int tempValue) {
        this.pool = pool;
        this.nbWorker = nbWorker;
        this.graphUnion = graphUnion;
        this.reasoner = reasoner;
        this.testing = testing;
        this.visualization = visualization;
        this.fileName = fileName;
        this.executionTimer = executionTimer;
        this.query = query;
        this.id = id;
        this.tempValue = tempValue;
    }

    @Override
    public void run() {

        Model m = graphUnion;
        if (reasoner != null) {
            m = ModelFactory.createInfModel(reasoner, m);
        }

        if(evaluateQueryThreaded.lockType().equals("SRMW"))
            m.enterCriticalSection(LockSRMW.READ);
        else
            m.enterCriticalSection(LockMRSW.READ);


        try {

            long start = System.currentTimeMillis();
            QueryExecution result = QueryExecutionFactory.create(query, m);
            int n = 0;
            if (query.isSelectType()) {
                n = pool.evaluateSelectQuery(result, fileName, id, tempValue, testing);
            } else if (query.isConstructType()) {
                pool.evaluateConstructQuery(result, fileName);
            } else if (query.isDescribeType()) {
                pool.evaluateDescribeQuery(result, fileName);
            } else if (query.isAskType()) {
                pool.evaluateAskQuery(result, fileName);
            }

            executionTimer.addTime(System.currentTimeMillis()-start);
            m.leaveCriticalSection();
            //timer.stop();
            System.out.println("query duration "+(System.currentTimeMillis()-start));

            if (testing) {
                String includedViewsStr = "";
                synchronized(pool.includedViewsSet) {
                    includedViewsStr = pool.includedViewsSet.toString();
                }
                if (visualization) {
                    pool.message2(n + "," + tempValue + "," + TimeUnit.MILLISECONDS.toMillis(pool.timer.getTotalTime()));
                } else {
                    pool.message(id + "\t" + tempValue + "\t" + TimeUnit.MILLISECONDS.toMillis(pool.wrapperTimer.getTotalTime())
                            + "\t" + TimeUnit.MILLISECONDS.toMillis(pool.graphCreationTimer.getTotalTime())
                            + "\t" + TimeUnit.MILLISECONDS.toMillis(executionTimer.getTotalTime())
                            + "\t" + TimeUnit.MILLISECONDS.toMillis(pool.timer.getTotalTime())
                            + "\t" + graphUnion.size() + "\t" + includedViewsStr);
                }
            }
            //timer.resume();

        } catch (java.io.IOException ioe) {
            System.err.println("problems writing to "+fileName);
        } catch (java.lang.OutOfMemoryError oome) {
            executionMCDSATThreaded.deleteDir(new File(fileName));
            System.out.println("out of memory while querying");
        }
        nbWorker--;
    }
}
