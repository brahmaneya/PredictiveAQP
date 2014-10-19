package dataextraction;

import static java.lang.System.out;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import solvers.ProbabilisticSolvers;
import utils.Experiments;
import utils.PerformanceAnalysis;

public class ProsperDataExtractor {
	final static String FILELOCATION1 = "C:/Users/manas/Box Sync/Hector Papers/PredictiveAQP/TestDatasets/Prosper Data/ProsperDataExport_xml-200905/ProsperDataExport.xml";
	final static String FILELOCATION2 = "C:/Users/manas/Box Sync/Hector Papers/PredictiveAQP/TestDatasets/Prosper Data/ProsperDataExport_xml/ProsperDataExport.xml";
	public final static String FILELOCATION = "C:/Users/manas/Box Sync/Hector Papers/PredictiveAQP/TestDatasets/Prosper Data/ProsperDataExport_xml/ProsperDataExportLoans.csv";
	public final static String ARFFFILELOCATION = "C:/Users/manas/Box Sync/Hector Papers/PredictiveAQP/TestDatasets/Prosper Data/ProsperDataExport_xml/ProsperDataExportLoans.arff";
	public final static int TARGET = 12;
	final static String[] IGNORETARGETARRAY = {"Cancelled", "Defaulted (PaidInFull)", "missing"};
	final static String[] GOODTARGETARRAY = {"Paid", "Payoff in progress", "Repurchased"};
	final static String[] BADTARGETARRAY = {"1 month late", "2 months late", "3 months late", "4+ months late", 
		"Charge-off", "Defaulted (Bankruptcy)", "Defaulted (Delinquency)", "Late"};
	public final static Set<String> IGNORETARGET = new HashSet<String>(Arrays.asList(IGNORETARGETARRAY));
	public final static Set<String> GOODTARGET = new HashSet<String>(Arrays.asList(GOODTARGETARRAY));
	public final static Set<String> BADTARGET = new HashSet<String>(Arrays.asList(BADTARGETARRAY));
	
	/**
	 * Function to read xml file and create loan csv file. The file has to be post processed by replacing
	 * regexp ^.+Curent.+$ by empty string, and then \n\r\n by \n, and so on (the empty string from earlier is \r\n)
	 */
	public static void writeImprovedFile() throws Exception {
		SAXParserFactory factory = SAXParserFactory.newInstance();
		SAXParser parser = factory.newSAXParser();
		XMLReader xmlReader = parser.getXMLReader();
		final PrintWriter pw = new PrintWriter(new FileWriter(FILELOCATION));
		
		
		ContentHandler contentHandler = new DefaultHandler() {
			Set<String> types; 
			long counter;
			int loanCounter;
			int memberCounter;
			Boolean isLoan;
			String[] loanMembers = {"AgeInMonths", "AmountBorrowed", "BorrowerRate", "CreationDate", "CreditGrade",
					"ProsperRating", "DebtToIncomeRatio", "GroupKey", "Key", "LenderRate", "ListingKey", "OriginationDate", "Status",
					"Term", "ModifiedDate"};
			
			public void startDocument() {
				types = new HashSet<String>();
				counter = 0;
				loanCounter = 0;
				memberCounter = 0;
				isLoan = false;
			}
			
			public void startElement(String uri, String localName,String qName, Attributes attributes) throws SAXException {
				//System.out.println("Start Element :" + qName);
				if (!types.contains(qName)) {
					types.add(qName);
					out.printf("Counter: %d, Type: %s\n", counter, qName);
				}
				if (qName.equals("Loan")) {
					memberCounter = 0;
					isLoan = true;
					loanCounter++;
					if (loanCounter % 10000 == 0) {
						out.printf("LoanCounter: %d\n", loanCounter);
					}
				}
				if (isLoan && !qName.equals("Loan")) {
					while (!qName.equals(loanMembers[memberCounter])) {
						pw.print("\"\",");
						memberCounter++;
					}
					pw.print("\"");
				}
				counter++;
			}
			
			public void endElement(String uri, String localName, String qName) throws SAXException {
				if (qName.equals("Loan")) {
					pw.println();
					isLoan = false;
				}
				if (isLoan) {
					pw.print("\",");
					memberCounter++;
				}
				
				if (counter % 100000000 == 0) {
					out.printf("Counter: %d\n", counter);
				}
			}
			
			public void characters(char ch[], int start, int length) throws SAXException {
				if (isLoan) {
					pw.print(new String(ch, start, length));
				}
			}
		};
		xmlReader.setContentHandler(contentHandler);
		xmlReader.parse(FILELOCATION1);
		out.println("Done with File 1");
		xmlReader.parse(FILELOCATION2);
		out.println("Done with File 2");
		
		pw.close();
	}
	
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

