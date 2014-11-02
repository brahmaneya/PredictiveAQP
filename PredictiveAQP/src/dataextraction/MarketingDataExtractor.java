package dataextraction;

import static java.lang.System.out;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import solvers.ProbabilisticSolvers;
import utils.Experiments;
import utils.PerformanceAnalysis;

public class MarketingDataExtractor {
	public final static String FILELOCATION = "C:/Users/manas/Box Sync/Hector Papers/PredictiveAQP/TestDatasets/Marketing Data/bank-additional-full.csv";
	public final static String ARFFFILELOCATION = "C:/Users/manas/Box Sync/Hector Papers/PredictiveAQP/TestDatasets/Marketing Data/bank-additional-full.arff";
	public final static int TARGET = 20;
	public final static String[] IGNORETARGETARRAY = {""};
	public final static String[] GOODTARGETARRAY = {"yes"};
	public final static String[] BADTARGETARRAY = {"no"};
	public final static Set<String> IGNORETARGET = new HashSet<String>(Arrays.asList(IGNORETARGETARRAY));
	public final static Set<String> GOODTARGET = new HashSet<String>(Arrays.asList(GOODTARGETARRAY));
	public final static Set<String> BADTARGET = new HashSet<String>(Arrays.asList(BADTARGETARRAY));
	
