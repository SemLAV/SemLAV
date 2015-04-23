package semLAV;

import com.hp.hpl.jena.query.ResultSetRewindable;
import com.hp.hpl.jena.query.ResultSetFormatter;
import com.hp.hpl.jena.query.ResultSetFactory;
import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QueryExecutionFactory;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.reasoner.Reasoner;
import com.hp.hpl.jena.shared.Lock;
import com.hp.hpl.jena.rdf.model.RDFNode;

import java.util.concurrent.TimeUnit;
import java.util.HashSet;
import java.util.ArrayList;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.File;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.shared.LockMRSW;
import com.hp.hpl.jena.shared.LockSRMW;

public class QueryingStream extends Thread {

    private Model graphUnion;
    private Reasoner reasoner;
    private Query query;
    public Timer timer;
    private Timer executionTimer;
    private Counter counter;
    private BufferedWriter info;
    private BufferedWriter info2;
    private int time = 1000;
    public int lastValue = 0;
    private String dir;
    private boolean queried = false;
    private Counter ids;
    Timer wrapperTimer;
    Timer graphCreationTimer;
    HashSet<Predicate> includedViewsSet;
    public int id;
    public int tempValue;
    private int timeout;
    private boolean testing;
    private String output;
    private boolean visualization;
    private String queryStrategy;
    private int querySleepTime;
    private long queryTimeEnd = 0;
    private long statementsSleepTime;
    private long statements = 0;
    private int nbWorker = 0;

    public QueryingStream (Model gu, Reasoner r, Query q, Timer et, Timer t, 
                           Counter c, BufferedWriter i, BufferedWriter i2, String dir, Timer wrapperTimer, Timer graphCreationTimer, Counter ids, HashSet<Predicate> includedViewsSet, int timeout, boolean testing, String output, boolean v, String queryStrategy, int querySleepTime, long statementsSleepTime) {
        this.graphUnion = gu;
        this.reasoner = r;
        this.query = q;
        this.executionTimer = et;
        this.timer = t;
        this.counter = c;
        this.info = i;
        this.info2 = i2;
        this.dir = dir;
        this.wrapperTimer = wrapperTimer;
        this.graphCreationTimer = graphCreationTimer;
        this.ids = ids;
        this.includedViewsSet = includedViewsSet;
        this.timeout = timeout;
        this.testing = testing;
        this.output = output;
        this.visualization = v;
        this.queryStrategy = queryStrategy;
        this.querySleepTime = querySleepTime;
        this.statementsSleepTime = statementsSleepTime;
    }

    private void evaluateQuery() {

        long graphSize = graphUnion.size();
        boolean isLoadByTime = (queryStrategy.equals("time") && (System.currentTimeMillis() >= queryTimeEnd+querySleepTime));
        boolean isLoadBynbTriples = (queryStrategy.equals("nbTriples") && graphSize >= statements+statementsSleepTime);
        boolean isLoadByViews = (queryStrategy.equals("views") && this.counter.getValue() != this.lastValue);
        if ( (isLoadByViews || isLoadByTime || isLoadBynbTriples) && nbWorker < 5) {
            nbWorker++;
            long start = System.currentTimeMillis();
            if(isLoadByTime) {
                System.out.println("query run with time");
                queryTimeEnd = start+querySleepTime;
            }
            if(isLoadBynbTriples) {
                System.out.println("query run with nb of triples");
                statements = graphSize+statementsSleepTime;
            }
            tempValue = this.counter.getValue();
            id = this.ids.getValue();
            int myId = id;
            int myTempValue = tempValue;
            this.ids.increase();
            String fileName = "";
            if (testing && !visualization) {
                fileName = this.dir + "/solution"+id;
            } else if (!testing) {
                fileName = output;
            }
            lastValue = tempValue;
            QueryingStreamWorker worker = new QueryingStreamWorker(this, nbWorker, graphUnion, reasoner, testing, visualization, fileName, executionTimer, query, myId, myTempValue);
            Thread thread = new Thread(worker);
            thread.start();
        }
    }

    public synchronized void evaluateConstructQuery(QueryExecution result, String fileName) throws java.io.IOException {
        Model m = result.execConstruct();
        //executionTimer.stop();
        //timer.stop();
        OutputStream out = new FileOutputStream(fileName);
        m.write(out, "N-TRIPLE");
        out.close();
        //executionTimer.stop();
        //timer.stop();
    }

    public synchronized void evaluateDescribeQuery(QueryExecution result, String fileName) throws java.io.IOException {
        Model m = result.execDescribe();
        //executionTimer.stop();
        //timer.stop();
        OutputStream out = new FileOutputStream(fileName);
        m.write(out, "N-TRIPLE");
        out.close();
        //executionTimer.resume();
        //timer.resume();
    }

