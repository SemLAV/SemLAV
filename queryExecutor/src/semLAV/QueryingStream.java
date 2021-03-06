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
    private Timer timer;
    private Timer executionTimer;
    private Counter counter;
    private BufferedWriter info;
    private BufferedWriter info2;
    private int time = 1000;
    private int lastValue = 0;
    private String dir;
    private boolean queried = false;
    private Counter ids;
    Timer wrapperTimer;
    Timer graphCreationTimer;
    HashSet<Predicate> includedViewsSet;
    private int id;
    private int tempValue;
    private int timeout;
    private boolean testing;
    private String output;
    private boolean visualization;
    //private String queryStrategy;
    private boolean viewStrategy = false;
    private boolean timeStrategy = false;
    private boolean dataStrategy = false;
    private int querySleepTime;
    private long queryTimeEnd = 0;
    private long statementsSleepTime;
    private long statements = 0;
    private boolean firstResult = false;
    private boolean useAsk = false;
    private long firstResultTime;

    public QueryingStream (Model gu, Reasoner r, Query q, Timer et, Timer t, 
                           Counter c, BufferedWriter i, BufferedWriter i2, String dir, 
                           Timer wrapperTimer, Timer graphCreationTimer, Counter ids, 
                           HashSet<Predicate> includedViewsSet, int timeout, boolean testing, 
                           String output, boolean v, boolean viewStrategy, boolean timeStrategy, 
                           boolean dataStrategy, int querySleepTime, 
                           long statementsSleepTime, boolean ua) {
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
        //this.queryStrategy = queryStrategy;
        this.viewStrategy = viewStrategy;
        this.timeStrategy = timeStrategy;
        this.dataStrategy = dataStrategy;
        this.querySleepTime = querySleepTime;
        this.statementsSleepTime = statementsSleepTime;
        this.useAsk = ua;
        this.statements = 0;
        this.queryTimeEnd = 0;
    }

    private void evaluateQuery() {

        long graphSize = graphUnion.size();
        //boolean isLoadByTime = (queryStrategy.equals("time") && (System.currentTimeMillis() >= queryTimeEnd+querySleepTime));
        //boolean isLoadBynbTriples = (queryStrategy.equals("nbTriples") && graphSize >= statements+statementsSleepTime);
        //boolean isLoadByViews = (queryStrategy.equals("views") && this.counter.getValue() != this.lastValue);
        //if (isLoadByViews || isLoadByTime || isLoadBynbTriples) {
        boolean loadByViewOkay = !viewStrategy || (this.counter.getValue() != this.lastValue);
        timer.stop();
        long t = this.timer.getTotalTime();
        timer.resume();
        boolean loadByTimeOkay = !timeStrategy || (t >= queryTimeEnd+querySleepTime);
        boolean loadByDataOkay = !dataStrategy || (graphSize >= statements+statementsSleepTime);
        if ((loadByViewOkay && loadByTimeOkay && loadByDataOkay)) { // || this.isInterrupted()) {
            long start = t;

            Model m = graphUnion;
            if (reasoner != null) {
                m = ModelFactory.createInfModel (reasoner, m);
            }

            //if(isLoadByTime) {
            if (timeStrategy) {
                //System.out.println("query run with time");
                queryTimeEnd = start;
            }
            //if(isLoadBynbTriples) {
            if (dataStrategy) {
                //System.out.println("query run with nb of triples");
                statements = graphSize;
            }

            if(evaluateQueryThreaded.lockType().equals("SRMW"))
                m.enterCriticalSection(LockSRMW.READ);
            else
                m.enterCriticalSection(LockMRSW.READ);

            executionTimer.resume();

                boolean runQuery = true;

                if(!firstResult && useAsk && query.isSelectType()) {
                    String q = query.toString();
                    q = q.replace("\n", " ");
                    q = q.replaceAll("SELECT(.*)WHERE","ASK WHERE");
                    Query selectToAsk = QueryFactory.create(q);
                    QueryExecution r = QueryExecutionFactory.create(selectToAsk, m);
                    runQuery = r.execAsk();
                    if(runQuery) {
                        firstResult = true;
                        //System.out.println("ok" + (System.currentTimeMillis()-firstResultTime));
                    } //else
                    //System.out.println("ko");
                        
                }

                executionTimer.stop();

                 m.leaveCriticalSection();

if(runQuery) {
            tempValue = this.counter.getValue();
            id = this.ids.getValue();
            this.ids.increase();
            String fileName = "";
            if (testing && !visualization) {
                fileName = this.dir + "/solution"+id;
            } else if (!testing) {
                fileName = output;
            }
            try {

           

                
                    if(evaluateQueryThreaded.lockType().equals("SRMW"))
                m.enterCriticalSection(LockSRMW.READ);
            else
                m.enterCriticalSection(LockMRSW.READ);

                    executionTimer.resume();
                    QueryExecution result = QueryExecutionFactory.create(query, m);

                    int n = 0;
                    if (query.isSelectType()) {
                        n = evaluateSelectQuery(result, fileName, id, tempValue, testing);
                    } else if (query.isConstructType()) {
                        evaluateConstructQuery(result, fileName);
                    } else if (query.isDescribeType()) {
                        evaluateDescribeQuery(result, fileName);
                    } else if (query.isAskType()) {
                        evaluateAskQuery(result, fileName);
                    }

                    executionTimer.stop();
                    m.leaveCriticalSection();
                    timer.stop();
                    System.out.println("query duration " + (this.timer.getTotalTime() - start));

                    if (testing) {
                        String includedViewsStr = "";
                        synchronized (includedViewsSet) {
                            includedViewsStr = includedViewsSet.toString();
                        }
                        if (visualization) {
                            message2(n + "," + tempValue + "," + TimeUnit.MILLISECONDS.toMillis(timer.getTotalTime()));
                        } else {
                            message(id + "\t" + tempValue + "\t" + TimeUnit.MILLISECONDS.toMillis(wrapperTimer.getTotalTime())
                                    + "\t" + TimeUnit.MILLISECONDS.toMillis(graphCreationTimer.getTotalTime())
                                    + "\t" + TimeUnit.MILLISECONDS.toMillis(executionTimer.getTotalTime())
                                    + "\t" + TimeUnit.MILLISECONDS.toMillis(timer.getTotalTime())
                                    + "\t" + graphUnion.size() + "\t" + includedViewsStr);
                        }
                    }
                    timer.resume();
                    this.lastValue = tempValue;
        
            } catch (java.io.IOException ioe) {
                System.err.println("problems writing to "+fileName);
            } catch (java.lang.OutOfMemoryError oome) {
                executionMCDSATThreaded.deleteDir(new File(fileName));
                System.out.println("out of memory while querying");
            }
                    }
        }
    }

    private void evaluateConstructQuery(QueryExecution result, String fileName) throws java.io.IOException {
        Model m = result.execConstruct();
        executionTimer.stop();
        timer.stop();
        OutputStream out = new FileOutputStream(fileName);
        m.write(out, "N-TRIPLE");
        out.close();
        executionTimer.stop();
        timer.stop();
    }

    private void evaluateDescribeQuery(QueryExecution result, String fileName) throws java.io.IOException {
        Model m = result.execDescribe();
        executionTimer.stop();
        timer.stop();
        OutputStream out = new FileOutputStream(fileName);
        m.write(out, "N-TRIPLE");
        out.close();
        executionTimer.resume();
        timer.resume();
    }

    private void evaluateAskQuery(QueryExecution result, String fileName) throws java.io.IOException {
        boolean b = result.execAsk();
        executionTimer.stop();
        timer.stop();
        BufferedWriter output = new BufferedWriter(new OutputStreamWriter(
                                      new FileOutputStream(fileName), "UTF-8"));
        output.write(Boolean.toString(b));
        output.flush();
        output.close();
        executionTimer.resume();
        timer.resume();
    }

    private int evaluateSelectQuery(QueryExecution result, String fileName, int id, int tempValue, boolean testing) throws java.io.IOException {

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
                    //System.out.println("-->end");
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

    private void message(String s) {
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

    private void message2(String s) {
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
            firstResultTime = System.currentTimeMillis();
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
                    try {
                        Thread.sleep(1);
                    } catch (Exception e) {
                        //System.out.println("Querying received exception");
                        //e.printStackTrace();
                    }
                }
            }
            //System.out.println("endQuery");

    }
}