	/*
	 * The original downloaded file had ';' as a separator. Replaced that with ','
	 */
	public static String[] csvParse(String s) {
		String[] fields = s.split(",");
		for (int i = 0; i < fields.length; i++) {
			if (fields[i].startsWith("\"")) {
				fields[i] = fields[i].substring(1, fields[i].length() - 1);
			}
		}
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
			//if(s.equals("")) { 	
			//	break;
			//}
			fields = csvParse(s);
			if (fields.length != numFields) {
				throw new IllegalArgumentException("Row has wrong number of columns");
				//break;
			}
			String target = "";
			String predictorValue = "";
			for (Integer predictor : predictors) {
				predictorValue = predictorValue + "\"" + fields[predictor] + "\",";
			}
			
			if(IGNORETARGET.contains(fields[TARGET])) {
				continue;
			} else if (GOODTARGET.contains(fields[TARGET])) {
				target = "good";
			} else if (BADTARGET.contains(fields[TARGET])) {
				target = "bad";
			} 
			
			incrementInMap(predCountList, predictorValue);
			if (!pairCounts.containsKey(predictorValue)) {
				pairCounts.put(predictorValue, new HashMap<String, Integer>());
				pairCounts.get(predictorValue).put("good", 0);
			}
			incrementInMap(pairCounts.get(predictorValue), target);
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
	
	public static void extractAllSizeSelectivities (List<String> samples, List<Map<String, Double>> sizesList, List<Map<String, Double>> selectivitiesList) {
		String[] fields = csvParse(samples.get(0));
		int numFields = fields.length;
		List<Map<String, Map<String, Integer>>> pairCountsList = new ArrayList<Map<String, Map<String, Integer>>>();
		List<Map<String, Integer>> predCountListList = new ArrayList<Map<String, Integer>>();
		for (int col = 0; col < numFields; col++) {
			pairCountsList.add(new HashMap<String, Map<String, Integer>>());
			predCountListList.add(new HashMap<String, Integer>());
		}
		for (String s : samples) {
			if(s.equals("")) { 	
				break;
			}
			fields = csvParse(s);
			if (fields.length != numFields) {
				break;
			}
			String loanStatus = "";
			if(IGNORETARGET.contains(fields[TARGET])) {
				continue;
			} else if (GOODTARGET.contains(fields[TARGET])) {
				loanStatus = "good";
			} else if (BADTARGET.contains(fields[TARGET])) {
				loanStatus = "bad";
			} 
			for (int col = 0; col < numFields; col++) {
				final Map<String, Map<String, Integer>> pairCounts = pairCountsList.get(col);
				final Map<String, Integer> predCountList = predCountListList.get(col);
				final String predictorValue = fields[col];
				incrementInMap(predCountList, predictorValue);
				if (!pairCounts.containsKey(predictorValue)) {
					pairCounts.put(predictorValue, new HashMap<String, Integer>());
					pairCounts.get(predictorValue).put("good", 0);
				}
				incrementInMap(pairCounts.get(predictorValue), loanStatus);
			}
		}
		
		for (int col = 0; col < numFields; col++) {
			final Map<String, Double> sizes = new HashMap<String, Double>();
			sizesList.add(sizes);
			final Map<String, Double> selectivities = new HashMap<String, Double>();
			selectivitiesList.add(selectivities);
			final Map<String, Map<String, Integer>> pairCounts = pairCountsList.get(col);
			final Map<String, Integer> predCountList = predCountListList.get(col);
			for (String predictorValue : pairCounts.keySet()) {
				sizes.put(predictorValue, predCountList.get(predictorValue).doubleValue());
				selectivities.put(predictorValue, (0 + pairCounts.get(predictorValue).get("good"))/(0 + sizes.get(predictorValue)));
			}			
		}
	}
	
	/**
	 * Takes a sample of labelled tuples, uses them to estimates selectivities for all columns, and runs sizesKnown algo to get cost on using
	 * that column as correlated column. Chooses min cost column among those columns that have < valThreshold distinct values (
	 */
	public static Integer getBestColumn (List<String> samples, Integer valThreshold) {
		List<Map<String, Double>> sizesList = new ArrayList<Map<String, Double>>();
		List<Map<String, Double>> selectivitiesList = new ArrayList<Map<String, Double>>();
		extractAllSizeSelectivities (samples, sizesList, selectivitiesList);
		Integer bestColumn = -1;
		Double minCost = Double.MAX_VALUE;
		final Integer numCols = sizesList.size();
		for (int i = 0; i < numCols; i++) {
			if (i == TARGET) {
				continue;
			}
			final Map<String, Double> sizes = sizesList.get(i);
			final Map<String, Double> selectivities = selectivitiesList.get(i);
			if (sizes.size() > valThreshold || sizes.size() < 2) {
				continue;
			}
			Map<String, Double> retrieve = new HashMap<String, Double>();
			Map<String, Double> evaluate = new HashMap<String, Double>();
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
		List<String> samples = Sampling.getSamples(1.0, FILELOCATION);
		Long timer = System.currentTimeMillis();
		out.println(getBestColumn(samples, 10));
		out.println("Time : " + (System.currentTimeMillis() - timer));
		
		int predictor = 15; // best predictor 19
		List<Integer> predictors = new ArrayList<Integer>();
		//predictors.add(19);
		predictors.add(predictor);
		Map<String, Double> sizes = new HashMap<String, Double>();
		Map<String, Double> selectivities = new HashMap<String, Double>();
		extractSizeSelectivity(predictors, sizes, selectivities);
		
		Double retrieveCost = 1.0;
		Double evaluateCost = 3.0;
		Double alpha = 0.8;
		Double beta =  0.8; 
		Double rho = 0.8; 
		
		//Experiments.samplingCost(sizes, selectivities, alpha, beta, rho, retrieveCost, evaluateCost, predictors, "constant");
		//Experiments.samplingCost(sizes, selectivities, alpha, beta, rho, retrieveCost, evaluateCost, predictors, "two-third-power");
		//Experiments.logisticRegression(ARFFFILELOCATION, TARGET, sizes, selectivities, alpha, beta, rho, retrieveCost, evaluateCost);
		//Experiments.samplingAccuracy(sizes, selectivities, alpha, beta, rho, retrieveCost, evaluateCost);
		
		/*for (int i = 12; i < 20; i ++) {
			sizes = new HashMap<String, Double>();
			selectivities = new HashMap<String, Double>();
			extractSizeSelectivity(i, sizes, selectivities);
			if (sizes.keySet().size() > 20) {
				continue;
			}
			out.println(i);
			Experiments.samplingCost(sizes, selectivities, alpha, beta, rho, retrieveCost, evaluateCost, new ArrayList<Integer>(), "two-third-power");
			//out.println(sizes.keySet().size());
			//out.println(sizes.toString());
			//out.println(selectivities.toString());
			//out.println();
		}*/
		Experiments.performanceComparison(sizes, selectivities, alpha, beta, rho, retrieveCost, evaluateCost);
		
		if (1!=2) return;
		Map<String, Integer> positive = new HashMap<String, Integer>();
		Map<String, Integer> negative = new HashMap<String, Integer>();
		for (String key : sizes.keySet()) {
			positive.put(key, (int) Math.round(sizes.get(key) * selectivities.get(key)));
			negative.put(key, (int) Math.round(sizes.get(key) * (1 - selectivities.get(key))));
		}
		//Experiments.performanceComparison(sizes, selectivities, alpha, beta, rho, retrieveCost, evaluateCost);
		
	}
}
