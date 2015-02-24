import java.io.*;
import java.util.*;
import semLAV.Predicate;
import semLAV.IgnoringStream;

public class computeCoverageGUN {

    public static void main (String[] args) throws Exception {

        String rewritings = args[0];
        String query = args[1];
        String berlinMappings = args[2];
        String berlinMappingsUsed = args[3];
        String rewriterCommand = args[4];
        String outFile = args[5];
        int j = Integer.parseInt(args[6]);

        ArrayList<Predicate> usedViews = getRVs(rewritings, j);
        //System.out.println("used views: "+usedViews.toString());
        BufferedWriter output = new BufferedWriter(new OutputStreamWriter(
                                new FileOutputStream(outFile), "UTF-8"));
        int k = usedViews.size();
        //int l = (int) (usedViews.size()/10);
        //for (int k = l; k <= usedViews.size(); k=k+l) {
            ArrayList<Predicate> uVs = new ArrayList<Predicate>(usedViews.subList(0, k));
            //System.out.println("for k="+k+" selected views: "+uVs);
            selectUsedViews(uVs, berlinMappings, berlinMappingsUsed);

            Process p = startRewriter(berlinMappingsUsed, query, rewriterCommand);
            InputStream is = p.getInputStream();
            InputStream es = p.getErrorStream();
            Thread terror = new IgnoringStream(es);
            terror.setPriority(Thread.MIN_PRIORITY);
            terror.start();

            InputStreamReader isr = new InputStreamReader(is);
            BufferedReader br = new BufferedReader(isr);
            String line = null;
            Double d = 0.0;
            while ((line=br.readLine())!= null) {
                int i = line.indexOf("models=");
                if (i>=0) {
                    line = line.substring(i+7);
                    d = Double.parseDouble(line.substring(0, line.indexOf(":")).trim());
                    break;
                }
            }
            is.close();
            es.close();
            p.destroy();
            output.write(k+"\t"+d+"\n");
            output.flush();
        //}
        output.flush();
        output.close();
        File f = new File(berlinMappingsUsed);
        //f.delete();
    }

    public static Process startRewriter(String mappingsFile, String queryFile, String rewriterCommand) throws Exception {
        List<String> l = new ArrayList<String>();
        l.add(rewriterCommand);
        l.add("-t");
        l.add("RW");
        l.add("-v");
        l.add(mappingsFile);
        l.add("-q");
        l.add(queryFile);

        ProcessBuilder pb = new ProcessBuilder(l);
        Process p = pb.start();
        return p;
    }

    public static void selectUsedViews(ArrayList<Predicate> usedViews, String inFile, String outFile) throws Exception {

        BufferedReader br = new BufferedReader(new FileReader(inFile));
        BufferedWriter output = new BufferedWriter(new OutputStreamWriter(
                                new FileOutputStream(outFile, false), "UTF-8"));
        String l = br.readLine();

        while (l != null) {
            String viewName = l.substring(0, l.indexOf(":")).trim();
            Predicate p = new Predicate(viewName);
            //System.out.println("testing with: "+p);
            if (usedViews.contains(p)) {
                //System.out.println("including: "+p);
                output.write(l);
                output.newLine();
            }
            l = br.readLine();
        }
        output.flush();
        output.close();
        br.close();
    }

    public static ArrayList<Predicate> getRVs(String fileName, int k) throws Exception {

        ArrayList<Predicate> rvs = new ArrayList<Predicate>();
        BufferedReader br = new BufferedReader(new FileReader(fileName));
        //String rvl = "";
        String l = br.readLine();
        int i = 0;
        while (l != null && i < k) {
            //if (!l.startsWith("#")) {
            //    rvl = l;
            //}
            int pos = l.indexOf(":-");
            l = l.substring(pos+1);
            StringTokenizer st = new StringTokenizer(l, ")");
            //int j = 0;
            while (st.hasMoreTokens()) {
                String tmp = st.nextToken();
                if (tmp.startsWith(",")) {
                    tmp = tmp.substring(1);
                }
                tmp = tmp + ")";
                Predicate p = new Predicate(tmp);
                rvs.add(p);
                //j++;
            }
            l = br.readLine();
            i++;
        }
        br.close();
        return rvs;
    }
}
