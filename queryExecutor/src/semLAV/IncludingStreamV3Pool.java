package semLAV;

import com.hp.hpl.jena.graph.Triple;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.shared.Lock;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
//import java.util.concurrent.ThreadPoolExecutor;
//import java.util.concurrent.LinkedBlockingQueue;
// This version includes views considering the arguments
public class IncludingStreamV3Pool extends Thread {

     Model graphUnion;
     Counter includedViews;
     Catalog catalog;
    HashMap<String, String> constants;
    Timer wrapperTimer;
    Timer graphCreationTimer;
    Timer executionTimer;
    Timer totalTimer;
    BufferedWriter info2;
    Counter ids;
    HashSet<Predicate> includedViewsSet;
    HashMap<Triple,ArrayList<Predicate>> buckets;
    int[] current;
    Triple[] keys;
    boolean testing;
    boolean[] finished;
    List[] runNow;
    ExecutorService executor;
    //ThreadPoolExecutor executor;
    boolean finish = false;
    boolean isReseting = false;
    int nbWorker;
    int nbTripleByLock;

    public IncludingStreamV3Pool(HashMap<Triple, ArrayList<Predicate>> buckets, Model gu, Counter iv, Catalog c,
                                 HashMap<String, String> cs, Timer wrapperTimer,
                                 Timer graphCreationTimer, Timer executionTimer,
                                 Timer totalTimer, BufferedWriter info2, Counter ids, HashSet<Predicate> includedViewsSet,
                                 boolean testing, int nbWoker, int nbTripleByLock) {
        this.nbWorker = nbWoker;
        this.nbTripleByLock = nbTripleByLock;
        this.buckets = buckets;
        this.graphUnion = gu;
        this.includedViews = iv;
        this.catalog = c;
        this.constants = cs;
        this.wrapperTimer = wrapperTimer;
        this.graphCreationTimer = graphCreationTimer;
        this.executionTimer = executionTimer;
        this.totalTimer = totalTimer;
        this.info2 = info2;
        this.ids = ids;
        this.includedViewsSet = includedViewsSet;
        Set<Triple> ks = this.buckets.keySet();
        int n = ks.size();
        this.current = new int[n];
        this.keys = new Triple[n];
        int i = 0;
        for (Triple k : ks) {
            this.keys[i] = k;
            this.current[i] = 0;
            i++;
        }
        this.finished = new boolean[keys.length];
        this.runNow = new List[keys.length];
        this.testing = testing;

        executor = Executors.newFixedThreadPool(nbWoker);
        //executor = new ThreadPoolExecutor(keys.length, nbWoker, 5000, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>());
        for (int j = 0; j < keys.length; j++) {
            finished[j] = false;
            runNow[j] = Collections.synchronizedList(new ArrayList<Integer>());
        }
    }

    public void reset() {
        if (testing) {
        message(this.ids.getValue() + "\t" + this.includedViews.getValue() + "\t"
                                              + TimeUnit.MILLISECONDS.toMillis(wrapperTimer.getTotalTime())
                                              + "\t" + TimeUnit.MILLISECONDS.toMillis(graphCreationTimer.getTotalTime())
                                              + "\t" + TimeUnit.MILLISECONDS.toMillis(executionTimer.getTotalTime())
                                              + "\t" +  TimeUnit.MILLISECONDS.toMillis(totalTimer.getTotalTime())
                                              + "\t" + graphUnion.size());
        wrapperTimer.start();
        graphCreationTimer.start();
        executionTimer.start();
        includedViews.reset();
        }
    }

