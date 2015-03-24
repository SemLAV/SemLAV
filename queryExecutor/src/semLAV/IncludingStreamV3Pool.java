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
    boolean finish = false;
    boolean isReseting = false;
    int nbWorker;

    public IncludingStreamV3Pool(HashMap<Triple, ArrayList<Predicate>> buckets, Model gu, Counter iv, Catalog c,
                                 HashMap<String, String> cs, Timer wrapperTimer,
                                 Timer graphCreationTimer, Timer executionTimer,
                                 Timer totalTimer, BufferedWriter info2, Counter ids, HashSet<Predicate> includedViewsSet,
                                 boolean testing, int nbWoker) {
        this.nbWorker = nbWoker;
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
        executor.shutdownNow();
        executor = Executors.newFixedThreadPool(this.nbWorker);
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
                System.out.println("reset subgoal "+j);
                totalTimer.resume();
            }
        }
        isReseting = false;
    }

    public void run () {

    try {

            while (!finish) {
                while (isReseting) {}
                for (int i = 0; i < keys.length; i++) {
                    if (finished[i] && sizeRunNow(i) == 0) {
                        continue;
                    }
                    Triple k = this.keys[i];
                    ArrayList<Predicate> rvs = this.buckets.get(k);
                    for(int j = 0; j<rvs.size(); j++) {
                        Predicate view = rvs.get(j);
                        addRunNow(i, j);
                        Runnable worker = new IncludingStreamV3Worker(this, i, j, view);
                        executor.execute(worker);
                    }

                }
                if (this.isInterrupted()) {
                    break;
                }
                finish = true;
                for (int i = 0; i < keys.length; i++) {
                    if (!finished[i]) {
                        finish = false;
                        break;
                    }
                }
                Thread.sleep(1);
            }
            executor.shutdown();

            while (!executor.isTerminated()) {}
        } catch (InterruptedException ie) {
            System.out.println("View inclusion ended");
        }
    }

    public void removeRunNow(int i, int j) {
        synchronized(runNow[i]) {
            Iterator iterator = runNow[i].iterator();
            while (iterator.hasNext()) {
                Integer nb = (Integer) iterator.next();
                System.out.println(nb);
                if(nb.intValue() == j)
                    runNow[i].remove(nb);
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
}
