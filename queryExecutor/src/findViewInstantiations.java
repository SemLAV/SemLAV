import java.util.*;
import java.io.*;

import semLAV.*;

class findViewInstantiations {

    public static void main (String args[]) {

        try {

            String nameIn = args[0];
            String nameOut = args[1];
            HashMap<String, String> constants
                               = executionMCDSATThreaded.loadConstants(args[2]);
	        BufferedReader br = new BufferedReader(new FileReader(nameIn));
	        String l = br.readLine();
	        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(nameOut, true), "UTF-8"));
	        HashSet<String> hs = new HashSet<String>();
	        while (l != null) {
                HashSet<String> aux = processLine(l, constants);
		        hs.addAll(aux);
		        l = br.readLine();
	        }
	        for (String e : hs) {
                bw.write(e);
	            bw.newLine();
	        }
	        bw.flush();
	        bw.close();
        } catch (Exception e) {
            e.printStackTrace(System.out);
	    }
    }

    public static HashSet<String> processLine(String l, HashMap<String, String> constants) {

        HashSet<String> res = new HashSet<String>();
        //if (l.startsWith("view")) {
            StringTokenizer st2 = new StringTokenizer(l, "(,)", false);
            String res2 = st2.nextToken();
            boolean include = false;
            while (st2.hasMoreTokens()) {
                String arg = st2.nextToken();
                //char c = arg.charAt(0);
                //if ((Character.isLetter(c) && Character.isLowerCase(c))|| Character.isDigit(c)) {
                if (constants.keySet().contains(arg)) {
                    include = true;
                    break;
                }
            }
            if (include) {
                res.add(l);
            }
        //}
        return res;
    }
}
