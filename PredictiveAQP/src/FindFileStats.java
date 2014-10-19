
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.lang.System.out;

public class FindFileStats {
	
	public static String[] csvParse(String s) {
		String[] fields = s.substring(1, s.length()-1).split("\",\"");
		return fields;
	}
	
	private static<F> void incrementInMap (Map<F, Integer> counter, F f) {
		if (counter.containsKey(f)) {
			counter.put(f, counter.get(f) + 1);
		} else {
			counter.put(f, 1);
		}
	}
	
	public static void main(String[] args) throws IOException {
		final String FILELOCATION = "C:/Users/manas/Documents/My Box Files/Hector Papers/PredictiveAQP/TestDatasets/Lending Club Statistics/LoanStats3a_securev1.csv";
		final String FILELOCATION2 = "C:/Users/manas/Documents/My Box Files/Hector Papers/PredictiveAQP/TestDatasets/Lending Club Statistics/LoanStats3b_securev1.csv";
		final int target = 19; // Loan Status. The thing we're trying to predict.
		int predictor = 23; // Variable used to predict loan status.
		BufferedReader br = new BufferedReader(new FileReader(FILELOCATION));
		String s;
		s = br.readLine();
		s = br.readLine();
		String[] fields = csvParse(s);
		int numFields = fields.length;
		List<String> fieldNames = new ArrayList<String>();
		for (int i = 0; i < numFields; i++) {
			fieldNames.add(fields[i]);
		}
		
		Map<String, Map<String, Integer>> pairCounts = new HashMap<String, Map<String, Integer>>();
		List<Map<String, Integer>> valCountList = new ArrayList<Map<String, Integer>>();
		List<Set<String>> valSetList = new ArrayList<Set<String>>();
		for (int i=0; i < numFields; i++) {
			valSetList.add(new HashSet<String>());
			valCountList.add(new HashMap<String, Integer>());
		}

		while ((s = br.readLine()) != null) {
			if(s.equals("")) { 	
				break;
			}
			fields = csvParse(s);
			if (fields.length != numFields) {
				break;
			}
			if(fields[target].equals("Current") || fields[target].equals("Issued")) {
				continue;
			} else if (fields[target].equals("Fully Paid")) {
				fields[target] = "good";
			} else {
				fields[target] = "bad";
			}
			
			for (int fieldNo = 0; fieldNo < numFields; fieldNo++) {
				final String field = fields[fieldNo];
				if (valSetList.get(fieldNo).size() <= 9999) {
					valSetList.get(fieldNo).add(field);
				}
			}
			
			incrementInMap(valCountList.get(predictor), fields[predictor]);
			incrementInMap(valCountList.get(target), fields[target]);
			if (!pairCounts.containsKey(fields[predictor])) {
				pairCounts.put(fields[predictor], new HashMap<String, Integer>());
			}
			incrementInMap(pairCounts.get(fields[predictor]), fields[target]);
		}
		br.close();
		br = new BufferedReader(new FileReader(FILELOCATION2));
		s = br.readLine();
		s = br.readLine();
		while ((s = br.readLine()) != null) {
			if(s.equals("")) { 	
				break;
			}
			fields = csvParse(s);
			if (fields.length != numFields) {
				break;
			}
			if(fields[target].equals("Current") || fields[target].equals("Issued")) {
				continue;
			} else if (fields[target].equals("Fully Paid")) {
				fields[target] = "good";
			} else {
				fields[target] = "bad";
			}
			
			for (int fieldNo = 0; fieldNo < numFields; fieldNo++) {
				final String field = fields[fieldNo];
				if (valSetList.get(fieldNo).size() <= 9999) {
					valSetList.get(fieldNo).add(field);
				}
			}
			
			incrementInMap(valCountList.get(predictor), fields[predictor]);
			incrementInMap(valCountList.get(target), fields[target]);
			if (!pairCounts.containsKey(fields[predictor])) {
				pairCounts.put(fields[predictor], new HashMap<String, Integer>());
			}
			incrementInMap(pairCounts.get(fields[predictor]), fields[target]);
		}
		br.close();
		
		for (String predictorValue : pairCounts.keySet()) {
			out.println(predictorValue);
			final Map<String, Integer> targetMap = pairCounts.get(predictorValue);
			for (String targetValue : targetMap.keySet()) {
				out.println(targetValue + " : " + targetMap.get(targetValue).doubleValue()/valCountList.get(predictor).get(predictorValue));
			}
		}
		
		for (int fieldNo = 0; fieldNo < numFields; fieldNo++) {
			// out.println(fieldNames.get(fieldNo) + '\t' + valSetList.get(fieldNo).size());
		}
	}

}