	/**
	 * Creates maps of sizes and selectivities, give a single predictor column.
	 * @param predictor			Column number of variable used to predict loan status.
	 * @param sizes				Map's predictor value to number of tuples with that value.
	 * @param selectivities		Map's predictor value to selectivity for that value.
	 * @throws IOException 
	 */
	public static void extractSizeSelectivity (int predictor, Map<String, Double> sizes, Map<String, Double> selectivities) throws IOException {
		List<Integer> predictors = new ArrayList<Integer>();
		predictors.add(predictor);
		extractSizeSelectivity (predictors, sizes, selectivities);
	}
	

	/**
	 * Creates maps of sizes and selectivities, given a list of predictor columns.
	 * @param predictors		List of column numbers of variables used to predict loan status.
	 * @param sizes				Map's predictor value to number of tuples with that value.
	 * @param selectivities		Map's predictor value to selectivity for that value.
	 * @throws IOException 
	 */
	public static void extractSizeSelectivity (List<Integer> predictors, Map<String, Double> sizes, Map<String, Double> selectivities) throws IOException {
		BufferedReader br = new BufferedReader(new FileReader(FILELOCATION));
		String s;
		s = br.readLine();
		System.out.println(s); //
		String[] fields = csvParse(s);
		int numFields = fields.length;
		List<String> fieldNames = new ArrayList<String>();
		for (int i = 0; i < numFields; i++) {
			fieldNames.add(fields[i]);
		}

		Map<String, Map<String, Integer>> pairCounts = new HashMap<String, Map<String, Integer>>();
		//List<Map<String, Integer>> valCountList = new ArrayList<Map<String, Integer>>();
		Map<String, Integer> predCountList = new HashMap<String, Integer>();

		while ((s = br.readLine()) != null) {
			if(s.equals("")) { 	
				break;
			}
			fields = csvParse(s);
			if (fields.length != numFields) {
				break;
			}
			String loanStatus = "";
			String predictorValue = "";
			for (Integer predictor : predictors) {
				predictorValue = predictorValue + "\"" + fields[predictor] + "\",";
			}
			
			if(IGNORETARGET.contains(fields[TARGET])) {
				continue;
			} else if (GOODTARGET.contains(fields[TARGET])) {
				loanStatus = "good";
			} else if (BADTARGET.contains(fields[TARGET])) {
				loanStatus = "bad";
			} 
			
			incrementInMap(predCountList, predictorValue);
			if (!pairCounts.containsKey(predictorValue)) {
				pairCounts.put(predictorValue, new HashMap<String, Integer>());
				pairCounts.get(predictorValue).put("good", 0);
			}
			incrementInMap(pairCounts.get(predictorValue), loanStatus);
		}
		br.close();
		
		for (String predictorValue : pairCounts.keySet()) {
			sizes.put(predictorValue, predCountList.get(predictorValue).doubleValue());
			selectivities.put(predictorValue, pairCounts.get(predictorValue).get("good")/sizes.get(predictorValue));
		}
	}
	