    public synchronized void evaluateAskQuery(QueryExecution result, String fileName) throws java.io.IOException {
        boolean b = result.execAsk();
        //executionTimer.stop();
        //timer.stop();
        BufferedWriter output = new BufferedWriter(new OutputStreamWriter(
                                      new FileOutputStream(fileName), "UTF-8"));
        output.write(Boolean.toString(b));
        output.flush();
        output.close();
        //executionTimer.resume();
        //timer.resume();
    }

    public synchronized int evaluateSelectQuery(QueryExecution result, String fileName, int id, int tempValue, boolean testing) throws java.io.IOException {

        executionTimer.stop();
        timer.stop();
        int i = 0;

        if (visualization) {
            //FileOutputStream output = new FileOutputStream(fileName);
            executionTimer.resume();
            timer.resume();
            ResultSet rs = result.execSelect();
            ResultSetRewindable rsr = ResultSetFactory.makeRewindable(rs);
            i = rsr.size();
            /* ResultSetFormatter.outputAsCSV(output, rsr);
            executionTimer.stop();
            timer.stop();
            if (!queried && testing && i > 0) {
                message(id + "\t" + tempValue + "\t" + TimeUnit.MILLISECONDS.toMillis(wrapperTimer.getTotalTime())
                            + "\t" + TimeUnit.MILLISECONDS.toMillis(graphCreationTimer.getTotalTime())
                            + "\t" + TimeUnit.MILLISECONDS.toMillis(executionTimer.getTotalTime())
                            + "\t" + TimeUnit.MILLISECONDS.toMillis(timer.getTotalTime())
                            + "\t" + graphUnion.size()
                            + "\t1");
                time = 10;
                queried = true;
            }
            executionTimer.resume();
            timer.resume();*/
        } else {
            BufferedWriter output = new BufferedWriter(new OutputStreamWriter(
                                                new FileOutputStream(fileName), "UTF-8"));
            executionTimer.resume();
            timer.resume();
            for (ResultSet rs = result.execSelect(); rs.hasNext();) {
                QuerySolution binding = rs.nextSolution();
                ArrayList<String> s = new ArrayList<String>();
                for (String var : query.getResultVars()) { 
                    RDFNode n = binding.get(var);
                    String val = null;
                    if (n != null) {
                        val = n.toString();
                    }
                    s.add(val);
                }
                executionTimer.stop();
                timer.stop();
                
                if (!queried && testing) {
                    message(id + "\t" + tempValue + "\t" + TimeUnit.MILLISECONDS.toMillis(wrapperTimer.getTotalTime())
                            + "\t" + TimeUnit.MILLISECONDS.toMillis(graphCreationTimer.getTotalTime())
                            + "\t" + TimeUnit.MILLISECONDS.toMillis(executionTimer.getTotalTime())
                            + "\t" + TimeUnit.MILLISECONDS.toMillis(timer.getTotalTime())
                            + "\t" + graphUnion.size()
                            + "\t1");
                    time = 10;
                    queried = true;
                }
                output.write(s.toString());
                output.newLine();
                executionTimer.resume();
                timer.resume();
                if (TimeUnit.MILLISECONDS.toMillis(timer.getTotalTime()) >= timeout) {
                    System.out.println("-->end");
                    break;
                }
            }
            executionTimer.stop();
            timer.stop();
            output.flush();
            output.close();
            executionTimer.resume();
            timer.resume();
        }
        return i;
    }

    public synchronized void message(String s) {
        synchronized(timer) {
            timer.stop();
            try {
                info.write(s);
                info.newLine();
                info.flush();
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
            timer.resume();
        }
    }

    public synchronized void message2(String s) {
        synchronized(timer) {
            timer.stop();
            try {
                info2.write(s);
                info2.newLine();
                info2.flush();
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
            timer.resume();
        }
    }

    public static long getModelSize(String tmpFile, Model m) {
        try {
            OutputStream out = new FileOutputStream("/tmp/"+tmpFile);
            if(evaluateQueryThreaded.lockType().equals("SRMW"))
                m.enterCriticalSection(LockSRMW.READ);
            else
                m.enterCriticalSection(LockMRSW.READ);
            m.write(out, "N-TRIPLE");
            m.leaveCriticalSection();
            File f = new File("/tmp/"+tmpFile);
            long size = f.length();
            f.delete();
            out.close();
            return size;
        } catch (IOException ioe) {
            ioe.printStackTrace();
            System.exit(1);
        }
        return 0;
    }

    public void run () {

            Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    evaluateQuery();
                    if (testing) {
                    long size = getModelSize("unionGraph.n3", graphUnion);
                    message("# Graph Size in bytes: "+size);
                    try {
                        info.flush();
                        info.close();
                    } catch (IOException ioe) {
                        ioe.printStackTrace();
                        System.exit(1);
                    }
                    }
                }
            });
            while (!((timeout > 0 && TimeUnit.MILLISECONDS.toMillis(timer.getTotalTime()) >= timeout) || this.isInterrupted())) {
                if (testing) {
                    evaluateQuery();
                }
            }
            System.out.println("endQuery");

    }
}
