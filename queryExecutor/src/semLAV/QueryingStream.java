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
    private String queryStrategy;
    private int querySleepTime;
    private long queryTimeEnd = 0;

    public QueryingStream (Model gu, Reasoner r, Query q, Timer et, Timer t, 
                           Counter c, BufferedWriter i, BufferedWriter i2, String dir, Timer wrapperTimer, Timer graphCreationTimer, Counter ids, HashSet<Predicate> includedViewsSet, int timeout, boolean testing, String output, boolean v, String queryStrategy, int querySleepTime) {
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
    }

    private void evaluateQuery() {


        boolean isLoadByTime = (queryStrategy.equals("time") && (System.currentTimeMillis() >= queryTimeEnd));
        if ( (this.counter.getValue() != this.lastValue) || isLoadByTime) {
            long start = System.currentTimeMillis();
            if(isLoadByTime)
                queryTimeEnd = System.currentTimeMillis()+querySleepTime;
            Model m = graphUnion;
            if (reasoner != null) {
                m = ModelFactory.createInfModel (reasoner, m);
            }
            if(isLoadByTime)
                System.out.println("run with timeout");
            if(evaluateQueryThreaded.lockType.equals("SRMW"))
                m.enterCriticalSection(LockSRMW.READ);
            else
                m.enterCriticalSection(LockMRSW.READ);
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
                System.out.println("query duration "+(System.currentTimeMillis()-start));

            if (testing) {
                String includedViewsStr = "";
                synchronized(includedViewsSet) {
                    includedViewsStr = includedViewsSet.toString();
                }
                if (visualization) {
                    message2(n+","+tempValue+","+TimeUnit.MILLISECONDS.toMillis(timer.getTotalTime()));
                } else {
                    message(id + "\t" + tempValue + "\t" + TimeUnit.MILLISECONDS.toMillis(wrapperTimer.getTotalTime()) 
                                            + "\t" + TimeUnit.MILLISECONDS.toMillis(graphCreationTimer.getTotalTime())
                                            + "\t" + TimeUnit.MILLISECONDS.toMillis(executionTimer.getTotalTime())
                                            + "\t" +  TimeUnit.MILLISECONDS.toMillis(timer.getTotalTime())
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
            if(evaluateQueryThreaded.lockType.equals("SRMW"))
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