	public static void getColumnStats (List<String> samples) {
		int numFields;
		{
			String s = samples.get(0);
			String[] fields = csvParse(s);
			numFields = fields.length;
		}
		Map<Integer, Map<String, Integer>> goodTuples = new HashMap<Integer, Map<String, Integer>>();
		Map<Integer, Map<String, Integer>> totalTuples = new HashMap<Integer, Map<String, Integer>>();
		Map<Integer, Map<String, Double>> selectivities = new HashMap<Integer, Map<String, Double>>();
		for (int col = 0; col < numFields; col++) {
			goodTuples.put(col, new HashMap<String, Integer>());
			totalTuples.put(col, new HashMap<String, Integer>());
			selectivities.put(col, new HashMap<String, Double>());
		}

		String[] fields;
		for (String s : samples) {
			fields = csvParse(s);
			if (fields.length != numFields) {
				throw new IllegalArgumentException("Row has wrong number of columns");
				//break;
			}
			String target = "";
			if(IGNORETARGET.contains(fields[TARGET])) {
				continue;
			} else if (GOODTARGET.contains(fields[TARGET])) {
				target = "good";
			} else if (BADTARGET.contains(fields[TARGET])) {
				target = "bad";
			} 
			
			if (target.equals("good")) {
				for (int col = 0; col < numFields; col++) {
					final String predictor =  fields[col];
					incrementInMap(goodTuples.get(col), predictor);
					incrementInMap(totalTuples.get(col), predictor);
				}
			} else {
				for (int col = 0; col < numFields; col++) {
					final String predictor =  fields[col];
					incrementInMap(totalTuples.get(col), predictor);
				}
			}
		}
		for (int col = 0; col < numFields; col++) {
			final Map<String, Integer> totalMap = totalTuples.get(col);
			final Map<String, Integer> goodMap = goodTuples.get(col);
			final Map<String, Double> selectivityMap = selectivities.get(col);
			for (String key : totalMap.keySet()) {
				incrementInMap(totalMap, key);
				incrementInMap(totalMap, key);
				incrementInMap(goodMap, key);
				selectivityMap.put(key, goodMap.get(key) * 1.0 / totalMap.get(key));
			}
			if (col == TARGET) {
				continue;
			}
			
			Double sum = 0.0;
			Double sqsum = 0.0;
			Double total = 0.0;
			Double centropy = 0.0;
			Double ientropy = 0.0;
			
			out.println(col);
			for (String key : selectivityMap.keySet()) {
				//out.println(totalMap.get(key) + ", " + selectivityMap.get(key));
				total += totalMap.get(key);
				sum += totalMap.get(key) * selectivityMap.get(key);
				sqsum += totalMap.get(key) * selectivityMap.get(key) * selectivityMap.get(key);
				centropy += totalMap.get(key) * selectivityMap.get(key) * (-Math.log(selectivityMap.get(key)));
				centropy += totalMap.get(key) * (1 - selectivityMap.get(key)) * (-Math.log(1 - selectivityMap.get(key)));
			}
			for (String key : selectivityMap.keySet()) {
				final Double fraction = totalMap.get(key) / total;
				ientropy += fraction * (-Math.log(fraction)) + (1 - fraction) * (-Math.log(1 - fraction));
			}
			out.printf("NumKeys : %d\nEntropy : %f\nVariance : %f", totalMap.keySet().size(), ientropy + centropy / total, sqsum / total - (sum * sum) / (total * total));
			out.println("\n");
			//out.println(col + "\n" + selectivities.get(col).toString() + "\n");
		}
	}
	
	public static void extractSizeSelectivity (List<String> samples, List<Integer> predictors, Map<String, Double> sizes, Map<String, Double> selectivities) {
		String[] fields = csvParse(samples.get(0));
		int numFields = fields.length;
		Map<String, Map<String, Integer>> pairCounts = new HashMap<String, Map<String, Integer>>();
		//List<Map<String, Integer>> valCountList = new ArrayList<Map<String, Integer>>();
		Map<String, Integer> predCountList = new HashMap<String, Integer>();

		for (String s : samples) {
			if(s.equals("")) { 	
				break;
			}
			fields = csvParse(s);
			if (fields.length != numFields) {
				break;
			}
			String loanStatus = "";
			String predictorValue = "";
			for (Integer predictor : predictors) {
				predictorValue = predictorValue + "\"" + fields[predictor] + "\",";
			}
			
			if(IGNORETARGET.contains(fields[TARGET])) {
				continue;
			} else if (GOODTARGET.contains(fields[TARGET])) {
				loanStatus = "good";
			} else if (BADTARGET.contains(fields[TARGET])) {
				loanStatus = "bad";
			} 
			
			incrementInMap(predCountList, predictorValue);
			if (!pairCounts.containsKey(predictorValue)) {
				pairCounts.put(predictorValue, new HashMap<String, Integer>());
				pairCounts.get(predictorValue).put("good", 0);
			}
			incrementInMap(pairCounts.get(predictorValue), loanStatus);
		}
		
		for (String predictorValue : pairCounts.keySet()) {
			sizes.put(predictorValue, predCountList.get(predictorValue).doubleValue());
			selectivities.put(predictorValue, (0 + pairCounts.get(predictorValue).get("good"))/(0 + sizes.get(predictorValue)));
		}
	}
	