    private void message(String s) {
        synchronized(totalTimer) {
            totalTimer.stop();
            try {
                info2.write(s);
                info2.newLine();
                info2.flush();
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
            totalTimer.resume();
        }
    }

    protected void workerError(int i, boolean outOfMemmory) {
        isReseting = true;
        //executor.shutdownNow();
        myInterrupt();
        executor = Executors.newFixedThreadPool(this.nbWorker);
        //executor = new ThreadPoolExecutor(keys.length, nbWorker, 5000, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>()); 
        for (int j = 0; j < keys.length; j++) {
            runNow[j] = Collections.synchronizedList(new ArrayList<Integer>());
        }
        this.current[i] = this.current[i] - 1;
        reset();
        graphUnion.removeAll();
        includedViewsSet = new HashSet<Predicate>();
        totalTimer.stop();
        if(outOfMemmory)
            System.out.println("out of memory error");
        else
            System.out.println("jena exception");
        totalTimer.resume();
        // Should also reset all the ones that are empty
        for (int j = 0; j != i && j < keys.length; j++) {
            if (finished[j]) {
                this.current[j] = 0;
                finished[j] = false;
                totalTimer.stop();
                //System.out.println("reset subgoal "+j);
                totalTimer.resume();
            }
        }
        isReseting = false;
    }

    public void run () {

    try {
            while (!(finish || this.isInterrupted())) {
                while (isReseting) {}
                HashMap<Triple,Integer> processedViews = new HashMap<Triple, Integer>();
                for (Triple k : keys) {
                    processedViews.put(k, 0);
                }
                boolean allProcessed = false;
                while (!allProcessed && !this.isInterrupted()) {
                allProcessed = true;
                for (int i = 0; i < keys.length && !this.isInterrupted(); i++) {
                    Triple k = this.keys[i];
                    ArrayList<Predicate> rvs = this.buckets.get(k);
                    //System.out.println("bucket "+i+" processedViews: "+processedViews.get(k)+". bucketsize: "+rvs.size());
                    if (processedViews.get(k).equals(rvs.size())) { //finished[i] || (sizeRunNow(i) != 0)) {
                        continue;
                    }
                    int j = processedViews.get(k);
                    Predicate view = rvs.get(j);
                    addRunNow(i, j);
                    Runnable worker = new IncludingStreamV3Worker(this, i, j, view);
                    synchronized(executor) {
                      if(!this.isInterrupted()&&!executor.isShutdown()) {
                        //boolean b = executor.getActiveCount() > 0;
                        //if (b) {
                        //    Thread.sleep(2000);
                        //}
                        executor.execute(worker);
                        j++;
                        //long ts = (long) (rvs.size()-processedViews.get(k));
                        //System.out.println("threads for bucket "+i+" view "+j+" is going to sleep "+ts+" msecs");
                        //if (ts > 1)
                        //    Thread.sleep(ts);
                        //System.out.println("c'est bon");
                        processedViews.put(k, j);
                      }
                    }
                    if (j < rvs.size()) {
                        allProcessed = false;
                    }
                }
                }
                //Thread.sleep(1000);
                /*for (int i = 0; i < keys.length; i++) {
                    if (finished[i] || (sizeRunNow(i) != 0)) {
                        continue;
                    }
                    Triple k = this.keys[i];
                    ArrayList<Predicate> rvs = this.buckets.get(k);
                    for(int j = 0; j<rvs.size(); j++) {
                        Predicate view = rvs.get(j);
                        addRunNow(i, j);
                        Runnable worker = new IncludingStreamV3Worker(this, i, j, view);
                 
                        if(!this.isInterrupted())
                            executor.execute(worker);
                        
                    }

                }*/

                finish = true;
                for (int i = 0; i < keys.length; i++) {
                    //System.out.println("bucket: "+i+" processedViews: "+processedViews.get(this.keys[i])+". finished[i]: "+finished[i]+" this.sizeRunNow(i): "+this.sizeRunNow(i));
                    Triple k = this.keys[i];
                    ArrayList<Predicate> rvs = this.buckets.get(k);
                    if (!(processedViews.get(k)==rvs.size())||!(this.sizeRunNow(i) == 0)) {
                        finish = false;
                        break;
                    }
                }
                Thread.sleep(1);
            }
            //System.out.println("endPool");
        } catch (InterruptedException ie) {
            //System.out.println("View inclusion ended");
            //ie.printStackTrace();
        } finally {
            if(!executor.isShutdown())
                myInterrupt();
            //System.out.println("finish: "+finish);
            //System.out.println("this thread has been interrupted: "+this.isInterrupted());
        }
    }

    public void removeRunNow(int i, int j) {
        synchronized(runNow[i]) {
            Iterator iterator = runNow[i].iterator();
            while (iterator.hasNext()) {
                if(((Integer) iterator.next()).intValue() == j)
                    iterator.remove();
            }
        }

    }

    public int sizeRunNow(int i) {
        synchronized(runNow[i]) {
            return runNow[i].size();
        }
    }

    public void addRunNow(int i, int j) {
        synchronized(runNow[i]) {
            runNow[i].add(Integer.valueOf(j));
        }
    }

    public boolean myInterrupt() {
        synchronized(executor) {
            executor.shutdownNow();
        }
        return executor.isShutdown();
    }
}

