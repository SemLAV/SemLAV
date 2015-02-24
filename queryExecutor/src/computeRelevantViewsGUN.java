import java.io.*;
import java.util.*;
import semLAV.Predicate;

public class computeRelevantViewsGUN {

    public static void main (String[] args) throws Exception {

        String rewritings = args[0];
        String outFile = args[1];
        int j = Integer.parseInt(args[2]);

        ArrayList<Predicate> usedViews = computeCoverageGUN.getRVs(rewritings, j);
        HashSet<Predicate> hs = new HashSet<Predicate>();
        for (Predicate p : usedViews) {
            boolean alreadyIn = false;
            for (Predicate p1 : hs) {
                if (p1.equals(p)) {
                    alreadyIn = true;
                }
            }
            if (!alreadyIn) {
                hs.add(p);
            }
        }
        BufferedWriter output = new BufferedWriter(new OutputStreamWriter(
                                new FileOutputStream(outFile), "UTF-8"));
        int k = hs.size();

        output.write(k+"\n");
        for (int i = 1; i <= 15; i++) {
            int nv = 0;
            for (Predicate p1 : hs) {
                if (p1.getName().startsWith("view"+i+"_")) {
                    nv++;
                }
            }
            output.write(nv+"\t");
        }
        output.write("\n");
        for (Predicate p : hs) {

            output.write(p+"\n");
        }
        output.flush();
        output.close();
/*
        String s1 = "view12_15(VendorURI,Vendorname,_0,_1,_2,_3,_4)";
        String s2 = "view12_15(_0,_1,_2,VendorURI,Vendorname,_3,_4)";
        Predicate p1 = new Predicate(s1);
        Predicate p2 = new Predicate(s2);
        System.out.println(p1.equals(p2));
        HashSet<Predicate> hs1 = new HashSet<Predicate>();
        hs1.add(p1);
        System.out.println(hs1.contains(p1));
        System.out.println(hs1.contains(p2));
        if (!hs1.contains(p2)) {
            hs1.add(p2);
        }
        System.out.println(hs1.size());*/
    }
}