	/**
	 * Takes a sample of labelled tuples, uses them to estimates selectivities for all columns, and runs sizesKnown algo to get cost on using
	 * that column as correlated column. Chooses min cost column among those columns that have < valThreshold distinct values (
	 */
	public static Integer getBestColumn (List<String> samples, Integer valThreshold) {
		Integer bestColumn = -1;
		Double minCost = Double.MAX_VALUE;
		for (int i = 0; i < 90; i++) {
			if (i == TARGET) {
				continue;
			}
			Map<String, Double> sizes = new HashMap<String, Double>();
			Map<String, Double> selectivities = new HashMap<String, Double>();
			Map<String, Double> retrieve = new HashMap<String, Double>();
			Map<String, Double> evaluate = new HashMap<String, Double>();
			List<Integer> predictors = new ArrayList<Integer>();
			predictors.add(i);
			try{
				extractSizeSelectivity(samples, predictors, sizes, selectivities);
			} catch (Exception e)  {
				break;
			}
			if (sizes.size() > 20) {
				continue;
			}
			//out.println(sizes.size());
			//out.println(sizes.toString());
			//out.println(selectivities.toString());
			Double retrieveCost = 1.0;
			Double evaluateCost = 3.0;
			Double alpha = 0.8;
			Double beta =  0.8; 
			Double rho = 0.8; 
			ProbabilisticSolvers.sizesKnownEvaluate(sizes, selectivities, alpha, beta, rho, retrieve, evaluate);
			Double numEvaluated = PerformanceAnalysis.evaluateCost(sizes, evaluate);
			if (numEvaluated < minCost) {
				minCost = numEvaluated;
				bestColumn = i;
			}
		}
		return bestColumn;
	}
	
	public static void main(String[] args) throws Exception {
		List<String> samples = Sampling.getSamples(0.01, FILELOCATION);
		out.println(getBestColumn(samples, 10));
		if (1!=2) return;
		
		int predictor = 4; // Good values: 4 (grade) 
		List<Integer> predictors = new ArrayList<Integer>();
		predictors.add(predictor);
		predictors.add(13);
		Map<String, Double> sizes = new HashMap<String, Double>();
		Map<String, Double> selectivities = new HashMap<String, Double>();
		extractSizeSelectivity(predictors, sizes, selectivities);
		out.println(sizes.toString());
		out.println(selectivities.toString());
		
		Double retrieveCost = 1.0;
		Double evaluateCost = 3.0;
		Double alpha = 0.8;
		Double beta =  0.8; 
		Double rho = 0.8; 
		
		Map<String, Integer> positive = new HashMap<String, Integer>();
		Map<String, Integer> negative = new HashMap<String, Integer>();
		for (String key : sizes.keySet()) {
			positive.put(key, (int) Math.round(sizes.get(key) * selectivities.get(key)));
			negative.put(key, (int) Math.round(sizes.get(key) * (1 - selectivities.get(key))));
		}
		
		//Experiments.samplingAccuracy(sizes, selectivities, alpha, beta, rho, retrieveCost, evaluateCost);
		
		//Experiments.samplingCost(sizes, selectivities, alpha, beta, rho, retrieveCost, evaluateCost, predictors, "constant");
		//Experiments.samplingCost(sizes, selectivities, alpha, beta, rho, retrieveCost, evaluateCost, predictors, "two-third-power");
		Experiments.performanceComparison(sizes, selectivities, alpha, beta, rho, retrieveCost, evaluateCost);
		if(!sizes.isEmpty()) return;
		

		Experiments.performanceComparison(sizes, selectivities, alpha, beta, rho, retrieveCost, evaluateCost);
		Experiments.samplingAccuracy(sizes, selectivities, alpha, beta, rho, retrieveCost, evaluateCost);
		
		/*
		 * Repeat samplingCost for several predictors. Note: To repeat, need to call extractSizeSelectivity again, not 
		 * just change the predictors parameter in call below. 
		 */
		Experiments.samplingCost(sizes, selectivities, alpha, beta, rho, retrieveCost, evaluateCost, predictors, "constant");
		Experiments.samplingCost(sizes, selectivities, alpha, beta, rho, retrieveCost, evaluateCost, predictors, "two-third-power");
		// Commented out below because results are not singificantly better than for non-adaptive
				//Experiments.adaptiveSampling(sizes, selectivities, alpha, beta, rho, retrieveCost, evaluateCost, predictors);
		Experiments.logisticRegression(FILELOCATION, TARGET, sizes, selectivities, alpha, beta, rho, retrieveCost, evaluateCost);
		
		Experiments.constraintsCost(sizes, selectivities, alpha, beta, rho, retrieveCost, evaluateCost);
	}
}
